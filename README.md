## Current development build: 1.9.0-dev2.5.3

### Captain-style equipped CTF carrier flag (1.9.0-dev2.5.3)

- The carried CTF banner is now equipped directly on the player in the same vanilla head slot used by pillager captains.
- It no longer uses a follower armor stand, so the banner stays perfectly attached to the carrier during movement and turning.
- Temporary head equipment is restored when the carrier drops or loses the flag.
- Network protocol remains 79; Minigame definition schema remains 10; Minigame recovery schema remains 2.


### Physical temporary setup banners (1.9.0-dev2.5.2)

- Lobby, spectator, team/player-spawn and linked Domination-respawn setup points now use real temporary standing banners rather than holographic cloth rectangles.
- Those setup banners are removed before a match and before reset snapshots, so they cannot block gameplay or become part of arena restoration. The stored coordinates remain active.
- Actual CTF flags and Domination node banners are never removed by setup-marker cleanup.
- Network protocol remains 79; Minigame definition schema remains 10; Minigame recovery schema remains 2.

### Domination compile fix and dropped CTF flags (1.9.0-dev2.5.1)

- Fixes the missing `ACCENT` GUI constant in the Domination editor.
- CTF flags now drop as their real configured banner when a carrier dies or deliberately crouches.
- Enemy-team players can instantly pick up a dropped flag by right-clicking it; the flag's own team instantly returns it to base.
- The HUD distinguishes flags at base, carried and dropped, and a capture only scores while the carrier's own flag is back at base.
- Network protocol remains 79; Minigame definition schema remains 10; Minigame recovery schema remains 2.


### CTF Minecraft 26.2 compile hotfix (1.9.0-dev2.4.1)

- Temporary back-banner entities now use a hidden custom-name marker for orphan cleanup instead of the unavailable `Entity#getTags()` call.
- Carrier glow colors now use Minecraft 26.2 `Optional<TeamColor>` values and retain nearest-color matching.
- Network protocol remains 78; Minigame definition schema remains 9; Minigame recovery schema remains 2.


### Physical CTF flags and interruptible pickup (1.9.0-dev2.4)

- New and existing CTF setup now shows the actual configured banner blocks at both bases, including after an older snapshot reset. Lobby, spectator and spawn previews use clean cross/vertical guides instead of custom square cloth markers.
- Taking the enemy flag requires a configurable right-click cast. Movement, leaving the flag/arena or incoming damage interrupts the castbar.
- Successful pickup plays Sing to the carrier's team and Seek to the opposing team.
- The carrier now has the real configured banner following directly behind their back, with no upward beam, plus a team-colored glowing outline visible to every player.
- Temporary banner entities, glow/team state and castbars are restored safely on score, death, disconnect, match finish, reload or shutdown.
- The accidental arena-edit `INTERACT` bypass from dev2.3.3 is removed; normal BREAK and PLACE behavior is unchanged.
- Network protocol 78; Minigame definition schema 9; Minigame recovery schema 2.


### Physical Domination banners and final capture sounds (1.9.0-dev2.3.2)

- Active assaults now recolor the actual physical banner with a claimant-colored upper half and previous-owner/neutral lower half; the old crossed cuboid overlay is removed.
- Base labels are ten times larger and centered above the banner.
- Final capture plays beacon activation to the capturing team; a previous owner that definitively loses the base hears beacon deactivation.
- Network protocol remains 76; Minigame definition schema remains 8; Minigame recovery schema remains 2.



### Compile hotfix (1.9.0-dev2.3.1)

- Replaces the removed `ServerPlayer#playNotifySound(...)` call with a targeted `ClientboundSoundPacket`, compatible with Minecraft 26.2.
- Domination Sing/Seek horns remain audible only to the intended team/player recipients.
- Network protocol remains 76; Minigame definition schema remains 8; Minigame recovery schema remains 2.

### Interruptible Domination base claims (1.9.0-dev2.3)
- Domination nodes are now claimed by right-clicking the physical banner and completing a configurable stationary cast. Movement, leaving the flag/arena or incoming damage interrupts the cast.
- A successful cast starts a second configurable, globally visible capture-delay timer. The base gives no points during this period and changes owner only when the timer completes.
- Assaulted flags show the claimant color on top and the old owner/neutral color below; a colored base label and timer float above every node.
- The former owner can right-click an assaulted base to defend it immediately. Sing plays to the claiming/defending team and Seek to the opposing team.
- Network protocol 76; Minigame definition schema 8; Minigame recovery schema 2.


### Dedicated minigame administration and in-world setup (1.9.0-dev2.2)
- The player Minigame Lobby now contains information and Join/Leave controls only. All configuration and live administrator operations live under **Admin Center → Minigames**.
- The old generic Create Minigame button is removed. Supported games are created from actual selected arena bounds with the dedicated Minigame Setup Tool.
- Right-click the Setup Tool to select a game, arena and action; left-click in the world to resize the arena, set lobby/spectator areas, place spawns, define a Spleef playfloor, place CTF flags or Domination nodes, or recapture the arena snapshot.
- Protected physical editing is available only in explicit arena edit mode. The game remains disabled until the changed arena has a newly verified reset snapshot.
- Network protocol 75; Minigame definition schema 7; Minigame recovery schema 2.

