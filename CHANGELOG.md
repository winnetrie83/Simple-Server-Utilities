## 1.5.0-dev2.1

- Rebuilt Mail Compose around a true `MultiLineEditBox`; the caret starts at the top-left, Enter inserts new lines, long messages scroll, and the full body area is writable.
- Rendered the Players dropdown after normal widgets so it stays in front of the Money and Message fields instead of behind them.
- Expanded the server-authoritative player picker from 8 to 256 alphabetically sorted known players and added scrolling through the dropdown.
- Moved the centered 27-slot inventory and hotbar farther down and increased their vertical separation so the Hotbar label no longer overlaps inventory slots.
- Increased the compose screen height to 430 while retaining the centered inventory, left-aligned attachments and right-aligned balance.
- Increased the network protocol from `21` to `22`; client and server must both use 1.5.0-dev2.1.

## 1.5.0-dev2

- Added individual deletion for Sent Mail plus double-confirmed bulk clearing for both Inbox and Sent Mail.
- Bulk Inbox clearing removes only read, attachment-safe visible mail. Unread mail, queued overflow mail and mail with unclaimed items or money are preserved.
- Added `ssu.mail.sent_limit` as a permission-driven cap for retained Sent Mail records; the default rank receives 20. A zero value disables Sent Mail retention without disabling mail sending.
- Separated the rolling 24-hour anti-spam history from the visible Sent Mail list, so deleting or capping outgoing records can never reset send cooldowns or daily limits.
- Sent Mail now mirrors when the recipient first opened the mail and when all item stacks and/or the money attachment were claimed.
- Added a server-authoritative known-player suggestion list to the recipient field. Exact/prefix matches are prioritised and suggestions are alphabetised; up to eight are displayed at a time.
- Unknown recipients are rejected before any item or money escrow is committed. The composer remains open, keeps every field and attachment unchanged and displays the error in red.
- Added visible placeholder lock icons to unavailable attachment slots, ready to be replaced by a future resource-pack texture.
- Prevented the inventory key (normally `E`) from closing the compose container, so it can safely be used while writing recipient, subject, message or money text.
- Added per-player auto-delete preferences for fully claimed attachment mail from private players, server/system sources and the future Auction House. All three default to off.
- Reorganised personal Settings into General, Minimap, World Map, Borders and Mail categories. Every currently persisted SSU player preference is exposed, and empty future module categories are omitted until that module has actual settings.
- Added a Borders-setting action to clear all personally selected/pinned region borders without searching the Regions page.
- Persisted world-map claim and region overlay choices and added matching fallback `/ssu settings worldmap` and `/ssu settings mail` commands.
- Migrated mailbox storage to schema 3 and player UI preferences to schema 2 without changing existing mail IDs, escrow, claim, region, economy or permission data.
- Increased the network protocol from `20` to `21`; client and server must both use 1.5.0-dev2.
- Build with `gradlew clean build` on Java 25 and verify mailbox status, clearing, suggestions and categorized settings in Minecraft.

## 1.5.0-dev1.1

- Fixed `MailComposeScreen` compilation on Minecraft 26.2 by passing the custom 176 x 248 container dimensions to the five-argument `AbstractContainerScreen` constructor.
- Removed assignments to the now-final inherited `imageWidth` and `imageHeight` fields.
- Network protocol remains `20`; this hotfix does not change packets, mail storage, permissions or gameplay behaviour.
- Existing 1.5.0-dev1 mail and world data remains compatible.

## 1.5.0-dev1

