# Permission & Rank Core — 1.3.0-dev1

## Effective permission order

SSU now treats rank permissions as the player's base and personal permissions as the final override:

1. personal permission
2. compatible world-context rule (region, claim role or dimension)
3. assigned rank permission, including rank inheritance
4. command/module fallback

The context layer remains internally available for existing regions and claim roles, but it is no longer exposed as a separate primary administration workflow. An explicit personal value always wins.

## Default rank

Server-wide permission policy is stored in:

```text
simpleserverutilities/permissions/settings.json
```

Default settings:

```json
{
  "schema": 1,
  "defaultRank": "default",
  "assignDefaultRankOnFirstJoin": true
}
```

When a player joins, SSU creates or refreshes the player's permission profile. If the player has no rank, the configured default rank is assigned automatically.

Changing the default rank does not forcibly replace ranks already assigned to existing players.

## Unified commands

### Rank management

```mcfunction
/ssu rank list
/ssu rank create <rank>
/ssu rank delete <rank>
/ssu rank rename <old> <new>
/ssu rank setdefault <rank>
/ssu rank assign <player> <rank>
/ssu rank reset <player>
/ssu rank info <rank>
```

`assign` selects one base rank for the player. Personal permissions are preserved. `reset` assigns the configured default rank.

### Rank permissions

```mcfunction
/ssu perm rank <rank> list
/ssu perm rank <rank> set <permission> <value>
/ssu perm rank <rank> unset <permission>
```

### Personal permissions

```mcfunction
/ssu perm player <player> list
/ssu perm player <player> set <permission> <value>
/ssu perm player <player> unset <permission>
```

### Effective check

```mcfunction
/ssu perm check <online-player> <permission>
```

The check displays the direct personal override separately from the effective value.

## Claim limits

Claim limits no longer use a separate player-limit command path. Configure them as normal permissions:

```mcfunction
/ssu perm rank member set ssu.claims.max_groups 3
/ssu perm rank member set ssu.claims.max_chunks 100
/ssu perm rank member set ssu.claims.max_chunks_per_group 50
```

Personal progression or an admin override uses the same keys:

```mcfunction
/ssu perm player Dev set ssu.claims.max_groups 5
/ssu perm player Dev unset ssu.claims.max_groups
```

Old explicit `/claims chunks` and `/claims groups` values are migrated once into personal permissions. The old command branches are no longer registered.
The permission write is flushed before the legacy limit records are retired; if that flush fails, the old records are retained for a later retry.

## New GUI-related permission keys

```text
ssu.settings.use
ssu.admin.menu
ssu.minimap.use
```

- `ssu.settings.use`: allows access to personal SSU settings.
- `ssu.admin.menu`: controls visibility of the future dedicated admin button/menu.
- `ssu.minimap.use`: controls whether the player may enable the SSU minimap.
