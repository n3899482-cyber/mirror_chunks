# MirrorChunks

MirrorChunks is a server-side Paper plugin that mirrors player actions across
loaded chunks at matching local chunk coordinates.

## Features

- Mirrors block placement and breaking at matching local chunk coordinates.
- Lets operators control placement, replacement, and block-break mirroring.
- Mirrors hoe use on dirt, water and lava buckets, buttons, levers, and spawn eggs.
- Applies saved changes when a previously unloaded chunk loads.
- Keeps Overworld, Nether, End, and custom worlds separate.
- Requires no client-side mod or resource pack.

## Requirements

- Paper 1.21.x
- Java 21 or newer for the server

## Building

```bash
./gradlew build
```

The plugin JAR is created in `build/libs/`.

## Administration

All administration commands require `mirrorchunks.admin` (operators have it by
default):

- `/mirrorchunks help`
- `/mirrorchunks status`
- `/mirrorchunks reload`
- `/mirrorchunks block-place enable|disable|status`
- `/mirrorchunks block-place replace enable|disable|status`
- `/mirrorchunks block-break enable|disable|status`

`config.yml` defaults to mirroring every existing action, including replacement
of occupied non-block-entity targets. Target block entities are never replaced,
because their data cannot safely be mirrored as BlockData.

## Installation

1. Copy the JAR into the server's `plugins/` folder.
2. Restart the Paper server.

## Publishing

Release notes, platform-ready descriptions, and the publishing checklist are in
[`docs/release/`](docs/release/). The square project icon is
[`assets/mirrorchunks-icon.png`](assets/mirrorchunks-icon.png).

The existing CurseForge page is
[MirrorChunks on CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/mirror-chunks).

## License

Licensed under the [MIT License](LICENSE).
