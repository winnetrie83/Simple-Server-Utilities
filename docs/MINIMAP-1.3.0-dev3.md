# HUD Minimap Core — SSU 1.3.0-dev3

## Scope

Dev3 activates the first real SSU minimap using the preferences introduced in dev1 and exposed through the dev2 Settings page.

The minimap is an always-visible HUD layer while normal gameplay is active. It is hidden while another screen is open so it does not cover the dashboard, inventory or claim map.

## Rendered information

- top-down terrain for a 128×128-block area centered near the player;
- Minecraft map colors with simple height shading;
- a centered player-direction marker;
- a north indicator when north-up mode is enabled;
- current X/Z coordinates;
- owned claim chunks;
- trusted claim chunks;
- claim chunks owned by other players;
- overlapping server-region areas and boundaries.

The terrain is generated only from chunks already present on the client. It does not send block data over SSU networking and does not request or force-load chunks on the server. Missing client terrain stays visible as a dark checker pattern.

## Existing settings now active

The graphical Settings page and command fallback both control the live minimap:

```mcfunction
/ssu settings minimap enabled <true|false>
/ssu settings minimap size <64-256>
/ssu settings minimap shape <circle|rectangle>
/ssu settings minimap position <top_left|top_right|bottom_left|bottom_right>
/ssu settings minimap northup <true|false>
/ssu settings minimap claims <true|false>
/ssu settings minimap regions <true|false>
```

Changes are applied locally at once and confirmed with a fresh server snapshot.

## Client terrain work

The fixed internal terrain texture is 128×128 pixels, where one texture pixel represents approximately one horizontal world block. Rebuilding is spread over multiple client ticks instead of sampling the whole map in one frame.

The map recenters after meaningful player movement. Large movement and teleportation force an early rebuild. A shape change or changed claim/region snapshot also invalidates the terrain texture so overlays cannot remain stale.

## Server-authoritative overlays

The client requests a compact minimap snapshot. The server decides:

- whether `ssu.minimap.use` is allowed;
- the validated player settings;
- nearby claim ownership/trust status;
- nearby region rectangles;
- semantic colors from the existing border configuration.

The client never determines claim ownership or region access itself. Payloads are bounded to 512 claim entries and 256 region entries, although the normal seven-chunk search radius is much smaller.

## Network and storage compatibility

- Network protocol: `10`.
- Dev2 protocol: `9`; dev2 and dev3 client/server builds must not be mixed.
- No claim, region, economy, rent, rank, permission, home or warp storage format changed.
- The existing `player_settings/<uuid>.json` schema is reused unchanged.

## Current limitations

- The first implementation is a surface map, not a cave-layer map.
- In a ceiling dimension such as the Nether, the topmost surface or roof may dominate the terrain view.
- There is no minimap zoom control yet; the configurable size changes HUD size, not world scale.
- Waypoints, biome labels, entity radar and map icons are not included.
- The full-screen interactive claim map still uses its own terrain renderer. Sharing one advanced renderer remains a later optimization/refactor.
