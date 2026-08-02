package me.nzoros.mirrorChunks;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

/** Mirrors player actions without force-loading chunks. */
final class MirrorBlockListener implements Listener {
    private static final String CHANGES_PATH = "mirrored-changes";
    private static final int PHYSICS_UPDATES_PER_TICK = 4;

    private final JavaPlugin plugin;
    private final Map<UUID, Map<LocalPosition, MirroredChange>> changesByWorld = new HashMap<>();
    private final Queue<PhysicsUpdate> pendingPhysicsUpdates = new ArrayDeque<>();
    private final Set<PhysicsUpdate> queuedPhysicsUpdates = new HashSet<>();

    MirrorBlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
        loadChanges();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::processPhysicsUpdates, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block source = event.getBlock();
        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.breakBlock());
        forEachTarget(source, target -> setTypeAndQueuePhysics(target, Material.AIR));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block source = event.getBlockPlaced();
        BlockData blockData = source.getBlockData();
        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.place(blockData.getAsString()));
        forEachTarget(source, target -> placeIfAir(target, blockData));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked.getBlockData() instanceof Powerable) {
            plugin.getServer().getScheduler().runTask(plugin, () -> mirrorPowerState(clicked));
        }
        if (event.getItem() != null && event.getItem().getType().name().endsWith("_HOE")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> mirrorTilling(clicked));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block fluidSource = event.getBlockClicked().getRelative(event.getBlockFace());
        plugin.getServer().getScheduler().runTask(plugin, () -> mirrorFluid(fluidSource));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnEgg(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        Location sourceLocation = event.getLocation();
        Block source = sourceLocation.getBlock();
        forEachTarget(source, target -> {
            Location targetLocation = new Location(target.getWorld(), target.getX() + 0.5, sourceLocation.getY(), target.getZ() + 0.5);
            target.getWorld().spawnEntity(targetLocation, event.getEntity().getType());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        applySavedChanges(event.getChunk());
    }

    private void mirrorPowerState(Block source) {
        BlockData blockData = source.getBlockData();
        if (!(blockData instanceof Powerable)) {
            return;
        }

        forEachTarget(source, target -> {
            if (target.getType() == source.getType() && target.getBlockData() instanceof Powerable) {
                target.setBlockData(blockData, false);
                queuePhysicsUpdate(target);
            }
        });
    }

    private void mirrorTilling(Block source) {
        if (source.getType() != Material.FARMLAND) {
            return;
        }

        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.till());
        forEachTarget(source, target -> {
            if (target.getType() == Material.DIRT) {
                setTypeAndQueuePhysics(target, Material.FARMLAND);
            }
        });
    }

    private void mirrorFluid(Block source) {
        if (source.getType() != Material.WATER && source.getType() != Material.LAVA) {
            return;
        }

        BlockData blockData = source.getBlockData();
        changesFor(source.getWorld()).put(LocalPosition.from(source), MirroredChange.place(blockData.getAsString()));
        forEachTarget(source, target -> placeIfAir(target, blockData));
    }

    private void applySavedChanges(Chunk chunk) {
        World world = chunk.getWorld();
        Map<LocalPosition, MirroredChange> changes = changesByWorld.get(world.getUID());
        if (changes == null) {
            return;
        }

        for (Map.Entry<LocalPosition, MirroredChange> entry : changes.entrySet()) {
            LocalPosition position = entry.getKey();
            if (position.y() >= world.getMinHeight() && position.y() < world.getMaxHeight()) {
                applyChange(chunk.getBlock(position.x(), position.y(), position.z()), entry.getValue());
            }
        }
    }

    private void applyChange(Block target, MirroredChange change) {
        if (change.kind() == ChangeKind.BREAK) {
            setTypeAndQueuePhysics(target, Material.AIR);
        } else if (change.kind() == ChangeKind.TILL) {
            if (target.getType() == Material.DIRT) {
                setTypeAndQueuePhysics(target, Material.FARMLAND);
            }
        } else if (target.getType().isAir()) {
            target.setBlockData(Bukkit.createBlockData(change.blockData()), false);
            queuePhysicsUpdate(target);
        }
    }

    private void placeIfAir(Block target, BlockData blockData) {
        if (target.getType().isAir()) {
            target.setBlockData(blockData, false);
            queuePhysicsUpdate(target);
        }
    }

    /**
     * Physics is delayed and throttled. Calling it for all chunks in a single
     * player event can cause Paper to synchronously wait for chunk generation.
     */
    private void processPhysicsUpdates() {
        for (int processed = 0; processed < PHYSICS_UPDATES_PER_TICK && !pendingPhysicsUpdates.isEmpty(); processed++) {
            PhysicsUpdate update = pendingPhysicsUpdates.remove();
            queuedPhysicsUpdates.remove(update);
            if (!update.world().isChunkLoaded(update.chunkX(), update.chunkZ())) {
                continue;
            }

            Block block = update.world().getBlockAt(update.x(), update.y(), update.z());
            BlockData blockData = block.getBlockData();
            if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                // Re-setting an unchanged fluid state does not schedule a fluid tick.
                // Replace it once so Minecraft starts the normal fluid simulation.
                block.setType(Material.AIR, false);
            }
            block.setBlockData(blockData, true);
        }
    }

    private void setTypeAndQueuePhysics(Block target, Material material) {
        target.setType(material, false);
        queuePhysicsUpdate(target);
    }

    private void queuePhysicsUpdate(Block block) {
        PhysicsUpdate update = new PhysicsUpdate(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (queuedPhysicsUpdates.add(update)) {
            pendingPhysicsUpdates.add(update);
        }
    }

    /** Visits equivalent positions only in chunks Paper has already loaded. */
    private void forEachTarget(Block source, BlockAction action) {
        int localX = source.getX() & 15;
        int localZ = source.getZ() & 15;
        int y = source.getY();
        World sourceWorld = source.getWorld();
        int sourceChunkX = source.getChunk().getX();
        int sourceChunkZ = source.getChunk().getZ();

        if (y < sourceWorld.getMinHeight() || y >= sourceWorld.getMaxHeight()) {
            return;
        }
        for (Chunk chunk : sourceWorld.getLoadedChunks()) {
            if (chunk.getX() == sourceChunkX && chunk.getZ() == sourceChunkZ) {
                continue;
            }
            action.apply(chunk.getBlock(localX, y, localZ));
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
                if (position != null && change != null) {
                    changes.put(position, change);
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
