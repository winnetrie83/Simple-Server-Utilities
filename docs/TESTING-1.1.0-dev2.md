# SSU 1.1.0-dev2 test plan

Use a copy of a test world. Both the physical client and server must run the dev2 JAR because border data uses a new network payload.

## Build and startup

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm `build/libs/simpleserverutilities-1.1.0-dev2.jar` exists.
3. Start a client and dedicated/integrated server with the same JAR.
4. Confirm connection succeeds without a payload/version mismatch. A dev1 client should be rejected clearly because dev2 uses protocol version 2.

## Claim contour

1. Create a claim and add one chunk.
2. Run `/claims show <name>`.
3. Confirm a green four-block-high wire contour is visible on all four chunk edges.
4. Add an adjacent chunk while the contour is visible.
5. Confirm the shared internal chunk edge disappears automatically.
6. Create an L-shaped claim and confirm only the true outer contour is shown.
7. Run `/claims hide` and confirm the contour disappears immediately.
8. Delete a currently shown claim and confirm the contour disappears.
9. Deny `ssu.claims.visualize` and confirm `/claims show` is refused.
10. Travel to another dimension and confirm the old dimension contour is not rendered there.

## Region border

1. Run `/regions show <name>` for an existing 3D region.
2. Confirm the exact min/max cuboid is shown with gold wireframe and subtle fill.
3. Enter and leave the region; confirm no particle spam is produced.
4. Redefine the shown region and confirm the border refreshes to the new bounds.
5. Delete the shown region and confirm the border disappears.
6. Run `/regions hide` and confirm only the region layer disappears.

## Selection border

1. Set point 1 only and confirm no invalid box is rendered.
2. Set point 2 and confirm a cyan exact 3D selection box appears.
3. Repeat with the bound selection tool.
4. Run `/regions selection clear` and confirm the cyan box disappears.
5. Create or redefine a region from the selection and confirm the selection box is cleared.

## Layer independence and cleanup

1. Show one claim and one region at the same time; confirm both remain visible.
2. Complete a region selection; confirm all three layers can coexist.
3. Hide only the claim and confirm region/selection remain.
4. Disconnect and join another world/server; confirm no stale borders remain.

## Performance observations

1. Compare server logs and tick behavior with dev1 `/regions show`: there should be no repeated particle packets.
2. Test a large connected claim; confirm internal edges are omitted and rendering remains smooth.
3. Confirm borders beyond roughly 512 blocks are skipped clientside.