- Added the first complete SSU Mail System with a dashboard Mail button, `/mail`, an inbox, sent-mail history and an inventory-backed compose screen.
- Added durable per-player mailbox storage below `simpleserverutilities/mail/mailboxes`; incoming mail has no hard delivery limit.
- Added a permission-driven visible inbox soft cap. Excess mail remains durably queued on disk and is promoted in arrival order when visible space becomes available.
- Added login and live-delivery alerts for unread mail and a full visible inbox.
- Added up to nine item-stack attachment slots per mail. `ssu.mail.max_attachments` defaults to one and is hard-capped at nine.
- Added exact minor-unit money attachments backed by a dedicated mail escrow account and idempotent claim transaction keys.
- Added outgoing-mail permissions, a rolling 24-hour send limit and a configurable send cooldown. Incoming server/system mail remains unrestricted by player inbox capacity.
- Added configurable visible-mail retention. The retention clock starts only when queued mail becomes visible; visible mail with unclaimed attachments is returned to the durable queue instead of being destroyed.
- Added reusable idempotent server/pre-escrowed delivery APIs for future Auction House expiry, sale and recovery mail.
- Added bounded once-per-minute mailbox maintenance for offline cleanup and queue promotion.
- Added mailbox permissions: `ssu.mail.access`, `ssu.mail.send`, `ssu.mail.send.items`, `ssu.mail.send.money`, `ssu.mail.max_attachments`, `ssu.mail.inbox_soft_cap`, `ssu.mail.daily_send_limit`, `ssu.mail.send_cooldown` and `ssu.mail.admin`.
- Added mail money transaction types for escrow deposit, claim and refund. Existing world, claim, region, home, warp, spawn, economy and permission data remains compatible.
- Increased the network protocol from `19` to `20`; client and server must both use 1.5.0-dev1.
- The compose screen exposes the nine mail slots, all 27 main-inventory slots and the nine-slot hotbar. Unsent attachments are returned on normal close; drafts are not persisted across an abrupt server/process crash in this first development build.
- Build with `gradlew clean build` on Java 25 and verify runtime/UI behaviour in Minecraft.

## 1.4.0-dev4

- Unified all normal player-initiated SSU teleports behind one context-aware policy and the shared TeleportManager: homes, warps, server spawn, owned claims and accessible server regions.
- Added `ssu.teleport.escape` as an umbrella permission for blocking every player-initiated escape teleport from a region or dimension.
- Added `ssu.teleport.region_bypass` as the sole general bypass for authoritative region teleport denies. The dev3 `ssu.spawn.region_bypass` remains supported as a legacy spawn-only bypass.
- Added the canonical `ssu.teleport.require_still` permission. Existing `ssu.teleport.cancel_on_move` data remains readable as a legacy fallback until the new key is explicitly configured.
- Delayed teleports now compare the player's real coordinates rather than only block coordinates. Walking inside one block, jumping, falling, swimming, vehicle movement or changing dimension cancels a teleport when standing still is required; tiny server correction noise is tolerated.
- Countdown text only tells the player to remain still when the effective permission requires it.
- Added execution-time permission guards to homes, warps, claim teleports and region teleports, matching the dev3 server-spawn guard. Entering a denied region/dimension or losing access during the countdown cancels without applying cooldown.
- Routed normal claim and region teleports through delay, cooldown, safety and cancellation handling. Administrative claim teleports remain immediate and separate from player escape policy.
- Added Dimensions to the visual Admin Center permission editor. Loaded dimensions and stored custom dimension scopes can be searched and edited with the same typed validation and persistence as commands.
- Normalized dimension permission commands through the shared PermissionCatalog, including true/false, allow/deny, yes/no and 1/0 boolean input.
- Improved teleport denial messages so explicit region and dimension sources are named when known.
- Existing claim, region, home, warp, spawn and permission storage remains compatible. No world-data migration is required.
- Increased the network protocol from `18` to `19`; client and server must both use 1.4.0-dev4.
- The separate JUnit source set remains removed. Build with `gradlew clean build` and verify runtime/UI behaviour in Minecraft with Java 25.

## 1.4.0-dev3

