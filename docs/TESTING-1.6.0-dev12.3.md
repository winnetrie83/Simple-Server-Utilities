# SSU 1.6.0-dev12.3 — Marker visual polish test plan

## Build and compatibility

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the resulting dev12.3 jar on both client and dedicated server.
3. Confirm that protocol 35 accepts dev12.3 ↔ dev12.3 and rejects a mismatched client/server build.
4. Reuse an existing dev12.2 world and confirm that personal markers still load without migration.

## Camera-facing world icon

1. Enable in-world marker icons and create a marker on level terrain.
2. Walk a full circle around it while looking at the marker.
3. Confirm that the coloured circular icon always faces the camera instead of lying flat on the ground.
4. Move above and below the marker where practical and confirm that the icon continues to face the camera without collapsing into a line.
5. Toggle marker beams independently and confirm that the vertical beam remains present/absent without changing the billboard icon.
6. Confirm that the icon is centred on the saved block coordinate and that existing distance limits remain unchanged.

## Sixteen Minecraft colours

1. Open Create Marker and Edit Marker.
2. Confirm that exactly sixteen colour buttons are shown in two rows of eight.
3. Confirm the complete palette is available: Black, Dark Blue, Dark Green, Dark Aqua, Dark Red, Dark Purple, Gold, Gray, Dark Gray, Blue, Green, Aqua, Red, Light Purple, Yellow and White.
4. Confirm that every button face visibly uses its represented colour rather than a generic gray button.
5. Hover each swatch and confirm its colour name and hex value appear.
6. Select several dark and bright colours and confirm the selected swatch has a clear bright outline.
7. Save markers in several colours and verify matching colours on the World Map, minimap, in-world icon and beam.
8. Edit a marker created in an earlier build and confirm its stored colour remains intact.

## World Map context frame

1. Right-click empty terrain and confirm Add marker plus Close appear inside one padded, double-outline panel.
2. Right-click an existing marker and confirm Edit, Delete/Confirm and Close use the same frame.
3. Test menus near every map edge and corner; the full frame must remain inside the map viewport.
4. Confirm all modal click behaviour from dev12.2 still works and no click falls through to the map.
