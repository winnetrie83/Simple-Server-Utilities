# SSU 1.6.0-dev12.1 — Map interaction hotfix test plan

## Build and compatibility

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the resulting dev12.1 jar on both client and dedicated server.
3. Confirm the connection succeeds with network protocol 35.
4. Confirm existing personal markers, map settings and map cache remain available.

## World Map marker clicks

1. Open the World Map.
2. Right-click empty terrain without moving the mouse.
3. Confirm the compact marker menu opens immediately and offers **Add marker**.
4. Create a marker and confirm it appears on the World Map, minimap and in-world views according to personal settings.
5. Right-click the marker icon.
6. Confirm the menu offers **Edit** and **Delete**.
7. Edit the marker, then delete it using the confirmation action.
8. Confirm ordinary left-clicks and toolbar buttons still behave normally.

## Map panning

1. On the World Map, hold the middle mouse button and drag in all four directions.
2. Confirm the terrain and overlays move with the cursor and the requested map centre updates on release.
3. Confirm right-click no longer starts panning.
4. Repeat the middle-button drag test on the Claim Map.
5. Confirm left-click chunk selection still works on the Claim Map.
6. Confirm the mouse wheel still zooms both maps.

## Layout and labels

1. Confirm the bottom Back and Close controls on both maps are 5 pixels higher than dev12.
2. Confirm neither map has a duplicate map-switch button in the bottom row.
3. Confirm the left toolbar still contains the World Map/Claim Map switch.
4. Hover the marker manager icon and confirm the tooltip reads `Manage markers`.
5. Hover the terrain refresh icon and confirm the tooltip reads `refresh`.
6. Confirm the bottom help text says **Middle-drag: pan**.
