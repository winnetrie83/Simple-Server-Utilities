## 1.3.0-dev3

- Added the first real always-visible SSU HUD minimap.
- Added a 128×128 dynamic terrain texture sampled incrementally from chunks already loaded by the client.
- Added circular and rectangular map shapes, all four screen corners and sizes from 64 to 256 GUI pixels.
- Added north-up and player-up rotation modes with a centered heading marker, north indicator and live X/Z coordinates.
- Added server-authoritative nearby claim overlays for owned, trusted and other-player claim chunks.
- Added server-authoritative region-area overlays with stronger outer boundaries.
- Reused the existing semantic border colors and persistent dev1/dev2 minimap settings.
- Added immediate minimap refresh after graphical or command-based settings changes.
- Added periodic compact overlay synchronization and an early refresh after large movement or same-dimension teleportation.
- Kept terrain generation clientside and limited it to locally available chunks; the minimap never requests or force-loads world chunks.
- Added safe payload limits and normalization for shape, position, dimensions, region names and overlay counts.
- Increased the network protocol from 9 to 10 because dev3 adds minimap request and snapshot payloads.
- Existing claims, regions, economy, rents, ranks, permissions, homes, warps and player-settings data remain compatible.

### Current limitations

- This first renderer is a surface minimap, not a cave map. In ceiling dimensions such as the Nether it may primarily show the upper surface or roof.
- Waypoints, entity radar, biome labels, zoom controls and a shared full-screen advanced map remain future work.
- Terrain can only be shown for chunks the Minecraft client has already received. Unavailable terrain is displayed as a dark checker pattern.

## 1.3.0-dev2

- Replaced the first vanilla-button dashboard with a Bedrock-inspired SSU dashboard shell.
- Added a framed, draggable 3D player portrait using the active Minecraft skin.
- Added a responsive profile panel with player name, base rank, wallet balance and module counts.
- Added texture-backed module tiles with hover glow for Claims, Travel, Wallet and Regions.
- Added dedicated Settings and admin-only shield buttons in the dashboard header.
- Added an Admin Center with Players & Permissions, Economy, Regions and Core sections.
- Added a graphical player/rank administration page for rank assignment and personal permission overrides while preserving server-side validation.
- Added a fully functional settings page for dashboard hints and all prepared minimap preferences.
- Added responsive layouts that hide the profile panel and switch the tile grid when the available GUI width is limited.
- Preserved the existing claim map, economy, renting, home, warp, border, rank and permission command paths as the authoritative action layer.
- Kept network protocol version 9 because the dashboard payload schema and save formats are unchanged.
- The actual always-visible HUD minimap and shared advanced map renderer remain scheduled for 1.3.0-dev3.

## 1.3.0-dev1

- Reworked permission precedence so personal permissions always override contextual and rank values.
- Added persistent permission settings with a configurable default rank.
- New players now receive the configured default rank automatically on first join.
- Added unified `/ssu rank` and `/ssu perm` command trees and stopped registering the old `/permissions` root.
- Rank assignment now selects one base rank while preserving all personal permissions.
- Removed the separate `/claims chunks` and `/claims groups` administration paths. Claim limits are now ordinary personal or rank permissions.
- Added one-time migration of old claim-limit overrides into `ssu.claims.max_chunks` and `ssu.claims.max_groups` personal permissions.
- Added `ssu.settings.use`, `ssu.admin.menu` and `ssu.minimap.use`.
- Added persistent player UI/minimap preferences as groundwork for the new dashboard and minimap.
- Added the supplied dashboard button, glow, portrait-frame and section icon textures to the resource pack.
- Extended the dashboard payload with player name, primary rank and validated UI/minimap settings.
- Network protocol increased from 8 to 9.

## 1.2.0-dev2

- Connected server-region renting to the built-in Economy Core with exact minor-unit rent and renewal payments.
- Added a durable cross-module region-rent journal that reconciles economy transactions with region rental sequences after a restart.
- Added deterministic owner payouts and configurable server share through `/regions rentconfig ownershare <0-100>`.
- Added configurable pro-rata cancellation refunds for player cancellations and administrative cancellations.
- Added `REGION_RENT`, `REGION_RENEW`, owner-payout, refund and rollback economy transaction types.
- Rent and extension offers now show Belgian-formatted prices and the player's current balance before confirmation.
- Region cancellation confirmation now shows the configured refund percentage and current estimated refund.
- Added Wallet-admin controls for owner share, player refund and admin refund policy.
- Added rentable and currently rented regions to the player dashboard with Rent, Extend and Unrent actions.
- Added exact `priceMinor`, rental sequence and refundable-value metadata while preserving the legacy whole-unit `amount` field.
- Added safe recovery for uncertain storage outcomes: money is never automatically charged twice, and incomplete compensation remains visible as a pending rent operation.
- Fixed paused rentals so their refundable value no longer decreases while the timer is paused.
- Prevented deletion of actively rented regions until the rental is safely cancelled.
- Network protocol increased from 7 to 8 because the dashboard payload now contains rental and policy data.

