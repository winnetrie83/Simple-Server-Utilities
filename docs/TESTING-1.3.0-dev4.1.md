# Testing SSU 1.3.0-dev4.1

Use a copy of the world. Build with Java 25 and install the same dev4.1 JAR on client and server. Dev4.1 uses network protocol 12; dev4 uses protocol 11 and must be rejected rather than mixed.

## 1. Build and basic load

Run on Windows:

```bat
gradlew.bat clean test build
```

Confirm:

- the build and JUnit tests pass;
- the JAR reports `1.3.0-dev4.1`;
- the copied dev4 world opens without migration errors;
- claims, regions, balances, permissions, homes, warps and rents are unchanged.

## 2. Drop-free snapshot reset

Create a temporary test region containing at least:

- a chest with several uniquely counted item stacks;
- a furnace with input, fuel and output contents;
- a barrel and hopper with contents;
- mature wheat or another support-sensitive crop;
- torches or another wall-mounted block;
- one normal item frame with a rotated item;
- one glow item frame with a different item and rotation;
- one painting.

Save it:

```text
/regions save <region>
```

Wait for the job to complete. Modify or remove the blocks, inventories, crops, frames and painting. Then reset:

```text
/regions reset <region>
```

Confirm:

- no wheat seeds, chest contents, furnace contents or other reset-generated item entities appear on the ground;
- every inventory contains exactly the saved stacks and counts, with no duplication;
- crops and wall-mounted blocks remain present after the neighbour-reconciliation phase;
- item frames return on the correct face with the correct item and rotation;
- glow item frames remain glow frames;
- the painting returns in the same location and variant;
- the log contains no block-entity or hanging-entity serialization/restore failure.

Important: a dev4 version-2 snapshot cannot contain a frame that dev4 already deleted. Replace missing frames first and save the region again with dev4.1 before testing exact frame restoration.

## 3. Old snapshot compatibility

Keep one unmodified version-2 `.ssusnap` from dev4. Place frames/paintings in that region before reset and reset it with dev4.1.

Confirm:

- the old blocks and inventories restore;
- currently present frames/paintings are preserved rather than deleted;
- saving the region again completes and writes a version-3 snapshot;
- a second reset restores the exact saved frame/painting state.

## 4. Minimap outer claim perimeter

Use a connected claim containing multiple adjacent chunks, preferably an L-shape and a filled rectangle.

Confirm on the HUD minimap:

- no green/claim-coloured lines appear between chunks belonging to the same claim;
- only the true outer perimeter is visible;
- touching chunks from a different claim still have a separating boundary;
- claim fill remains subtle and the region rectangle remains independent.

## 5. Right-click drag

Open the claim map and the world map separately.

For each map:

1. Press and hold the right mouse button inside the terrain area.
2. Drag several chunks horizontally and vertically.
3. Release inside the map.
4. Repeat, but release outside the map widget while still inside the screen.

Confirm:

- the old completed map follows the cursor during the drag;
- release commits the new centre in the expected direction;
- the new terrain is published only when ready;
- claims, regions and player marker remain aligned;
- left-click claim selection still works on the claim map;
- zoom scrolling still works.

## 6. Height relief and cache rebuild

Visit terrain with one-block terraces, stairs, rolling hills and a cliff. Compare minimap, claim map and world map at close and normal zoom.

Confirm:

- consecutive one-block height changes remain visible rather than merging into one flat colour;
- flat plateaus do not show a checkerboard pattern;
- north/west-facing edges are lighter and opposing edges darker;
- the same terrain relief is consistent on all three maps;
- old dev4 disk tiles are not reused: terrain is rebuilt once under the new renderer fingerprint;
- after a normal reconnect, the rebuilt dev4.1 tiles load from disk.

## 7. Regression checks

Confirm that:

- `/ssu jobs` still reports save/reset progress;
- cancelling or crashing a destructive reset still leaves the region in the unresolved safety state;
- region delete/redefine/save/clear guards still work;
- minimap/world-map packet sizes remain bounded and no protocol decode error occurs;
- there is no sustained client stutter while newly loaded tiles are rebuilt.
