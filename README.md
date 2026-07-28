# Simple Server Utilities

Simple Server Utilities (SSU) is a modular NeoForge server utility mod for Minecraft 26.2.

## Current systems

- Player chunk claims with connected claim groups, trust, limits, flags and claim spawns
- Three-dimensional admin regions with priorities, nesting, members, renting and snapshots
- Configurable default rank plus rank permissions and higher-priority personal permissions
- Homes, global warps and delayed teleports with cooldowns
- Protection for blocks, entities, PvP, explosions, pistons, fluids, hoppers, fire and redstone
- A Bedrock-inspired player dashboard and permission-aware Admin Center opened with `U` or `/ssu menu`
- An interactive, server-authoritative chunk claim map for creating, expanding and shrinking connected claim groups
- Client-rendered claim ribbons and exact 3D region/selection borders with persistent player toggles, multiple selected regions and admin colors
- Batched dirty-record storage for claims, regions, homes, warps and permissions
- Bounded multi-tick region world-edit jobs
- Region spatial indexing, permission-result caching and an admin performance dashboard
- Exact server-authoritative economy accounts, transaction journals and player payments
- Paid server-region renting with renewals, owner payout policy and pro-rata cancellation refunds
- Persistent player dashboard/minimap preferences, custom dashboard textures and a framed 3D skin portrait
- An always-visible client HUD minimap with locally sampled terrain, player heading, coordinates and server-authoritative claim/region overlays

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

SSU stores server data below the world save in the `simpleserverutilities` folder. Development builds preserve existing claim, region, home, warp and permission data. The 1.2.0 line adds isolated economy and region-rent journal records and extends region rental JSON with backwards-compatible optional fields. The 1.3.0 line adds permission settings, automatic default-rank assignment and isolated player UI preferences. Dev3 adds only transient minimap networking and client rendering; it does not change saved world or player-settings schemas.

Always back up a world before installing a development build.
