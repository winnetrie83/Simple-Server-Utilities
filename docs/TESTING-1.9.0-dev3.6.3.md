# SSU 1.9.0-dev3.6.3 — Region Setup Tool Polish Test Checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Use the exact same dev3.6.3 build on client and server.
- Confirm the network protocol remains `91` and no storage migration is requested.

## All regions

- Open Region Setup Tool → All regions.
- Confirm the duplicate `Create region from current selection` button is gone.
- Confirm the page counter, region rows, Edit and Teleport buttons remain aligned.
- Confirm region creation still works from Selection → Selection actions & block fill.

## Live snapshot-name validation

- Create or select a valid two-point selection.
- Open Selection → Full snapshots & ghost preview.
- Start with an empty name and confirm `Save full snapshot` is disabled.
- Type a valid name such as `beach_house_01` and confirm the button becomes enabled immediately, without switching tabs.
- Remove the name and confirm the button disables immediately.
- Try spaces and characters such as `/`, `:`, `?` and confirm the validation message explains the allowed characters.
- Save a valid snapshot and confirm it appears in the saved snapshot list.
- Confirm all text, the name field, Save button, status line and list remain separated at several GUI scales.

## Minimal preview edit mode

- Click Preview beside a saved snapshot.
- Confirm the full Region Setup Tool closes and the world remains visible.
- Confirm only the compact button overlay and short status text are shown, with no large background panel.
- Test Move -X/+X, -Y/+Y and -Z/+Z.
- Test Rotate left, Rotate right and Rotate 180.
- Test Mirror X and Mirror Z.
- Confirm the ghost preview changes while the actual world remains untouched.
- Click Confirm placement and verify the complete snapshot is placed once and the preview disappears.
- Start another preview, click Cancel preview once and confirm a second explicit confirmation is required.
- Confirm Escape also requires an explicit second cancellation action rather than silently placing or discarding the preview.

## Free mode

- Start a preview and click Free mode.
- Confirm the compact button overlay closes and a small Free mode instruction appears.
- Walk/fly and look around the snapshot from multiple directions.
- Confirm the preview remains at the same world position while the player moves.
- Left-click once and confirm the compact edit controls return.
- Confirm that left-click does not damage an entity or break a block.

## Preview action lock

While a preview is active, verify that the administrator cannot:

- break or place blocks;
- use blocks or items;
- attack or interact with entities;
- open or operate inventory containers;
- pick up or drop items;
- execute commands.

Confirm normal actions work again after Confirm placement or Cancel preview.

## Cleanup and recovery

- Cancel a preview and confirm both the ghost render and server preview session disappear.
- Confirm a preview, wait for placement completion and confirm no stale preview remains.
- Log out during Free mode and log back in; confirm no preview or action lock remains.
- Change dimension during a preview and confirm it is cancelled.
- Die during a preview and confirm it is cancelled.
- Reload or restart the server after all previews are closed and confirm snapshot files remain usable.