- Added one persistent, dimension-aware server spawn stored below `simpleserverutilities/spawn/server_spawn.json`.
- Added `/spawn`, `/spawn set`, `/spawn clear`, `/spawn info`, `/spawn cancel`, plus `/setspawn` and `/delspawn` aliases.
- Added `ssu.spawn.use`, `ssu.spawn.admin`, `ssu.spawn.teleport.delay`, `ssu.spawn.teleport.cooldown` and `ssu.spawn.region_bypass` to the permission catalogue and default-rank migration.
- Added server spawn to the dashboard Travel page, with permission-gated Set spawn here and Clear spawn controls.
- Added a searchable, paged region-permission editor to the visual region Settings screen. Boolean values use Default/Allow/Deny; integer and text values use validated Set/Reset controls.
- Region permission mutations remain available through `/regions perm <region> list|set|unset` and now use the same catalogue normalization, persistence and permission-cache invalidation as the GUI.
- Made an explicit effective-region deny for `ssu.spawn.use` authoritative over ordinary personal/rank allows. Only `ssu.spawn.region_bypass` can ignore that region layer; unrelated non-region denies still apply.
- Extended the teleport manager with an optional execution guard. Delayed `/spawn` requests re-check the player's current effective region immediately before teleporting, preventing countdown-based region escapes.
- Added server-side validation and administrator checks for every region-permission editor request and mutation. Region managers may still change ordinary region settings but cannot change permission overrides without region edit/admin access.
- Existing claims, regions, homes, warps, economy, rentals and permission data remain compatible. New spawn storage is optional and absent until an administrator sets it.
- Increased the network protocol from `17` to `18`; client and server must both use 1.4.0-dev3.
- The separate JUnit source set remains removed. Build with `gradlew clean build` and verify runtime/visual behaviour in Minecraft.

## 1.4.0-dev2.1

- Retired the experimental permanent Server Treasury introduced in dev2. The synthetic treasury account is removed on load and its remaining balance is explicitly retired from circulation; historical treasury transaction types remain readable for backwards compatibility.
- Removed region owner payouts. Server regions are now explicitly server-owned and paid rent/renewal charges are journaled money sinks rather than transfers to an administrator or system account.
- Renamed region owners to region managers throughout commands, storage, dashboard payloads and interface text. Existing `owners` arrays are migrated into `managers` when old region JSON is loaded.
- Kept cancellation refunds durable and idempotent without a treasury: the frozen pro-rata refund is credited back to the former renter and remains recoverable through the existing rent journal when a write fails.
- Added a double-confirmed Delete claim action directly to the interactive claim map. The server rechecks claim ownership and `ssu.claims.delete` before deleting the full connected claim group.
- Added paged visual Settings screens for claims and regions, opened from the dashboard and from the claim map.
- Added claim controls for all existing protection flags, welcome message, trusted-player overview and add/remove actions, plus setting or clearing the claim spawn.
- Added region controls for all existing protection flags, priority, welcome/leave messages, manager/member overview and add/remove actions, rental price/period/reset options, plus setting or clearing the region spawn.
- Added a hover tooltip to every setting with a short explanation, input type, allowed integer range where applicable, default value and current value.
- All settings mutations use bounded typed payloads and are revalidated server-side against claim ownership, claim permissions, region manager/admin access and rental administration permissions.
- Increased the network protocol from `16` to `17`; client and server must both use 1.4.0-dev2.1.
- The separate JUnit source set remains removed. Build with `gradlew clean build` and verify runtime/visual behaviour in Minecraft.

## 1.4.0-dev2

