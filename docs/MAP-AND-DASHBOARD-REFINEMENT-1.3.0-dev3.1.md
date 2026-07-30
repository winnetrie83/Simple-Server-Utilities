# Map and Dashboard Refinement — SSU 1.3.0-dev3.1

This build is a focused visual and rendering correction on top of 1.3.0-dev3. It does not add or migrate saved server data, and it keeps network protocol `10`.

## Claim-map quality

The full claim map now renders terrain at two texture pixels per world block. At radius 12 this produces an internal 800×800 terrain texture instead of the previous 400×400 texture. The number of world-block samples is unchanged; the denser texture prevents the map from being enlarged from a low-resolution source at common GUI scales.

Claim and region contours are thinner:

- ordinary contour: 1 GUI pixel;
- selected or highlighted contour: 2 GUI pixels;
- current-player chunk contour: 1 GUI pixel.

The terrain uses a shared continuous north-west surface-light calculation. It preserves Minecraft map colours while adding smoother slope relief than the four coarse map brightness steps.

## Flicker-free minimap cache

The old renderer cleared its only 128×128 texture whenever a rebuild started. Because the server sends a compact overlay snapshot periodically, even an unchanged snapshot could briefly expose the grey checkerboard.

Dev3.1 uses three separate stages:

1. a 192×192-world-block terrain cache is built in an off-screen integer buffer;
2. the complete cache is published atomically only after all rows are ready;
3. a 256×256 visible viewport shows 128×128 blocks at two texture pixels per block.

The visible texture is never cleared during an ordinary refresh. Walking moves the viewport inside the larger cache. A replacement cache begins in the background near the edge of its safe margin. Claim/region changes keep the old complete map visible until the new overlay cache is finished.

## Dashboard corrections

- Button and Back-button glow images are transparent overlays again; they no longer replace the normal frame texture.
- The portrait is five pixels lower and gently bobs/sways while the menu is open.
- A permanent X button closes the screen from the upper-right corner.
- The sidebar counters are replaced with a Profile button.
- The Profile page shows account, rank, balance, property and personal display information already included in the server-authoritative menu snapshot.

## Compatibility

- Mod version: `1.3.0-dev3.1`
- Minecraft: `26.2`
- NeoForge: `26.2.0.7-beta`
- Java: `25`
- Network protocol: `10` (unchanged from dev3)
- Storage schema: unchanged
