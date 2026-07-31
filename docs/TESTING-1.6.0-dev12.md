# SSU 1.6.0-dev12 — Map redesign and marker test plan

Use the exact same dev12 JAR on the dedicated server and client. Network protocol is 35.

## 1. Build and startup

1. Build with Java 25 using `gradlew.bat clean build`.
2. Replace the mod on both client and server.
3. Start an existing dev11.4 test world and confirm claims, regions, mail, holograms, custom statistics and Block Information still load.
4. Confirm no marker-storage or aerial-cache errors appear in the log.

## 2. World Map redesign

1. Open the World Map with `M` at several GUI scales and resolutions.
2. Confirm the map uses nearly the full available screen height and keeps the toolbar, right information panel and bottom status bar compact.
3. Hover every compact control and confirm its tooltip explains the action.
4. Test mouse-wheel zoom, right-drag panning, center-on-player, terrain refresh, Claim Map switching and returning to the SSU menu.
5. Toggle Claims, Server regions and Markers independently and confirm only the selected layers are rendered.
6. Move the cursor over the map and confirm X/Y/Z update in the location panel.

## 3. Marker creation and automatic height

1. Place a diamond block with its top surface at Y=60 in a loaded chunk.
2. Right-click its position on the World Map without dragging.
3. Confirm the create menu opens with X/Z from the clicked map position and initial Y=61.
4. Choose a name and colour, then create the marker.
5. Confirm the server-corrected marker remains at Y=61. `WORLD_SURFACE` already returns the first free block above the surface; SSU must not add another block.
6. Repeat in a previously explored but currently unloaded chunk. Confirm the cached mapped surface supplies the initial Y and the marker is still saved safely.
7. Change Y manually in the editor and confirm the manually entered value is retained instead of being automatically recalculated.
8. Test negative coordinates and coordinates near the active dimension's minimum/maximum build height.

## 4. Marker edit and delete

1. Right-click an existing marker on the World Map.
2. Edit its name, colour and coordinates and confirm all views update after saving.
3. Right-click it again, click Delete, then confirm deletion with the second click.
4. Open the remote marker manager from the World Map toolbar.
5. Edit a marker remotely and delete another marker remotely.
6. Create more than one page of markers and test previous/next navigation.
7. Restart the server and reconnect. Confirm all remaining marker names, colours, dimensions and coordinates persist.
8. Confirm one player never sees or edits another player's personal markers.

## 5. World and minimap marker rendering

1. Confirm each marker is a compact coloured circular icon on the World Map.
2. Enable the minimap and confirm the same marker appears as a coloured circle at the correct relative position.
3. Test north-up and rotating minimap modes.
4. Test circular and square minimap shapes and all four minimap positions.
5. Confirm markers outside the minimap's visible area are clipped cleanly.
6. Disable `Marker overlay` under Minimap settings and confirm only minimap markers disappear.
7. Disable `Map marker layer` under World Map settings and confirm only World Map markers disappear.

## 6. In-world circles and beams

1. Approach a marker in the same dimension and confirm a coloured horizontal circle appears at its saved coordinate.
2. Confirm the beam spans from the dimension minimum build height through the maximum build height.
3. Confirm the beam is visible through terrain as a navigation aid.
4. Disable `World marker icons` and confirm the circle disappears while other marker views remain unchanged.
5. Disable `Marker beams` and confirm only beams disappear.
6. Set beam distance to 128 and verify the beam appears/disappears around that horizontal distance.
7. Test the minimum 16 and maximum 512 values and confirm values outside the range are server-clamped.
8. Confirm markers and beams from another dimension are never rendered.

## 7. Claim Map redesign/regression

1. Open the Claim Map from the dashboard and from the World Map.
2. Confirm the professional shell, compact toolbar and right-side claim/action panel render without overlap at several GUI scales.
3. Test zoom, panning, center-on-player and switching back to the World Map.
4. Create a connected claim, expand it, shrink it and clear the selection.
5. Select and delete a claim using the existing confirmation path.
6. Confirm claim limits, connectedness, permissions and server-side validation remain unchanged.

## 8. Day/night and light sources

1. View the World Map, Claim Map and minimap during full daylight.
2. Change the world to dusk/night and confirm all three maps darken clearly without hiding overlays or markers.
3. Place torches, lanterns, glowstone or other block-light sources at exposed surface positions.
4. Confirm their lit surroundings remain visibly brighter/warm on all three maps at night.
5. Return to daytime and confirm normal map colours return.
6. Confirm day/night changes rebuild through double buffering rather than blanking the existing map.

## 9. Sharper relief

1. Inspect steep hills, valleys, cliffs and terraced terrain on all three maps.
2. Confirm height transitions are clearer and sharper than dev11.4.
3. Confirm the relief remains coherent while zooming and does not introduce internal chunk borders, colour noise or visible seams.
4. Confirm tree canopies, water and other existing atlas features remain intact.

## 10. Cache and performance

1. Explore several chunks, close the client and reopen it.
2. Confirm the client creates/uses `map-cache-v3` and automatically rebuilds old cache data in format version 4.
3. Confirm the map keeps its prior complete texture visible while a new view or night phase builds.
4. Pan and zoom repeatedly and watch for frame stalls, stale overlays, texture leaks or continuously repeated marker packets.
5. Create many markers up to a practical test count and confirm map/minimap/world rendering remains stable.