### Compact lobby, focused rewards and Domination foundation (1.9.0-dev2.1)
- The Minigame Lobby is exactly 25% smaller and its player/admin controls no longer overlap.
- Direct reward selection now shows only player unlocks, reputation, permissions and personal claim-capacity bonuses. Items and money stay in their dedicated Mail-backed reward fields.
- `add_claim_chunks` is available as a direct reward; for example `amount=5` adds five permanent personal claim chunks.
- Domination is runnable with a dedicated six-tab editor, two teams, 3–9 physical banner nodes, capture/neutralization progress, configurable resource scoring, respawns, HUD, winner effects and verified arena reset.
- Selection Tool creation can generate a five-node, Arathi Basin-inspired battlefield from a selected region of at least 15×15 blocks.
- Network protocol 74; Minigame definition schema 6; Minigame recovery schema 2.

### Capture the Flag foundation and winner effects (1.9.0-dev2)
- Reward ghost copies are released by right-clicking outside the inventory and reward slots, and direct reward actions use a cycle selector populated from the server's registered Content Core action types.
- Every participant receives a large winner title; winning players receive colored star fireworks.
- Spleef floor blocks disappear directly without ever creating item drops.
- Capture the Flag is now runnable with a dedicated five-tab editor, two balanced teams, physical banner flags, right-click pickup, team scoring, respawns, HUD and snapshot reset.
- A carried enemy flag is shown on the player's back with an upward beam in the configured flag color; death, disconnect or leaving the arena returns it to base.
- Network protocol 73; Minigame definition schema 5; Minigame recovery schema 2.


### Compact Spleef editor and inventory-style reward placement (1.9.0-dev1.1.1)
- The full five-tab Spleef editor is about 25% smaller and uses bounded wrapped help text so labels and explanations no longer overlap.
- Reward items now use a visible ghost cursor: select a real inventory stack, left-click a mail slot to copy the whole stack, or right-click to add one matching item. The real inventory is never consumed.
- All nine mail slots retain their exact positions, including intentional empty gaps, and item placement is validated against the administrator inventory on the server.
- Network protocol 72; Minigame definition schema 4; Minigame recovery schema 2.


### Dedicated Spleef administration and safe reward mail (1.9.0-dev1.1)
- Spleef now has its own clear five-tab editor with labeled fields, short coordinate values, player-spawn terminology and mode-specific guidance.
- Participation and winner rewards support exact inventory-selected item stacks, normal Economy amounts and immediate Content Core actions.
- Items and money arrive through SSU Mail with a clear reason; direct permissions/unlocks/keys apply immediately and are also described in the mail.
- Eliminated spectators are server-confined to the arena vicinity and returned to the configured spectator point if they leave it.
- Network protocol 71; Minigame definition schema 3; Minigame recovery schema 2.


### Minigame Core expansion and first playable Spleef mode (1.9.0-dev1)
- Adds a concrete game-type layer to the existing queue, arena and match framework. Spleef is the first fully implemented mode; King of the Hill, Capture the Flag, Domination, Team Deathmatch, Parkour Race and Prop Hunt are reserved for later builds.
- The Region Tool right-click menu can create a managed Spleef arena from the current selection. SSU creates a protected hidden region, captures a verified reset snapshot and saves the minigame as a disabled draft for administrator review.
- Spleef supports configurable breakable blocks, tool requirement, PvP, drop cleanup, elimination depth, per-player spawns, countdown lobby, spectator spawn, last-player-standing victory and automatic arena restoration.
- Adds server-authoritative match HUD scoreboards with mode, state, timer and alive-player information.
- Captures inventory, armor/offhand slots, gamemode, health, hunger, experience and flight state before a match. State is restored before rewards and persisted for disconnect/crash recovery; legacy schema-1 recovery entries never clear a live inventory.
- The exact recovery file is synchronously flushed and verified before countdown or late-join state replacement. A failed critical write leaves the player untouched and pauses new starts instead of risking inventory loss.
- Generic item and money rewards are registered by Content Core, so Minigame rewards do not depend on the Quest module being enabled.
- Match participants cannot use normal SSU teleports or ordinary block/item/entity interactions to escape or contaminate temporary match state. Configured same-match PvP remains available. Arena block breaking is allowed only for an active Spleef participant using the configured tool on an allowed block; idle arenas and outsiders remain protected.
- Forced administrator test matches never grant rewards. Countdown cancellation safely restores players and requeues them, while failed arena resets block the arena until a real snapshot restoration succeeds.
- Managed arena ownership is server-only metadata. Client editor payloads cannot claim, retarget or silently remove a selection-created region, and one physical region cannot be assigned to multiple minigames.
- Network protocol is 70. Minigame definition and recovery schemas are 2; all unrelated SSU storage schemas remain unchanged.


### Crash-safe per-claim taxation and permanent claim-capacity collateral (dev18.6)
- Every player claim now owns an independent cycle beginning at claim creation. Its taxable peak is the highest chunk count reached in that cycle and can never be lower than the current claim size.
- Reminder mail is deliberately an estimate: expansion may increase the final bill and collateral, while shrinking a claim never lowers the current cycle peak.
- Deleting a taxable claim requires the Claim Map GUI choice **Pay tax & delete** or **Forfeit capacity & delete**, followed by a second confirmation. Final-chunk and command paths cannot bypass this settlement.
- Simultaneously due claims are charged in one idempotent Economy transaction. Only an explicit insufficient-balance result can start confiscation; every technical or storage failure fails closed without destructive action.
- Confirmed non-payment removes all current claims and linked Homes without restoring world blocks, then permanently subtracts exactly the due/taxed claim peaks from the player's effective claim capacity.
- Permanent confiscations are stored separately from ranks and keyed by settlement UUID, so they survive rank changes and cannot be applied twice during recovery.
- The schema-2 settlement ledger journals payment, claim removal, Home cleanup and penalty application. Exact on-disk records are verified before advancing each destructive phase.
- Ambiguous or damaged recovery data creates a restart-persistent `tax_safety_halt.json` marker. Tax enforcement and claim mutation remain fail-closed until an administrator repairs the data and deliberately removes the marker.
- Network protocol is 69. Player Claim storage, Claim-tax settings and the settlement ledger use schema 2; warp storage remains schema 2 and unrelated schemas are unchanged.

