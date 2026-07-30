# SSU 1.3.0-dev3.4 map refinements

## Scope

Dev3.4 is a client-map refinement build on top of dev3.3.1. It does not change the network payloads, server authority, save files or protocol number.

## One composite pixel per block

The shared aerial atlas now stores a 16x16 base tile for every 16x16 world chunk. Every world block contributes one carefully composited map pixel. The pixel can still use the active resource pack, biome tint, fluid depth, multiple visible surface layers and terrain lighting, but SSU no longer stores a visible 4x4 texture pattern for each block.

This reduces atlas memory, avoids noisy enlarged block-texture patterns and matches the basic map resolution used by mature map pipelines more closely.

## Smooth claim-map view changes

The claim map now uses two terrain textures:

- the last fully completed texture remains published and visible;
- a second hidden texture is built for the new centre or zoom level;
- the published texture is cropped and scaled according to world coordinates while the new texture is prepared;
- only a completed hidden texture replaces the visible texture.

Zooming or panning therefore no longer clears the entire claim map to checkerboard data.

## Live panning

Holding the right mouse button and dragging now offsets terrain, claims, regions and the player marker immediately. Releasing the mouse converts the pixel displacement to a chunk displacement and requests the authoritative new viewport.

The client updates its viewport before the network response. Old claim and region entries remain anchored to their world coordinates until the complete response for the new viewport arrives. Responses for an older centre or zoom level are ignored.

## Leaf canopies

Minecraft leaf textures use transparent cutout pixels because they are rendered as three-dimensional blocks. Treating that alpha coverage literally on an aerial map caused forests to look like holes in the ground.

Blocks in the vanilla leaves tag now form an opaque top-down canopy pixel. Their texture-derived colour and biome foliage tint remain in use, but the transparent cutout pattern no longer exposes the ground beneath it.

## Relief

The atlas now combines two lighting passes:

1. normalized local hill shading from the eight immediately surrounding surface heights;
2. a wider directional pass using heights three blocks away in each cardinal direction.

The local pass exposes block-scale slopes and cliffs. The wider pass makes hills, ridges and valleys readable at map scale. Both passes are bounded to avoid fully black cliffs or overexposed ridges.
