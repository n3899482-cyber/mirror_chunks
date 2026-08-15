package me.nzoros.mirrorChunks;

import java.io.File;
import java.io.IOException;
import java.util.List;
import me.nzoros.mirrorChunks.core.MirrorSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns Paper YAML I/O while exposing an immutable settings snapshot. */
final class PaperConfigManager {
    private static final String OLD_REPLACE_PATH = "replace-blocks-enabled";
    private final JavaPlugin plugin;
    private MirrorSettings settings = MirrorSettings.defaults();

    PaperConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    MirrorSettings settings() {
        return settings;
    }

    boolean loadInitial() {
        plugin.saveDefaultConfig();
        return reload();
    }

    boolean reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("MirrorChunks configuration could not be read: " + exception.getMessage());
            return false;
        }
        migrateOldReplacementSetting(candidate);
        applyDefaults(candidate);
        MirrorSettings.ValidationResult result = read(candidate);
        if (!result.isValid()) {
            plugin.getLogger().warning("MirrorChunks configuration was rejected: " + String.join(" ", result.errors()));
            return false;
        }

        // Validation happened before replacing the active settings.
        plugin.reloadConfig();
        FileConfiguration activeConfig = plugin.getConfig();
        migrateOldReplacementSetting(activeConfig);
        applyDefaults(activeConfig);
        plugin.saveConfig();
        settings = result.settings();
        return true;
    }

    void setBlockPlaceEnabled(boolean enabled) {
        update("settings.block-place.enabled", enabled);
    }

    void setReplaceExistingBlocks(boolean enabled) {
        update("settings.block-place.replace-existing-blocks", enabled);
    }

    void setBlockBreakEnabled(boolean enabled) {
        update("settings.block-break.enabled", enabled);
    }

    private void update(String path, boolean value) {
        FileConfiguration config = plugin.getConfig();
        config.set(path, value);
        plugin.saveConfig();
        MirrorSettings.ValidationResult result = read(config);
        if (result.isValid()) {
            settings = result.settings();
        }
    }

    private void applyDefaults(FileConfiguration config) {
        MirrorSettings defaults = MirrorSettings.defaults();
        config.addDefault("settings.block-place.enabled", defaults.blockPlaceEnabled());
        config.addDefault("settings.block-place.replace-existing-blocks", defaults.replaceExistingBlocks());
        config.addDefault("settings.block-break.enabled", defaults.blockBreakEnabled());
        config.addDefault("settings.tilling.enabled", defaults.tillingEnabled());
        config.addDefault("settings.fluids.enabled", defaults.fluidsEnabled());
        config.addDefault("settings.power-state.enabled", defaults.powerStateEnabled());
        config.addDefault("settings.spawn-eggs.enabled", defaults.spawnEggsEnabled());
        config.addDefault("performance.max-physics-updates-per-tick", defaults.maxPhysicsUpdatesPerTick());
        config.addDefault("debug.enabled", defaults.debugEnabled());
        config.options().copyDefaults(true);
    }

    private void migrateOldReplacementSetting(FileConfiguration config) {
        String newPath = "settings.block-place.replace-existing-blocks";
        if (!config.contains(newPath) && config.contains(OLD_REPLACE_PATH)) {
            config.set(newPath, config.getBoolean(OLD_REPLACE_PATH));
            plugin.getLogger().info("Migrated replace-blocks-enabled to " + newPath + ".");
        }
    }

    private MirrorSettings.ValidationResult read(FileConfiguration config) {
        List<String> booleanPaths = List.of(
            "settings.block-place.enabled", "settings.block-place.replace-existing-blocks",
            "settings.block-break.enabled", "settings.tilling.enabled", "settings.fluids.enabled",
            "settings.power-state.enabled", "settings.spawn-eggs.enabled", "debug.enabled"
        );
        for (String path : booleanPaths) {
            if (!config.isBoolean(path)) {
                return new MirrorSettings.ValidationResult(null, List.of(path + " must be true or false."));
            }
        }
        if (!config.isInt("performance.max-physics-updates-per-tick")) {
            return new MirrorSettings.ValidationResult(null,
                List.of("performance.max-physics-updates-per-tick must be an integer."));
        }
        return MirrorSettings.validate(
            config.getBoolean("settings.block-place.enabled"),
            config.getBoolean("settings.block-place.replace-existing-blocks"),
            config.getBoolean("settings.block-break.enabled"),
            config.getBoolean("settings.tilling.enabled"),
            config.getBoolean("settings.fluids.enabled"),
            config.getBoolean("settings.power-state.enabled"),
            config.getBoolean("settings.spawn-eggs.enabled"),
            config.getInt("performance.max-physics-updates-per-tick"),
            config.getBoolean("debug.enabled")
        );
    }
}