### Selection transforms, claim-tax foundation and player-warp rentals (dev18.5)
- Fill Mix accepts normal blocks, water/lava buckets and an automatic air remainder when configured percentages total less than 100%.
- The selection editor can rotate 90°/180°, mirror horizontally and flip vertically while transforming compatible block states.
- Economics contains disabled-by-default Player Claim-tax configuration with per-chunk rates, cycle/reminder timing and per-dimension multipliers.
- Players with explicit permissions can prepay and manage public/private rented warps entirely through GUI menus; automatic renewal removes an unpaid expired warp.
- Warp storage is schema 2 and network protocol remains 68.

### GUI-first Region Tool and selection editor (dev18.4)
- Select point 1 and point 2 with the Region Tool, then right-click to choose between creating a server region, editing the selected blocks or clearing the selection.
- Region creation now asks only for the unique name. Existing region flags, priority, messages and rental configuration remain under **Admin Center → Regions → Settings**.
- The selection editor provides a temporary clipboard, paste-at-point-1, clear, weighted inventory-block fill mixes and reusable server/client templates.
- Fill mixes accept only block items currently present in the administrator's inventory, do not consume those items and must total exactly 100%.
- Templates save block states only; inventories, block-entity data and entities are never copied. GUI block replacement discards destination container contents without drops.
- Server templates are stored below the SSU regions storage folder. Client templates are stored under `.minecraft/simpleserverutilities/region_templates` and are validated server-side before use.
- The Admin Center no longer exposes the overlapping Region Maintenance tile or rental-action buttons inside its Regions list.
- Network protocol is 68. Existing SSU storage schemas remain unchanged; `.ssusel` selection templates use their own new internal format version 1.


### Auction House maintenance compile hotfix (dev18.3.1)
- Restores the accidentally removed Auction House maintenance lifecycle without changing the Economics GUI migration.
- Network protocol remains 67 and all storage schemas remain unchanged.

### Economics administration and transaction UX (dev18.3)
- Adds a protected **Admin Center → Economics** hub for Accounts, Transactions, Auction House tax, Player Claim tax status and the Rent Journal.
- Transactions now has a scrollable known-player selector, exact name/UUID compatibility input, independent journal search, transaction details and history-retention management.
- Removes the old refund-policy controls from the dashboard and moves Auction House tax editing out of the player-facing Auction House.
- Player Claim tax is explicitly shown as unavailable because no purchase price, billing cycle or non-payment policy exists yet; this build does not silently charge players.
- Player Travel no longer draws gray technical location text behind or below its search controls. Admin Travel Management retains coordinates and dimensions.
- Network protocol remains 67 and all storage schemas remain unchanged.

### Travel compile and claim-chunk Home cleanup hotfix (dev18.2.1)
- Fixes the Travel list effectively-final compile error.
- Removing valid claim chunks now also removes Homes physically located in those chunks, while rejected removals leave Homes untouched.

### Claim cleanup, focused claim navigation and Travel hubs (dev18.2)
- Deleting a player claim now removes every home physically linked to that claim before the claim disappears.
- Previous/next claim navigation in the Claim Map recenters the map on the selected claim and safely supports owned-claim viewports away from the player.
- Claim Map notices are left-aligned and width-clipped inside the panel instead of extending beyond the frame.
- Player **Travel** is now a shortcut-only directory for permitted claim-linked homes, warps and server spawn, with All/Homes/Warps/Other filters and search.
- Warp and server-spawn creation, relocation, deletion and test teleport controls now live under **Admin Center → Travel Management**, with All/Warps/Spawn filters and search.
- Network protocol is 67 because the Claim Map request payload now carries the explicit center-on-selected-claim flag. All storage schemas remain unchanged.

### Claim-bound Homes compile hotfix (dev18.1.1)
- Uses the Minecraft 26.2 `setScreenAndShow(...)` API when returning from Claim Settings or the Claim Map to the dashboard Homes page.
- Network protocol remains 66 and all storage schemas remain unchanged from dev18.1.

### Per-claim borders and claim-bound Homes (dev18.1)
- Adds persistent per-claim Show/Hide borders, splits the personal claim-border controls, and moves Homes management into each owned claim.
- New or moved homes must be inside the selected owned claim and respect all Home permissions and limits.

### GUI-first administration and command migration (dev18.0)
- Homes now live under Claims & Homes and can be created, updated, teleported to and deleted through the dashboard.
- Direct claim teleport is administrator-only; normal players use homes.
- Travel, player-claim administration, ranks, region maintenance, Utility Mining and technical maintenance now have dedicated GUI workflows.
- Minigame score administration and dungeon stage advancement are available in their existing lobby GUIs.
- Existing commands remain available, but the GUI and commands share the same server-side services and permission checks.
- Network protocol 65; no SSU storage/schema migration.

