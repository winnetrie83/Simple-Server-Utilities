# SSU 1.3.0-dev3.3 test plan

## Installation

1. Build with Java 25 and the included Gradle 9.2.1 wrapper.
2. Remove every older SSU JAR from both client and server.
3. Install the dev3.3 JAR on both sides.
4. Keep a world backup before testing.
5. Protocol remains 11, but mixed development versions are not a supported test setup.

## Compilation gate

- Run `gradlew clean build`.
- Confirm that `BlockTexturePalette`, `TerrainColorSampler`, `AerialMapAtlas`, `MinimapTerrainMap`, `ClaimTerrainMap` and `WorldMapTerrainMap` compile without mapping errors.
- Confirm that no JourneyMap package is required at runtime. JourneyMap must not be a dependency of SSU.

## Visual source tests

Test with the default resources first, then repeat with a visibly different resource pack.

- View an upright log from above: the map should favour its ring/top texture rather than bark.
- Compare grass, sand, stone, paths, planks, roofs, slabs and stairs.
- Compare flowers, crops, leaves, glass-like cutouts and the ground below them.
- Compare shallow and deep water and verify biome water tint.
- Inspect at least one compatible modded block with a custom model.
- Change/reload the resource pack and verify that newly rebuilt tiles use the new textures.

## Minimap

- Walk through several chunks and watch frame pacing.
- Verify that no grey refresh sheet or partially rebuilt texture becomes visible.
- Check that the 4×4-per-block detail is visible at larger minimap sizes.
- Test circle/rectangle, all corners, north-up/player-up and sizes 64–256.
- Confirm claim and region overlays still align with chunk and region boundaries.
- Teleport far away and confirm the last complete image remains until the replacement is ready.

## Claim map

- Open several claim groups containing forests, water, roads and buildings.
- Verify that normal outlines remain one GUI pixel and highlights remain two pixels.
- Check that terrain no longer consists mainly of repeated 4×4 flat squares.
- Confirm selection, create, expand and shrink actions are unchanged.
- Use the World Map switch and return to the claim map.

## World map

- Press `M` and test every zoom level.
- Pan across cached and unknown terrain.
- Look for chunk seams, unstable colour changes and shimmering while zooming.
- Verify that distant zoom levels use smooth, readable colours rather than noisy aliasing.
- Check claims/regions toggles and switching to the claim map.

## Performance and memory

- Watch client frame time while new chunks are first captured.
- Travel continuously for at least 15 minutes.
- Reopen all three maps several times.
- Check that memory stabilizes as the 2,048-tile per-dimension LRU limit begins evicting old session tiles.
- Report the resource-pack resolution, render distance, minimap size and any repeatable stutter location.

## Regression checks

- Dashboard opens and closes normally.
- Profile, Settings and Admin Center remain functional.
- Claims, regions, homes, warps, economy, rentals, ranks and permissions retain their data.
- Client/server protocol registration remains version 11.

## Useful feedback screenshots

For comparison, capture the same location in:

1. normal first-person view from a nearby height;
2. SSU minimap at maximum size;
3. SSU claim map;
4. SSU world map at two zoom levels.

A location containing forest, coast, roads, roofs and elevation changes gives the most useful comparison.
