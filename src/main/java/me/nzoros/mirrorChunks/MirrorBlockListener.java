package me.nzoros.mirrorChunks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import me.nzoros.mirrorChunks.core.MirrorOperation;
import me.nzoros.mirrorChunks.core.MirrorSettings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Mirrors player actions without force-loading chunks. */
final class MirrorBlockListener implements Listener {
    private static final String CHANGES_PATH = "mirrored-changes";
    /*
     * Every copied block queues itself and its six neighbours. A limit of four
     * meant that, with many loaded chunks, a water source could stay still for
     * minutes before its normal Minecraft fluid tick was started.
     */
    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST
    };

    private final JavaPlugin plugin;
    private final PaperConfigManager configManager;
    private final Map<UUID, Map<LocalPosition, MirroredChange>> changesByWorld = new HashMap<>();
    private final Queue<PhysicsUpdate> pendingPhysicsUpdates = new ArrayDeque<>();
    private final Set<PhysicsUpdate> queuedPhysicsUpdates = new HashSet<>();

    MirrorBlockListener(JavaPlugin plugin, PaperConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        loadChanges();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::processPhysicsUpdates, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        MirrorSettings settings = configManager.settings();
        if (!settings.blockBreakEnabled()) {
            return;
        }
        Block source = event.getBlock();
        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.breakBlock());
        forEachTarget(source, settings, target -> setTypeAndQueuePhysics(target, Material.AIR));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        MirrorSettings settings = configManager.settings();
        if (!settings.blockPlaceEnabled()) {
            return;
        }
        Block source = event.getBlockPlaced();
        // Hoe use can be reported as a block placement by some Paper versions.
        // It must be handled as a TILL action below, never as a "place farmland
        // into every air block" action.
        if (source.getType() == Material.FARMLAND) {
            return;
        }
        BlockData blockData = source.getBlockData();
        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.place(blockData.getAsString()));
        forEachTarget(source, settings, target -> placeBlock(target, blockData, settings.replaceExistingBlocks()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block clicked = event.getClickedBlock();
        MirrorSettings settings = configManager.settings();
        if (settings.powerStateEnabled() && clicked.getBlockData() instanceof Powerable) {
            plugin.getServer().getScheduler().runTask(plugin, () -> mirrorPowerState(clicked, settings));
        }
        if (settings.tillingEnabled() && event.getItem() != null && event.getItem().getType().name().endsWith("_HOE")) {
            Material originalType = clicked.getType();
            plugin.getServer().getScheduler().runTask(plugin, () -> mirrorTilling(clicked, originalType, settings));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        MirrorSettings settings = configManager.settings();
        if (!settings.fluidsEnabled()) {
            return;
        }
        Block fluidSource = event.getBlockClicked().getRelative(event.getBlockFace());
        plugin.getServer().getScheduler().runTask(plugin, () -> mirrorFluid(fluidSource, settings));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnEgg(CreatureSpawnEvent event) {
        MirrorSettings settings = configManager.settings();
        if (!settings.spawnEggsEnabled()) {
            return;
        }
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        Location sourceLocation = event.getLocation();
        Block source = sourceLocation.getBlock();
        forEachTarget(source, settings, target -> {
            Location targetLocation = new Location(target.getWorld(), target.getX() + 0.5, sourceLocation.getY(), target.getZ() + 0.5);
            target.getWorld().spawnEntity(targetLocation, event.getEntity().getType());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        applySavedChanges(event.getChunk(), configManager.settings());
    }

    private void mirrorPowerState(Block source, MirrorSettings settings) {
        BlockData blockData = source.getBlockData();
        if (!(blockData instanceof Powerable)) {
            return;
        }

        forEachTarget(source, settings, target -> {
            if (target.getType() == source.getType() && target.getBlockData() instanceof Powerable) {
                target.setBlockData(blockData, false);
                queuePhysicsUpdatesAround(target);
            }
        });
    }

    private void mirrorTilling(Block source, Material originalType, MirrorSettings settings) {
        // The interact event runs before the hoe changes the block. Checking
        // both states avoids treating a click on existing farmland as a new
        // tilling action.
        if (!isTillableMaterial(originalType) || source.getType() != Material.FARMLAND) {
            return;
        }

        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.till());
        forEachTarget(source, settings, target -> {
            if (canTillToFarmland(target)) {
                setTypeAndQueuePhysics(target, Material.FARMLAND);
            }
        });
    }

    private void mirrorFluid(Block source, MirrorSettings settings) {
        if (source.getType() != Material.WATER && source.getType() != Material.LAVA) {
            return;
        }

        BlockData blockData = source.getBlockData();
        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.place(blockData.getAsString()));
        forEachTarget(source, settings, target -> placeBlock(target, blockData, settings.replaceExistingBlocks()));
    }

    private void applySavedChanges(Chunk chunk, MirrorSettings settings) {
        World world = chunk.getWorld();
        Map<LocalPosition, MirroredChange> changes = changesByWorld.get(world.getUID());
        if (changes == null) {
            return;
        }

        for (Map.Entry<LocalPosition, MirroredChange> entry : changes.entrySet()) {
            LocalPosition position = entry.getKey();
            if (position.y() >= world.getMinHeight() && position.y() < world.getMaxHeight()) {
                applyChange(chunk.getBlock(position.x(), position.y(), position.z()), entry.getValue(), settings);
            }
        }
    }

    private void applyChange(Block target, MirroredChange change, MirrorSettings settings) {
        // Old versions could save a hoe action as "place farmland". Do not
        // replay that malformed rule into air, water, or the sky.
        if (change.isFarmlandPlacement()) {
            return;
        }
        if (change.kind() == ChangeKind.BREAK && settings.blockBreakEnabled()) {
            setTypeAndQueuePhysics(target, Material.AIR);
        } else if (change.kind() == ChangeKind.TILL && settings.tillingEnabled()) {
            if (canTillToFarmland(target)) {
                setTypeAndQueuePhysics(target, Material.FARMLAND);
            }
        } else if (change.kind() == ChangeKind.PLACE && settings.blockPlaceEnabled()) {
            placeBlock(target, Bukkit.createBlockData(change.blockData()), settings.replaceExistingBlocks());
        }
    }

    private void placeBlock(Block target, BlockData blockData, boolean replaceBlocks) {
        if (replaceBlocks && target.getState(false) instanceof TileState) {
            debug("Skipped block entity at " + target.getX() + "," + target.getY() + "," + target.getZ());
            return;
        }
        if (replaceBlocks || target.getType().isAir()) {
            // Applying physics here can synchronously load neighbouring chunks.
            // During ChunkLoadEvent that recursively invokes saved-change replay.
            // The throttled queue below only touches chunks already reported loaded.
            target.setBlockData(blockData, false);
            queuePhysicsUpdatesAround(target);
        }
    }

    /**
     * Physics is delayed and throttled. Calling it for all chunks in a single
     * player event can cause Paper to synchronously wait for chunk generation.
     */
    private void processPhysicsUpdates() {
        int limit = configManager.settings().maxPhysicsUpdatesPerTick();
        int processed = 0;
        for (; processed < limit && !pendingPhysicsUpdates.isEmpty(); processed++) {
            PhysicsUpdate update = pendingPhysicsUpdates.remove();
            queuedPhysicsUpdates.remove(update);
            if (!update.world().isChunkLoaded(update.chunkX(), update.chunkZ())) {
                continue;
            }

            Block block = update.world().getBlockAt(update.x(), update.y(), update.z());
            BlockData blockData = block.getBlockData();
            block.setBlockData(blockData, true);
        }
        if (processed > 0) {
            debug("Physics queue size: " + pendingPhysicsUpdates.size());
        }
    }

    private void setTypeAndQueuePhysics(Block target, Material material) {
        target.setType(material, false);
        queuePhysicsUpdatesAround(target);
    }

    /** Mirrors the vanilla requirement that the block above the soil is air. */
    private boolean canTillToFarmland(Block block) {
        return isTillableMaterial(block.getType())
            && block.getRelative(BlockFace.UP).getType().isAir();
    }

    private boolean isTillableMaterial(Material material) {
        return material == Material.DIRT
            || material == Material.GRASS_BLOCK
            || material == Material.DIRT_PATH;
    }

    /**
     * Block placement and removal affect more than the changed block itself.
     * Refreshing adjacent blocks lets Minecraft recalculate redstone power,
     * TNT priming, gravity blocks, fluids, and other neighbour-dependent logic.
     */
    private void queuePhysicsUpdatesAround(Block block) {
        queuePhysicsUpdate(block);
        for (BlockFace face : ADJACENT_FACES) {
            queuePhysicsUpdate(block.getRelative(face));
        }
    }

    private void queuePhysicsUpdate(Block block) {
        PhysicsUpdate update = new PhysicsUpdate(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (queuedPhysicsUpdates.add(update)) {
            pendingPhysicsUpdates.add(update);
        }
    }

    /**
     * Visits equivalent positions only in loaded chunks, expanding from the
     * source in chunk-sized rings. Each ring after the first runs one tick
     * later, giving the mirror effect a visible, gradual propagation.
     */
    private void forEachTarget(Block source, MirrorSettings settings, BlockAction action) {
        int localX = source.getX() & 15;
        int localZ = source.getZ() & 15;
        int y = source.getY();
        World sourceWorld = source.getWorld();
        int sourceChunkX = source.getChunk().getX();
        int sourceChunkZ = source.getChunk().getZ();

        if (y < sourceWorld.getMinHeight() || y >= sourceWorld.getMaxHeight()) {
            debug("Ignored out-of-range mirror operation.");
            return;
        }
        NavigableMap<Integer, List<Block>> targetsByDistance = new TreeMap<>();
        for (Chunk chunk : sourceWorld.getLoadedChunks()) {
            if (chunk.getX() == sourceChunkX && chunk.getZ() == sourceChunkZ) {
                continue;
            }
            int distance = Math.max(
                Math.abs(chunk.getX() - sourceChunkX),
                Math.abs(chunk.getZ() - sourceChunkZ)
            );
            targetsByDistance
                .computeIfAbsent(distance, ignored -> new ArrayList<>())
                .add(chunk.getBlock(localX, y, localZ));
        }

        if (targetsByDistance.isEmpty()) {
            debug("No loaded target chunks for source chunk " + sourceChunkX + "," + sourceChunkZ);
            return;
        }

        MirrorOperation operation = new MirrorOperation(settings, sourceChunkX, sourceChunkZ, localX, y, localZ);
        int targetCount = targetsByDistance.values().stream().mapToInt(List::size).sum();
        debug("Source chunk " + sourceChunkX + "," + sourceChunkZ + " local " + localX + "," + y + "," + localZ
            + ", targets: " + targetCount + ", rings: " + targetsByDistance.size());

        applyTargetRing(targetsByDistance.pollFirstEntry().getValue(), action, operation, 1);
        if (!targetsByDistance.isEmpty()) {
            spreadTargetRings(new ArrayDeque<>(targetsByDistance.values()), action, operation);
        }
    }

    private void spreadTargetRings(Queue<List<Block>> targetRings, BlockAction action, MirrorOperation operation) {
        new BukkitRunnable() {
            private int ring = 2;
            @Override
            public void run() {
                List<Block> ring = targetRings.poll();
                if (ring == null) {
                    cancel();
                    return;
                }
                applyTargetRing(ring, action, operation, this.ring++);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void applyTargetRing(List<Block> targets, BlockAction action, MirrorOperation operation, int ring) {
        debug("Applying ring " + ring + " to " + targets.size() + " chunks from "
            + operation.sourceChunkX() + "," + operation.sourceChunkZ());
        for (Block target : targets) {
            if (target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) {
                action.apply(target);
            }
        }
    }

    private void debug(String message) {
        if (configManager.settings().debugEnabled()) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    private Map<LocalPosition, MirroredChange> changesFor(World world) {
        return changesByWorld.computeIfAbsent(world.getUID(), ignored -> new HashMap<>());
    }

    private void loadChanges() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CHANGES_PATH);
        if (section == null) {
            return;
        }
        for (String worldKey : section.getKeys(false)) {
            UUID worldId;
            try {
                worldId = UUID.fromString(worldKey);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring changes for invalid world UUID: " + worldKey);
                continue;
            }
            ConfigurationSection worldSection = section.getConfigurationSection(worldKey);
            if (worldSection == null) {
                continue;
            }
            Map<LocalPosition, MirroredChange> changes = new HashMap<>();
            for (String key : worldSection.getKeys(false)) {
                LocalPosition position = LocalPosition.parse(key);
                String value = worldSection.getString(key);
                MirroredChange change = value == null ? null : MirroredChange.fromStorage(value);
                if (position != null && change != null && !change.isFarmlandPlacement()) {
                    changes.put(position, change);
                } else if (change != null && change.isFarmlandPlacement()) {
                    plugin.getLogger().warning("Discarding old invalid farmland placement rule: " + worldKey + "/" + key);
                } else {
                    plugin.getLogger().warning("Ignoring invalid saved mirror change: " + worldKey + "/" + key);
                }
            }
            if (!changes.isEmpty()) {
                changesByWorld.put(worldId, changes);
            }
        }
    }

    void saveChanges() {
        plugin.getConfig().set(CHANGES_PATH, null);
        for (Map.Entry<UUID, Map<LocalPosition, MirroredChange>> worldEntry : changesByWorld.entrySet()) {
            String worldPath = CHANGES_PATH + "." + worldEntry.getKey();
            for (Map.Entry<LocalPosition, MirroredChange> entry : worldEntry.getValue().entrySet()) {
                plugin.getConfig().set(worldPath + "." + entry.getKey().storageKey(), entry.getValue().toStorage());
            }
        }
        plugin.saveConfig();
    }

    private record LocalPosition(int x, int y, int z) {
        static LocalPosition from(Block block) {
            return new LocalPosition(block.getX() & 15, block.getY(), block.getZ() & 15);
        }

        static LocalPosition parse(String key) {
            String[] parts = key.split("_");
            if (parts.length != 3) {
                return null;
            }
            try {
                return new LocalPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        String storageKey() {
            return x + "_" + y + "_" + z;
        }
    }

    private enum ChangeKind {
        BREAK, PLACE, TILL
    }

    private record MirroredChange(ChangeKind kind, String blockData) {
        private static final String BREAK = "break";
        private static final String PLACE_PREFIX = "place:";
        private static final String TILL = "till";

        static MirroredChange breakBlock() {
            return new MirroredChange(ChangeKind.BREAK, null);
        }

        static MirroredChange place(String blockData) {
            return new MirroredChange(ChangeKind.PLACE, blockData);
        }

        static MirroredChange till() {
            return new MirroredChange(ChangeKind.TILL, null);
        }

        static MirroredChange fromStorage(String value) {
            if (BREAK.equals(value)) {
                return breakBlock();
            }
            if (TILL.equals(value)) {
                return till();
            }
            return value.startsWith(PLACE_PREFIX) ? place(value.substring(PLACE_PREFIX.length())) : null;
        }

        String toStorage() {
            return switch (kind) {
                case BREAK -> BREAK;
                case PLACE -> PLACE_PREFIX + blockData;
                case TILL -> TILL;
            };
        }

        boolean isFarmlandPlacement() {
            return kind == ChangeKind.PLACE && blockData.startsWith("minecraft:farmland");
        }
    }

    @FunctionalInterface
    private interface BlockAction {
        void apply(Block block);
    }

    private record PhysicsUpdate(World world, int x, int y, int z) {
        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }
}
