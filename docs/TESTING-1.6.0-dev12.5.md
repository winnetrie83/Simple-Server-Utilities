# SSU 1.6.0-dev12.5 test checklist

Build with Java 25:

```bat
gradlew.bat clean build
```

Use the exact same dev12.5 JAR on the client and dedicated server. Network protocol remains 35.

## In-world marker label

1. Enable in-world marker icons and stand within their normal visibility range.
2. Look away from every marker: no marker name or distance should be drawn.
3. Aim the crosshair at a marker disc: one compact label should appear above it.
4. Confirm the label contains the saved marker name and a live distance in metres.
5. Walk toward and away from the marker and verify the distance updates.
6. Put two markers close together and verify only the marker closest to the crosshair receives a label.
7. Disable in-world marker icons and verify the label disappears as well; beams remain governed by their separate setting.

## World Map cursor information

1. Open the World Map and move the cursor across explored terrain.
2. Confirm the right LOCATION panel shows X, Y, Z, Biome and Block.
3. Confirm the bottom status line also contains coordinates, biome and block when space allows.
4. Test plains/grass, water, forest canopy, sand and a deliberately placed distinctive surface block.
5. Pan to terrain that is cached but outside the currently loaded client chunks. Biome and block should remain available after its format-5 tile has been captured.
6. Right-click a location and confirm the fixed context location retains the same biome/block details.
7. Hover long modded registry names and confirm the panel remains within its bounds instead of overlapping controls.

## Cache migration

1. Confirm a new client cache root is created at `simpleserverutilities/map-cache-v4`.
2. Old `map-cache-v3` data may remain on disk but must not be read as format 5.
3. Revisit explored chunks and confirm terrain, block/biome details, night shading and surface lights rebuild normally.
4. Restart the client and confirm biome/block details load from the new cache without revisiting every chunk.

## Regression

- World Map middle-drag, wheel zoom, marker right-click menus and marker management still work.
- World Map, Claim Map and minimap terrain rendering remains unchanged visually.
- Marker disc, beam, map/minimap marker icons and visibility settings remain unchanged.
- Claims, regions and server data require no migration.
