# SSU 1.1.0-dev5.1 test plan

Use the same dev5.1 build on the client and server for normal testing. The payload protocol remains version 5.

## Build

1. Use Java 25.
2. Run `gradlew.bat clean build`.
3. Confirm `build/libs/simpleserverutilities-1.1.0-dev5.1.jar` exists.

## Terrain map

1. Join a world with a mixture of grass, water, trees, buildings and hills.
2. Open `/claims map`.
3. Confirm the dark cells progressively become a top-down terrain map within roughly one second at the default radius.
4. Confirm water, land and buildings are distinguishable.
5. Pan one to five chunks and confirm the map rebuilds at the new center.
6. Zoom in and out and confirm terrain stays aligned with chunk cells.
7. Pan beyond the client render distance and confirm unavailable chunks remain a dark checker pattern without loading/freezing the server.
8. Close and reopen the GUI several times and watch for texture corruption or increasing client memory use.

## Transparent overlays

1. Confirm wilderness shows terrain with only the subtle grid.
2. Confirm own claims show a translucent green overlay and green outline.
3. Confirm another player's claim shows a translucent blue overlay and blue outline.
4. Confirm a server region shows a translucent purple overlay and purple outline.
5. Select wilderness chunks and confirm the yellow selection remains transparent with a clear yellow outline.
6. Cycle to an owned claim and confirm its outline becomes slightly thicker without turning white.
7. Change a semantic border color as admin, reopen/request the map and confirm both fill and outline follow the new color.

## Existing claim actions

Repeat create, expand and remove tests from the dev5 plan. Confirm the terrain layer does not change server-side validation, limits, connectedness or persistence.

## Accessibility/regression

1. Confirm `ClaimMapWidget` compiles without the missing narration-method error.
2. Confirm keyboard focus and narration do not crash the screen.
3. Confirm right-drag pan, scroll zoom, current-player marker and buttons still work.
