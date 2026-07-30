# Simple Server Utilities

Simple Server Utilities (SSU) is a modular NeoForge server utility mod for Minecraft 26.2.

## Current systems

- Player chunk claims with connected claim groups, trust, limits, flags and claim spawns
- Three-dimensional server-owned admin regions with priorities, nesting, managers, members, renting and versioned, recoverable snapshots
- Configurable default rank plus rank permissions and higher-priority personal permissions, with a compact paged editor, per-key default/effective tooltips and direct player/rank selection
- Homes, global warps, a persistent server spawn and delayed teleports with cooldowns
- Protection for blocks, entities, PvP, explosions, pistons, fluids, hoppers, fire and redstone
- A Bedrock-inspired, page-driven player dashboard and permission-aware Admin Center opened with `U` or `/ssu menu`, using bounded server queries and typed actions
- An interactive, server-authoritative chunk claim map for creating, expanding, shrinking and safely deleting connected claim groups
- Client-rendered claim ribbons and exact 3D region/selection borders with persistent player toggles, multiple selected regions and admin colors
- Batched dirty-record storage for claims, regions, homes, warps and permissions
- Bounded multi-tick region world-edit and snapshot-capture jobs with region locks and destructive-reset checkpoints
- Region spatial indexing, permission-result caching and an admin performance dashboard with active-job, rental-recovery, account, permission and online/offline player-profile inspection
- Exact server-authoritative economy accounts, transaction journals and player payments
- Paid server-region renting with renewals, deliberate rent money sinks and journaled pro-rata cancellation refunds
- A durable World-of-Warcraft-style mailbox with visible-inbox soft caps, unlimited on-disk overflow queues, status-aware and capped sent mail, item-stack and money attachments, scrollable known-player picker, multiline composition, categorized auto-delete preferences and an Auction House-ready delivery API
- Paged visual claim and region settings editors with server-side validation, access lists, contextual region-permission overrides and per-setting hover explanations
- Persistent player dashboard/minimap preferences, custom dashboard textures and a framed mouse-following 3D skin portrait
- An always-visible, double-buffered high-resolution HUD minimap with resource-pack-aware aerial terrain, player heading, coordinates and server-authoritative claim/region overlays
- A full-screen world map on `M`, with zoom/pan controls and direct switching to and from the interactive claim map
- A shared persistent topographic aerial atlas with resource-pack-aware colours, separate terrain/canopy heightfields, fluid rendering, biome tint, multi-scale relief, gamma-correct zoom levels and an asynchronous disk cache

## Development requirements

- Java 25
- Gradle 9.2.1 through the included wrapper
- NeoForge 26.2.0.7-beta
- Minecraft 26.2

Build with:

```bash
./gradlew clean build
```

SSU does not include a separate JUnit source set. The normal build compiles and packages the mod; gameplay, networking and interface behaviour are verified in Minecraft.

The distributable JAR is written to `build/libs/`.

## Data compatibility

SSU stores server data below the world save in the `simpleserverutilities` folder. Development builds preserve existing claim, region, home, warp and permission data. The 1.2.0 line adds isolated economy and region-rent journal records and extends region rental JSON with backwards-compatible optional fields. The 1.3.0 line adds permission settings, automatic default-rank assignment and isolated player UI preferences. Dev3 adds transient minimap networking and client rendering; dev3.1 refines the map and dashboard renderers. Dev3.2 adds the world map and protocol 11; dev3.2.1 is a client compilation hotfix only. Dev3.3 replaces the visual terrain pipeline without changing payloads or save schemas. Dev3.4 returns the atlas to one composite pixel per block, adds smooth double-buffered claim-map zooming, live map dragging, closed leaf canopies and stronger terrain relief. Dev4 adds guarded region mutations, asynchronous version-2 snapshots with legacy reads and durable reset checkpoints, plus a persistent client aerial-map cache. Dev4.1 upgrades snapshots to version 3 for item frames/paintings, makes reset clearing drop-free, renders only the outer minimap claim perimeter and repairs right-click map dragging. Dev4.2 replaces the noisy texture-heavy aerial renderer with a muted cartographic surface filter and terrain-based multi-scale relief. Dev4.3 separates ground and visible-surface heights, restores dark three-dimensional tree crowns and adds a restrained two-distance terrain terrace pass. The 1.4.0-dev1 line replaces the monolithic dashboard transfer with compact page-specific payloads and closed typed actions, and moves Claims, Permissions, Regions, Teleport, Visualization and Menu into dependency-ordered Core 2.0 modules without changing their save formats. Version 1.4.0-dev1.1 is a source compilation hotfix for `SsuMenuService`. Version 1.4.0-dev1.2 adds the dropdown-driven, paged permission editor and mouse-following portrait. Version 1.4.0-dev1.3 compacts the permission rows and adds an administrator player-profile browser with effective-permission pages and direct permission-editor handoff. Version 1.4.0-dev1.3.1 fixes the former Gradle test classpath. Version 1.4.0-dev2 removes that separate test source set and completes lifecycle ownership for the remaining shared managers. Version 1.4.0-dev2.1 retires the experimental permanent treasury and region owner payout, migrates legacy region owners to administrative managers, treats region rent as a logged money sink with safe journaled refunds, adds claim deletion to the claim map and adds paged visual settings editors for claims and server regions. Version 1.4.0-dev3 adds a persistent server spawn, context-aware `/spawn`, guarded delayed teleports and a searchable visual region-permission editor. Version 1.4.0-dev4 unifies homes, warps, spawn, claims and regions behind contextual escape policy, adds precise permission-controlled stand-still cancellation, adds dimensions to the permission GUI and rechecks every normal delayed teleport at execution time. Version 1.5.0-dev1 adds durable per-player mailboxes, sent-mail history, on-disk visible-inbox overflow queues, item and money escrow attachments, retention maintenance and an Auction House-ready idempotent delivery API. Version 1.5.0-dev1.1 is a source compilation hotfix for the Minecraft 26.2 `AbstractContainerScreen` constructor. Version 1.5.0-dev2 adds sent-mail deletion and permission caps, recipient read/claim status, known-player suggestions, attachment-mail auto-delete preferences and categorized personal settings. Network protocol is 21; client and server must both use the same 1.5.0-dev2 build. Existing stored world/player data remains compatible; mailbox schema 3 and player UI preference schema 2 migrate automatically.

Always back up a world before installing a development build.
