# SSU dev4.3 map-topography refinement

This renderer revision was designed after a clean-room behavioural comparison with the user-supplied JourneyMap 6.0.2 JAR. The comparison was limited to public class metadata and bytecode-observable concepts such as block categories and multi-distance slope sampling. SSU does not include JourneyMap source, bytecode, textures, constants, assets or configuration files.

## Problem in dev4.2

Dev4.2 correctly removed decorative plants and used the ground below tree crowns for topographic height. That made the map calmer, but it also meant leaf columns had no independent visible-surface relief. Depending on biome tint and resource pack, crowns could therefore look like flat pale stains over otherwise shaded terrain.

## Dual-height model

Every mapped column now records:

- **terrain height**: ground/solid relief below decorative plants and below a tree crown;
- **mapped-surface height**: the visible top of solid blocks, water or leaves;
- **surface kind**: solid, canopy or water.

Broad and macro shading use terrain height, so mountains remain readable through forests. Canopies use the mapped-surface height for a separate restrained local pass, so trees have shape without being interpreted as mountain cliffs.

## Terrain lighting

The final terrain light combines:

- a restrained local 3x3 gradient;
- a two-distance north-west directional terrace cue;
- a six-block broad hill/valley pass;
- a sixteen-block macro landform pass.

The directional terrace cue samples nearby and secondary north/west neighbours, but uses independently designed SSU calculations and limits. It does not restore the high-frequency texture-edge noise removed in dev4.2.

## Canopy rendering

Leaf crowns are:

- top-down opaque;
- independently desaturated and darkened;
- luminance-compressed so bright biome foliage does not become a pale spot;
- locally shaded by actual neighbouring leaf heights;
- highlighted slightly on north/west exposed edges;
- shadowed on east/south exposed edges;
- clamped at forest boundaries so a crown-to-ground transition does not become a giant relief cliff.

## Persistence

Aerial cache tile format version 3 stores both heightfields and the surface-kind byte for all 256 columns in a chunk. The renderer fingerprint changed to `atlas-topographic-v4`, so older dev4.2 tiles are not mixed with the new metadata and rebuild automatically.

This is client map-cache data only. The network protocol remains 12 and no server/world save schema changed.
