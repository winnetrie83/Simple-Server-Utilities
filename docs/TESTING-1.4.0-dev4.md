# SSU 1.4.0-dev4 Minecraft test plan

Use a backup/copy of a dev3 world. Build with Java 25 and install the same dev4 JAR on client and server. Network protocol is 19; protocol-18 dev3 clients/servers must not be mixed.

## 1. Build and startup

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm `build/libs` contains the dev4 JAR.
3. Start a copied world that already contains claims, regions, homes, warps, permissions and a server spawn.
4. Confirm no data migration exception appears and all existing objects remain present.
5. Confirm the log/client rejects a protocol-18 mismatch rather than silently connecting.

## 2. Permission catalogue and GUI

1. Open Admin Center → Permissions.
2. Confirm the target filter offers Players, Ranks and Dimensions.
3. Select `minecraft:overworld`, `minecraft:the_nether` and `minecraft:the_end` where loaded.
4. Confirm an existing stored custom dimension scope also appears when its dimension is not currently loaded.
5. Search for:
   - `ssu.teleport.escape`
   - `ssu.teleport.region_bypass`
   - `ssu.teleport.require_still`
   - `ssu.teleport.cancel_on_move`
6. Set/reset boolean values and confirm they persist after restart.
7. Repeat one dimension change through `/permissions dimension <id> set|unset` and confirm GUI/command results match.
8. Confirm boolean command values accept `true/false`, `allow/deny`, `yes/no` and `1/0`.

## 3. Stand-still cancellation

Use a rank/player with a teleport delay of at least 3 seconds.

1. Set `ssu.teleport.require_still=true`.
2. Start `/spawn`, then walk a small distance without leaving the current block. Confirm cancellation.
3. Repeat and jump. Confirm cancellation.
4. Repeat while falling, swimming, being pushed or riding a moving vehicle. Confirm cancellation.
5. Repeat while only rotating the camera. Confirm the teleport completes.
6. Set `ssu.teleport.require_still=false`, move during the countdown and confirm the teleport completes.
7. Remove the new key, set legacy `ssu.teleport.cancel_on_move=false` and confirm movement remains allowed.
8. Set the new key back to true and confirm it takes precedence over the legacy value.

## 4. Execution-time guards

For each normal teleport type, start outside a blocked region with a delay and enter the region before the countdown ends:

- `/homes tp <name>`
- `/warps tp <name>`
- `/spawn`
- claim teleport
- `/regions tp <name>`

Confirm every request is cancelled before teleporting and no cooldown is applied. Then leave the region and confirm a fresh request works.

Also test changing the permission from Allow to Deny during the countdown; the pending teleport must be cancelled at execution time.

## 5. Umbrella escape permission

1. In region `servertestarea`, set `ssu.teleport.escape=false`.
2. Confirm homes, warps, spawn, claims and region teleports are all blocked inside it.
3. Confirm the denial message names `servertestarea`.
4. Set a normal personal/rank Allow for the individual teleport key and confirm it does not override the region escape Deny.
5. Grant `ssu.teleport.region_bypass=true` to a test player and confirm teleports work if no dimension/rank restriction still denies them.
6. Remove the bypass and confirm blocking returns.

## 6. Individual region permissions

1. Reset `ssu.teleport.escape` to Default.
2. Set only `ssu.spawn.use=false`; confirm only `/spawn` is blocked.
3. Set only `ssu.homes.teleport=false`; confirm only homes are blocked.
4. Repeat for warps, claims and regions.
5. Confirm the old `ssu.spawn.region_bypass=true` still bypasses only a spawn-specific region Deny and does not bypass home/warp/claim/region denies.

## 7. Dimension permissions

1. In the Nether dimensionscope, set `ssu.teleport.escape=false`.
2. Confirm all normal player teleports are blocked while standing in the Nether.
3. Confirm the denial message names `minecraft:the_nether`.
4. Reset the umbrella key and block individual teleport types one at a time.
5. Restart the server and confirm the dimensionscope persists.

## 8. Delay, cooldown and cancellation

1. Confirm each teleport type uses its existing delay key.
2. Confirm a successful teleport applies its existing cooldown key.
3. Confirm movement cancellation and permission-guard cancellation do not apply cooldown.
4. Confirm `/spawn cancel` still cancels any pending SSU teleport for the player.
5. Confirm only one pending teleport is allowed per player.

## 9. Administrative separation

1. Use an administrative claim teleport while standing in an escape-denied area.
2. Confirm it remains immediate and available to the authorized administrator.
3. Confirm normal player claim teleport remains denied.

## 10. Regression

Confirm no regressions in:

- home/warp creation, deletion and listing;
- server spawn set/clear/info;
- claim and region spawn storage;
- safe-destination search;
- region rentals and resets;
- claim/region settings screens;
- rank/player/region permission editors;
- map, minimap and border visualization.
