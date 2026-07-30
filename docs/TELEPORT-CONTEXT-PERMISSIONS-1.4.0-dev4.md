# SSU 1.4.0-dev4 — contextual teleport policy

## Scope

Dev4 applies one server-authoritative policy to every normal player-initiated SSU teleport:

- personal homes;
- server warps;
- persistent server spawn;
- owned claim spawns;
- accessible server-region spawns.

Administrative claim teleports remain a separate immediate moderation action and are not treated as a player escape request.

## Permission keys

| Key | Type | Built-in/default behaviour |
|---|---|---|
| `ssu.teleport.escape` | boolean | `true`; umbrella permission for all player-initiated SSU escape teleports |
| `ssu.teleport.region_bypass` | boolean | `false`; ignores authoritative region denies, then continues resolving non-region policy |
| `ssu.teleport.require_still` | boolean | `true`; movement during a delayed teleport cancels it |
| `ssu.teleport.cancel_on_move` | boolean | legacy fallback retained for old data |
| `ssu.teleport.delay.bypass` | boolean | `false` |
| `ssu.teleport.cooldown.bypass` | boolean | `false` |

The existing type-specific permissions continue to apply:

- `ssu.homes.teleport`
- `ssu.warps.teleport`
- `ssu.spawn.use`
- `ssu.claims.teleport`
- `ssu.regions.teleport`

Their existing delay and cooldown integer keys are unchanged.

## Resolution rules

1. SSU resolves the player's current position, effective region, claim context and dimension.
2. `ssu.teleport.escape` must allow the request.
3. The relevant type-specific permission must allow the request.
4. An explicit deny from the effective region for either key is authoritative over ordinary personal/rank allows.
5. `ssu.teleport.region_bypass=true` removes only the region layer; dimension, player and rank policy still applies.
6. For `/spawn`, the dev3 `ssu.spawn.region_bypass` remains accepted as a legacy spawn-only bypass.
7. The complete access check is repeated immediately before a delayed teleport executes.

## Standing still

`ssu.teleport.require_still` is evaluated in the same context as delay and cooldown. When it resolves to `true` and the delay is greater than zero, SSU records the player's exact starting position and dimension.

The teleport is cancelled when the player:

- walks, even without crossing a block boundary;
- jumps or falls;
- swims or is pushed;
- moves in a vehicle;
- changes dimension.

A very small tolerance is used for harmless server position corrections. Looking around does not count as movement.

Existing worlds that only contain `ssu.teleport.cancel_on_move` keep their behaviour. The new key takes precedence once explicitly configured.

## Dimension editor

Admin Center → Permissions now offers three target modes:

- Players
- Ranks
- Dimensions

The dimension list combines loaded dimensions with stored dimension scopes, so settings for temporarily unloaded custom dimensions remain editable. Boolean, integer and text values use the same PermissionCatalog normalization as the server commands.

Examples:

```text
minecraft:the_nether
  ssu.homes.teleport = false
  ssu.claims.teleport = false

minecraft:the_end
  ssu.teleport.escape = false
```

## Region examples

Prison/test area:

```text
ssu.teleport.escape = false
```

Minigame area where spawn is allowed but homes and warps are not:

```text
ssu.spawn.use = true
ssu.homes.teleport = false
ssu.warps.teleport = false
```

Allow movement during countdown inside a lobby:

```text
ssu.teleport.require_still = false
```
