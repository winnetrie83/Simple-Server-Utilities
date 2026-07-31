# SSU 1.6.0-dev2 implementation notes

## Hologram rendering

Floating text now emits one centered `TextGizmo` per line. The former offset grey pass was not a real font shadow and appeared as a duplicate line, so it has been removed. The persisted `shadow` member remains only for schema/wire compatibility and is normalized to `false`.

## Admin Tools

The Admin Center contains a dedicated Admin Tools page. The dashboard does not grant authority by itself: every Get Tool action and every editor submission is validated again by the server.

### Hologram Tool

- Item: named amethyst shard.
- Left-click: stores a temporary position offset from the clicked block face.
- Right-click: opens the custom editor.
- Supported editor types: text, HTTP(S) link, image definition and scoreboard.
- The server uses its stored anchor, not coordinates submitted by the client.
- The named tool item remains recognizable after relog or server restart; only its temporary anchor expires after ten minutes, logout or a dimension change.
- Only the initial left-click action is handled, so holding the mouse button does not repeatedly replace the anchor.

The image editor stores and validates the same image definition fields introduced in dev1. The actual textured billboard renderer is still not included; image definitions therefore retain the existing placeholder rendering.

### Region Tool

- Item: named wooden axe.
- First left-click: position 1.
- Second left-click: position 2.
- A new left-click after a complete selection starts a new selection.
- The named dashboard-issued tool remains recognizable after relog or server restart; every use still rechecks region permissions.
- Only the initial left-click action is handled, preventing a held click from setting both points.
- Right-click: opens the initial region editor.
- The GUI configures name, priority, eleven protection flags, renting, price, period and reset behaviour.
- The server creates the region from its live selection and rechecks region permissions and overlap rules.

## Natural-tree Treecapitator

Player-placed log and leaf positions are captured from the actual placed block state and persisted per dimension. Treecapitator excludes those positions from the origin and the discovered trunk. A natural tree additionally requires:

- at least two connected usable logs;
- vertical trunk height;
- a supported lowest trunk block;
- at least three non-persistent, non-decaying leaves;
- canopy leaves near the top of the trunk.

The configured leaf range controls canopy discovery. Leaves are not sent in the preview and do not count toward the log permission limit. When instant natural leaves are enabled, canopy removal runs only if the permission limit covered the complete detected trunk. Protection is rechecked for every leaf before removal.

Tracking is exact for player placements made after this version is installed. Pre-existing builds have no historical placement metadata, so they are rejected by structure and leaf-state heuristics rather than by guaranteed provenance.

## Compatibility

- Version: `1.6.0-dev2`
- Network protocol: `24`
- Player UI preference schema: unchanged at `3`
- Hologram schema: unchanged at `1`
- New isolated storage: `simpleserverutilities/utility_mining/player_placed_tree_blocks.json`
