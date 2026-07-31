# SSU 1.6.0-dev11.1 test checklist

## Required setup

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev11.1 jar on the server and all clients.
- Confirm the network protocol is 33.

## Compact Block Information

1. Grant `ssu.block_information.use=true` and keep `ssu.block_information.debug=false`.
2. Enable **Settings → General → Block information**.
3. Look at grass, dirt, logs and ordinary decorative blocks:
   - only the translated block name should appear;
   - no registry ID, namespace, hardness, tool words or state properties should appear.
4. Look at obsidian without a suitable pickaxe:
   - `Obsidian` should be shown with a diamond-pickaxe item icon;
   - the vertical indicator should be red.
5. Hold a diamond or netherite pickaxe while looking at obsidian:
   - the icon remains the minimum required diamond pickaxe;
   - the vertical indicator becomes green.
6. Look at blocks requiring stone/iron-tier pickaxes and confirm the minimum-tier icon is selected.
7. Look at an entity and confirm only its translated entity name is shown.
8. Open another screen and confirm the overlay disappears.

## Permission-gated debug mode

1. Without `ssu.block_information.debug`, confirm no debug toggle is visible in Settings, including for an operator account without an explicit grant.
2. Grant `ssu.block_information.debug=true` through rank or player permissions.
3. Reopen the dashboard and enable **Block info debug**.
4. For blocks, confirm debug mode adds:
   - registry ID;
   - hardness or Unbreakable;
   - required-tool text;
   - bounded state properties or `default`.
5. For entities, confirm debug mode adds the entity registry ID.
6. Revoke the debug permission while the mode is enabled and confirm technical details disappear within about one second.
7. Regrant permission and confirm the stored personal toggle becomes effective again.
8. Disable ordinary Block Information and confirm the debug control cannot remain effective.

## Compatibility

- Confirm existing player preferences migrate to schema 5 with debug OFF.
- Confirm minimap, maps, mail, utility mining, borders, statistics and holograms retain their settings/data.
