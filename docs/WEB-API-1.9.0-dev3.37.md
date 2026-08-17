# SSU Web API v1 - community statistics (dev3.37)

The API remains authenticated and **read-only**. dev3.37 adds analytics snapshots for the future Winnetrie website/community pages.

## Community-stat configuration
```toml
enableCommunityStatistics = true
communityStatsHistoryDays = 90
communityStatsSeasonId = "season-1"
```

- Lifetime never resets automatically.
- Day/week/month use UTC period keys.
- Changing `communityStatsSeasonId` starts a new season bucket and archives the previous season.
- Completed day history is configurable from 7 to 730 entries. Weekly/monthly/season retention is bounded internally.

## Existing Web API configuration
```toml
enableWebApi = true
webApiBindAddress = "127.0.0.1"
webApiPort = 8765
webApiToken = "replace-with-a-long-random-secret"
webApiAllowedOrigin = ""
```

Every request still requires `Authorization: Bearer <token>`.

## Statistics endpoints

### `GET /api/v1/stats/catalog`
Returns current period keys and the stable metric catalog. Each metric includes:
- `id`
- `displayName`
- `category`
- `unit`
- `scale` (`100` means divide raw value by 100 for display)
- `leaderboardSafe`
- `derived`

### `GET /api/v1/stats/server`
Returns server aggregate current periods plus completed daily/weekly/monthly/season histories.
Current-period server buckets also expose bounded breakdown maps when Content Core supplies subject/dimension/movement/role/team metadata.

### `GET /api/v1/stats/players?period=week&limit=100`
Returns a bounded list of tracked players and their values for one period. Valid periods are `lifetime`, `day`, `week`, `month`, `season`.

### `GET /api/v1/stats/player/<uuid-or-name>`
Returns all current period value maps for one tracked player.

### `GET /api/v1/stats/leaderboard?metric=minigame_wins&period=week&limit=10`
Returns descending player ranks for any known metric. The response repeats `leaderboardSafe`; websites should use this signal when a leaderboard is tied to meaningful rewards.

### `GET /api/v1/stats/history?metric=play_time_seconds&period=day`
Returns completed server aggregate history points for `day`, `week`, `month` or `season`.

## Initial metric families
Activity, world/building, combat, crafting, exploration, claims, auctions, quests, NPC interactions, progression/achievements, minigames and dungeons are tracked through the existing Content Event Core.

Derived metrics include:
- `active_days` (player)
- `active_players` (server)
- `player_active_days` (server engagement volume)
- `unique_biomes`
- `unique_dimensions`

## Competitive safety
Some counters are deliberately marked `leaderboardSafe=false`. For example raw block-breaking is useful for a server-wide community progress bar but can be farmed by repeatedly placing/breaking blocks. The metadata lets the website distinguish fun/raw activity from stats appropriate for ranked rewards.

## Threading
Community statistics are mutated only on the Minecraft server thread. The manager periodically creates immutable analytics views. `SsuWebBridge` copies those views into its HTTP snapshot; HTTP worker threads never traverse live Minecraft worlds, players or mutable statistic records.

## Future layers
This foundation is intended for:
- configurable community goals/challenges;
- lifetime/weekly/monthly/season leaderboards;
- activity trend charts;
- player profile pages and badges;
- minigame/dungeon-specific dashboards;
- later scoped/audited remote admin actions (still intentionally absent in dev3.37).
