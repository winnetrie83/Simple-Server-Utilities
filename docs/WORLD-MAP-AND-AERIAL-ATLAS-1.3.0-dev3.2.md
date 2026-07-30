# SSU 1.3.0-dev3.2 — World Map & Aerial Atlas

## World map

Dev3.2 adds a separate full-screen world map. The default key is `M` and can be changed in Minecraft's Controls screen.

The screen includes:

- right-drag panning;
- mouse-wheel zooming;
- directional pan buttons;
- recentering on the player;
- local claim-overlay and region-overlay toggles;
- a direct button to open the claim map;
- a manual terrain refresh button.

The claim map now contains a matching `World map` button, so both map screens can switch directly to the other view.

## Shared aerial atlas

The minimap, claim map and world map now reuse one client-side aerial atlas. A loaded chunk is sampled into one 16×16 surface-colour tile and reused by all three renderers.

The atlas:

- reads only chunks already available to the Minecraft client;
- never requests or force-loads chunks;
- accumulates explored terrain during the current play session;
- refreshes nearby cached chunks periodically so visible terrain changes are eventually reflected;
- retains up to 8,192 sampled chunks per dimension in memory;
- is cleared when the player leaves the server/world.

## Aerial-image improvements

The surface sampler now adds:

- biome-tinted grass;
- biome-tinted foliage;
- biome-tinted water;
- water-depth darkening;
- relief lighting across chunk boundaries;
- extra edge contrast on steep height changes;
- restrained saturation and brightness grading.

This is still a generated top-down map rather than an actual screenshot. It is intended to approach the clarity of established map mods while remaining independent and lightweight.

## Resolution

- Claim map: four texture pixels per world block.
- Minimap: four texture pixels per world block; the 128×128-block view uses a 512×512 terrain texture.
- World map: double-buffered texture between 384×384 and 768×768 pixels, selected from the available GUI space. At wider zoom levels it averages four terrain samples per output pixel to keep roads, coastlines and forests readable.

World-map viewport changes are constructed off-screen and published only after the new texture is complete, avoiding partially drawn refresh rows.

## Networking and compatibility

The server sends only claim/region overlay metadata for the world-map viewport. Terrain remains client-side.

- Network protocol: `11`.
- Existing world save data: unchanged.
- Existing claim, region, economy, rent, rank, permission, home, warp and player UI preference files: unchanged.
- Client and server must both run dev3.2.

## Current limitations

- Unexplored or unavailable chunks remain a dark checker pattern.
- Explored atlas tiles are currently session-only and are not yet written to disk.
- The map is surface-oriented; cave mode and special Nether-roof handling are not included.
- Waypoints, biome labels and entity radar are not included.
