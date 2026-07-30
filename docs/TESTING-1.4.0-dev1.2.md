# SSU 1.4.0-dev1.2 test plan

Use a copied world and install the same dev1.2 JAR on client and server.

## 1. Build and startup

Run with Java 25:

```bat
gradlew.bat clean test build
```

Confirm that the server starts with network protocol 14 and loads the existing permissions/ranks without migration errors.

## 2. Player and rank dropdowns

- Open Admin Center → Players & Permissions.
- Switch the first dropdown between Players and Ranks.
- Confirm that the second dropdown changes its contents.
- Test mouse-wheel scrolling with more than eight targets.
- Enter a partial target name, press Filter and confirm that only matching targets remain.
- Confirm that an offline player who previously joined can still be selected.

## 3. Permission list and pagination

- Select a player and verify that known permissions load.
- Search for `claims`, `teleport` and a full permission key.
- Use previous/next on multiple pages.
- Confirm that changing page or refreshing does not change the selected target.
- Select a rank and repeat the checks.

## 4. Boolean values

- Toggle a boolean permission ON and OFF.
- Verify the result with the corresponding command/gameplay action.
- Press `×` and confirm that the row returns to inherited/default.
- Verify that a personal override wins over an assigned rank.

## 5. Numeric and custom values

- Set a valid numeric limit such as `ssu.homes.max`.
- Try a negative value, decimal value and value above the stated maximum; each must be rejected without changing stored data.
- If a custom permission key already exists, confirm that it appears as a text value and can be changed.

## 6. Rank assignment

- Select a player.
- Choose a rank in the rank dropdown and press Assign.
- Refresh the player target and confirm that the target summary/effective values reflect the assigned rank.

## 7. Tooltips

Hover every control type and confirm that the tooltip contains:

- a useful description;
- boolean/integer/text type;
- numeric range where relevant;
- current direct or inherited/default value.

## 8. Portrait

- Confirm that the full 3D model stays inside the black opening of the stone frame.
- Move the pointer left/right and above/below the portrait.
- Confirm that the model follows the pointer smoothly and stops at a restrained angle.
- Resize the window and change GUI scale; the model must remain aligned.

## 9. Security and compatibility

- A player without `ssu.permissions.admin` must not receive editor data or change values.
- A stale response after switching Players/Ranks must be ignored.
- A protocol-13 client must be rejected rather than silently mixed with protocol 14.
- Restart the server and confirm that all edited permissions remain present.
