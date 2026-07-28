# SSU 1.1.0-dev5 test plan

Always test a development build on a backup or disposable world first.

## Build and protocol

1. Install Java 25.
2. Run `gradlew.bat clean build` on Windows or `./gradlew clean build` on Linux/macOS.
3. Confirm `build/libs/simpleserverutilities-1.1.0-dev5.jar` exists.
4. Use the same dev5 JAR on client and server.
5. Confirm a dev4 client cannot silently connect to a dev5 server; protocol version 5 must fail fast.

## Opening and navigation

1. Open the map with `/claims map`.
2. Open it with `/claims gui`.
3. Open the SSU dashboard with `U`, enter Claims and use Open claim map.
4. Pan with the arrow buttons.
5. Pan with right mouse drag.
6. Zoom with the mouse wheel and Zoom buttons.
7. Move away from the initial center and use Center.
8. Confirm the server clamps excessive navigation instead of trusting arbitrary client coordinates.

## Create a claim

1. Select New.
2. Select one wilderness chunk.
3. Enter a valid name such as `base_1`.
4. Confirm the Create button becomes active immediately.
5. Create the claim and verify the map refreshes to Expand mode.
6. Confirm the claim has one stable claim group and one saved chunk.
7. Try an invalid name containing spaces and confirm the action remains unavailable/server-rejected.
8. Select two disconnected wilderness islands and confirm the server rejects the final shape.
9. Select an L-shaped connected area and confirm it succeeds in one batch.
10. Try creating over a server region and over another claim; both must fail without partial changes.
11. Configure a region/dimension permission override that denies claiming at a remote target chunk, stand elsewhere where claiming is allowed, and confirm the map action is still rejected for that target.
12. Configure different claim limits across selected target contexts and confirm the most restrictive applicable limit rejects an oversized batch without partial changes.

## Expand an existing claim

1. Select an owned claim with the previous/next buttons.
2. Select several connected wilderness chunks, including a shape that only becomes connected as a complete batch.
3. Apply once and confirm all selected chunks are added.
4. Select a disconnected island and confirm it is rejected.
5. Reach total/per-claim limits and confirm the entire batch fails without partial additions.
6. Restart and confirm the original claim UUID, name, trust, flags and new chunks persist.

## Remove chunks

1. Select Remove.
2. Remove an outside edge chunk and confirm it succeeds.
3. Select a middle bridge chunk that would split the claim and confirm the entire batch fails.
4. Remove several chunks while leaving one connected remainder and confirm it succeeds.
5. Remove every chunk and confirm the empty claim group remains valid and reusable, matching existing SSU behavior.
6. Confirm chunks belonging to another claim cannot be submitted for removal.

## Selection and bounds

1. Toggle a selected chunk off by clicking it again.
2. Use Clear and confirm the selection resets without closing the map.
3. Attempt to select more than 256 chunks; the client must stop at 256.
4. Confirm every server action also rejects payloads above the hard limit.
5. Pan or zoom and confirm stale selections are cleared before a new viewport is used.

## Colors and status

1. Change `own_claim`, `other_claim`, `server_region` and `selection` colors as an admin.
2. Refresh/reopen the map and confirm the map and legend use the configured colors.
3. Confirm trusted and untrusted claims from other players use the other-claim color.
4. Confirm current player chunk, selected claim outline and selected operation chunks remain distinguishable.

## World border visualization

1. Show a player claim border.
2. Confirm the ribbon remains contour-only and follows the player's height.
3. Confirm it extends approximately eight blocks farther downward than dev4.
4. Confirm region and selection visualization are unchanged.

## Regression checks

- Existing `/claims create`, `/claims claimchunk`, `/claims unclaim`, trust, flags, spawn and teleport commands still work.
- `/claims map text <name>` displays the old chat map.
- Existing claims load without migration.
- Region protection continues to prevent player-claim overlap.
- Claim border preferences and pinned region borders persist.
- Homes, warps, permissions, storage jobs and the SSU dashboard continue to operate.
