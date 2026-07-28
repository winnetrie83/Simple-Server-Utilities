# SSU 1.1.0-dev1 test plan

Use a copy of a test world. Keep the 1.0.0 baseline available before opening an important world.

## Build verification

1. Use Java 25.
2. Run `./gradlew clean build` on Linux/macOS or `gradlew.bat clean build` on Windows.
3. Confirm one current JAR appears in `build/libs` and contains the version `1.1.0-dev1` in `META-INF/neoforge.mods.toml`.
4. Start both a client-integrated server and a dedicated server.

## Existing-data compatibility

1. Start a copied world containing existing ranks, permissions, claims, regions, homes and warps.
2. Confirm all records load without migration errors.
3. Restart the server and confirm no existing JSON record was lost or renamed.
4. Test at least one home, warp, claim spawn and region spawn.

## Bypass separation

Test with a non-operator player.

1. Grant `ssu.claims.admin.bypass=true` but not `ssu.regions.admin.bypass`.
2. Confirm the player bypasses claim building restrictions.
3. Confirm the same player cannot bypass a protected admin region.
4. Grant `ssu.regions.admin.bypass=true` and confirm region protection is then bypassed.
5. Confirm an operator still bypasses both systems.

## Redstone protection

For both a player claim and an admin region:

1. Set `allowRedstone=false`.
2. Test levers, buttons, pressure plates, redstone wire, repeaters, comparators, observers and powered rails inside the protected area.
3. Test a signal crossing from wilderness into the protected area.
4. Test a signal crossing from the protected area into wilderness.
5. Set `allowRedstone=true` and confirm normal updates resume.
6. Confirm unrelated neighbour updates, crop growth and block physics still function normally.

## Piston protection

1. Set `allowPistons=false` in a claim and repeat in a region.
2. Test a normal push and sticky-piston pull entirely inside the protected area.
3. Test movement from wilderness into protection and from protection into wilderness.
4. Test slime and honey branches with multiple connected blocks.
5. Test a piston attempting to destroy a breakable block.
6. Set `allowPistons=true` and confirm permitted structures move normally.

## Safe teleports

For homes, warps, claim spawns and region spawns:

1. Test a normal safe destination.
2. Obstruct the feet position and confirm a nearby vertical safe position is used or the teleport fails cleanly.
3. Obstruct both feet and head positions.
4. Replace the target with water and lava; confirm neither is selected as safe standing space.
5. Remove the floor and test over the void or a deep shaft.
6. Test a destination in an unloaded chunk.
7. Test a delayed teleport, make the destination unsafe during the delay and confirm no cooldown is applied after failure.
8. Confirm successful delayed teleports still apply their configured cooldown.

## Regression checks

- Claim create/delete/trust/untrust/flags/map.
- Region create/delete/edit/members/owners/flags/renting.
- Home set/delete/list/teleport.
- Warp set/delete/list/teleport.
- PvP, explosions, fluids, fire and hopper protection.
- Server stop and restart saves.

## Report with a failure

Include:

- `latest.log` and crash report, if any;
- exact command and permission values used;
- whether the player was operator;
- claim or region flags;
- smallest repeatable sequence;
- relevant SSU JSON record from the copied test world.
