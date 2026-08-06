# Repository Guidelines

## Project Structure & Module Organization

This workspace contains several independent projects. Work inside the specific
project directory named in the task; do not make cross-project changes. The
active Minecraft Paper plugin is `mods/MirrorChunks/`:

- `src/main/java/me/nzoros/mirrorChunks/` contains plugin code.
- `src/main/resources/plugin.yml` contains Paper metadata.
- `build.gradle.kts`, `gradle.properties`, and `gradlew` configure Gradle.
- `run/` is local server data and runtime output; do not commit it.
- `build/` is generated output; do not edit it directly.

## Build, Test, and Development Commands

Run commands from `mods/MirrorChunks/`.

- `./gradlew build` compiles the plugin and creates a JAR in `build/libs/`.
- `./gradlew runServer` starts a local Paper 1.21.11 server using the built
  plugin.
- `./gradlew clean build` rebuilds from scratch when generated output is stale.

The project currently has no automated test suite. For behavior changes, test
in the local Paper server: check source-chunk behavior, loaded target chunks,
and a newly loaded chunk. Do not rely only on compilation for world-physics
changes.

## Coding Style & Naming Conventions

Use Java 21 and four-space indentation. Keep one public plugin entry point in
`MirrorChunks.java`; put event and world logic in focused package-private
classes such as `MirrorBlockListener`. Use `camelCase` for methods and fields,
`PascalCase` for classes and records, and `UPPER_SNAKE_CASE` for constants.
Prefer Bukkit/Paper APIs over NMS internals. Avoid force-loading chunks: only
operate on chunks already reported as loaded.

## Commit & Pull Request Guidelines

Use short, imperative commit subjects, for example `Fix mirrored block physics
updates` or `Add delayed chunk rings`. Commit only files related to the change;
preserve unrelated local edits. Pull requests should state the gameplay effect,
the Paper version tested, and manual test steps. Include screenshots or a short
video when a change has visible in-game behavior.

## Configuration and Safety

Treat `plugin.yml` and `gradle.properties` as release-facing configuration.
Do not commit server worlds, player data, credentials, or generated JARs unless
the task explicitly requests a release artifact.
