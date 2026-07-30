# Spawn and Contextual Region Permissions — 1.4.0-dev3

## Persistent server spawn

SSU now owns one optional server-wide spawn destination. It stores the dimension, exact position, yaw, pitch, last administrator and update time in:

```text
<world>/simpleserverutilities/spawn/server_spawn.json
```

The record is loaded through the Core 2.0 module lifecycle and written through the coalescing storage worker. Existing worlds require no migration; the file is created only after an administrator sets a spawn.

Commands:

```text
/spawn
/spawn set
/spawn clear
/spawn info
/spawn cancel
/setspawn
/delspawn
```

The dashboard Travel page also lists Server Spawn. Players with `ssu.spawn.admin` receive **Set spawn here** and **Clear spawn** controls.

## Spawn permissions

| Permission | Default | Meaning |
|---|---:|---|
| `ssu.spawn.use` | `true` | Use `/spawn` or the Travel-page spawn button |
| `ssu.spawn.admin` | `false` | Set, clear and inspect the server spawn |
| `ssu.spawn.teleport.delay` | `0` | Spawn countdown in seconds |
| `ssu.spawn.teleport.cooldown` | `0` | Spawn cooldown in seconds |
| `ssu.spawn.region_bypass` | `false` | Ignore an explicit effective-region deny for `ssu.spawn.use` |

Global teleport permissions still apply. `ssu.teleport.delay.bypass` removes the countdown, `ssu.teleport.cooldown.bypass` removes the cooldown and `ssu.teleport.cancel_on_move` controls movement cancellation.

## Authoritative region deny

An explicit `ssu.spawn.use = false` on the player's effective region is treated as an escape-prevention rule:

- an ordinary personal or rank `ssu.spawn.use = true` cannot override it;
- `ssu.spawn.region_bypass = true` can ignore only that region layer;
- personal, claim-context, dimension or rank denies outside the region layer still apply;
- operators retain their normal administrator bypass;
- only the highest-priority containing region is considered, matching existing nested-region resolution.

For example, to block `/spawn` inside `servertestarea`:

```text
/regions perm servertestarea set ssu.spawn.use false
```

To return to inherited/default behaviour:

```text
/regions perm servertestarea unset ssu.spawn.use
```

## Guarded delayed teleport

The teleport manager now accepts an optional execution guard. `/spawn` checks its policy when requested and checks it again when a delayed teleport becomes due. If the player enters a blocking region during the countdown, the request is cancelled before destination safety resolution and no cooldown is applied.

Existing home, warp, claim and region teleports continue using the original overload and retain their current behaviour.

## Visual region-permission editor

Region Settings now contains a **Permissions** row for players with region edit/admin permission. It opens a dedicated editor with:

- server-side permission search;
- eight entries per page with bounded pagination;
- every built-in SSU permission plus custom keys already stored on the region;
- **Default**, **Allow** and **Deny** controls for booleans;
- validated input plus **Set** and **Reset** for integer/text values;
- tooltips showing description, type/range, direct region override, fallback preview and resolution source.

Region managers can continue changing ordinary protection/message settings, but permission overrides require `ssu.regions.edit` or `ssu.regions.admin`. Every request and mutation is revalidated on the server. The GUI and `/regions perm` command mutate the same normalized region map and invalidate the shared permission-resolution cache after saving.

## Compatibility

- Region JSON remains schema-compatible; permission keys are normalized to lowercase during load/write.
- Existing region overrides are immediately visible in the GUI.
- Existing world/player data is unchanged.
- Network protocol is `18`, so client and server must run the exact same dev3 build.
