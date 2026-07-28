# Dashboard GUI Core — 1.3.0-dev2

## Goal

Dev2 turns the dev1 data and texture foundation into the first functional Bedrock-inspired SSU dashboard. The client is responsible for presentation and navigation only. Existing commands and services remain the authoritative action layer and continue to perform all permission and state validation.

## Opening the dashboard

- Default key: `U`
- Command fallback: `/ssu menu`
- The dashboard does not pause the game.

## Dashboard shell

The dashboard now contains:

- a dark framed main panel;
- the current page title and subtitle;
- a texture-backed green back/close control;
- a small Settings button when `ssu.settings.use` is allowed;
- a shield button when the server snapshot grants Admin Center access;
- a framed, draggable 3D player model using the active client skin;
- player name, base rank, formatted wallet balance and summary counts;
- custom 54×54 module buttons with a supplied glow texture on hover.

On narrow GUI widths, the profile panel is hidden and module tiles reflow to keep the usable controls inside the screen.

## Player modules

The homepage links to:

- **Claims** — owned claims, claim focus controls and the interactive claim map;
- **Travel** — homes and permitted server warps;
- **Wallet** — balance, player payments, recent transactions and economy policy controls when permitted;
- **Regions** — rentable regions, active rentals and region-border selection.

## Settings

The graphical Settings page controls the persistent dev1 preferences:

- dashboard hints;
- minimap enabled;
- minimap size;
- circle or rectangle shape;
- screen corner;
- north-up orientation;
- claim overlay;
- region overlay;
- current-world claim and region border visibility.

The page updates immediately on the client and also sends the matching `/ssu settings ...` or `/ssu borders ...` command. The server remains authoritative and persists the validated value.

The settings are ready for the actual HUD minimap renderer, which remains a dev3 feature.

## Admin Center

The shield button opens a separate Admin Center with:

- **Players** — inspect personal permissions, assign one base rank, inspect a rank and set or unset personal overrides;
- **Economy** — existing Wallet and rent-policy administration;
- **Regions** — existing region visibility and rental tools;
- **Core** — scheduler, storage, permission-cache and spatial-index status.

Admin actions use the existing `/ssu rank`, `/ssu perm`, `/regions`, `/ssu core` and economy command paths. Showing a client button never bypasses server permissions.

## Compatibility

- Existing world data, UUID profiles, ranks, permissions, claims, regions, rentals, wallets, homes and warps are unchanged.
- `SsuMenuSnapshotPayload` is unchanged.
- Network protocol remains version `9`.
- A matching dev2 client and server is recommended so every player receives the same dashboard presentation.
