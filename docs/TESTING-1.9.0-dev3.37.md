# SSU 1.9.0-dev3.37 testing

## Build / startup
1. Build with the project's Java 25 toolchain and NeoForge 26.2 setup.
2. Start an existing dev3.36 world.
3. Confirm there are no community-stat schema/load errors.
4. Confirm `simpleserverutilities/statistics/community/server.json` and player JSON files appear after activity + storage flush.

## Basic counters
With one test player perform these actions and wait at least five seconds before stopping the server:
- join once;
- stay online at least 10 seconds;
- break and place blocks;
- craft/use/consume an item;
- walk/sprint several blocks;
- enter another biome if convenient;
- kill a mob and take/deal damage.

Restart and verify the counters continue from their previous values rather than resetting.

## Session regression
A single login should increment `sessions` by exactly **1**, not 2. This validates removal of the old duplicate login publisher.

## Period buckets
- Day/week/month/season values should increment alongside lifetime values.
- Set `communityStatsSeasonId` to another value, restart/refresh the module, perform activity and verify the new season starts at zero while the previous season is present in season history.
- Never change the system clock on a production world solely for testing rollovers.

## Exploration safeguards
- Normal movement should add `distance_travelled`.
- Teleporting a large distance should not add that teleport distance.
- Re-entering the same biome can increment biome visits, but `unique_biomes` should only increment once per period for that biome.

## Web API
With the existing Web API test token:
- `/api/v1/capabilities` reports `statistics: true`.
- `/api/v1/stats/catalog` returns metric metadata and current period keys.
- `/api/v1/stats/server` returns server aggregate period values and histories.
- `/api/v1/stats/players?period=week&limit=100` returns week values per tracked player.
- `/api/v1/stats/player/<uuid>` and `/api/v1/stats/player/<name>` return one player's period values.
- `/api/v1/stats/leaderboard?metric=play_time_seconds&period=week&limit=10` returns ranked rows.
- `/api/v1/stats/history?metric=play_time_seconds&period=day` returns completed daily points after at least one rollover exists.

## Security/threading regression
- Missing/wrong Bearer token still returns 401.
- POST remains unavailable (405).
- Remote actions remain false/unavailable.
- Web requests must not cause server-thread exceptions or ConcurrentModificationExceptions while players join/leave or stats update.