### NPC identity labels and centralized shop administration (dev14)
- NPC **Role / occupation** now lives on the Identity tab and no longer creates or changes services. The old **Apply preset** flow is removed.
- Visible NPC identity is rendered as three lines: a smaller role above the NPC name, and the faction name below it in red (hostile), yellow (neutral) or green (friendly).
- Shop creation and editing are available only through **Admin Center → Admin Tools → Shop Manager**.
- NPC Editor → Functions retains only a **Linked shop ID** reference; it no longer opens or edits shops.
- Legacy shop functions migrate into the explicit linked-shop field without breaking existing NPC templates.
- NPC definition schema 7; network protocol 60.

Dev13.2 affordability feedback, the dashboard/minimap calendar display and the compact Item Price Catalog remain included.

# Simple Server Utilities

Simple Server Utilities (SSU) is a modular NeoForge server utility mod for Minecraft 26.2.

## Current systems

- Player chunk claims with connected claim groups, trust, limits and flags
- Three-dimensional server-owned admin regions with priorities, nesting, managers, members, renting and versioned, recoverable snapshots
- Configurable default rank plus rank permissions and higher-priority personal permissions, with a compact paged editor, per-key default/effective tooltips and direct player/rank selection
- Homes, global warps, a persistent server spawn and delayed teleports with cooldowns
- Permission-gated prepaid player-warp rentals with private/public visibility, automatic renewal and GUI-only management through My Warps and Travel
- Optional per-claim recurring taxation with monotonic cycle peaks, configurable dimension multipliers, estimated reminder mail, idempotent Economy settlement, crash-safe claim/Home removal and permanent permission-independent claim-capacity confiscation on confirmed non-payment
- GUI region-selection fill mixes with block items, water/lava buckets and automatic air remainder, plus rotation, horizontal mirrors and vertical flipping
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
- A persistent, server-authoritative World-of-Warcraft-inspired Auction House with permission-separated dashboard/NPC entry, item-name search, fourteen categories, sortable scrollable listings, partial-quantity purchases, inventory-backed selling, active-auction limits, cancellation/expiry returns, configurable sale tax, crash-recoverable transaction journals and automatic nine-stack mailbox delivery
- An independent persistent NPC foundation with reusable templates and linked placements, compact paged administration, a searchable three-column vanilla/modded living-model picker, real inventory-backed visual equipment and nine-slot loot editors, gravity/swim/fly behavior, per-placement schedules and durable respawns, player/faction attitudes and combat, remote placement/template management, faction/reputation gates, configurable native attributes/home radii and graph-based server-authoritative dialogue. Dialogue choices can use shared Content Core conditions/actions and route through permission-aware Mail, Auction House, SSU Menu, healing, server-spawn and warp services without requiring Quest Core
- Shared NPC shops with reusable shop IDs, click-only buying/selling, timed personal buy-back, exact inventory-picked offers, a live-registry Item Price Catalog covering vanilla and modded items, per-shop item-ID/tag sale filters, finite/infinite stock and Monday–Sunday offer schedules with server-authoritative time validation
- An independent persistent Quest Core with a paged Questbook, tracking, abandon/turn-in lifecycle, repeatable cooldowns, event-driven objectives, transactionally delivered item/money/permission/unlock/reputation rewards and a structured administrator editor. Quest access is exclusively routed through either the SSU dashboard or optional NPC dialogue services, never both
- An independent persistent Minigame Framework with permission-aware queues, configurable teams and arena spawns, automatic/forced match lifecycle, late-join and eliminated-spectator handling, score and victory APIs, transactional rewards, optional region-snapshot resets, crash-safe player return locations, durable unsafe-arena blocking, a player lobby, administrator editor and optional NPC queue services
- An independent persistent Customized Dungeon Framework with permission-aware queues that form runtime parties, required SSU region-backed arenas, ordered manual/kill/checkpoint/survival stages, configurable lives and checkpoint respawns, completion/failure rewards, optional region-snapshot restoration, crash-safe player returns, durable unsafe-arena blocking, a player lobby, four-page administrator editor and optional NPC dungeon services
- Server-authoritative Treecapitator and Veinminer modules with player-selectable sneak/keybind activation, permission-upgradeable block limits and ore access, custom block/tag support, claim/region-safe breaking and merged glow-like structure outlines. Treecapitator selects only the targeted wood family from that block upward, accepts matching normal/stripped log and wood variants, rejects tracked hand-placed materials and conservatively removes an owned natural canopy; every automatic log/ore break performs one normal enchantment-aware durability attempt
- Permission-controlled Crops Harvesting with a global Admin Center toggle: right-clicking a mature vanilla or conventional tagged/modded age-based crop grants normal block loot and restores its first planted growth stage, with custom and disabled block lists for data-driven compatibility
- Server-gated, personally toggleable Block Information overlay with a compact translated block/entity name, optional minimum required/recommended tool icon and red/green harvestability indicator; permission-gated debug details and server-authoritative, protection-aware content previews cover vanilla containers, compatible modded item capabilities, flower pots, item frames, armor stands and related inventory-bearing objects
- Administrator-defined persistent player statistics for mining, placement, kills, deaths, damage and play time, with indexed event filters, paged lifecycle management, batched per-player storage and Floating Text personal-value/ranking/leaderboard sources
- Persistent dimension-aware holograms for rich floating text, clickable HTTP(S) links, live top/self scoreboard objectives and actual PNG/JPG/animated-GIF image billboards, with one camera-locked multiline background, selectable per-range bold/italic/underline/strikethrough and 16-colour formatting, automatic 40-visible-character line wrapping, editable coordinates, direct right-click placement, precise in-world selection and local/remote edit, teleport and delete controls. Images load asynchronously from internal resource identifiers or validated direct HTTP(S) sources, are cached client-side and use bounded decoding/render sampling for predictable performance
- Paged visual claim and region settings editors with server-side validation, access lists, contextual region-permission overrides and per-setting hover explanations
- A permission-aware Admin Tools page with purpose tooltips and Get Tool actions for the Hologram Tool and the two-point Region Tool, including custom creation/settings GUIs
- Persistent player dashboard/minimap preferences, custom dashboard textures and a framed mouse-following 3D skin portrait
- An always-visible, double-buffered high-resolution HUD minimap with resource-pack-aware aerial terrain, player heading, coordinates, server-authoritative claim/region overlays, personal coloured markers and shared day/night lighting
- A large near-full-height professional World Map on `M` with compact icon controls/tooltips, zoom/pan, layer and location panels, direct Claim Map switching and persistent right-click personal markers with remote edit/delete management
- Optional in-world coloured marker circles and build-height beams with independent personal visibility controls and a configurable 16–512 block beam range (128 by default)
- A shared persistent topographic aerial atlas with resource-pack-aware colours, separate terrain/canopy heightfields, fluid rendering, biome tint, strengthened multi-scale relief, cached surface block-light, day/night presentation, gamma-correct zoom levels and an asynchronous version-5 disk cache, a player-configurable live-update radius and bounded transient memory retention

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

