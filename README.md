# MirrorChunks

MirrorChunks is a server-side Paper plugin for Minecraft 1.21.x that mirrors player actions across loaded chunks in the same world.

## Features

- Mirrors block placement and breaking at matching local chunk coordinates.
- Mirrors hoe use on dirt, water and lava buckets, buttons, levers, and spawn eggs.
- Applies saved changes when a previously unloaded chunk loads.
- Keeps Overworld, Nether, End, and custom worlds separate.
- Requires no client-side mod or resource pack.

## Requirements

- Paper 1.21.x
- Java 21 or newer

## Building

```bash
./gradlew build
```

The plugin JAR is created in `build/libs/`.

## Installation

1. Copy the JAR into the server's `plugins/` folder.
2. Restart the Paper server.

## License

Licensed under the [MIT License](LICENSE).
