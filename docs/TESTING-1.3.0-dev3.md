# SSU 1.3.0-dev3 manual test plan

Use a backup test world. Install the same dev3 JAR on the NeoForge client and server. Dev3 uses network protocol `10`; a dev2 client or server uses protocol `9` and must be rejected rather than silently connecting.

## 1. Startup and compatibility

1. Build with Java 25 using `./gradlew build`.
2. Confirm the generated JAR reports version `1.3.0-dev3`.
3. Start a dedicated server and matching client.
4. Join an existing SSU test world containing claims, regions, ranks, permissions, homes, warps, economy data and region rents.
5. Confirm there are no data migration errors and the existing systems still work.

## 2. First minimap appearance

1. Join with `ssu.minimap.use` allowed.
2. Confirm the minimap appears without opening the SSU dashboard first.
3. Confirm terrain gradually replaces the initial checker pattern.
4. Walk several blocks and confirm the map recenters without freezing the game.
5. Cross at least two chunks quickly or teleport within the same dimension and confirm overlays refresh early.
6. Change dimension and confirm the old dimension is not displayed while the new snapshot is loading.

## 3. HUD information

1. Confirm the player marker remains centered.
2. With north-up enabled, rotate the player and confirm only the marker rotates.
3. Confirm the `N` indicator remains at the top in north-up mode.
4. Disable north-up and rotate the player; confirm the terrain rotates while the marker points forward.
5. Confirm the shown X/Z coordinates follow the player.
6. Open inventory, chat, dashboard and claim map screens; confirm the HUD minimap is hidden while a screen is open and returns after closing it.

## 4. Shape, position and size

Test both the graphical Settings page and command fallback.

1. Toggle enabled off/on.
2. Test circle and rectangle shapes.
3. Test top-left, top-right, bottom-left and bottom-right.
4. Test sizes 64, 96, 128, 192 and 256.
5. Repeat with a small window or large GUI scale and confirm the map and coordinate label stay on-screen.
6. Restart the client/server and confirm settings persist.

## 5. Claims overlay

Prepare nearby chunks in these states:

- wilderness;
- owned by the testing player;
- owned by another player who trusts the testing player;
- owned by another player without trust.

Then:

1. Enable claim overlays.
2. Confirm own, trusted and other claims use distinct presentation.
3. Confirm chunk outer edges are stronger than the translucent fill.
4. Add or remove a nearby claim and wait for the next refresh; confirm the minimap updates.
5. Disable claim overlays and confirm all claim tinting disappears while terrain remains.

## 6. Region overlay

1. Create or use a server region intersecting the visible map.
2. Enable region overlays.
3. Confirm the region interior is lightly tinted and exact outer boundaries are stronger.
4. Walk across the boundary and verify alignment with world coordinates.
5. Test a region partially outside the minimap area.
6. Disable region overlays and confirm region tinting disappears.

## 7. Permissions

1. Set a personal or rank permission `ssu.minimap.use=false`.
2. Confirm the server disables and hides the minimap even if the player's saved preference says enabled.
3. Restore permission and confirm it returns after refresh.
4. Confirm ordinary clients cannot submit coordinates or ownership data in the minimap request; the server uses the requesting player's actual state.

## 8. Terrain and dimension edge cases

1. Test plains, forest, water, steep terrain and player-built structures.
2. Move toward unloaded terrain and confirm missing chunks remain a checker pattern without forced chunk loading.
3. Test Overworld, End and Nether.
4. In the Nether, note that this first surface renderer may show the upper surface/roof rather than the current cave layer.
5. Disconnect and reconnect; confirm no stale texture or old-dimension overlay remains.

## 9. Performance soak

1. Sprint or fly continuously for at least five minutes with a 256-pixel minimap.
2. Rotate continuously in player-up mode.
3. Test with many claims and regions near the player.
4. Watch client FPS/frame pacing and server tick time.
5. Confirm no repeated packet flood, texture leak, forced chunk generation or growing error log.

## 10. Regression checks

Re-test:

- `U` dashboard opening;
- graphical Settings;
- Claims and the full-screen claim map;
- homes and warps;
- Wallet and payments;
- rentable regions;
- Admin Center;
- rank assignment and personal permission overrides;
- claim and region world-border visualization.
