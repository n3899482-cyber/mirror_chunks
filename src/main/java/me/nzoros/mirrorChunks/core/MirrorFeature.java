package me.nzoros.mirrorChunks.core;

import java.util.function.Predicate;

/** Features displayed by the administrative status command. */
public enum MirrorFeature {
    BLOCK_PLACE("Block place mirroring", MirrorSettings::blockPlaceEnabled),
    REPLACE_EXISTING_BLOCKS("Replace existing blocks", MirrorSettings::replaceExistingBlocks),
    BLOCK_BREAK("Block break mirroring", MirrorSettings::blockBreakEnabled),
    TILLING("Tilling mirroring", MirrorSettings::tillingEnabled),
    FLUIDS("Fluid mirroring", MirrorSettings::fluidsEnabled),
    POWER_STATE("Power state mirroring", MirrorSettings::powerStateEnabled),
    SPAWN_EGGS("Spawn egg mirroring", MirrorSettings::spawnEggsEnabled);

    private final String displayName;
    private final Predicate<MirrorSettings> enabled;

    MirrorFeature(String displayName, Predicate<MirrorSettings> enabled) {
        this.displayName = displayName;
        this.enabled = enabled;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isEnabled(MirrorSettings settings) {
        return enabled.test(settings);
    }
}
