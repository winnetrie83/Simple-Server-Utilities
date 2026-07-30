# SSU 1.3.0-dev3.3.1 water rendering hotfix

This hotfix changes only `TerrainColorSampler.java` plus version documentation.

The dev3.3 map pipeline multiplied the RGB values of the still-water texture directly by the biome water tint. Depending on the texture/resource pack and returned tint, this could make water nearly black or visually absent.

The corrected path:

1. obtains the live biome water colour through `BiomeColors`;
2. uses the water texture only as a local luminance/detail mask;
3. keeps depth shading and the existing transparent composition over the river/lake floor;
4. falls back to vanilla water blue when a custom tint provider fails.

Protocol remains 11. No server-side class or saved-data format changed.
