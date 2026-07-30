# SSU 1.3.0-dev3.4 test plan

Use the same dev3.4 JAR on client and server. Network protocol remains 11.

## 1. Basic loading

1. Start a dedicated or integrated server with Java 25.
2. Join with the matching client build.
3. Confirm the dashboard, minimap, world map and claim map open without disconnects or rendering exceptions.

## 2. One-pixel atlas

1. Visit an area containing grass, paths, stone, sand, water, forests and elevation changes.
2. Open the minimap, claim map and world map.
3. Confirm that blocks no longer show enlarged 4x4 texture patterns.
4. Confirm that the three maps use consistent colours for the same terrain.

## 3. Claim-map zoom

1. Open the claim map over an already rendered area.
2. Zoom in and out repeatedly with the mouse wheel and buttons.
3. Confirm that the previous complete terrain image remains visible and is scaled/cropped during the transition.
4. Confirm that the complete map does not reset to checkerboard or rebuild visibly from zero.
5. Confirm that claim outlines remain anchored to the same world chunks.
6. Repeat several zoom changes quickly and confirm an older build never replaces the newest requested view.

## 4. Claim-map panning

1. Hold the right mouse button over the map.
2. Drag at least one visible chunk width.
3. Confirm that the map, grid and claim shapes move directly with the mouse.
4. Release and confirm the new centre is retained after the server response.
5. Confirm left-click chunk selection still works when not dragging.

## 5. World-map panning and zoom overlays

1. Open the world map with M.
2. Hold right mouse and drag.
3. Confirm terrain, claims, regions and player marker move together live.
4. Release and confirm the map stays at the new centre.
5. Zoom in and out repeatedly.
6. Confirm claim and region borders resize immediately with the terrain, without a delayed second movement.
7. Quickly alternate zoom levels and confirm stale server responses do not move the map back.

## 6. Leaf canopies

1. View several dense oak, birch, spruce, jungle, acacia, dark-oak, mangrove and cherry trees where available.
2. Confirm the tree crowns are continuous foliage-coloured areas rather than holes showing the ground.
3. Confirm biome foliage tint still differs between biomes.
4. Check modded leaf blocks that use the vanilla leaves tag.

## 7. Relief

1. Compare flat plains, rolling hills, mountains, cliffs, river valleys and ravines.
2. Confirm north-west-facing and south-east-facing slopes differ visibly in brightness.
3. Confirm broad hills and valleys remain readable when zoomed out.
4. Confirm cliffs are not completely black and ridges are not pure white.
5. Compare the exact same location on minimap, claim map and world map.

## 8. Water regression

1. Check rivers, oceans, shallow shores and deep water.
2. Confirm water still renders after the dev3.3.1 water hotfix.
3. Confirm water depth produces a gradual darkening rather than disappearing.

## 9. Resource use

1. Travel continuously through new chunks for at least ten minutes.
2. Open and close all three maps several times.
3. Confirm no recurring grey refresh layer, major frame spikes or unbounded memory growth is observed.