## 1.2.0-dev1

- Added the reusable `SsuTransactionManager` with ordered steps, reverse rollback and idempotency protection.
- Added exact long-based economy accounts, Belgian Dutch euro formatting, player payments and administration commands.
- Added durable PREPARED/COMMITTED economy journals with account revisions and startup recovery.
- Added the Wallet dashboard with current balance, recent transaction history and player payment fields.
- Added `ssu.economy.*` permissions and isolated economy storage below `simpleserverutilities/economy`.
- Network protocol increased from 6 to 7.

## 1.1.0-dev6

- Added a coarse spatial index for admin regions, using 8×8-chunk cells with a safe overflow path for extremely large regions.
- Region protection and nearby-region visualization now query spatial candidates instead of linearly scanning every region.
- Added a bounded LRU permission-resolution cache with automatic invalidation after permission data changes and context-aware region fingerprints.
- Added internal performance counters for region lookups, permission cache efficiency, jobs and storage.
- Added `/ssu core performance` and `/ssu core performance reset`.
- Expanded the admin Core GUI with permission-cache and region-index statistics.
- Migrated homes, warps, ranks, player permissions, dimension scopes and claim-role scopes to the batched dirty-record storage layer without changing their JSON schemas.
- Storage flushes now remain unsuccessful while a path still requires retry, preventing legacy files from being archived after a terminal write failure.
- Network protocol increased from 5 to 6 because the menu snapshot now carries Core performance data.
- Includes the corrected dev5.2.1 claim-map widget baseline.

## 1.1.0-dev5.2.1

- Fixed `ClaimMapWidget` compilation errors introduced in dev5.2.
- Replaced the conflicting record factory method name and corrected group construction.
- Kept 16×16 terrain sampling and combined claim contours unchanged.

## 1.1.0-dev5.2

- Claim map terrain resolution increased from 8×8 to 16×16 samples per chunk for a clearer world view.
- Claim map overlays no longer outline every claimed chunk individually. Claims, other claims, regions and current selections are rendered as coherent transparent areas with only the true outer contour outlined.
- Selected claim contours remain highlighted without drawing internal chunk borders.
- Claim terrain generation workload was rebalanced for the higher-resolution map background.

# Changelog

## 1.1.0-dev5.1

Client-rendered terrain background for the interactive claim map.

### Added

- The interactive claim map now draws a top-down terrain layer from chunks already loaded on the client.
- Terrain is sampled at 8×8 points per chunk and cached in one nearest-filtered dynamic texture.
- Height-based vanilla map-color shading makes hills, water, forests and built areas easier to distinguish.
- Terrain generation is spread across client ticks and does not add server map-generation work or new network payload data.
- Unloaded client chunks remain a dark checker pattern instead of forcing chunk loads.

### Changed

- Claim and server-region cells are now translucent overlays, keeping the terrain visible underneath.
- Every claimed/region cell receives a thin outline in its configured semantic color.
- The currently selected claim uses a slightly thicker outline in its own category color instead of a white outline.
- Selected chunks use a translucent yellow preview plus a stronger yellow outline.
- Wilderness no longer receives an opaque dark fill; the terrain itself represents wilderness.
- The chunk grid is less opaque so it remains readable without dominating the map.
- Added the missing `AbstractWidget.updateWidgetNarration(...)` implementation to `ClaimMapWidget`.

### Compatibility

- No payload or save format changed; the network protocol remains version `5`.
- The terrain layer is entirely clientside. A dev5.1 client can interpret the same claim-map payload as dev5, though using the same build on client and server remains recommended.
- The map still displays only the current dimension and only terrain the client has received.

## 1.1.0-dev5

Interactive, server-authoritative chunk claim management.

### Added

- Fully interactive claim map opened with `/claims map`, `/claims gui` or the Claims page in the SSU dashboard.
- Left-click chunk selection for creating a new claim, expanding an existing claim or removing chunks.
- Right-drag panning, mouse-wheel zoom, directional pan buttons and recentering on the player.
- Claim cycling and live display of total chunk, claim-count and per-claim limits.
- One validated batch request for up to 256 selected chunks instead of one command/write per chunk.
- Server-side validation for claim names, ownership, dimensions, permissions, total/group limits, server-region overlap and final connected shape.
- Context-sensitive claim permissions and limits are resolved at every selected target chunk, not merely at the player's standing position.
- `ClaimShapeValidator`, a pure shared connectivity validator with randomized tests.
- `/claims map text <name>` retains the old chat-based character map.
- Claim-map colors now follow the admin-configurable semantic border colors.

