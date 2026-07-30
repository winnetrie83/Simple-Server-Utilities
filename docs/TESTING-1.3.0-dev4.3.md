# Testing SSU 1.3.0-dev4.3

Use a copied world. Build with Java 25 and install the same dev4.3 JAR on client and server. Network protocol remains 12.

## 1. Build and basic compatibility

```bat
gradlew.bat clean test build
```

Confirm:

- all tests pass;
- the JAR reports `1.3.0-dev4.3`;
- existing claims, regions, balances, rents, homes, warps, permissions and region snapshots are unchanged;
- minimap, claim map and world map open without errors.

## 2. One-time aerial cache rebuild

Open a location previously mapped with dev4.2.

Confirm:

- the old rendering is not reused;
- loaded chunks rebuild once under `atlas-topographic-v4`;
- after reconnecting, the rebuilt map returns from disk;
- panning into unknown terrain does not force-load chunks.

## 3. Tree and forest comparison

Test at least:

- one isolated oak or birch;
- a dense forest;
- a forest on a hillside;
- differently tinted biome foliage;
- a tree beside grass, sand and water;
- leaf blocks placed as a flat artificial roof.

Confirm:

- trees are clearly darker than the surrounding open grass instead of pale spots;
- isolated crowns have a restrained north-west highlight and south-east shadow;
- dense forest interiors remain calm rather than checker-patterned;
- different crown heights remain visible without harsh black outlines;
- the hill below a forest is still readable;
- flat artificial leaf roofs remain mostly uniform except at real edges.

## 4. Terrain relief comparison

Use the same plains, hills and mountain area shown in the dev4.2 screenshot and compare with JourneyMap only as a visual target.

Confirm:

- entire hills and valleys are readable at normal and distant zoom;
- Minecraft's one-block terraces remain visible;
- flat plains do not receive artificial contour bands;
- north-west-facing slopes are lighter and south-east-facing slopes darker;
- the image is calmer than dev4.1 and does not restore repeated block-texture detail;
- terrain colours are slightly less saturated than dev4.2.

## 5. Surface-category regression

Create adjacent patches of:

- flowers, tall grass, ferns, crops and saplings;
- leaves and logs;
- water and lava;
- slabs, stairs, paths, glass and buildings;
- snow layers.

Confirm:

- decorative plants still reveal the ground below and create no height bumps;
- leaf crowns remain visible;
- water remains smooth and level;
- solid structures remain visible and participate in relief;
- claim and region overlays stay readable over every surface.

## 6. Persistent cache metadata

Explore new chunks, close Minecraft normally, reconnect and reopen all maps.

Confirm:

- terrain and canopy shading are identical after disk reload;
- no trees revert to flat dev4.2 colouring after reconnect;
- no `.broken-*` cache files appear during a normal run;
- the client log contains no unsupported aerial tile version or length mismatch.

## 7. Existing dev4.1/dev4.2 regressions

Confirm again:

- minimap claims show only the connected outer perimeter;
- right-click drag works on claim map and world map, including release outside the map area;
- region reset is drop-free and restores item frames/paintings from a new snapshot;
- terrain, claims, regions and markers remain aligned at every zoom;
- no sustained client stutter occurs while nearby chunks rebuild.