SSU stores server data below the world save in the `simpleserverutilities` folder. Development builds preserve existing claim, region, home, warp and permission data. The 1.2.0 line adds isolated economy and region-rent journal records and extends region rental JSON with backwards-compatible optional fields. The 1.3.0 line adds permission settings, automatic default-rank assignment and isolated player UI preferences. Dev3 adds transient minimap networking and client rendering; dev3.1 refines the map and dashboard renderers. Dev3.2 adds the world map and protocol 11; dev3.2.1 is a client compilation hotfix only. Dev3.3 replaces the visual terrain pipeline without changing payloads or save schemas. Dev3.4 returns the atlas to one composite pixel per block, adds smooth double-buffered claim-map zooming, live map dragging, closed leaf canopies and stronger terrain relief. Dev4 adds guarded region mutations, asynchronous version-2 snapshots with legacy reads and durable reset checkpoints, plus a persistent client aerial-map cache. Dev4.1 upgrades snapshots to version 3 for item frames/paintings, makes reset clearing drop-free, renders only the outer minimap claim perimeter and repairs right-click map dragging. Dev4.2 replaces the noisy texture-heavy aerial renderer with a muted cartographic surface filter and terrain-based multi-scale relief. Dev4.3 separates ground and visible-surface heights, restores dark three-dimensional tree crowns and adds a restrained two-distance terrain terrace pass. The 1.4.0-dev1 line replaces the monolithic dashboard transfer with compact page-specific payloads and closed typed actions, and moves Claims, Permissions, Regions, Teleport, Visualization and Menu into dependency-ordered Core 2.0 modules without changing their save formats. Version 1.4.0-dev1.1 is a source compilation hotfix for `SsuMenuService`. Version 1.4.0-dev1.2 adds the dropdown-driven, paged permission editor and mouse-following portrait. Version 1.4.0-dev1.3 compacts the permission rows and adds an administrator player-profile browser with effective-permission pages and direct permission-editor handoff. Version 1.4.0-dev1.3.1 fixes the former Gradle test classpath. Version 1.4.0-dev2 removes that separate test source set and completes lifecycle ownership for the remaining shared managers. Version 1.4.0-dev2.1 retires the experimental permanent treasury and region owner payout, migrates legacy region owners to administrative managers, treats region rent as a logged money sink with safe journaled refunds, adds claim deletion to the claim map and adds paged visual settings editors for claims and server regions. Version 1.4.0-dev3 adds a persistent server spawn, context-aware `/spawn`, guarded delayed teleports and a searchable visual region-permission editor. Version 1.4.0-dev4 unifies homes, warps, spawn, claims and regions behind contextual escape policy, adds precise permission-controlled stand-still cancellation, adds dimensions to the permission GUI and rechecks every normal delayed teleport at execution time. Version 1.5.0-dev1 adds durable per-player mailboxes, sent-mail history, on-disk visible-inbox overflow queues, item and money escrow attachments, retention maintenance and an Auction House-ready idempotent delivery API. Version 1.5.0-dev1.1 is a source compilation hotfix for the Minecraft 26.2 `AbstractContainerScreen` constructor. Version 1.5.0-dev2 adds sent-mail deletion and permission caps, recipient read/claim status, known-player suggestions, attachment-mail auto-delete preferences and categorized personal settings. Version 1.5.0-dev2.1 repairs and expands the multiline mail composer and increases recipient suggestions to 256. Version 1.6.0-dev1 adds Treecapitator, Veinminer and the first persistent hologram/scoreboard slice. Version 1.6.0-dev1.1 is a source compilation hotfix for Minecraft/NeoForge 26.2. Version 1.6.0-dev2 adds Admin Tools, custom hologram/region editors, single-pass hologram text and natural-tree-only Treecapitator cleanup. Version 1.6.0-dev3 changes the Hologram Tool to direct right-click creation and in-world editing, adds searchable remote edit/teleport/delete management to the Admin Center, and requires main-hand axe/pickaxe item tags for Treecapitator/Veinminer. Version 1.6.0-dev4 recalibrates hologram scale and precise line selection, limits Treecapitator to the targeted tree from the selected height upward, adds enchantment-aware per-block tool wear and introduces permission-controlled right-click Crops Harvesting with a global Admin Center toggle. Version 1.6.0-dev5 adds ARGB hologram backgrounds, the two 16-colour preset palettes and fixes leaf cleanup for higher Treecapitator starting points. Version 1.6.0-dev5.1 lets Treecapitator continue on natural trunk remnants and treats normal logs, stripped logs, wood, stripped wood, stems and hyphae of the same species as one wood family; it also locks the hologram background to the exact camera billboard basis used by the text. Version 1.6.0-dev6 turns multiline backgrounds into one fitted billboard, keeps text reliably in front, adds automatic 40-visible-character wrapping, selection-based rich formatting with the 16 Minecraft colours and editable hologram coordinates. Version 1.6.0-dev6.1 replaces the visible formatting-code prototype with a real plain-text/per-character-style editor, renders selected colors and effects immediately in the GUI and world, and hardens Treecapitator around a missing trunk log and conventionally named modded wood variants. Version 1.6.0-dev6.2 fixes the remaining provenance gate so matching normal, stripped and bark-on-all-sides trunk variants are actually selected and broken together under a natural canopy. Version 1.6.0-dev7 replaces image placeholders with asynchronously loaded PNG/JPG/animated-GIF billboards, including bounded decoding, caching, remote-link safeguards and image-aware in-world selection. Version 1.6.0-dev8 adds runtime module activation through Admin Center → Module settings, proximity-only hologram synchronization and separately configurable hologram, claim-border and region-border distances. Dev8.1 polishes the dashboard; dev8.2 removes claim-specific spawns, adds claim-player dropdowns and repairs claim welcome messages; dev8.2.1 is a compile-only hotfix. Dev9 adds rich-text mail and independent per-scoreboard refresh intervals. Dev10 fixes duplicate border layers, makes server capability plus personal preference the single effective visibility rule, centers Back and moves Profile below the portrait balance. Dev10.1 hardens that capability gate so an explicit server deny also applies to operators and immediately clears every region overlay layer. Dev10.2 makes each Regions-page Show/Disable state a persistent server-owned region property, removes the former per-player focus rendering for that action and requires both server eligibility and the player master toggle. Region records use schema 4. Version 1.6.0-dev11 adds isolated schema-1 custom-statistic definitions/player values, migrates player UI preferences to schema 4 for Block Information and raises the network protocol to 32. Existing stored world, claim, region, economy and mailbox data remains compatible; regions remain schema 4, holograms remain schema 4, mailboxes remain schema 3 and border visualization settings remain schema 2. Dev11.1 compacts Block Information to a translated block/entity name plus a real required-tool item icon, retains the red/green tool-validity bar and moves registry IDs, hardness, exact tool and blockstate properties behind the explicit `ssu.block_information.debug` permission and personal debug toggle. Player UI preferences use schema 5 and the network protocol is 33. Use the exact same dev11.1 build on the client and server. Dev11.2 adds permission-limited, server-authoritative content previews to Block Information. The inventory preview defaults to one shown item stack, respects SSU interaction protection and container locks, avoids generating unopened loot tables, strips per-stack metadata from preview packets and uses NeoForge item capabilities as a bounded compatibility fallback. No data schema changes are required; the network protocol is 34 and client/server must use the exact same dev11.2 build. Dev11.3 adds compact recommended-tool icons for blocks that can be harvested by hand but are mined faster with a standard tool category. Dev11.4 separates that recommendation from harvest validity: the icon remains advisory while the red/green bar reports only whether the currently held item can obtain the block drops; protocol 34 and all schemas remain unchanged. Dev12 redesigns the World Map and Claim Map, adds schema-1 personal map-marker storage, migrates player UI preferences to schema 6, introduces marker overlays/in-world beams and upgrades the client aerial cache to version 4 under `map-cache-v3`. Night shading and cached surface block light are shared by the World Map, Claim Map and minimap. The network protocol is 35. Dev12.1 restores direct World Map right-click marker menus, moves map panning to held middle mouse, raises the bottom controls, removes the duplicate bottom map-switch buttons and shortens the marker/refresh tooltips without changing schemas or payloads. Client and server must use the exact same dev12.1 build. Dev12.2 makes the marker context overlay modal and resolves Add/Edit/Delete/Close hitboxes before the map widget, fixing non-responsive context-menu buttons without changing protocol or stored data. Client and server must use the exact same dev12.2 build. Dev12.3 turns the in-world marker ring into a camera-facing circular icon, exposes the full sixteen Minecraft marker colours as real colour swatches and adds a clearer framed World Map marker context panel. Protocol 35 and all schemas remain unchanged; client and server must use the exact same dev12.3 build. Dev12.4 replaces that hollow ring with a half-size solid camera-facing disc while keeping the beam, protocol 35 and all schemas unchanged. Dev12.5 shows marker name and distance only while the player aims at the in-world icon, adds cached biome and surface-block information under the World Map cursor, and upgrades the disposable client aerial cache to format 5 under `map-cache-v4`; protocol 35 and server data schemas remain unchanged. Dev12.6 adds bounded distance compensation so in-world marker icons and aimed labels retain a stable apparent size. Dev12.7 halves the marker disc again, enlarges the aimed label by 2.25× and relocates/alines the World Map and Claim Map Back, Close and left-column controls without changing protocol or schemas. Dev12.8 doubles the aimed marker label again and tightens its translucent background. Dev12.9 shifts the complete left toolbar panel—background and controls—three pixels right on both maps, without changing protocol or schemas. Dev12.10 adds a reliably visible modal marker-menu frame, migrates player UI preferences to schema 7 for a 1–32 chunk live terrain-update radius, keeps explored terrain in the asynchronous client disk cache while evicting cold off-radius tiles from RAM, strengthens topographic relief and raises the network protocol to 36. Dev12.11 fills the remaining marker-context button gaps and corrects the yellow World Map legend entry to Player without changing protocol or schemas. Version 1.7.0-dev1 adds the persistent Auction House, schema-1 listing/purchase/settings storage, permission-separated dashboard and trusted NPC access, categorized name-based browsing, inventory selling, partial purchases, global sale tax, mail delivery and crash-recoverable economy journals; the network protocol is 37. Version 1.7.0-dev1.1 is a source compilation hotfix that removes the unavailable Minecraft 26.2 `BlockTags.SAPLINGS` constant. Version 1.7.0-dev1.2 fixes Java generic type inference in the Auction House sort comparators by explicitly typing `AuctionListingView` lambda parameters; protocol 37 and all schemas remain unchanged. Version 1.7.0-dev1.3 fixes the dashboard sidebar frame at non-default GUI scales and expands the Create Auction screen with a highlighted offer slot and fully drawn inventory/hotbar grids; protocol 37 and all schemas remain unchanged. Version 1.7.0-dev1.4 adds the administrator listing overview, safe admin cancellation and mailbox seizure, plus a persistent base-item blacklist managed by held item or selected listing. Auction House settings and listings migrate to schema 2, while purchase journals remain schema 1; the network protocol is 38. Version 1.7.0-dev1.5 replaces held-item blacklist management with a visual inventory picker and validated item-ID entry, requires a reason for administrator cancellation/seizure, stores pending seizure reasons durably in listing schema 3 and raises the network protocol to 39. Version 1.7.0-dev1.5.1 is a source compilation hotfix for a shadowed Auction House registry-lookup lambda parameter; protocol 39 and all schemas remain unchanged. Version 1.8.0-dev1 adds the independent Content & Progression Core with schema-1 player/world flags, counters, unlocks and reputation, protocol 40 and dedicated permissions for future NPC, quest, minigame and dungeon modules. Version 1.8.0-dev1.1 corrects the Content Core dependency ID without changing protocol or data. Version 1.8.0-dev2 adds the first persistent NPC Foundation with definition and placement schema 1 and protocol 41; dev2.1 and dev2.2 are Minecraft 26.2 source/API compatibility hotfixes. Version 1.8.0-dev3 adds reusable graph dialogue schema 1, migrates NPC definitions to schema 2 for their optional dialogue link and raises the network protocol to 42; NPC placements and Content Progression remain schema 1. Version 1.8.0-dev4 adds independent schema-1 quest definitions and player journals, a paged server-authoritative Questbook, event-driven objectives, transactionally delivered rewards and optional NPC quest services while raising the network protocol to 43; all NPC and Content Progression schemas remain unchanged. Version 1.8.0-dev5 begins Advanced NPCs by migrating reusable NPC definitions to schema 3, adding optional native-attribute overrides, persistent equipment, faction/reputation gates and configurable home radii through a tabbed administrator editor; the network protocol is 44 while NPC placements, dialogues, quests and Content Progression keep their existing schemas. Version 1.8.0-dev5.1 is a compile-only Minecraft 26.2 equipment-drop API hotfix; protocol 44 and all schemas remain unchanged.

