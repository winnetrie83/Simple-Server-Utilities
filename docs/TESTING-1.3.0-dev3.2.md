# SSU 1.3.0-dev3.2 test plan

Use the same dev3.2 JAR on client and server. Protocol 11 is intentionally incompatible with dev3/dev3.1 clients.

## 1. Startup and compatibility

1. Back up the test world.
2. Build with Java 25 using `./gradlew build`.
3. Install the resulting JAR on client and server.
4. Confirm the world loads without migration errors.
5. Confirm existing claims, regions, homes, warps, ranks, permissions, economy and rentals remain present.

## 2. M-key world map

1. Join the world and press `M` while no other screen is open.
2. Confirm the World Map opens.
3. Confirm `M` appears as `Open SSU world map` in Controls and can be rebound.
4. Confirm the map is not a pause screen.
5. Confirm `Done` closes it.

## 3. Navigation

1. Right-drag the map and release.
2. Confirm the viewport pans in the expected direction without a partially drawn frame.
3. Scroll up and down over the map.
4. Confirm zoom changes through the supported levels.
5. Use all directional buttons.
6. Use `Center` and confirm the viewport returns to the player's chunk.
7. Confirm the coordinate readout below the map follows the mouse.

## 4. Claim-map switching

1. Open the world map with `M`.
2. Press `Open claim map` or the lower `Claim map` button.
3. Confirm the claim map opens around the same viewport center.
4. Press `World map` at the bottom of the claim map.
5. Confirm the world map reopens around the same area.
6. Repeat after panning away from the player.

## 5. Terrain quality

Test in a mixed area containing forest, grass, sand, water, hills and player buildings.

1. Compare the minimap, world map and claim map.
2. Confirm the same terrain colours and relief direction are used in all three.
3. Confirm grass and foliage vary with biome colour where Minecraft supplies a tint.
4. Confirm deeper water is darker than shallow water.
5. Confirm hills have continuous relief without obvious chunk-edge lighting seams.
6. Confirm individual blocks/buildings are clearer than in dev3.1.
7. Confirm the claim map remains readable at radius 5 and radius 12.

## 6. Resolution and smooth publication

1. Set the minimap to 256 pixels.
2. Confirm it remains sharp and does not show grey refresh flashes.
3. Walk continuously for at least three minutes across chunk boundaries.
4. Confirm completed terrain remains visible while background updates occur.
5. Pan and zoom the world map repeatedly.
6. Confirm the old complete viewport remains visible until the new viewport is ready.
7. Confirm no horizontal partially rendered rows appear.

## 7. Explored atlas

1. Walk or fly several chunks away from spawn.
2. Return and open the world map.
3. Confirm terrain visited during this game session remains visible even when some of those chunks are no longer loaded.
4. Travel into another dimension and confirm its terrain is kept separate.
5. Log out and back in; confirm the current dev3.2 limitation that the atlas starts fresh for the new session.

## 8. Overlays

1. View your own claims, trusted claims, other-player claims and server regions.
2. Confirm ordinary world-map outlines are one GUI pixel.
3. Confirm overlay fills remain transparent enough to see terrain clearly.
4. Toggle Claims and Regions independently.
5. Confirm toggles affect only the world-map presentation and do not alter server data.
6. Confirm claim-map selection and editing still work normally.

## 9. Performance

1. Test with normal render distance and the minimap enabled.
2. Watch client frame time while moving quickly.
3. Open the world map at radius 32 and allow it to finish.
4. Confirm the server does not load new chunks because of any map screen.
5. Confirm network traffic contains overlay metadata only, not terrain/block data.
6. Report persistent FPS drops, memory growth or visible stutter with the reproduction steps and GUI scale.

## 10. Nether and special dimensions

1. Open all three maps in the Nether.
2. Record whether the roof dominates the surface view.
3. Test a custom dimension if available.
4. Confirm switching dimensions never mixes terrain tiles or overlays.