- Completed the planned Core 2.0 lifecycle ownership for the remaining shared managers: Storage, Job Scheduler, Performance Monitor, Transaction Manager, Homes, Warps and Player UI Preferences now start, stop and register through dependency-ordered `SsuModule` implementations.
- Added a pre-shutdown lifecycle phase so active bounded jobs are cancelled before region, claim, home, warp, preference and economy state performs its final persistence, while the asynchronous storage worker remains alive until every module has stopped.
- Updated `/ssu reload` to follow the same dependency order, recreate the treasury account after an economy reload and safely reload/reconcile the region-rent journal.
- Removed the separate JUnit/test source set and its ModDevGradle configuration. The normal development build is again `gradlew clean build`; runtime and visual verification remains in Minecraft.
- Added a persistent server-owned treasury account backed by the existing exact minor-unit economy journal. The treasury uses a deterministic UUID inside each world's isolated economy folder and starts at zero rather than receiving the configured player starting balance.
- Added explicit treasury permissions: `ssu.economy.treasury.view` and `ssu.economy.treasury.admin`. Both default to false; wildcard administrator ranks continue to resolve them normally.
- Added an Admin Center Treasury page with current balance, bounded/searchable transaction history, details, administrator deposits, withdrawals and payments to player economy accounts.
- Excluded the synthetic treasury account from normal economy-account lists, permission-player dropdowns and Player Info profiles.
- Routed the server remainder of every paid region rent or renewal into the treasury after the configured owner payout. Both shares remain journaled and idempotent, with compensation when a later step or region persistence fails.
- Changed cancellation refunds to transfer real funds from the treasury instead of minting new currency. If the treasury cannot cover a refund, the cancellation remains committed and the durable rent journal keeps the refund pending for recovery after the treasury is replenished.
- Added treasury transaction types for region income/reversal, administrator deposit/withdrawal and treasury payments while retaining backwards-compatible reads of existing economy and rent-journal data.
- Increased the network protocol from `15` to `16`; client and server must both use 1.4.0-dev2.
- Existing claim, region, snapshot, permission, home, warp, UI-preference and aerial-map save formats remain unchanged. Existing rent-journal JSON gains optional treasury fields that default safely when absent.

## 1.4.0-dev1.3.1

- Enabled ModDevGradle's dedicated JUnit integration so the `test` source set receives the Minecraft and NeoForge development classpath.
- Fixed `compileTestJava` failing to resolve `MinecraftServer` and `CustomPacketPayload` from tests that directly or indirectly reference Minecraft classes.
- Added the JUnit Platform launcher as a test runtime dependency, as required by the ModDevGradle unit-test setup.
- No gameplay, network protocol, payload, save-format, permission, profile, map or UI behaviour changes. Protocol remains `15`.

## 1.4.0-dev1.3

- Compacted the Players & Permissions page: the selected target summary now has its own row below the search controls, the repeated per-row source/effective subtitle was removed, and up to ten permissions fit on a normal page.
- Permission hover tooltips now show the permission explanation, accepted value type/range, module default, effective value/source and any direct override.
- Added an administrator-only Player Info & Profile category to the Admin Center.
- Added a bounded alphabetical dropdown containing known online and offline players, plus a separate player-name filter/search field. Offline names are merged from permission profiles and economy accounts.
- Added a paged player profile view with UUID, online state, primary/assigned ranks, access state, balance, claim groups/chunks, homes, rented regions, live dimension/position/health/food where available and direct permission-override count.
- Added a paged text list of every effective built-in/custom permission and its resolution source for the selected player.
- Added an Edit permissions button that opens the permission editor in Player mode with the inspected player already selected.
- Added dedicated bounded player-profile request/response payloads with server-side administrator checks and stale-response rejection.
- Increased the network protocol from `14` to `15`; client and server must both use 1.4.0-dev1.3.
- Existing claim, region, permission, economy, snapshot, map-cache, home and warp save formats remain unchanged.

## 1.4.0-dev1.2

