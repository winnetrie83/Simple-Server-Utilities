# SSU 1.3.0-dev1 manual test plan

## Required setup

- Back up the test world.
- Use 1.3.0-dev1 on both client and server.
- Do not mix protocol 8 and protocol 9 builds.

## Default rank

1. Run `/ssu rank list` and note the configured default rank.
2. Join with a player that has no SSU permission profile.
3. Run `/ssu perm player <player> list`.
4. Confirm the player received the default rank.
5. Create a rank and make it default:
   - `/ssu rank create member`
   - `/ssu rank setdefault member`
6. Join with another new player and confirm that player receives `member`.
7. Confirm an existing player's assigned rank was not silently replaced.

## Rank and personal precedence

1. Set a rank value:
   - `/ssu perm rank member set ssu.claims.max_groups 3`
2. Assign the rank:
   - `/ssu rank assign Dev member`
3. Open the claim map and confirm it shows a maximum of 3 claim groups.
4. Set a personal override:
   - `/ssu perm player Dev set ssu.claims.max_groups 5`
5. Reopen the claim map and confirm it shows 5.
6. Remove the personal override:
   - `/ssu perm player Dev unset ssu.claims.max_groups`
7. Reopen the map and confirm it returns to 3.
8. Use `/ssu perm check Dev ssu.claims.max_groups` after each step.

## Legacy claim-limit migration

1. Start from a dev2.1 world with an explicit `/claims groups <player> set <number>` value.
2. Start dev1 once.
3. Run `/ssu perm player <player> list`.
4. Confirm the value now appears as `ssu.claims.max_groups` personal permission.
5. Confirm the claim map uses the migrated value.
6. Confirm `/claims groups` and `/claims chunks` are no longer registered.

## Rank administration

Test:

```mcfunction
/ssu rank create tester
/ssu rank rename tester tester2
/ssu rank assign Dev tester2
/ssu rank reset Dev
/ssu rank delete tester2
```

Confirm personal permissions on `Dev` remain after rank assignment/reset.

## Admin menu permission

1. Set `ssu.admin.menu = false` personally on an admin-rank player.
2. Reopen the dashboard and confirm the admin-only state is hidden for a non-OP player.
3. Remove the personal value and confirm the rank wildcard or rank permission applies again.

## Player settings storage

1. Run `/ssu settings`.
2. Change each minimap preference using `/ssu settings minimap ...`.
3. Restart the server.
4. Run `/ssu settings` again and confirm every value persisted.
5. Confirm files appear below `simpleserverutilities/player_settings/`.

## Regression checks

- Create, expand and remove claim chunks through the claim map.
- Confirm rank-based `ssu.claims.max_chunks` and `ssu.claims.max_chunks_per_group` still apply.
- Confirm economy, paid region renting, homes and warps still work.
- Confirm `/ssu menu` opens and the existing pages still render.
- Confirm supplied textures are present in the built JAR resource path.
