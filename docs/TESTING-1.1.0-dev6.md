# SSU 1.1.0-dev6 manual test plan

## Preparation

- Make a backup copy of the test world.
- Use the same dev6 JAR on the client and server.
- Confirm the client can join; dev6 uses network protocol version 6.
- Keep copies of the existing `simpleserverutilities` world-data folder before first start.

## 1. dev5.2.1 claim-map baseline

1. Open `/claims map`.
2. Confirm the 16×16 terrain layer renders.
3. Confirm one connected claim has one outer contour, without internal chunk lines.
4. Create, expand and shrink a claim through the map.
5. Confirm disconnected or invalid operations are still rejected server-side.

## 2. Region spatial index

1. Test block break/place, interaction, PvP, pistons, fluids and explosions inside and outside regions.
2. Test nested/overlapping regions with different priorities.
3. At equal priority, confirm the smallest containing region remains effective.
4. Create, delete and redefine a region, then immediately retest protection.
5. Toggle nearby region borders and confirm all nearby regions still appear.
6. Start a fill/reset near regions and verify conflicting resource locks still work.

## 3. Permission cache correctness

1. Run `/ssu core performance` and note the initial permission cache statistics.
2. Perform repeated protected interactions; cache hits should rise.
3. Grant and revoke a player permission while the player remains online.
4. Confirm the new permission result applies immediately after each change.
5. Change rank inheritance, a dimension override, claim-role permission and region override.
6. Confirm none of the old values remain cached.
7. Run `/ssu core performance reset` and confirm counters return to zero.

## 4. Storage migration

1. Before starting dev6, preserve existing homes, warps, ranks and player permissions.
2. Start dev6 and verify all records still load.
3. Change only one home/player/rank record and allow the storage queue to drain.
4. Restart and verify all data remains intact.
5. Run `/permissions save`, `/permissions reload`, and `/ssu core status`.
6. Confirm `pending=0` and `retryRequired=0` under normal conditions.

Existing JSON paths and schemas should remain unchanged.

## 5. GUI Core performance page

1. Open the SSU menu with `U` or `/ssu menu` as an administrator.
2. Open Core status.
3. Confirm permission checks, hit rate, cache entries, region lookups, index cells and references are shown.
4. Use Refresh status.
5. Use Reset counters and refresh again.

## 6. Jobs and regressions

1. Run a region fill or snapshot reset.
2. Confirm it still runs over multiple ticks.
3. Confirm job completion/cancellation appears in `/ssu core performance`.
4. Retest homes, warps, claim/region borders and permission commands.
5. Restart the server cleanly and verify no data is lost.