- Replaced the free-text permission inspection page with a dedicated, server-authoritative permission editor.
- Added an expandable Player/Rank filter and a second expandable target list containing known players or configured ranks. Offline players remembered by the permission store remain selectable.
- Added separate target and permission filters, bounded server-side result lists and permission pagination.
- Selecting a player or rank now loads the full built-in SSU permission catalogue plus custom keys already present on that target or its inherited ranks.
- Boolean permissions use direct ON/OFF buttons. Integer permissions use bounded numeric input and custom permissions use text input. The reset button removes only the direct override and returns the permission to its inherited or module-default value.
- Added hover tooltips with a short explanation, accepted value type/range and the current direct/inherited value for every permission.
- Permission values are normalized and validated on the server before a player or rank record can be changed.
- Added player-rank assignment directly from the selected-player editor.
- Added bounded request/response payloads dedicated to the permission editor and stale-response protection on the client.
- Realigned the 3D player model to the transparent opening of the portrait frame and added mouse-following head/body rotation through a client-only accessor mixin.
- Increased the network protocol from `13` to `14`; client and server must both use 1.4.0-dev1.2.
- Existing claim, region, permission, economy, snapshot, map-cache, home and warp save formats remain unchanged.

## 1.4.0-dev1.1

- Fixed all ten invalid `ServerPlayer#getServer()` calls in `SsuMenuService` for the Minecraft 26.2 mappings.
- Dashboard claim details, permission lookup, payments, rental cancellation, home/warp teleport resolution, permission mutations and region member-name rendering now obtain the server through `ServerPlayer#level().getServer()`.
- No network protocol, payload, save-format, map-renderer or gameplay behaviour changes. Protocol remains `13`.

## 1.4.0-dev1

- Combined the planned dashboard/admin expansion and the first broad Core 2.0 lifecycle migration into one development build.
- Replaced the monolithic dashboard data transfer with a compact shell plus bounded page-specific request/response payloads for claims, travel, regions, transactions, economy accounts, active jobs, rental recovery records and permission data.
- Added server-side search and pagination. Opening the dashboard no longer sends every claim, region, home, warp and transaction at once.
- Added closed typed dashboard actions for payments, settings, border visibility, claim/region visualization, renting, teleports, rent policy, economy account mutations, permission overrides/rank assignment, job cancellation and performance-counter reset.
- Removed free-form command execution from dashboard controls. Every action is decoded as a known action type and revalidates permissions, target visibility and input on the server.
- Added detailed claim, region, transaction and rent-operation views, economy account search with give/take/set controls, active-job progress/cancellation and player/rank permission inspection. Region unrent now requires a second confirmation click before the typed action is sent.
- Dashboard refreshes preserve the active page, pagination, search and unfinished input while updating the compact shell and requested page data independently. Admin tiles and rent-policy controls are enabled from explicit permission summaries instead of only a broad admin flag.
- Bounded all new dashboard strings and list sizes before network encoding, including defensive page-index arithmetic and stale-request rejection on the client.
- Increased the network protocol from `12` to `13`; dev4.3 and 1.4.0-dev1 clients/servers must not be mixed.
- Migrated Claims, Permissions, Regions/Snapshots/Rent Journal, Teleport, Visualization and Menu into dependency-ordered `SsuModule` lifecycle ownership while retaining their existing managers as the authoritative compatibility facade.
- Module startup now follows dependencies and shutdown runs in reverse order. Active world-edit jobs are cancelled before module-owned managers perform their final save.
- Teleport pending requests and cooldowns are cleared between server lifecycles.
- Added JUnit coverage for dashboard payload normalization/bounds and module dependency/start/stop ordering, missing dependencies and cycles.
- Existing claim, region, snapshot, rent, permission, economy, home, warp and UI-preference save schemas remain unchanged.
- The confirmed dev4.3 aerial map renderer and cache format are unchanged; no map-cache rebuild is required.

## 1.3.0-dev4.3

