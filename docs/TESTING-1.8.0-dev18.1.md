# Testing — SSU 1.8.0-dev18.1

Use the exact same dev18.1 build on client and server. Network protocol: **66**.

## Upgrade and persistence

1. Start a copy of a dev18.0 world containing existing claims, homes and border preferences.
2. Confirm the world loads without deleting or renaming any existing home or claim.
3. Confirm `simpleserverutilities/visualization/players/*.json` migrates to schema 3 after save.
4. Restart and confirm the selected claim borders and **Show other claims** state persist.

## Per-claim borders

1. In Settings → Borders, turn **Enable claim borders** off.
2. In Claims & Land, press **Show** on one owned claim. Confirm the button changes to **Hide**, but no outline renders while the master gate is off.
3. Enable claim borders. Confirm only the selected owned claim renders.
4. Select a second claim and confirm both render independently.
5. Hide either claim and confirm the other remains visible.
6. Turn **Show other claims** on/off near another player's claim and confirm only non-owned outlines change.
7. Turn the master gate off and confirm every claim outline clears while selections remain stored for re-enabling.
8. Delete a selected claim and confirm other selected claim outlines remain intact.
9. Deny `ssu.border.claims.view` and confirm both border setting buttons and claim Show/Hide controls are unavailable and no border renders.

## Claim-bound Homes GUI

1. Open Claims & Land → a claim's Settings → Homes → Manage.
2. Confirm only homes whose saved coordinates fall inside that exact connected claim appear.
3. Stand inside the selected claim, create a home, teleport to it and delete it using the GUI.
4. Move an existing same-name home while inside the selected claim and confirm it updates instead of consuming another limit slot.
5. Stand outside the selected claim or inside a different owned claim. Confirm **Save here** is disabled and the server rejects a forged action.
6. Reach the `ssu.homes.max` limit and confirm a new home is rejected with the effective limit, while moving an existing home still succeeds.
7. Deny `ssu.homes.set`, `ssu.homes.teleport` and `ssu.homes.delete` separately and confirm the matching controls disable and direct packets/legacy commands remain server-denied.
8. Open Claim Settings from both the dashboard and the interactive Claim Map; confirm **Homes → Manage** reaches the selected claim in both paths.
9. Confirm old homes outside claims remain stored and command teleport remains compatible, but `/homes sethome` cannot create or move a home outside an owned claim.

## Regression checks

- Claim map create/expand/shrink/delete and Trusted Players still work.
- Admin claim teleport remains admin-only.
- Region borders and map/minimap claim overlays are unaffected.
- `/claims show`, `/claims hide`, `/homes`, `/homes tp` and `/homes delhome` remain registered.
- Dashboard works at small, default and large GUI scales.
