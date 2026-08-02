package me.nzoros.mirrorChunks;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MirrorChunks extends JavaPlugin {
    private MirrorBlockListener mirrorBlockListener;

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        mirrorBlockListener = new MirrorBlockListener(this);
        pluginManager.registerEvents(mirrorBlockListener, this);

        getLogger().info("MirrorChunks enabled.");
    }

    @Override
    public void onDisable() {
        if (mirrorBlockListener != null) {
            mirrorBlockListener.saveChanges();
        }
    }
}
