package me.nzoros.mirrorChunks.core;

/** Snapshot shared by every ring of one mirror operation. */
public record MirrorOperation(
    MirrorSettings settings,
    int sourceChunkX,
    int sourceChunkZ,
    int localX,
    int y,
    int localZ
) {
}
