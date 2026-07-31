# SSU 1.6.0-dev12.10 test checklist

Build with Java 25:

```bat
gradlew.bat clean build
```

Use the exact same dev12.10 jar on the client and dedicated server. Network protocol 36 intentionally rejects older dev12 builds.

## 1. Marker context frame

1. Open the World Map.
2. Right-click empty terrain.
3. Confirm that **Add marker** and **Close** sit inside one visible dark panel with an outer light border and inner dark-blue border.
4. Right-click an existing marker.
5. Confirm the same frame surrounds **Edit**, **Delete** and **Close**.
6. Test near all four map edges; the complete frame must remain inside the map viewport.
7. Confirm all context actions still work and clicks outside close the modal without clicking through.

## 2. Persistent explored terrain

1. Travel through several chunks and allow the minimap/atlas to capture them.
2. Open World Map and verify the terrain is visible.
3. Close Minecraft completely, reconnect to the same server and reopen World Map without revisiting those chunks.
4. Confirm the explored terrain is restored from the client cache instead of becoming blank.
5. Confirm separate servers and dimensions do not share terrain.

Expected cache: client `map-cache-v4`, format 5. Dev12.10 changes the renderer fingerprint to `atlas-topographic-v6`, so existing tiles rebuild once with the sharper relief.

## 3. Live terrain radius setting

1. Open **Settings → World map**.
2. Locate **Live terrain: N chunks**.
3. Click through 1, 2, 4, 6, 8, 12, 16, 24 and 32; the next click must wrap to 1.
4. Set it to 1 chunk and reconnect. Reopen Settings or wait for the minimap state sync and confirm the value remains 1.
5. With a second player or remote admin edit, change the visible surface well outside the configured radius.
6. Refresh/open World Map while staying away. The cached terrain must remain unchanged.
7. Walk within the configured radius of the changed chunk and wait for background capture. The map must update.
8. Repeat with 8 or 16 chunks to confirm the larger live area is respected.

## 4. Client memory lifecycle

1. Pan the World Map to distant cached terrain and allow it to finish loading.
2. Close the map and remain away from that terrain for at least 10 seconds.
3. Reopen the same distant area. It may briefly reload from disk, but must not require world re-exploration.
4. Repeatedly visit multiple distant map areas and close the screen. Confirm client memory does not grow indefinitely and no crash/stutter loop occurs.
5. Change dimensions and wait at least 10 seconds; inactive-dimension tiles should be eligible for RAM eviction while remaining on disk.

## 5. Sharper topography

Compare hills, terraces, valleys, cliffs and coastlines with dev12.9:

- elevation transitions should be easier to distinguish;
- north-west-facing slopes should read brighter and opposite slopes darker;
- forests should remain coherent rather than turning into harsh noisy outlines;
- water, night darkening and cached light sources must remain correct on World Map, Claim Map and minimap.

## 6. Regression

- World Map and Claim Map middle-button dragging still work.
- Right-click marker creation/edit/delete still works.
- World Map biome/block cursor information still works.
- Minimap, claims, regions and marker layers still render.
- Back/Close positions and the shifted left tool panels remain correct.
- Existing player preferences migrate to schema 7 with an 8-chunk live radius.