### Changed

- `/claims map [name]` now opens the interactive map; `/claims gui [name]` remains an alias.
- Creating, extending and shrinking a claim through the map writes the validated batch only once.
- The focused player-claim ribbon now extends eight additional blocks downward while retaining the calm contour-only style.
- Claims from other players, including trusted claims, use the configured `other_claim` color on the map.
- Network protocol version increased from `4` to `5`; dev4 and dev5 clients/servers must not be mixed.

### Compatibility

- Existing claim IDs, claim JSON files, limits, permissions and all other save formats remain unchanged.
- The existing multiple-claim model remains: a player may own several separate claim groups, while every individual claim group must remain internally connected and belong to one dimension.
- Commands remain available as a fallback for every map operation.

### Current limitations

- The map displays the current dimension only.
- Client selection provides a visual preview, while final validity is intentionally decided by the server after confirmation.
- Claim trust, role and flag editors still use the existing dashboard/commands and will be added to dedicated claim-detail GUI pages later.
- The region spatial index and permission-result cache remain planned for the next Performance Core milestone.

## 1.1.0-dev4

First interactive player/admin dashboard and calmer multi-region visualization.

### Added

- First reusable SSU GUI Core with a server-authoritative snapshot payload.
- Open the dashboard with the configurable `U` key or `/ssu menu`.
- Player pages for claims, homes/warps and personal border settings.
- Admin pages for independent region-border control and Core storage/job status.
- Persistent per-player set of individually selected region borders.
- `/regions hide <name>` to hide one selected region while keeping the others visible.
- `/regions hide` now clears all individually selected region borders.
- Key-binding and dashboard language entries.

### Changed

- `/regions show <name>` now adds that region to the player's visible set instead of replacing the previously shown region.
- Individually selected regions are independent from the automatic nearby-region overview toggle.
- Claim borders now render as a low translucent ribbon along the true outer claim contour near the player's current height.
- Claim ribbons are depth-tested, limited to 192 blocks and no longer draw a 128-block-high filled volume through the landscape.
- Exact server-region and temporary-selection wireframes remain unchanged.
- Menu lists are permission-aware and capped before networking.
- Network protocol version increased from `3` to `4`; dev3 and dev4 clients/servers must not be mixed.

### Compatibility

- Existing claim, region, home, warp, permission and snapshot formats remain unchanged.
- Existing visualization preference files migrate in place from schema version 1 to 2 by adding the optional `pinnedRegions` set.
- Commands remain available as a complete fallback for the first GUI release.

### Current limitations

- This is the functional GUI foundation, not the final visual skin. Later modules will reuse and progressively polish it.
- The first dashboard uses existing commands for actions after receiving server-authoritative menu data.
- Search boxes, advanced editors, permission-source explanations and module-specific admin settings are planned for later GUI milestones.

## 1.1.0-dev3

Persistent border preferences, configurable semantic colors, batched storage and bounded server jobs.

### Added

- Persistent per-player claim- and region-border visibility preferences.
- `/ssu borders status`, `/ssu borders claims on|off`, `/ssu borders regions on|off` and `/ssu borders refresh`.
- Short aliases `/claims borders on|off` and `/regions borders on|off`.
- Admin-managed semantic border colors with list, set, reset and reset-all commands.
- Default categories: own claims (green), other claims (blue), server regions (purple), temporary selections (yellow) and hostile territory (red).
- Reserved color categories for allied territory, safe zones, quest areas and minigame areas.
- Translucent claim-volume fills generated from merged chunk rectangles.
- `BatchedStorageService` with a single storage worker, path-based write coalescing, atomic writes, deletion queue, retries, flush and statistics.
- `DirtyJsonRecordStore` for writing only changed JSON records.
- `SsuJobScheduler` for fair, bounded, multi-tick server work with progress, cancellation and resource locks.
- `/ssu core status`, `/ssu core jobs list` and `/ssu core jobs cancel <uuid>`.
- New permissions `ssu.borders.claims.view`, `ssu.borders.regions.view`, `ssu.visualization.admin` and `ssu.core.admin`.

### Changed

