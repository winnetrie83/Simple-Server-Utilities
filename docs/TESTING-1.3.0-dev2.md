# SSU 1.3.0-dev2 manual test plan

## Required setup

- Back up the test world.
- Use 1.3.0-dev2 on both client and server.
- Keep a non-admin player and an admin/operator player available.
- Test once with the normal GUI scale and once with a larger GUI scale or a smaller window.

## Opening and layout

1. Press `U` and confirm the dashboard opens.
2. Run `/ssu menu` and confirm it opens the same screen.
3. Confirm the game does not pause.
4. Confirm the custom panel, module textures, hover glow and green back button render.
5. Confirm the current player skin appears as a 3D model inside the portrait frame.
6. Drag on the player model and confirm it rotates normally.
7. Confirm player name, base rank and wallet balance are correct.
8. Resize the window or increase GUI scale.
9. Confirm the profile panel hides on narrow layouts and no module tile leaves the screen.
10. Confirm **Close** closes the homepage and **Back** returns to the preceding dashboard page.

## Homepage modules

1. Open **Claims** and confirm owned claims render.
2. Use **Map**, **Show**, **Hide border** and **Open claim map**.
3. Return from the claim map and reopen the dashboard.
4. Open **Travel** and teleport to one home and one permitted warp.
5. Open **Wallet** and confirm the balance and recent transactions match `/balance` or the economy command output.
6. Make a player payment and confirm the server validates and records it.
7. Open **Regions** and test Show/Hide, Rent, Extend and Unrent where applicable.
8. Confirm unavailable modules render disabled and cannot be opened.

## Settings page

For every setting below, click it once, close the dashboard, reopen it and confirm the new value remains visible:

- Dashboard hints
- Minimap enabled
- Minimap size
- Minimap shape
- Minimap position
- North-up
- Claim overlay
- Region overlay
- Claim borders
- Region borders

Restart the server and confirm the values still persist below `simpleserverutilities/player_settings/` and the existing visualization settings storage.

The HUD minimap itself is not expected in dev2.

## Admin visibility

1. Open the dashboard as a normal player without `ssu.admin.menu`.
2. Confirm the shield button is absent.
3. Open as an operator or player allowed to use the Admin Center.
4. Confirm the shield button is present and opens **Admin Center**.
5. Set a personal `ssu.admin.menu = false` override on a non-OP admin-rank test player.
6. Reopen the dashboard and confirm the shield disappears.
7. Remove the override and confirm rank/default resolution applies again.

## Players & Permissions

1. Open **Admin Center → Players**.
2. Enter a player and use **View player**; confirm the permission list appears in chat.
3. Enter an existing rank and use **View rank**.
4. Use **List ranks**.
5. Assign the test player a different base rank.
6. Set a personal permission such as `ssu.claims.max_groups = 5`.
7. Confirm `/ssu perm check <player> ssu.claims.max_groups` resolves to the personal value.
8. Unset the personal permission and confirm the rank/default value returns.
9. Confirm personal permissions unrelated to the rank assignment remain stored.
10. Try each action without its required permission and confirm the server refuses it.

## Admin module regression

- Wallet rent-policy percentages accept only values from 0 through 100.
- Region visibility changes refresh correctly.
- Core status reports jobs, pending writes, permission-cache values and region-index values.
- **Reset counters** still requires the normal Core administration permission.

## Existing-system regression

- Create, expand and shrink a claim through the claim map.
- Confirm rank and personal claim limits still apply.
- Create/use a home and use a global warp.
- Rent and renew a paid region; verify wallet changes and owner payout.
- Cancel a rental and verify the configured refund path.
- Restart the server and confirm claims, regions, rentals, economy, ranks, permissions and UI settings survive.
