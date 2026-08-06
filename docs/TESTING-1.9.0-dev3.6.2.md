# SSU 1.9.0-dev3.6.2 — Region Setup Compact Layout Test Checklist

## Build

- Use Java 25.
- Run `gradlew.bat clean build`.
- Use the exact same dev3.6.2 build on client and server.

## Compact Region Setup Tool

- Open the Region Tool at several GUI scales and common resolutions.
- Confirm the panel is substantially smaller than dev3.6.1 and remains centered.
- Open all six tabs: General, Protection, Rent & access, Scheduled reset, Selection and All regions.
- Confirm no heading, explanation, status line or list text appears behind a button or edit box.
- Confirm Save, Refresh and Close remain clickable and do not overlap the notice line.

## General page

- Open an existing region locally and remotely through All regions.
- Confirm the General page shows Teleport, Select region, spawn controls, Redefine and Delete without overlap.
- Confirm welcome/leave inputs and their labels remain readable.

## Select / Unselect region

- Open an existing region and click `Select region`.
- Confirm point 1 and point 2 become the exact minimum/maximum region bounds in the correct dimension.
- Confirm the Region Tool selection border appears immediately.
- Confirm the button changes to `Unselect region` after the server response.
- Click `Unselect region` and confirm both points and the border are cleared.
- Repeat with a region opened remotely in another dimension; confirm selection data is set to that region dimension without teleporting.
- Select another custom cuboid, then click `Select region`; confirm it is replaced by the exact region bounds.

## Compact reset and selection pages

- Add all six weighted preset entries on Scheduled reset and Selection.
- Confirm the two-column layout remains readable and percentage fields and X buttons are clickable.
- Confirm the inventory grid click targets still match the rendered slots.
- Test Equalize, Clear and Fill/Save actions.

## Snapshots and remote regions

- Browse more than five snapshots and use pagination.
- Start a ghost preview and test every movement, rotation, mirror, confirm and cancel button.
- Browse more than six regions and use pagination.
- Confirm Edit and Teleport buttons align with each region row and remain clickable.

## Compatibility

- Network protocol remains 91.
- Region storage remains schema 5.
- Portable selection snapshots remain format 1.
- No migration is expected from dev3.6.1.
