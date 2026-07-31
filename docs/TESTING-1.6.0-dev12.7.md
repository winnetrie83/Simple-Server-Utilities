# SSU 1.6.0-dev12.7 test checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev12.7 JAR on client and dedicated server.
- Confirm the network protocol remains 35 and existing markers/preferences/cache load normally.

## In-world marker readability

1. Create or use a marker with in-world icons enabled.
2. Check the filled disc nearby: it should be approximately half the diameter of dev12.6.
3. Move to roughly 32, 64, 128 and 256 blocks away.
4. Aim at the marker and confirm the name plus distance label is at least twice as large and clearly readable.
5. Confirm the disc and label retain a stable apparent screen size as distance increases.
6. Confirm the label still disappears when looking away and only one best marker is labelled.
7. Confirm beams and map/minimap marker icons are unchanged.

## World Map layout

1. Open the World Map at several GUI scales/resolutions.
2. Confirm the entire left control column is shifted three pixels right and remains inside its panel.
3. Confirm Back is at the bottom of the left column.
4. Confirm Close is in the top-right corner of the complete map shell.
5. Confirm neither button overlaps the map, location information, title or status line.
6. Verify zoom, layers, marker manager, Claim Map switch, refresh, right-click marker menus and middle-drag still work.

## Claim Map layout

1. Open the Claim Map at several GUI scales/resolutions.
2. Confirm the left controls are shifted three pixels right.
3. Confirm Back is at the bottom of the left column and Close is top-right.
4. Confirm claim selection, middle-drag, zoom, create/expand/remove/settings/delete and World Map switching still work.
