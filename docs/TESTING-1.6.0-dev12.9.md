# SSU 1.6.0-dev12.9 test checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Use the exact same dev12.9 JAR on client and dedicated server.
- Confirm network protocol remains 35.

## World Map left toolbar

1. Open the World Map at the same GUI scale used in the feedback screenshot.
2. Confirm the complete dark left toolbar panel begins three pixels farther right than in dev12.8.
3. Confirm the Zoom, Center, layer, marker, map-switch, refresh and Back buttons moved together with the panel.
4. Confirm every 28-pixel button now has approximately three pixels of panel padding on both its left and right side.
5. Confirm the map viewport, title bar, information panel and top-right Close button did not move.

## Claim Map left toolbar

1. Open the Claim Map.
2. Confirm its complete dark left toolbar panel is aligned identically to the World Map panel.
3. Confirm Zoom, Center, World Map and Back controls remain centered inside the shifted panel.
4. Confirm the claim map viewport and right claim-management panel did not move.

## Regression

- Confirm middle-mouse panning and mouse-wheel zoom still work on both maps.
- Confirm World Map right-click marker menus still work.
- Confirm Back and Close still perform their previous actions.
- Confirm the bottom location/status bars remain aligned.
