# SSU 1.8.0-dev18.4 testing guide

## Environment

- Minecraft 26.2
- NeoForge 26.2.0.7-beta
- Java 25
- Exact same dev18.4 build on client and server
- Network protocol 68

## Admin Center cleanup

1. Open **Admin Center**.
2. Confirm **Region Maintenance** is no longer shown.
3. Open **Regions**.
4. Confirm no **Rent**, **Extend** or **Unrent** buttons are present.
5. Confirm **Details**, **Settings** and **Show/Disable** still work.
6. Confirm existing region settings, including rental configuration, remain editable under **Settings**.

## Region Tool action menu

1. Obtain the Region Tool from **Admin Center → Admin tools**.
2. Left-click a block for point 1 and another block for point 2.
3. Right-click a block or right-click in the air.
4. Confirm the action GUI shows the selection coordinates, volume and dimension.
5. Confirm it offers **Create server region**, **Edit selected blocks** and **Clear selection**.
6. Confirm permission-disabled options are visibly disabled.
7. Select more than 1,000,000 blocks and confirm region creation remains available when permitted, while block editing reports the safety limit.

## Region creation

1. Choose **Create server region**.
2. Confirm the screen asks only for a unique region name.
3. Create a region and verify the selection is cleared.
4. Open **Admin Center → Regions → Settings** and confirm all existing flags, priority, messages and rental settings are still available.
5. Test duplicate and invalid names and confirm clear error messages are shown.

## Selection clipboard

1. Make a small selection containing several block states, air and a container.
2. Choose **Edit selected blocks → Actions → Copy selection**.
3. Confirm the job completes and the clipboard reports ready.
4. Move point 1 to an empty destination and choose paste.
5. Confirm the destination is resized to the template dimensions and block states are reproduced.
6. Confirm container inventories and block-entity content are not copied.
7. Confirm existing destination container contents are discarded without drops after the confirmation step.
8. Log out and back in and confirm the temporary clipboard is empty.

## Weighted fill mix

1. Put multiple block items in the administrator inventory.
2. Open **Fill mix** and click inventory block slots.
3. Confirm non-block items and empty slots are rejected.
4. Confirm duplicate slots and more than six entries are rejected.
5. Use **Equalize %** and verify the total becomes 100%.
6. Enter invalid percentages, zero values and totals other than 100%; confirm the operation is refused.
7. Fill a test selection and confirm the chosen blocks appear in approximately the configured proportions.
8. Confirm source inventory items are not consumed.
9. Confirm destination container contents are discarded without drops.

## Clear selection blocks

1. Select blocks including a filled container.
2. Choose **Clear selected blocks** and cancel at the first confirmation stage by switching tabs or leaving the screen.
3. Repeat and confirm the destructive action.
4. Verify all selected blocks become air, no item drops are created and container contents are discarded.
5. Confirm the separate **Clear selection points** action removes only the selection outline/points and does not edit world blocks.

## Server templates

1. Open **Templates**, leave storage on **SERVER**, enter a valid name and save.
2. Confirm the capture job completes and the template appears after refresh.
3. Save with the same name and confirm overwrite requires a second click.
4. Select the template row and load it at point 1.
5. Restart the server and confirm the template remains available.
6. Confirm files are created under the SSU region selection-template storage folder.

## Client templates

1. Switch storage to **CLIENT** and save a selection.
2. Confirm the template appears in the local list and under `.minecraft/simpleserverutilities/region_templates`.
3. Close the GUI while export is running and confirm the completed file is still saved locally with a chat result.
4. Select and load the client template; confirm the server validates and pastes it.
5. Corrupt or replace a local file and confirm malformed, oversized, unknown-block or invalid-state data is rejected without editing the world.

## Regression checks

- Existing `/regions` commands remain registered.
- Existing region snapshots, resets, redefine, delete and rental services still function through their retained command/service routes.
- Existing region selection visualization still appears after setting points.
- Existing region settings and rental data are unchanged after upgrading.
- Existing Admin Center modules still paginate and open normally.
- Client and server reject protocol-mismatched builds.