Version 1.8.0-dev6 adds the independent schema-1 Minigame Framework, persistent player/arena recovery, generic queues, teams, match lifecycle, scores, transactional rewards, region-snapshot reset integration, lobby/editor GUIs and optional NPC queue services while raising the network protocol to 45. NPC, Quest and Content Progression schemas remain unchanged. Concrete minigame rule implementations are intentionally layered on top of this framework rather than hard-coded into its core.

Version 1.8.0-dev7 adds the independent schema-1 Customized Dungeon Framework with region-backed arena slots, queue-formed runtime parties, ordered manual/kill/checkpoint/survival stages, lives and checkpoint respawns, transactional completion/failure rewards, crash-safe return recovery, durable unsafe-arena reset protection, lobby/editor GUIs and optional NPC dungeon services. The network protocol is 46; all existing Minigame, NPC, Quest and Content Progression schemas remain unchanged.

Version 1.8.0-dev8 refines the NPC foundation with a six-tab administrator editor, searchable model picker, inventory-drag visual equipment, schema-4 nine-slot independently rolled custom loot, gravity/swim/fly behavior and schema-2 per-placement schedules with walking, teleporting and arrival activities. Equipment is visual-only and never contributes attributes or death drops. The network protocol is 47; all dialogue, Quest, Minigame, Dungeon and Content Progression schemas remain unchanged. Version 1.8.0-dev8.1 corrects the Minecraft 26.2 schedule clock accessor. Version 1.8.0-dev8.2 corrects the Minecraft 26.2 server-player level accessor used by the NPC editor and tool; protocol 47 and all schemas remain unchanged. Version 1.8.0-dev8.3 compacts the NPC/model/dialogue interfaces, replaces the former ghost-form fields with a real server-synchronised inventory container, makes the configured nine-slot table the only NPC loot source, repairs No-AI gravity and NPC Tool routing, adds friendly/neutral/hostile player and faction relations, durable per-placement respawns, an automatic reusable-template library and searchable remote NPC management. NPC definitions migrate to schema 5, placements to schema 3 and the network protocol is 48. Version 1.8.0-dev9 starts NPC Functions with fourteen descriptive roles, dialogue/direct-service/service-menu routing, up to eight registered services per template and a generated server-validated player menu. NPC definitions migrate to schema 6, placements remain schema 3 and the network protocol is 49. Version 1.8.0-dev9.1 begins Dialogue Editor 2.0 with four compact pages, complete node-entry/choice-action list editing, server-synchronised condition/action/service selectors, visual node/service targets and safe node-link cleanup. The data schemas remain unchanged and the network protocol is 50. Version 1.8.0-dev9.2 expands the editor to five pages with a visual nested-condition tree, child creation/deletion/reordering, AND/OR/NOT wrappers, per-node registered types and starter parameters, and strict server validation for NOT groups. Protocol 50 and every storage schema remain unchanged. Version 1.8.0-dev9.3 completes the first Dialogue Editor 2.0 phase with a side-effect-free graph preview, shared client/server validation, unreachable/cycle diagnostics and server-synchronised warp/quest/minigame/dungeon target browsers. The network protocol is 51; NPC definition schema 6, placement schema 3 and dialogue schema 1 remain unchanged.

