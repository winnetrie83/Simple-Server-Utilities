# Map topography refinement — SSU 1.3.0-dev4.2

Dev4.1 made one-block differences stronger, but the result still mixed raw block-texture detail, vegetation height and relief into the same image. Forests and flowered terrain therefore looked noisy while the actual shape of hills remained difficult to read.

Dev4.2 separates three concerns: visible surface selection, cartographic colour and terrain relief.

## Visible surface selection

The column sampler still scans from the loaded chunk's world surface and never loads a chunk for the map. It now skips decorative layers before selecting the map colour:

- bush-based plants such as flowers, crops, saplings and tall grass;
- empty-collision plant-coloured blocks;
- empty-collision, non-occluding utility details with no map colour.

The following remain visible:

- full and partial collision-bearing blocks;
- leaves as closed canopies;
- water and other fluids;
- glass and other transparent structural layers;
- snow layers, paths, slabs, stairs, fences, containers and buildings.

## Calm cartographic colour

The resource pack remains the colour source, but SSU no longer copies the internal 4x4 texture pattern into every world block. Each block uses its representative average colour across its four detail pixels. This preserves biome tint and resource-pack identity while removing repeated microtexture.

The final colour grade reduces saturation to 84% and brightness to 97%. Water keeps depth shading but no longer receives a checker-pattern ripple.

## Terrain-based relief

The visible colour and the relief height are now independent.

- leaf crowns are visible but do not raise the terrain surface;
- logs below a detected canopy are skipped while finding the ground height;
- decorative plants never affect height;
- water uses its visible surface height;
- solid buildings and terrain remain part of height.

The atlas combines four height cues:

1. local Sobel hill shading for immediate slope direction;
2. broad six-block shading for hills and valleys;
3. macro twelve-block shading for whole landforms;
4. a narrow terrace rim only where neighbouring blocks truly differ in height.

There is no absolute-height modulo contour, so flat plateaus remain a single even colour.

## Compatibility

The map tile cache fingerprint changes to `atlas-topographic-v3`. Existing cached terrain is ignored and rebuilt as loaded chunks are revisited. This is client cache invalidation only.

- network protocol remains 12;
- claim, region, permission, economy and rental data are unchanged;
- snapshot format remains version 3.
