# Release checklist

## Before building

- [ ] Install a JDK 21; a Java runtime without `javac` cannot build this project.
- [ ] Confirm `version` in `gradle.properties` is the intended release version.
- [ ] Review `CHANGELOG.md` and `docs/release/PLATFORM_COPY.md`.
- [ ] Test on Paper 1.21.11 with Java 21.

## Build and verify

- [ ] Run `./gradlew clean build`.
- [ ] Upload only `build/libs/MirrorChunks-<version>.jar`.
- [ ] Do not upload `run/`, worlds, player data, Gradle caches, or source archives.
- [ ] Start a clean Paper server and confirm the plugin enables successfully.
- [ ] Verify `/mirrorchunks help`, `status`, `reload`, and tab completion as an operator.
- [ ] Verify a non-operator is denied `/mirrorchunks` administration commands.
- [ ] Verify placement replacement, disabled placement, disabled break mirroring, and protected chests.
- [ ] Verify a malformed config leaves active settings unchanged after reload.
- [ ] Verify saved `mirrored-changes` are preserved across a restart.

## CurseForge

- [ ] Use the existing project: https://www.curseforge.com/minecraft/bukkit-plugins/mirror-chunks
- [ ] Upload `assets/mirrorchunks-icon.png` as the square project icon.
- [ ] Paste the Summary and Description from `PLATFORM_COPY.md`.
- [ ] Upload the built JAR as a Release after verification.
- [ ] Select Paper 1.21.11 as the supported game version.
- [ ] Paste the `1.1.0` section from `CHANGELOG.md` as the file changelog.

## Modrinth and Hangar

- [ ] Use the same icon, summary, description, requirements, and changelog.
- [ ] Mark the loader/platform as Paper and the game version as 1.21.11.
- [ ] Link the source repository and issue tracker only after their public URLs exist.
