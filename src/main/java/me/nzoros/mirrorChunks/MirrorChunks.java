package me.nzoros.mirrorChunks;

import java.util.Objects;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MirrorChunks extends JavaPlugin {
    private MirrorBlockListener mirrorBlockListener;
    private PaperConfigManager configManager;

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        configManager = new PaperConfigManager(this);
        if (!configManager.loadInitial()) {
            getLogger().warning("Using default MirrorChunks settings because config.yml is invalid.");
        }
        mirrorBlockListener = new MirrorBlockListener(this, configManager);
        pluginManager.registerEvents(mirrorBlockListener, this);
        MirrorChunksCommand command = new MirrorChunksCommand(configManager);
        Objects.requireNonNull(getCommand("mirrorchunks")).setExecutor(command);
        Objects.requireNonNull(getCommand("mirrorchunks")).setTabCompleter(command);

        getLogger().info("MirrorChunks enabled.");
    }

    @Override
    public void onDisable() {
        if (mirrorBlockListener != null) {
            mirrorBlockListener.saveChanges();
        }
    }
}
