# Testing SSU 1.3.0-dev4.2

Use a copy of the world. Build with Java 25 and install the same dev4.2 JAR on client and server. The network protocol remains 12, so dev4.1 and dev4.2 are protocol-compatible, although matching builds are recommended for consistent client behaviour.

## 1. Build and basic load

```bat
gradlew.bat clean test build
```

Confirm:

- all tests pass;
- the JAR reports `1.3.0-dev4.2`;
- the copied world opens with all existing claims, regions, balances, rents, homes, warps and permissions unchanged;
- region snapshots still report format version 3 when newly saved.

## 2. Force the new map cache

Open the minimap, claim map and world map in terrain already visited with dev4.1.

Confirm:

- old tiles are not reused under the new renderer fingerprint;
- loaded chunks rebuild once;
- reconnecting later loads the rebuilt tiles from disk;
- no chunk is force-loaded merely by panning into unknown terrain.

## 3. Vegetation filtering

Find or create adjacent test patches containing:

- plain grass blocks;
- tall grass and ferns;
- several flower types;
- mature crops;
- saplings;
- leaves and logs;
- snow layers;
- slabs, stairs, paths, fences and glass;
- water and lava.

Confirm on all three maps:

- grass, flowers, crops, saplings and similar decorative plants do not add noisy coloured dots or false height bumps;
- the ground below them supplies the visible colour;
- leaves remain visible as a continuous canopy;
- solid and partial structural blocks remain visible;
- fluids remain visible and water depth shading is smooth rather than checker-patterned.

## 4. Hill and mountain readability

Compare flat plains, rolling hills, terraced slopes, cliffs and mountains at close, normal and distant zoom.

Confirm:

- flat areas remain even and do not show repeating four-level bands;
- north-west-facing slopes are lighter and opposing slopes darker;
- whole hills remain readable at normal zoom due to broad/macro shading;
- one-block height steps have a narrow light/dark rim;
- tree canopies do not create a field of artificial bumps over forests;
- rivers and lakes remain level while shore and cliff height changes stay clear.

## 5. Visual calmness

Compare the same area with the supplied dev4.1 screenshot.

Confirm:

- block interiors no longer show repeated 4x4 texture detail;
- colours are less saturated;
- forests and flowered plains are less busy;
- structures remain identifiable by their simplified block colours;
- claim and region overlays remain readable over the calmer terrain.

## 6. Regression checks

Repeat the key dev4.1 checks:

- only the outer perimeter of a connected claim is shown on the minimap;
- right-click drag works on claim map and world map, including release outside the widget;
- region reset produces no item drops and restores version-3 hanging entities;
- map markers, claims and regions stay aligned at every zoom;
- there is no sustained client stutter while tiles rebuild.
