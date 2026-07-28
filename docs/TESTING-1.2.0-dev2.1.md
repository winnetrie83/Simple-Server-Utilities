# SSU 1.2.0-dev2.1 test plan

## Claim limit permission hotfix

1. Give a rank `ssu.claims.max_groups = 3`.
2. Assign the rank to an online player.
3. Run the permission check and confirm the integer result is 3.
4. Open the claim map and confirm `Claims: <used> / 3`.
5. Create claims until the third claim is accepted and the fourth is rejected.
6. Set a personal override with `/claims groups <player> set 5`; confirm the map shows 5.
7. Clear it with `/claims groups <player> clear`; confirm the rank value 3 applies again.
8. Repeat the same checks for `ssu.claims.max_chunks` and `/claims chunks <player> clear`.

## Legacy migration

1. Start from a dev2 world containing a limit file with the old default values.
2. Upgrade to dev2.1.
3. Confirm rank permissions are no longer hidden by that file.
4. Confirm an old customized value different from the configured default remains an explicit personal override.

## Regression

- Claim map opens.
- New, Expand and Remove modes work.
- Existing claims and chunks remain present.
- Region rent and economy functionality from dev2 still work.
