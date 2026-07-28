# Dashboard & Minimap Foundation — 1.3.0-dev1 to dev3

Dev1 prepared the data and assets, dev2 implemented the dashboard, and dev3 activates the first real HUD minimap renderer.

## Bundled user-provided textures

The supplied PNG files are stored under:

```text
assets/simpleserverutilities/textures/gui/dashboard/
```

Included assets:

- `button.png`
- `button_glow.png`
- `button_back.png`
- `button_back_glow.png`
- `portrait_framework.png`
- `cogwheel.png`
- `shield.png`
- `claim.png`
- `market.png`
- `multiplayer.png`
- `portal.png`
- `questbook.png`
- `ticket.png`

Dev2 now consumes these assets in the Bedrock-inspired dashboard renderer. It adds texture-backed module tiles, hover glow, the framed 3D player portrait, Settings navigation and the admin-only shield entry point.

## Persistent player UI preferences

Preferences are stored per UUID in:

```text
simpleserverutilities/player_settings/<uuid>.json
```

The first schema contains:

- dashboard hints on/off
- minimap enabled on/off
- minimap size, clamped to 64–256 pixels
- minimap shape: circle or rectangle
- minimap position: any screen corner
- north-up on/off
- claim overlay on/off
- region overlay on/off

The menu snapshot carries these validated values to the client. Dev2 exposes them in Settings and dev3 applies them directly to the HUD minimap without changing the player-settings schema.

## Settings command fallback

```mcfunction
/ssu settings
/ssu settings hints <true|false>
/ssu settings minimap enabled <true|false>
/ssu settings minimap size <64-256>
/ssu settings minimap shape <circle|rectangle>
/ssu settings minimap position <top_left|top_right|bottom_left|bottom_right>
/ssu settings minimap northup <true|false>
/ssu settings minimap claims <true|false>
/ssu settings minimap regions <true|false>
```

These commands remain the complete fallback. Dev2 exposes the same validated values through the graphical Settings page, and dev3 applies every listed minimap option to the visible HUD renderer.

## Network

The dashboard snapshot now also carries:

- player display name
- primary/base rank
- whether personal settings are available
- validated UI/minimap preferences
- whether the dedicated admin interface may be shown

Dev1/dev2 use protocol version 9. Dev3 adds dedicated minimap request and snapshot payloads and therefore uses protocol version 10.

## Dev3 implementation status

The dashboard and graphical settings layer remain from 1.3.0-dev2. Version 1.3.0-dev3 adds the always-visible HUD minimap, locally sampled surface terrain, heading/coordinate indicators and server-authoritative claim/region overlays. The saved settings schema is unchanged; only the transient network protocol increases to version 10.