Version 1.8.0-dev10 begins NPC Shops & Paid Services with independent schema-1 fixed-price shop definitions, exact ItemStack buy/sell offers, finite or infinite persistent stock, wall-clock restocking, Economy Core journaling and rollback, a paged player shop GUI, command-based administration and NPC/dialogue service integration. This first slice keeps administration command-based; the visual shop editor and generic paid services remain follow-up work. The network protocol is 52; NPC definition schema 6, placement schema 3 and dialogue schema 1 remain unchanged. Version 1.8.0-dev10.0.1 is a source compilation hotfix for the Minecraft 26.2 inherited `Screen#rebuildWidgets()` visibility collision in the NPC Shop screen; protocol 52 and every storage schema remain unchanged. Version 1.8.0-dev10.1 adds the visual NPC Shop Manager and two-page Shop Editor, exact held-stack capture, offer duplication/reordering, economy-formatted price fields and visual finite/infinite stock/restock configuration. Shop IDs are immutable after first save, all drafts are revalidated server-side, the network protocol is 53 and every storage schema remains unchanged.
Version 1.8.0-dev11 replaces the player shop quantity-and-button workflow with direct left/right clicking on eighteen shop slots and the complete player inventory, adds a nine-entry timed Buy-back tab with reserved stock, converts prices and stock to individual item units, migrates NPC Shop storage to schema 2 and raises the network protocol to 54. Version 1.8.0-dev12 formalizes reusable shared shops by permanent shop ID, adds Shop library and Edit linked shop controls to the NPC editor, allows a selected library shop to be assigned directly to the NPC, adds previous/next browsing and a Linked NPCs reference page to the Shop Editor, prevents deletion while NPC templates still reference a shop and raises the network protocol to 55. NPC Shop schema remains 2; NPC definition schema 6, placement schema 3 and dialogue schema 1 remain unchanged. Version 1.8.0-dev12.1 replaces the remaining held-item capture in the administrator Shop Editor with a server-validated 36-slot inventory picker and raises the network protocol to 56 without changing any storage schema. Version 1.8.0-dev13 adds a live-registry schema-1 Item Price Catalog for every vanilla and modded item, moves base buy/sell prices out of individual offers, lets shops accept globally priced player sales through exact-ID/tag whitelists and blacklists, adds a shared Monday–Sunday calendar and per-offer day/time availability, migrates NPC Shop storage to schema 3 and raises the network protocol to 57. Version 1.8.0-dev14 centralizes all shop editing in Admin Center → Shop Manager, replaces NPC-side shop editor navigation with one linked shop ID, moves Role to Identity without preset behavior, adds three-line role/name/faction labels, migrates NPC definitions to schema 7 and raises the network protocol to 60. Version 1.8.0-dev15 makes Admin Tools scrollable, reduces the complete Shop Editor to 570×360, adds independent enabled/all-day/start/end controls for every weekday, replaces typed shop sale rules with visual live-registry item and tag selectors, removes Basic interaction text from Identity and replaces linked-shop and faction-relation IDs with searchable choice windows. NPC Shop storage migrates to schema 4 and the network protocol is 61.

