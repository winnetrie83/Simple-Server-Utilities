# Simple Server Utilities

Simple Server Utilities (SSU) is a modular NeoForge server utility mod for Minecraft 26.2.

## Current systems

- Player chunk claims with connected claim groups, trust, limits, flags and claim spawns
- Three-dimensional admin regions with priorities, nesting, members, renting and snapshots
- Internal ranks and permissions with inheritance, wildcards and contextual overrides
- Homes, global warps and delayed teleports with cooldowns
- Protection for blocks, entities, PvP, explosions, pistons, fluids, hoppers, fire and redstone
- A first interactive player/admin dashboard opened with `U` or `/ssu menu`
- An interactive, server-authoritative chunk claim map for creating, expanding and shrinking connected claim groups
- Client-rendered claim ribbons and exact 3D region/selection borders with persistent player toggles, multiple selected regions and admin colors
- Batched claim/region storage and bounded multi-tick region world-edit jobs

## Development requirements

- Java 25
- Gradle 9.2.1 through the included wrapper
- NeoForge 26.2.0.7-beta
- Minecraft 26.2

Build with:

```bash
./gradlew build
```

The distributable JAR is written to `build/libs/`.

## Data compatibility

SSU stores server data below the world save in the `simpleserverutilities` folder. Development builds in the 1.1.0 line keep the existing 1.0.0 JSON formats unless a changelog explicitly announces a migration.

Always back up a world before installing a development build.