- Refined the shared minimap, claim-map and world-map renderer after a clean-room behavioural comparison with the user-supplied JourneyMap 6.0.2 JAR. No JourneyMap source, bytecode, textures or assets are copied into SSU.
- Split every map column into two heightfields: underlying terrain height for hills/mountains and mapped-surface height for visible leaves, water and solid structures.
- Tree crowns now retain their own local height relief while the broader hill shade still follows the ground below the forest. This prevents trees from appearing as flat pale spots and keeps forested mountains readable.
- Added an explicit surface classification for solid terrain, canopy and water and persisted it with each aerial tile.
- Darkened and desaturated canopy colours independently from grass/terrain, made top-down leaf crowns visually closed and added restrained north-west crown highlights with south-east edge shadows.
- Added a two-distance directional terrace pass alongside local, broad and macro terrain shading. Minecraft's stepped slopes now remain legible without restoring the noisy per-block texture outlines removed in dev4.2.
- Further reduced general terrain and water saturation for a calmer cartographic appearance.
- Updated persistent aerial-tile format to version 3 so terrain height, mapped-surface height and surface kind survive reconnects. The renderer fingerprint is now `atlas-topographic-v4`, so dev4.2 tiles rebuild automatically.
- Added regression coverage for flat multi-scale lighting, elevation invariance, directional slopes, bounded canopy edges and crown-light direction.
- Network protocol remains `12`; no server world-data, claim, region, economy or snapshot schema changes were made.
- Phases 4 and 5 remain paused until this renderer is visually confirmed in Minecraft.

## 1.3.0-dev4.2

- Reworked the shared aerial renderer into a calmer cartographic/topographic style for minimap, claim map and world map.
- Replaced per-block 4x4 resource-pack texture detail with one representative resource-pack-aware colour per block, greatly reducing visual noise while preserving block identity.
- Decorative surface vegetation is skipped when selecting the visible map surface: flowers, crops, saplings, tall grass and similar bush blocks no longer cover the ground colour. Empty-collision plant/utility details are also omitted.
- Solid/collision-bearing blocks, leaves, water, lava and transparent structural layers remain visible.
- Decoupled visual surface colour from topographic height. Leaf crowns and tree logs beneath a canopy no longer create artificial relief bumps; water retains its visible surface height and buildings remain part of the relief.
- Reduced global saturation and brightness slightly and removed the per-detail water checker ripple.
- Replaced the old high-frequency relief stack with restrained local, broad and macro hill shading plus real one-block terrace rims. Flat terrain remains uniform and no longer receives arbitrary elevation-band striping.
- Bumped the aerial renderer fingerprint to `atlas-topographic-v3`, forcing existing dev4.1 terrain tiles to rebuild once with the new renderer.
- Added regression tests for neutral flat lighting, absolute-elevation invariance, directional landform shading and restrained terrace rims.
- Network protocol remains `12`; no server data or snapshot schema changes were made.
- Phases 4 and 5 remain paused until the revised map appearance is confirmed in Minecraft.

## 1.3.0-dev4.1

- Fixed destructive region resets ejecting chest, furnace, hopper, barrel and similar inventory contents into the world. Reset clearing now detaches block entities and suppresses drops before replacing block states.
- Restores the complete saved block structure without neighbour reactions, then performs a bounded neighbour-reconciliation pass. This prevents crops, torches and other support-sensitive blocks from popping while their support blocks are still being restored.
- Added snapshot format version 3 with saved hanging-entity SNBT. Item frames, glow item frames, their displayed items and rotation, plus paintings, are now captured and restored.
- Version 1 and version 2 snapshots remain readable. Since they contain no hanging-entity data, a reset from an older snapshot preserves the currently present frames/paintings; saving the region again upgrades it to version 3.
- Snapshot capture now fails instead of publishing incomplete data when a block entity or hanging entity cannot be serialized; reset likewise fails safely when structural data cannot be restored.
- Minimap claim rendering now uses claim UUIDs and draws only the actual outer perimeter of a connected claim instead of outlining every claimed chunk.
- Increased the network protocol from `11` to `12` because minimap claim overlays now include the claim UUID. Dev4 and dev4.1 clients/servers must not be mixed.
- Fixed right-mouse drag handling on the full claim map and world map at screen level, including drag release outside the map widget.
- Added stronger per-block elevation relief, one-block terrace rims and elevation contours to the shared aerial atlas used by minimap, claim map and world map.
- Bumped the aerial-renderer cache fingerprint so dev4 tiles are rebuilt automatically with the new relief renderer.
- Phases 4 and 5 remain intentionally paused until this hotfix is confirmed in Minecraft.

