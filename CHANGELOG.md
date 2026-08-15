# Changelog

All notable changes to MirrorChunks are documented here.

## 1.1.0

- Added administrative `/mirrorchunks` commands, help, status, reload, and tab completion.
- Added persistent configuration for placement, replacement, block breaking, tilling, fluids, power states, spawn eggs, physics limits, and debug logging.
- Added safe configuration reload validation.
- Added migration from the temporary `replace-blocks-enabled` setting.
- Added protection for target block entities during replacement.
- Kept loaded-chunk ring propagation and throttled physics processing.

### Compatibility

- Requires Paper 1.21.11 and Java 21.
- Existing `mirrored-changes` data is retained.
- `replace-existing-blocks` is enabled by default for new configurations; existing configurations retain their selected value.