- Claim border volumes now follow the player vertically from 64 blocks below to 64 blocks above the camera instead of using a four-block-high outline.
- Nearby claim and region overview layers update automatically when a player changes chunk, relevant data changes or settings change.
- Manual `/claims show` and `/regions show` focus layers remain available for targeted testing.
- Claim and region overview geometry, colors and visibility are server-authoritative while rendering remains clientside.
- Claim and region records now use dirty-record tracking and coalesced disk writes without changing their existing JSON formats.
- Region selection fill, region clear and snapshot reset now run as bounded jobs instead of processing the full volume in one server tick.
- Rent-triggered region resets are scheduled through the same bounded job system.
- When rent expiry or unrent requires a snapshot reset, rental access is finalized only after the reset job completes; cancellation leaves the rental pending for a safe retry.
- Conflicting long-running jobs cannot own the same region resource simultaneously.
- SSU reload is refused while long-running jobs are active.
- Network protocol version increased from `2` to `3`; dev2 and dev3 clients/servers must not be mixed.

### Compatibility

- Existing claim, region, home, warp, permission and snapshot data formats remain unchanged.
- New visualization settings are stored separately under `simpleserverutilities/visualization/`.
- Player border preferences default to off until each player enables the desired layer.
- Hostile territory is a reserved visual category in this release; automatic hostility classification requires a later team/faction system.

### Current limitations

- The first storage migration covers claims, regions and visualization settings. Homes, warps and permissions still use their existing synchronous save paths.
- Jobs are not yet persisted across a server restart. Cancelling or stopping during a world-edit job can leave a deliberately partial operation.
- Region snapshots still restore block states only; block-entity NBT, container contents, signs and entities are not yet preserved.

## 1.1.0-dev2

Client-rendered claim and region border visualization.

### Added

- Reusable border visualization payload and client state with independent claim, region and selection layers.
- Client-side Minecraft 26.2 Gizmo renderer; no server particles, block changes or visual tick loop are used.
- `/claims show <name>` and `/claims hide`.
- `ssu.claims.visualize` permission, enabled by default when claims are available.
- `/regions selection clear`.
- Automatic client visualization of completed region selections made by commands or the bound selection tool.
- Pure claim contour builder that removes internal chunk borders and merges adjacent straight edges.

### Changed

- `/regions show <name>` now sends one compact border snapshot to the client instead of spawning END_ROD particles every second.
- Region borders render as exact three-dimensional wireframes with a subtle translucent fill.
- Claim borders render as a four-block-high contour around the current camera height so chunk boundaries stay visible on uneven terrain.
- Visible claim contours refresh after claiming or unclaiming chunks and disappear if the shown claim is deleted.
- Visible region borders refresh after redefining a region and disappear if the shown region is deleted.
- Region selection visualization is cleared after creating, redefining or explicitly clearing a selection.
- Client visualization state is cleared when leaving a server or world.
- SSU network protocol version increased from `1` to `2` so dev1 and dev2 clients fail fast instead of silently disagreeing about visualization payloads.

### Compatibility

- Existing mod ID and all existing save formats are unchanged.
- Existing `/regions show` and `/regions hide` commands remain available.
- The new visualization data is transient and is never written to the world save.

## 1.1.0-dev1

First data-compatible Core 2.0 migration build.

### Added

- Typed `SsuServiceRegistry` for gradually replacing global manager access.
- Dependency-aware `SsuModuleRegistry` and module lifecycle contract.
- `SsuCore` lifecycle integration without changing existing module save formats.
- Dedicated `ssu.regions.admin.bypass` permission.
- Redstone protection enforcement for the existing claim and region `allowRedstone` setting.
- Safe teleport destination resolution with world-border, build-height, collision, fluid and floor checks.
- GitHub Actions upload step for the built mod JAR.

### Changed

- Claim and region protection bypasses are now resolved separately.
- Piston protection validates the full resolved piston structure, including slime/honey branches and destroyed blocks.
- Piston flags are now also respected for movement completely inside one claim.
- Homes, warps and delayed teleports validate their destination before teleporting and again when a delay completes.
- Claim and region direct teleports now search for a nearby safe standing position.
- Expired teleport cooldown records are removed when encountered.
- Technical identifiers are normalized with `Locale.ROOT`.
- Project metadata, README and language keys no longer contain Example Mod placeholders.
- JAR output is configured to be reproducible.

### Compatibility

- Existing mod ID remains `simpleserverutilities`.
- Existing claim, region, home, warp, permission and snapshot save formats are unchanged.
- Existing commands are unchanged.
- Servers that previously used `ssu.claims.admin.bypass` as an accidental region bypass must grant the new `ssu.regions.admin.bypass` permission where appropriate. Operators continue to bypass both systems.

### Not yet included

- Region spatial indexing.
- Permission-result caching.
- Dirty-record/batched storage.
- Transaction and job scheduler services.
- Economy, mail, Auction House, bags, NPCs, quests or minigames.
