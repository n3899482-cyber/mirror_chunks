package me.nzoros.mirrorChunks.core;

import java.util.List;

/** Immutable, platform-independent settings used by a mirror operation. */
public record MirrorSettings(
    boolean blockPlaceEnabled,
    boolean replaceExistingBlocks,
    boolean blockBreakEnabled,
    boolean tillingEnabled,
    boolean fluidsEnabled,
    boolean powerStateEnabled,
    boolean spawnEggsEnabled,
    int maxPhysicsUpdatesPerTick,
    boolean debugEnabled
) {
    public static final int DEFAULT_MAX_PHYSICS_UPDATES_PER_TICK = 128;

    public static MirrorSettings defaults() {
        return new MirrorSettings(true, true, true, true, true, true, true,
            DEFAULT_MAX_PHYSICS_UPDATES_PER_TICK, false);
    }

    public static ValidationResult validate(
        boolean blockPlaceEnabled,
        boolean replaceExistingBlocks,
        boolean blockBreakEnabled,
        boolean tillingEnabled,
        boolean fluidsEnabled,
        boolean powerStateEnabled,
        boolean spawnEggsEnabled,
        int maxPhysicsUpdatesPerTick,
        boolean debugEnabled
    ) {
        if (maxPhysicsUpdatesPerTick <= 0) {
            return new ValidationResult(null, List.of("performance.max-physics-updates-per-tick must be greater than zero."));
        }
        return new ValidationResult(new MirrorSettings(
            blockPlaceEnabled, replaceExistingBlocks, blockBreakEnabled, tillingEnabled,
            fluidsEnabled, powerStateEnabled, spawnEggsEnabled, maxPhysicsUpdatesPerTick, debugEnabled
        ), List.of());
    }

    public record ValidationResult(MirrorSettings settings, List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
