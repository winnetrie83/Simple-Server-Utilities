# SSU 1.6.0-dev2 smoke-test checklist

## Build and connection

1. Build with Java 25: `gradlew.bat clean build`.
2. Install the same dev2 JAR on client and server.
3. Confirm protocol 24 accepts dev2/dev2 and rejects mixed dev1.1/dev2.

## Floating text

1. Load an existing text hologram that previously showed white and grey copies.
2. Confirm every line is rendered once.
3. Confirm colour, scale, bold, italic, underline, strikethrough, see-through and range still work.
4. Confirm clickable links still open only while looking at the link and right-clicking without the Hologram Tool held.

## Admin Tools page

1. Open `/ssu menu` as an administrator.
2. Open Admin Center → Admin Tools.
3. Hover both rows and verify full explanatory tooltips.
4. Use Get Tool for Region Tool and Hologram Tool.
5. Verify a player without the relevant permission cannot obtain or use the tool.

## Hologram Tool and GUI

1. Left-click each face of a block and verify the temporary position is offset from that face.
2. Right-click before selecting a position and verify the explanatory message.
3. Create TEXT, LINK and SCOREBOARD holograms from the GUI.
4. Hold left-click briefly and confirm the anchor is selected once rather than repeatedly.
5. Try a duplicate ID, blank text, invalid URL and blank objective; verify server errors keep the screen open. A non-existent objective may be saved deliberately and should render its missing-objective status until that objective is created.
6. Create an IMAGE definition with an internal resource ID. Confirm it saves and shows the known placeholder until the dedicated image renderer is added.
7. Relog while keeping the named tool, select a new anchor and confirm the item still works.
8. Disable the hologram module and verify an existing tool can no longer create holograms.

## Region Tool and GUI

1. Left-click point 1 and point 2; confirm the selection border updates and a held click does not set both points.
2. Right-click and create a region with non-default priority and flags.
3. Verify its bounds, flags, rent price/period and reset options in the existing region admin screens.
4. Test duplicate names, player-claim overlap and changing dimension after selection.
5. After a completed selection, left-click again and confirm a fresh point 1 starts.
6. Relog while keeping the named Region Tool and confirm it remains recognized.

## Treecapitator

1. Aim at a naturally generated or sapling-grown tree. Confirm the preview outlines logs only.
2. Break the tree with instant leaves enabled. Confirm all selected logs break and only the owned natural canopy is removed.
3. Disable `/ssu utilitymining tree break_leaves false`; confirm logs still break but leaves remain.
4. Set a block permission limit below the full trunk size. Confirm only the allowed logs are selected and leaves are not removed.
5. Place logs and leaves by hand, including a tree-shaped build. Confirm Treecapitator does not activate.
6. Place one log against a natural trunk. Confirm the placed log is excluded while the natural tree remains resolvable.
7. Grow two trees with touching canopies. Confirm felling one does not remove the other tree's canopy.
8. Test large/dark-oak/acacia/modded tagged trees and adjust leaf range where needed.
9. Restart the server and confirm newly hand-placed logs/leaves are still excluded.
10. Verify claim and region protection for every extra log and leaf.

## Regression

- Veinminer preview and breaking remain unchanged.
- Mail, dashboard, claims, regions, maps and existing hologram commands still open and function.
