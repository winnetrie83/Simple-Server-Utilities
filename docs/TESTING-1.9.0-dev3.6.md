# SSU 1.9.0-dev3.6 — Region Setup & Snapshot Preview Test Checklist

## Build and compatibility

- Build with **Java 25** using `gradlew.bat clean build`.
- Use exactly **SSU 1.9.0-dev3.6** on both client and dedicated server.
- Confirm the client/server handshake accepts network protocol **91**.
- Back up the test world before testing destructive Selection actions or snapshot placement.

## 1. Region Tool selection workflow

1. Bind or obtain the normal SSU Region Tool.
2. Select point 1 and point 2 directly in the world using the existing in-world tool controls.
3. Right-click to open Region Setup.
4. Confirm there are **no** `Set point 1/2 to current position` buttons.
5. Open **Selection** and verify point coordinates, dimension and volume match the in-world selection.
6. Use **Clear selection points** and confirm the border/selection disappears.
7. Re-select both points and verify the screen refreshes correctly.

## 2. Create region from selection

1. With a complete selection, choose **Create region from selection**.
2. Confirm the normal region parameters are available before saving: name, priority, border, messages, protection, rent/access and scheduled reset.
3. Submit invalid data first and verify no partial region is created.
4. Submit valid data and verify the new region is saved with the selected bounds.
5. Reopen it with the Region Tool and verify all settings survived a server restart.

## 3. Local detection and remote region administration

1. Stand inside an existing region and right-click the Region Tool.
2. Confirm that region opens automatically and is identified as the current/local region.
3. Open **All regions** and switch to another region without travelling there first.
4. Edit a harmless setting remotely, save, reopen and verify persistence.
5. Return to **All regions** and ensure the local region is still identified separately.
6. Test pagination with more than eight regions.

## 4. Teleport to region

### Configured spawn

1. Configure a region spawn.
2. Select **Teleport to region** from the region editor and from **All regions**.
3. Confirm the administrator arrives safely at the configured spawn with the stored yaw/pitch.

### Safe fallback

1. Clear the region spawn.
2. Teleport to the region again.
3. Confirm SSU finds a safe location inside or immediately above the region.
4. Test a region containing walls, fluids, uneven terrain and a low ceiling.
5. Verify the operation fails with a clear message when no safe location exists.
6. Test a remote region in another loaded dimension.

## 5. Selection world-edit actions

Use a small disposable test selection containing ordinary blocks and containers.

- **Clear to air:** every selected block becomes air; container contents must not drop or duplicate.
- **Fill water:** the complete selection is filled with water.
- **Fill lava:** the complete selection is filled with lava.
- **Weighted block mix:** select up to six inventory block items or water/lava buckets, set percentages and fill.
- Verify unused percentage becomes air.
- Verify source inventory items are not consumed.
- Verify invalid, duplicated or empty inventory slots are rejected safely.
- Verify totals above 100% are rejected.
- Verify large operations run through the bounded job system instead of freezing one server tick.
- Verify overlapping region/minigame/resource locks prevent unsafe concurrent mutation.

## 6. Full selection snapshots

Build a small test structure containing:

- ordinary and directional blocks;
- a chest/barrel with named and enchanted items;
- signs or other block entities;
- an item frame and painting;
- an armor stand with equipment.

Then:

1. Select the complete structure.
2. Enter a valid snapshot name and choose **Save full snapshot**.
3. Confirm the snapshot appears in the snapshot list.
4. Restart the server and verify it remains available.
5. Modify or clear the original structure.
6. Load its preview and eventually place it elsewhere.
7. Confirm block states, block-entity data, inventories and supported structural entities are restored.
8. Verify destination containers/entities are replaced without item duplication.
9. Test invalid names, oversized selections and a deliberately missing/corrupt snapshot file.

## 7. Ghost preview workflow

1. Select a saved snapshot and choose **Preview**.
2. Confirm the ghost appears approximately five horizontal blocks in front of the administrator, even while looking steeply up or down.
3. Confirm the preview is translucent and does not modify the world.
4. Test all controls:
   - Rotate left
   - Rotate right
   - Rotate 180
   - Mirror X
   - Mirror Z
   - Move ±X
   - Move ±Y
   - Move ±Z
5. Confirm directional block states and structural entities follow rotation/mirroring correctly after placement.
6. Choose **Cancel preview** and verify no world changes occur.
7. Start another preview and close the screen; verify the preview disappears.
8. Start another preview and disconnect; verify both client and server preview state are cleared.
9. Confirm placement:
   - the world remains unchanged until confirmation;
   - placement uses the complete snapshot, even when the visual preview is sampled;
   - the Region Tool selection updates to the pasted bounds;
   - containers and structural entities are restored only once;
   - a player whose edit permission was removed before confirmation cannot place it.
10. Attempt confirmation from another dimension and verify it is rejected safely.

## 8. Large preview and packet bounds

- Test a snapshot containing more than 4,096 non-air blocks.
- Confirm the preview indicates that it is sampled.
- Confirm at most 4,096 preview blocks are sent/rendered.
- Confirm final placement still contains every saved block.
- Watch client FPS and server tick time while moving/rotating the preview.

## 9. Regression checks

- Existing region General, Protection, Rent & access and Scheduled reset tabs still save correctly.
- Region snapshots used by scheduled reset remain separate from portable selection snapshots.
- Region redefine/delete and manager/member access still work.
- Player Claims and minigame resource locks retain priority over unsafe region edits.
- Existing Region Selection Edit screen continues to support its older block-only clipboard workflow.
- Scheduled snapshot and weighted-preset resets still work after restart.
