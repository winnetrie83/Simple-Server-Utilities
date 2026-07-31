# Border visualization architecture

Current SSU development builds use the Minecraft 26.2 client Gizmo system for transient world-space visualization. The server remains authoritative for categories, colors, visibility permissions and geometry; the client only caches and renders received snapshots.

## Network compatibility

SSU 1.6.0-dev10.2 uses network protocol `31`. Client and server must use the exact same development build.

## Independent layers

- `CLAIM`: automatic nearby claim overview.
- `REGION`: automatic nearby server-region overview.
- `CLAIM_FOCUS`: one claim selected with `/claims show <name>`.
- `REGION_FOCUS`: retired legacy layer; dev10.2 actively clears it.
- `SELECTION`: the current completed temporary region selection.

Updating or clearing one layer does not remove the others.

## Player visibility preferences

Each player has independent, persistent master visibility toggles:

```text
/ssu borders claims on|off
/ssu borders regions on|off
```

Aliases:

```text
/claims borders on|off
/regions borders on|off
```

The same toggles are available in the SSU dashboard. Both default to off and are stored per UUID under:

```text
simpleserverutilities/visualization/players/<uuid>.json
```

The server permissions are:

```text
ssu.borders.claims.view
ssu.borders.regions.view
```

Effective visibility is the conjunction of three conditions: the relevant server module is enabled, the server permission allows the player to view the layer, and the player's own master toggle is on. A local preference cannot bypass a denied server permission, and the server does not force a border onto a player who turned it off. From dev10.1 onward this capability check deliberately does not use the normal operator bypass: an explicit deny remains a hard server gate even for an operator account.

## Server-owned region eligibility

From dev10.2 onward every server region has a persistent `borderVisible` setting. The Regions page controls this server-owned setting:

```text
Show        # make this region eligible for players
Disable     # remove this region from every player overview
Disable all # disable every server-region border
```

The equivalent administrator commands are:

```text
/regions show <name>
/regions hide <name>
/regions hide
```

This is not a forced render and it is not a personal pin. A region is sent only when all of the following are true:

1. the Server Regions module is enabled;
2. the region's own `borderVisible` setting is enabled by the server;
3. `ssu.borders.regions.view` allows that player;
4. that player enabled the personal Region borders master switch;
5. the region is in the player's configured render range and current dimension.

Existing region records without `borderVisible` migrate safely as hidden. The property is stored in region record schema `4` and survives restart and region-boundary redefinition. The old per-player `pinnedRegions` values are ignored for rendering and the legacy `REGION_FOCUS` layer is cleared, so a region cannot be duplicated or become visible again merely because it was disabled from the Regions page.

## Semantic categories and default colors

| Category | Serialized name | Default |
|---|---|---|
| Own claims | `own_claim` | Green `#42F56C` |
| Other players' claims | `other_claim` | Blue `#4287F5` |
| Server regions | `server_region` | Purple `#A855F7` |
| Temporary selections | `selection` | Yellow `#FFD447` |
| Hostile territory | `hostile_territory` | Red `#F54242` |

Reserved future categories: `allied_territory`, `safe_zone`, `quest_area`, and `minigame_area`.

## Admin color management

```text
/ssu borders color list
/ssu borders color set <category> <RRGGBB>
/ssu borders color reset <category>
/ssu borders color resetall
```

Only console or players with `ssu.visualization.admin` can change the global colors.

## Claim geometry in dev5

Claim chunks are reduced to their true exposed outer contour. Shared internal chunk edges are removed and adjacent collinear edges are merged before networking.

The client renders each edge as a low translucent ribbon near the current camera height:

- approximately 10.5 blocks tall, extending about 8.75 blocks below and 1.75 blocks above the current camera block level;
- narrow thickness around the exact chunk boundary;
- top, bottom and end accent lines;
- depth-tested instead of always visible through terrain;
- skipped beyond 192 blocks;
- moves vertically with the player without new network packets.

This remains deliberately calmer than the dev3 128-block-high filled claim volume. Dev5 extends the same contour ribbon eight additional blocks downward so slopes, cellars and shallow underground approaches remain easier to read without restoring a full wall around every claimed chunk.

## Region and selection geometry

Server regions and completed temporary selections retain their exact inclusive 3D block coordinates, translucent fill and wireframe. Region primitives are distance-filtered and frustum-culled clientside.

## Performance model

- No particles, marker entities or temporary blocks.
- No server visualization tick loop that emits world effects.
- The server only sends bounded snapshots on show/change/hide or relevant overview changes.
- Rendering, distance filtering and frustum culling happen clientside.
- Internal claim edges are removed and straight edges are merged before networking.
- Overview snapshots are capped at 1,024 entries.
- Visualization never affects protection authority.

## Interactive claim-map colors

The dev5 interactive claim map receives the current server-configured `own_claim`, `other_claim`, `server_region` and `selection` colors in its bounded snapshot. The map therefore stays visually consistent with the world-space border renderer after an admin changes colors.
