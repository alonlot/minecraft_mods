# Hogwarts Magic

Hogwarts Magic is a Minecraft Forge 1.21 mod that adds Harry Potter-inspired magic systems, spellcasting, wands, broom flight, magical creatures, house progression, relics, Floo travel, owl post, and world events.

The feature reference in `MOD_FEATURES_WIKI.md` is based on the code and registries in this repository.

## Requirements

- Java 21
- Minecraft Forge 1.21 / ForgeGradle
- The included Gradle wrapper

## Build

On Windows:

```powershell
.\gradlew.bat build
```

On macOS/Linux:

```bash
./gradlew build
```

The built mod jar is written to `build/libs/`.

## Development

Useful Forge run tasks:

```bash
./gradlew genIntellijRuns
./gradlew genEclipseRuns
./gradlew runClient
./gradlew runServer
```

Generated build folders, local run data, IDE metadata, logs, and scratch extraction folders are intentionally ignored by Git.