Always back up a world before installing a development build.


## 1.8.0-dev15.1 compile hotfix

This hotfix updates the visual NPC Shop tag selector to the Minecraft 26.2 registry API. It uses `BuiltInRegistries.ITEM.getTags()` and reads each tag key through `tag.key().location()`. Network protocol 61 and all storage schemas are unchanged.

## 1.8.0-dev15.2 compile hotfix

- Fixes generic lambda inference in `NpcEditorService.java` when sorting shop and faction choices.
- Network protocol remains 61.
- Storage schemas remain unchanged.

## 1.8.0-dev15.3 Shop Editor usability hotfix

- Save shop now saves without closing the editor and reports the successful save in the editor header.
- Adds an upper-right close button, compact shop navigation arrows, corrected Offers-panel bounds and a framed selected-item preview with tooltip.
- Removes redundant green explanatory text from Offers and Trade rules.
- Adds a View: All / View: Added / View: Not added filter for quickly reviewing current whitelist and blacklist entries.
- Network protocol remains 61; storage schemas are unchanged.


## 1.8.0-dev15.4 NPC and Dialogue Editor usability hotfix

- NPC template actions remain on the Templates tab after completion.
- NPC Identity uses compact role arrows, and the NPC editor now has a dedicated close button.
- Saving an existing NPC keeps the editor open; deletion still closes it.
- Dialogue Editor pages now explain their purpose, with On open/On choose terminology and an in-editor workflow guide.
- Parameter guide windows list the known keys and examples for built-in conditions/actions while custom key=value input remains supported.
- Network protocol remains 61; storage schemas are unchanged.
