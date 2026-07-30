# SSU 1.3.0-dev3.3 — clean-room map-rendering analysis

## Scope and boundary

The supplied `journeymap-neoforge-26.2-6.0.2.jar` was inspected only to understand broad rendering architecture and the public Minecraft client APIs it relies on. The SSU implementation was written independently. No JourneyMap source, bytecode fragments, textures, shaders, configuration files, identifiers or other assets were copied into SSU.

This document records concepts, not an implementation transcript of JourneyMap.

## Why the earlier SSU maps looked flatter

The dev3.2.1 renderer primarily started from Minecraft `MapColor`, then added biome tint and height shading. That reliably distinguished broad terrain materials, but it discarded much of what the player actually sees:

- the active resource pack's top-face texture;
- different top and side textures, such as log rings versus bark;
- transparent or cutout surface layers;
- dedicated fluid textures;
- stable detail levels for different zoom factors.

Increasing the output texture size alone therefore enlarged the same simplified information.

## Architectural ideas observed in the reference mod

The JAR structure and public method calls show a mature pipeline with several separated concerns:

1. **Model-derived block appearance** — block-state models and baked quads are used to find representative texture sprites, with top-facing geometry preferred where available.
2. **World tinting** — grass, foliage, water and other tint-aware blocks use Minecraft's live colour providers.
3. **Layered surface composition** — transparent surface strata, fluids and lower solid layers are combined rather than choosing only one flat colour.
4. **Independent relief data** — height and neighbouring slopes are used to light terrain without baking all terrain meaning into the base colour.
5. **Chunk/region tiles** — terrain is cached spatially and only dirty areas need to be regenerated.
6. **Multiple detail levels** — lower-resolution images are precomputed with gamma-aware filtering so distant zoom levels remain stable and readable.
7. **Persistent map storage** — mature map data can survive restarts instead of existing only while the current client session is open.
8. **Buffered presentation** — complete textures are published to the screen instead of exposing partially rebuilt images.

The most important visual difference is not one special shader. It is the combination of accurate source colours, layered surface sampling, relief and zoom-specific cached images.

## Independent SSU implementation in dev3.3

### Resource-pack-aware block palette

SSU now asks Minecraft's own model manager for the current block-state model. It independently collects the model parts and prefers textures from upward-facing baked quads. If none are available, it falls back in this order:

1. general model quads;
2. the block state's particle sprite;
3. Minecraft `MapColor`.

Fluids use their dedicated still-fluid model sprite. Because the textures come from Minecraft's active atlas, a compatible resource pack also changes the SSU maps.

### Four-by-four aerial fingerprint

Each visible world block is reduced from its real texture to a 4×4 alpha-aware fingerprint. This is sixteen distinct samples per block, rather than one repeated material colour. It gives the claim map and minimap real internal block detail without storing full 16×16 textures for every block.

### Live tint and transparent strata

The texture fingerprint is cached per block state. Biome and in-world tint is applied at the sampled position without allocating a new pixel array for every column. The surface sampler can composite several layers, allowing cutout leaves or plants to retain the terrain beneath them. Water uses both its texture and measured depth.

### Relief and boundaries

A separate height pass evaluates eight neighbouring columns with a Sobel-style slope calculation. Lighting is based on world coordinates and can query already loaded neighbouring chunks, reducing the obvious chunk-local lighting seams produced by the older renderer.

### Shared high-resolution atlas and LOD

Every captured 16×16 chunk becomes a 64×64 base tile. SSU then builds a gamma-correct mip chain down to 1×1. The world map selects a suitable level for its zoom footprint, while the minimap and claim map use the high-detail base tile. The one-pixel-per-block level is now selected correctly from mip level 2.

The cache is bounded to 2,048 least-recently-used tiles per dimension to control memory after quadrupling base-tile pixel count.

## What dev3.3 does not yet reproduce

The largest remaining architectural gap is persistence. SSU's atlas still represents chunks encountered during the current client session. It does not yet store compressed region tiles on disk. A full snapshot-worker system, cave layers and a dedicated GPU region renderer are also not part of this build.

Those features matter for scale and long-term smoothness, but they are separable from this first visual-quality upgrade. In-game screenshots are needed before deciding whether the next priority should be colour/relief tuning or persistent region storage.
