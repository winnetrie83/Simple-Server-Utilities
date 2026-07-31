# SSU 1.6.0-dev10 test checklist

Use Java 25 and build with `gradlew.bat clean build`. Install the exact same dev10 JAR on the client and server. Network protocol remains 31, but using matching builds is required.

## 1. Dashboard placement

1. Open the dashboard at a resolution where the portrait sidebar is visible.
2. Confirm Profile is no longer present as a tile in the main player dashboard grid.
3. Confirm a Profile button appears below the player name, rank and balance in the portrait sidebar.
4. Open Profile and confirm the button becomes inactive while that page is selected.
5. Open Claims, Travel, Wallet, Mail, Settings and Admin pages and confirm the Profile button remains in the same sidebar position.
6. Confirm the Back texture is horizontally centered inside the sidebar and its clickable area matches the texture.
7. Reduce the window until the portrait sidebar disappears and confirm Profile remains reachable through a normal dashboard tile.

## 2. Region visibility authority and duplicate rendering

Prepare at least one region inside the configured region-border render distance.

1. Enable the Server Regions module and allow `ssu.borders.regions.view` for the test player.
2. Turn the player's Region borders setting OFF. Confirm no automatic or individually selected region box is visible.
3. Select that region with the Regions-page Show action or `/regions show <name>`. Confirm it remains invisible while the personal master setting is OFF.
4. Turn Region borders ON. Confirm the selected region appears exactly once, without a darker/doubled wireframe or fill.
5. Move around and cross chunk boundaries. Confirm the region remains single-rendered after overview refreshes.
6. Select several regions. Confirm every selected region appears once and ordinary nearby regions also appear once.
7. Clear selected region borders. Nearby regions may remain visible through the enabled overview, but no region may be rendered by both overview and focus layers.
8. Deny `ssu.borders.regions.view` or disable the Server Regions module. Confirm all region overview and selected-region borders clear immediately and the personal Region borders control is disabled.
9. Restore server permission/module access. Confirm borders return only when the player's Region borders setting is still ON.

## 3. Claim duplicate rendering and priority

Prepare an owned claim inside the configured claim-border render distance.

1. Enable Player Claims and allow `ssu.borders.claims.view`.
2. Turn Claim borders ON and confirm the nearby claim overview appears once.
3. Use Show on the same claim. Confirm its ribbon is still rendered exactly once rather than once in CLAIM and once in CLAIM_FOCUS.
4. Move between chunks and refresh borders. Confirm no duplicate ribbon appears.
5. Turn Claim borders OFF. Confirm both overview and focused claim layers clear.
6. Deny `ssu.borders.claims.view` or disable Player Claims. Confirm the border stays hidden and the personal Claim borders control is disabled.
7. Restore access and confirm the player's personal setting remains the final on/off choice.

## 4. Permission refresh

1. While a player stands still with borders visible, remove the matching border-view permission through the permission editor.
2. Confirm the border clears without requiring movement, relogging or `/ssu borders refresh`.
3. Restore the permission or assign a rank that grants it and confirm the border state refreshes immediately.
4. Repeat with a dimension or region permission override if those are used on the server.

## 5. Regression checks

- Region selection-tool boxes remain available to authorized administrators and are not confused with normal region borders.
- Minimap and world-map claim/region overlays continue to follow their own map settings.
- Region and claim colors and configured render distances remain unchanged.
- Existing selected-region preferences load without migration errors.
- No network decode errors occur and protocol remains 31.