## 1.3.0-dev4

- Combined the planned region-safety, snapshot-hardening and persistent-map milestones into one development build.
- Added one central region mutation guard for delete, redefine, snapshot save, manual clear and manual reset operations.
- Region mutations are now rejected while a rental transaction, active region job or unresolved destructive snapshot reset can make the operation unsafe.
- Deleting or redefining a region archives its previous snapshot so reusing a region name cannot expose stale reset data.
- Region redefine only preserves the old region spawn when that spawn remains inside the new bounds.
- Dashboard snapshot refreshes now update the open dashboard in place instead of replacing the screen and losing its page, pagination or draft input.
- Replaced synchronous region snapshot capture with a bounded multi-tick capture job and asynchronous storage-worker serialization.
- Added snapshot format version 2: gzip-compressed JSON, palette-based block states, relative coordinate packing, SHA-256 checksum, atomic publication and block-entity SNBT where supported.
- Version 1 snapshot JSON remains readable when no version 2 generation exists; a successful version 2 capture archives obsolete version 1 generations.
- Snapshot loading can recover a missing or corrupt primary file from its validated backup generation without silently falling back from a newer version 2 snapshot to stale version 1 data.
- Snapshot reset loading and parsing now run on the storage worker before any destructive server-thread work begins.
- Destructive reset jobs now write durable checkpoints. Failed, cancelled or server-interrupted resets keep the region in an unresolved safety state until a later full reset succeeds.
- Added reusable immutable storage-worker tasks and included them in shutdown flush accounting.
- Added persistent client aerial-map tiles, isolated by hashed server/world identity, resource-pack fingerprint, dimension and 32x32-chunk cache region.
- Aerial tiles are read and written asynchronously, use atomic gzip files, archive corrupt entries and never request or force-load chunks.
- Added per-tile write coalescing, missing-tile suppression and a session barrier so resource-pack invalidation cannot delete newly captured tiles.
- Added a configurable client aerial-map disk cap (`aerialMapCacheMiB`, default 512 MiB) with oldest-entry pruning; archived corrupt cache entries count toward the cap.
- Pending aerial-map writes are drained during a normal JVM shutdown on a best-effort five-second flush.
- Added aerial-map diagnostics for memory tiles, estimated memory use, disk tiles/bytes, cache hits/misses, pending reads, queued writes and average capture time.
- Added JUnit 5 test infrastructure plus regression coverage for snapshot coordinate packing, negative cache-region coordinates and batched storage coalescing.
- Network protocol remains `11`; claim, region, rent, permission, home, warp and economy schemas remain compatible.

## 1.3.0-dev3.4

- Returned the shared aerial atlas, claim map and minimap to one high-quality map pixel per world block.
- Replaced the claim map's destructive zoom rebuild with a double-buffered renderer: the previous completed terrain view remains visible and is spatially remapped while the new view is prepared.
- Claim-map and world-map navigation now update the viewport locally before the server response, while stale responses for older centres or zoom levels are ignored.
- Right-mouse dragging now moves both maps live beneath the cursor; releasing commits the corresponding chunk pan.
- Removed the floating "Release to pan" text and clarified the fixed interaction hint.
- World-map claim and region overlays now remain geographically aligned with terrain during zoom and pan instead of waiting visually for a completed terrain rebuild.
- Leaf blocks tagged as leaves are rendered as opaque aerial crowns, preventing transparent leaf texels from appearing as holes through forests.
- Added stronger normalized local hill-shading plus a broader six-block relief pass for clearer slopes, ridges and valleys.
- No network payload or storage schema changes; protocol remains 11.

## 1.3.0-dev3.3.1

