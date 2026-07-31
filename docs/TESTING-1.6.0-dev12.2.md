# SSU 1.6.0-dev12.2 — Marker context button hotfix test plan

## Build and compatibility

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the resulting dev12.2 jar on both client and dedicated server.
3. Confirm the connection succeeds with network protocol 35.
4. Confirm existing markers, map settings and terrain cache remain intact.

## Empty-terrain context menu

1. Open the World Map and right-click empty terrain.
2. Confirm the compact menu shows **Add marker** and **Close**.
3. Click **Close** and confirm the menu immediately disappears.
4. Open it again and click **Add marker**.
5. Confirm the marker editor opens with the clicked X/Z and mapped surface Y.
6. Save a marker and confirm it appears on the World Map.

## Existing-marker context menu

1. Right-click an existing marker icon.
2. Click **Edit** and confirm the editor opens for that exact marker.
3. Change its name or colour and save.
4. Right-click it again and click **Delete**.
5. Confirm the button changes to **Confirm** and the marker is not yet removed.
6. Click **Confirm** and verify the marker is deleted after the server response.

## Modal click behaviour

1. Open a marker context menu.
2. Left-click outside the menu but still over the map.
3. Confirm the menu closes and the click does not activate a toolbar control or start another map action.
4. Confirm middle-mouse panning, right-click context opening, mouse-wheel zoom and all toolbar buttons still work normally after closing the menu.
