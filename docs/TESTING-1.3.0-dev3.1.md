# SSU 1.3.0-dev3.1 manual test plan

Use a backup test world. Install the same dev3.1 JAR on client and server. Dev3 and dev3.1 both use protocol `10`, but using the same build on both sides is still recommended.

## Build and startup

1. Run `gradlew.bat build` with Java 25.
2. Confirm `build/libs/simpleserverutilities-1.3.0-dev3.1.jar` exists.
3. Start a client and test server with the JAR installed on both.
4. Confirm existing ranks, permissions, claims, homes, warps, regions, rentals and economy balances remain present.

## Full claim map

1. Open `/claims gui` at radius 6 and radius 12.
2. Confirm terrain is sharper than dev3 and no large nearest-neighbour pixels dominate at normal GUI scale.
3. Compare flat ground, hills, water, forest and built structures; slopes should have visible but not excessively dark relief.
4. Confirm ordinary green/purple/yellow contours are approximately half the previous thickness.
5. Select chunks and confirm selected/highlighted contours remain visible at two pixels.
6. Confirm click selection, right-drag panning, scrolling, zoom, create, expand and remove still work.

## Minimap refresh and movement

1. Stand still with the minimap visible for at least three minutes.
2. Confirm no grey checkerboard or grey layer appears during the 40-tick server refresh cycle.
3. Walk and sprint continuously for at least 200 blocks.
4. Confirm terrain scrolls by complete block steps without the map being wiped during cache refreshes.
5. Cross claim and region borders while walking; overlays must remain aligned with terrain.
6. Teleport at least 100 blocks in the same dimension. The old complete image may remain briefly while the new cache builds, but no partial grey refresh may be shown.
7. Change dimension and return. Confirm the new dimension map appears after its initial background build and no stale overlay remains.
8. Test 64, 96, 128, 192 and 256 GUI-pixel sizes.
9. Test rectangle/circle, all four corners, north-up/player-up, and both overlay toggles.

## Dashboard textures

1. Open the dashboard with `U` or `/ssu menu`.
2. Hover Claims, Travel, Wallet and Regions.
3. Confirm the normal metal button remains visible and the glow is layered over it.
4. Move off each tile and confirm only the glow disappears.
5. Hover the bottom Back/Close button and confirm the same base-plus-glow behaviour.

## Portrait, Close and Profile

1. Confirm the 3D player is slightly lower inside the portrait frame than in dev3.
2. Leave the menu open for ten seconds and confirm the portrait gently bobs/sways rather than remaining fully static.
3. Confirm the portrait does not visibly escape the frame or cover the name.
4. Confirm an X button exists at the upper right and closes the menu immediately.
5. Confirm Settings and, for admins, Admin Center still open from the shifted header buttons.
6. Confirm Claims/Homes/Warps counters are absent below the balance.
7. Open Profile and verify name, base rank, admin state, balance, claim count/chunk total, homes, warps, rentals, border states and minimap summary.
8. Use Back from Profile and confirm it returns to the page from which Profile was opened.

## Regression

1. Open every main dashboard module.
2. Test payment, travel, claim-map opening, region rent controls, Settings and Admin Center.
3. Verify small windows/large GUI scale hide the entire portrait sidebar rather than overlapping the content.
4. Reconnect and restart the server; no UI setting or gameplay data may be lost.
