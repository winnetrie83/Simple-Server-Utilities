# Simple Server Utilities storage examples

These files show the new split JSON save layout generated inside a world save folder:

```text
<world>/simpleserverutilities/
  permissions/
    ranks/*.json
    players/*.json
    dimensions/*.json
    claim_context/*.json
  homes/players/*.json
  player_claims/
    claims/*.json
    limits/*.json
    player_index.json
  regions/
    _settings.json
    entries/*.json
  region_snapshots/*.json
  warps.json
```

Old single-file saves are migrated automatically on server start:

```text
permissions.json      -> permissions/...
homes.json            -> homes/players/...
player_claims.json    -> player_claims/...
regions.json          -> regions/...
```

The old file is kept as `*.legacy-YYYYMMDD-HHMMSS`. Broken files are moved to `*.broken-YYYYMMDD-HHMMSS` so one corrupt JSON file does not destroy the whole storage system.