- Hotfix: water columns on minimap, claim map and world map now use the live biome water colour directly.
- Avoids relying on the fluid-model texture profile for water visibility.
- Existing depth shading and ripple grading remain active.
- No network or storage changes; protocol remains 11.

## 1.3.0-dev3.3

- Replaced the map-colour-first terrain pipeline with a resource-pack-aware aerial renderer shared by the minimap, claim map and world map.
- Added clean-room block-model inspection through Minecraft's public client rendering APIs; no JourneyMap code, textures or other assets are included.
- Prefer upward-facing model-quad sprites, then general model sprites, particle sprites and finally `MapColor` as a safe fallback.
- Added dedicated still-fluid texture sampling and live in-world tinting for water, foliage, grass and compatible modded blocks.
- Increased the per-block aerial fingerprint from 2×2 to 4×4 real texture-derived samples.
- Added deeper top-down strata compositing for leaves, plants, glass-like cutouts, water and the ground below.
- Added 8-neighbour Sobel-style terrain relief, water-depth shading and cross-chunk height sampling.
- Increased each cached chunk tile from 32×32 to 64×64 pixels and added gamma-correct mip levels for clean zoom transitions.
- Fixed one-pixel-per-block atlas sampling for the new four-pixels-per-block base level.
- Reduced the per-dimension LRU atlas cap to 2,048 tiles to bound memory after the resolution increase.
- Removed per-column tinted-profile array allocations; biome tint is now applied to cached texture fingerprints on access.
- Network protocol remains `11`; no payload or persistent server-data schema changed.

### Known differences from mature mapping mods

- Atlas tiles are still session-only and are not yet persisted as region files on disk.
- Chunk block access and tile capture still use SSU's incremental client-tick pipeline rather than a full snapshot-worker architecture.
- Cave maps, underground layer selection, waypoints and entity radar remain outside this build.
- Visual parity is not claimed; dev3.3 is the first texture-model-based SSU renderer and requires in-game comparison testing.

## 1.3.0-dev3.2.1

- Fixed the Minecraft 26.2 `BiomeColors` import in `TerrainColorSampler`.
- `BiomeColors` now imports from `net.minecraft.client.renderer` instead of the removed/invalid `net.minecraft.world.level.biome` package.
- Restores compilation of biome-tinted grass, foliage and water sampling for the minimap, claim map and world map.
- Network protocol remains `11`; no save or payload schemas changed.

## 1.3.0-dev3.1

- Halved the interactive claim-map boundary thickness: ordinary claim/region outlines are now one GUI pixel and highlighted/selected boundaries are two pixels.
- Doubled the full claim-map terrain texture density from one to two texture pixels per world block.
- Added a shared continuous terrain-lighting sampler so the claim map and minimap use the same more aerial, relief-rich surface style.
- Replaced destructive minimap refreshes with a double-buffered 192×192-block terrain cache and an atomic viewport swap.
- Added a 256×256 visible minimap texture (two texture pixels per world block across the same 128-block view).
- Added a moving viewport inside the larger cache so walking no longer clears or rebuilds the visible map every few blocks.
- Identical periodic server snapshots no longer invalidate the minimap texture; overlay changes rebuild invisibly in the background.
- Fixed dashboard tile hover rendering: the normal button is now always drawn first and the transparent glow texture is layered over it.
- Applied the same base-plus-glow layering to the dashboard Back/Close button.
- Lowered the 3D player portrait by five GUI pixels and added a subtle continuous idle bob/sway animation.
- Added a dedicated X close button in the upper-right dashboard header; Settings and Admin shift left automatically.
- Removed Claims/Homes/Warps counters from below the portrait and replaced them with a Profile button.
- Added a Profile page showing player name, base rank, admin state, balance, claim/chunk totals, homes, available warps, active rentals, border toggles and minimap status.
- Network protocol remains `10`; saved claims, regions, economy, rents, ranks, permissions, homes, warps and UI preferences remain compatible with dev3.

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
