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
