# Interactive Claim Map — SSU 1.1.0-dev5.1

## Claim model

SSU keeps the existing hybrid claim model:

- A player may own multiple separate claim groups.
- Every claim group has its own stable UUID, name, dimension, chunks, trusted players, flags, spawn and metadata.
- Chunks inside one claim group must form one four-directionally connected area.
- Different claim groups may be far apart and may exist in different dimensions.
- Player claims remain chunk-based and protect the full vertical column. Precise three-dimensional cuboids remain the responsibility of server regions.

This avoids forced claim corridors while preventing scattered checkerboard chunks inside one logical claim.

## Opening the map

- `/claims map`
- `/claims map <claim>`
- `/claims gui`
- `/claims gui <claim>`
- `U` → Claims → Open claim map

The old chat map remains available through `/claims map text <claim>`.

## Terrain background

The map now renders the actual top surface of the current client world behind the claim grid.

- Each visible chunk is sampled on an 8×8 grid.
- Vanilla block map colors are used, with simple height shading for terrain relief.
- Sampling and texture updates run clientside in bounded batches.
- One cached dynamic texture is rendered for the complete viewport, avoiding thousands of terrain draw calls every frame.
- SSU never force-loads chunks for the GUI. Chunks outside the client's current view distance appear as a dark checker pattern.
- Panning or zooming rebuilds only the client terrain texture; no extra terrain payload is sent by the server.

Claims, other claims, server regions and selections are translucent overlays. Their configured semantic color is also used as the chunk outline, so the terrain remains readable underneath.

## Controls

- Left click: select or deselect a valid chunk.
- Right mouse drag: pan by whole chunks.
- Mouse wheel: zoom in or out.
- Arrow controls: pan one chunk.
- Center: return to the player's current chunk.
- Previous/next arrows: cycle through owned claims in the current dimension.
- Expand: add selected wilderness chunks to the selected claim.
- Remove: remove selected chunks from the selected claim.
- New: enter a new claim name and create it from the selected wilderness chunks.

A single confirmed operation is capped at 256 chunks.

## Client/server responsibilities

The client handles:

- rendering;
- selection state;
- panning and zooming;
- local button state;
- map legend and previews.

The server remains authoritative for:

- permissions;
- ownership;
- current dimension;
- name validity;
- total and per-claim limits;
- existing claims;
- server-region overlap;
- final connectedness after add/remove;
- target-chunk permission scopes and the most restrictive applicable limits across a batch;
- mutation and persistence.

The selected batch is validated completely before the in-memory claim is changed. A successful batch triggers one claim save rather than one save per selected chunk.

## Network bounds

- Radius: 2–12 chunks around the map center.
- Maximum map cells: 625.
- Maximum owned claims sent for claim cycling: 256.
- Maximum selected chunks per action: 256.
- The server clamps the map center to 128 chunks from the player's current position.
- Action chunks must fall inside the submitted visible viewport.

## Colors

The map uses the same server-managed semantic colors as world border visualization:

- own claim;
- other claim;
- server region;
- temporary selection.

Changing these through `/ssu borders color ...` is reflected the next time the map snapshot is requested.


### dev5.2 map rendering

- Terrain background now uses 16×16 samples per chunk.
- Claim, other-claim, region and selection overlays are transparent fills with a single combined outer contour.
- Internal chunk borders are no longer drawn inside the same contiguous claim or selection.
