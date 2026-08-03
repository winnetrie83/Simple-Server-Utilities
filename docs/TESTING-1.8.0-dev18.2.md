# SSU 1.8.0-dev18.2 test checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev18.2 build on client and server; protocol is 67.
- Confirm an existing dev18.1.1 world loads without storage migration errors.

## Claim deletion and Homes

1. Create two homes in one owned claim and one home in another owned claim.
2. Delete the first claim through the Claim Map. Confirm only the two linked homes disappear.
3. Repeat through Admin Center → Player Claims and through the legacy claim-delete command.
4. Restart the server and confirm deleted homes do not return.
5. Confirm deleting or shrinking unrelated claim chunks does not remove homes that remain inside the final claim.

## Claim Map navigation

1. Create claims far apart in the same dimension.
2. Open Claim Map and use previous/next. Confirm terrain and overlays recenter on each selected claim.
3. Confirm the selected claim is visible and editable at the remote viewport.
4. Confirm the Center button returns to the player.
5. Confirm claims in another dimension are not mixed into the current dimension list.
6. Trigger a disconnected-selection error and confirm the full notice stays inside the bottom frame.

## Player Travel

1. Confirm Travel lists permitted claim-linked Homes, Warps and Server Spawn.
2. Confirm All, Homes, Warps and Other filters work; Other currently contains Server Spawn.
3. Confirm search works together with each filter.
4. Confirm rows contain only shortcut actions: Teleport; no create, move, edit or delete controls.
5. Confirm denied/disabled destination types are not listed and server-side teleport policy is still enforced.
6. Confirm Cancel teleport works.

## Admin Travel Management

1. Open Admin Center → Travel Management.
2. Test All, Warps and Spawn filters plus search.
3. Create a warp, move an existing warp, teleport to it and delete it with confirmation.
4. Set server spawn, test teleport and clear it.
5. Confirm controls are disabled when the effective admin permission is missing and actions are denied server-side when attempted through packets/commands.
6. Test compact and large GUI scales for overlap and pagination.
