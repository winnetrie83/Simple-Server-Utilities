# 1.9.0-dev3.40.6.1

- Compile hotfix for the Arcane Missiles VFX pass.
- Minecraft/NeoForge 26.2 changed `DRAGON_BREATH` to a typed `PowerParticleOption`, so it can no longer be passed directly to the simple `sendParticles(...)` overload.
- Replaced the three dragon-breath haze calls with the simple purple `WITCH` particle. The custom purple dust beam, sinus ribbons, orb clusters, charge-up and impact visuals remain unchanged.
- No network protocol or schema changes.

# 1.9.0-dev3.40.6

- Reworked **Arcane Missiles** into a more premium visual effect instead of the old simple end-rod line.
- Added a dedicated purple charge-up telegraph swirl around the caster during the channel windup.
- Each missile pulse now renders a sinus-wave arcane beam with layered purple dust/glow ribbons and dragon-breath haze.
- Added visible purple arcane orb clusters moving along the beam path so the spell reads like real missiles instead of a flat laser.
- Added a brighter arcane impact burst on the target plus an amethyst-style sound accent.
- Keeps the proven dev3.40.5.2 safe Player-NPC mainhand/offhand renderer path and the dev3.40.4.1 AI/Manager fixes.
- No network protocol or schema changes.

# Simple Server Utilities 1.9.0-dev3.40.4.1

## Emergency client-render recovery hotfix

- Fixes the black-screen-at-startup regression introduced by dev3.40.4.
- The regression was isolated to the new Player-NPC humanoid/equipment renderer bootstrap. That renderer path is rolled back to the known-working dev3.40.3 implementation so Minecraft can initialize and draw the title/world UI normally again.
- The dev3.40.4 NPC movement ownership/pathfinding fixes remain included.
- The dev3.40.4 NPC Manager duplicate-widget fix remains included.
- Player-NPC visual armor/held-item rendering is temporarily disabled again in this rescue build and will be reintroduced separately after the startup renderer path is validated. Server-side loadout data/gameplay remains unchanged.
- Network protocol remains `118`; NPC definition/placement/ability schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.40.4

## NPC runtime recovery + visible Player-NPC loadouts + Manager render hotfix

### NPC movement ownership
- Normal reconcile/refresh/sync no longer hard-positions a living NPC at its placement anchor. Hard positioning is now reserved for spawn, respawn and explicit administrator moves such as Bring or edited placement coordinates.
- After combat, STATIONARY/LOOK_AT/WANDER/NATIVE NPCs return to their configured home through the shared path-navigation controller instead of snapping back. PATROL resumes its logical patrol route without resetting its waypoint state.
- Scheduled NPCs recover from combat by pathfinding to the currently active schedule destination. Explicit TELEPORT schedule entries still teleport on normal time-slot activation, but never as a combat-return shortcut.
- Static/no-AI gravity settling may only correct the saved Y coordinate while the NPC is still at its home X/Z anchor; combat or knockback can no longer silently rewrite the placement/home position.
- Live template refreshes no longer respawn an NPC for ordinary stat, equipment, gravity or behavior edits. A runtime respawn is limited to changes that require a different physical entity shell.
- Periodic reconcile preserves temporary combat/return AI ownership so stationary shells cannot be re-frozen halfway through a return path.

### Player-NPC equipment rendering
- Player-model NPCs now use the humanoid renderer path so main-hand/off-hand equipment is rendered using the standard held-item layer.
- Added dedicated humanoid armor layers for helmet, chest, legs and boots using the real equipped ItemStacks, preserving normal equipment material/tint/glint rendering.
- Added combat arm poses for bow, crossbow and shield loadouts; swords and other held items retain normal humanoid item/attack animation behavior.
- Both Wide/Steve and Slim/Alex player models remain supported, including held-item attachment alignment for slim arms.

### NPC Manager duplicate-widget fix
- Fixed the actual cause of the apparent double NPC Manager when right-clicking air with the NPC Tool. The initial network payload was calling `rebuildWidgets()` from the screen constructor before the real display-size initialization, leaving an orphaned set of tabs/search/row buttons behind the correctly initialized manager.
- Initial construction now leaves widget creation to the normal screen init pass. Later list/search/action payloads only rebuild widgets when that exact NPC Manager is already the active screen.
- The existing server-side duplicate-open guard and top-level manager replacement behavior remain in place as additional protection.

### Compatibility
- Network protocol remains `118`.
- NPC definition schema remains `19`; NPC placement schema remains `4`; Ability Library schema remains `1`.
- No world/persistence migration is required.

# Simple Server Utilities 1.9.0-dev3.40.3

## NPC Manager single-render hotfix
- Fixed the NPC Manager opened by right-clicking air with the NPC Tool being visible twice: a scaled manager in front with an older/unscaled manager layer behind it.
- The NPC Manager now opens as a replacement top-level tool screen through `Gui#setScreen`, which clears NeoForge background screen layers instead of allowing a stale manager layer to remain behind it.
- Closing the NPC Manager uses the same replacement semantics when returning to its optional parent.
- The NPC Manager is now non-pausing, matching SSU's other in-world setup/tool screens.
- Added a short server-side duplicate-open guard for one physical NPC Tool right-click.
- No network protocol or persistence schema changes.

# Simple Server Utilities 1.9.0-dev3.40.2

## Dashboard fixed 4-column layout

- Dashboard/Home textured module tiles no longer switch from four columns to three when Minecraft exposes less than 500 logical GUI pixels of content width.
- The compact/plain-button fallback for Home also uses four columns.
- Dashboard pages now use a fixed canonical logical canvas (680×390, or 544×312 for Wallet/Profile) instead of shrinking/reflowing their layout when Minecraft exposes a smaller logical viewport.
- SSU GUI Scale now has an automatic fit safeguard for fixed-canvas screens: the configured 60–100% remains the maximum, but the effective scale may become smaller when necessary to keep the canonical Dashboard canvas fully on-screen.
- The portrait sidebar therefore remains part of the same Dashboard layout on narrow displays instead of disappearing and causing a different module arrangement.
- This keeps the canonical Dashboard layout stable across resolutions and Minecraft GUI Scale settings; the independent SSU GUI Scale is responsible for shrinking the complete SSU screen when desired.
- Prevents 12-module dashboards from becoming 4 rows tall and extending below the panel on narrower logical displays.
- No network, NPC, ability-library, placement or community-statistics schema changes.

# Simple Server Utilities 1.9.0-dev3.40.1

## Shared ability phase-gate hotfix

- Fixed NPC Editor saves failing with messages such as `Ability 'charge_1' references missing boss phase ...`.
- Boss-phase gating belongs to the NPC-specific ability assignment, not the shared library definition. A stale/missing assignment phase is now repaired to blank (`All phases`) instead of hard-failing the whole NPC save.
- The client editor also sanitizes ability-assignment and attack-pattern phase gates after boss phases are loaded, so old/migrated references no longer remain visible as `(missing)`.
- Disabling boss mode or deleting/renaming phases can no longer strand shared ability assignments behind an invalid phase reference.
- No network protocol or persistence schema changes versus dev3.40.

# Simple Server Utilities 1.9.0-dev3.40

## Shared Ability Library + smarter ability AI

### Server-wide reusable Ability Library
- NPC abilities are no longer authored as private copies inside every NPC template. SSU now persists a server-wide reusable ability catalogue under `simpleserverutilities/npcs/abilities/`.
- NPC templates store only ability assignments/references plus an optional NPC-specific boss-phase restriction. Editing one shared ability immediately changes the behavior of every NPC template that uses it.
- Added **Admin Tools -> Ability Library**. The Ability Workshop is now fully independent from the NPC Tool/editor and supports up to 256 shared definitions.
- The NPC **Abilities** page is now an assignment page. Open the shared library, select an ability and press **Assign**; Unassign only removes the link from that NPC and keeps the shared definition.
- Shared abilities cannot be deleted while they are assigned to NPC templates. The library shows usage counts.
- Attack Patterns and Boss phase Trigger Ability actions continue to reference stable ability IDs.

### Smarter cast/ability eligibility
- Added explicit **Requires stationary** and **Min targets** AI requirements to reusable abilities and the standalone Ability Workshop.
- Stationary casts stop native `PathNavigation`, put `MoveControl` in WAIT, face the target and suppress horizontal movement before the cast starts. This prevents Arcane Missiles from cancelling itself because an old chase path was still active.
- Interrupt-on-move now observes real displacement after cast start rather than residual path velocity; interrupt-on-damage remains independent.
- Thunderclap and other Around-self AoE abilities validate actual hostile targets inside their configured AoE shape before the AI may select them. Merely seeing a distant enemy is no longer enough.
- Around-self AoE selection is independent of the distance to the current primary combat target; if an enemy is actually inside the radius the ability can fire, otherwise it stays unavailable.
- Cone eligibility aims its selection cone toward the current combat target instead of relying on potentially stale body yaw.
- Presets now provide sensible movement/target requirements: Arcane Missiles, Thunderclap, Slash, Arrow Volley, Fire/Ice Ball, ranged weapon casts and Self Heal default to stationary where appropriate; Thunderclap requires at least one enemy in its AoE.

### Migration / compatibility
- NPC definition schema `18 -> 19`.
- Network protocol `117 -> 118` for the standalone Ability Library/Workshop payloads.
- New shared Ability Library persistence schema `1`.
- Existing dev3.39 embedded abilities are migrated automatically into the shared catalogue while preserving each NPC's assignment, attack-pattern references and boss phase-action references. Existing NPC-specific ability copies are deliberately not merged together automatically, preventing an edit on one migrated NPC from unexpectedly changing another.
- NPC placement schema remains `4`; Community Statistics schema remains `1`.

# Simple Server Utilities 1.9.0-dev3.39

## Equipment-driven NPC combat + Ability Workshop

### Equipment is now gameplay
- NPC Attack Damage, Armor and Armor Toughness are no longer manually authored Stats-page values. The equipped ItemStacks are the combat baseline.
- Full encoded equipment stacks are preserved instead of stripping gameplay attribute modifiers/enchantments.
- Physical equipment-backed melee delegates to the normal Mob attack path when available so equipped gameplay hit/enchantment behavior can participate.
- The generic ranged executor derives power from the equipped ranged weapon; bow Power modifies damage and Flame/Punch are represented.
- Configured NPC equipment is authoritative, has zero normal equipment-drop chance and is continuously restored/repaired so durability never permanently decreases or destroys the configured item.
- Armor multiplier scales both equipment armor and toughness.

### Independent combat tuning
- Retained configurable stats: Max Health, Magic Resistance, Armor multiplier, Melee damage multiplier, Ranged damage multiplier, Magic damage multiplier, Walking speed, Running speed, Follow range, Knockback resistance and Scale.
- Patrol/wander/schedules use Walking speed; active combat chase uses Running speed. Running speed cannot be configured below Walking speed.
- Melee, Ranged and Magic attack channels can be toggled independently in any combination.
- Sword-mainhand + ranged-offhand loadouts can use melee nearby and ranged at distance; ranged mainhand loadouts prefer ranged attacks.

### Ability Workshop
- Replaced the cramped ability editing flow with a dedicated `NPC Ability Workshop` screen while keeping abilities directly attached to the NPC template and compatible with Attack Patterns/Boss Phase actions.
- Added editable preset starting points: Regular Melee, Regular Ranged, Charge, Thunderclap, Slash, Arcane Missiles, Arrow Volley, Fireball, Ice Ball, Leap, Mortal Strike, Bladestorm, Self Heal and Custom.
- Added attack-channel classification (Melee/Ranged/Magic), target shapes (Single/Around self/Around target/Cone) and damage schools (Physical/Fire/Arcane/Ice/Nature/Shadow).
- Custom ability fields cover direct or equipment-scaled damage, healing, range/chance/cooldown, radius/cone/knockback, stun/slow, arbitrary mob-effect/debuff IDs, bleed, DoT, HoT, multi-hit/projectile count, pulse interval, wind-up/recovery, channeling interruption and charge speed.
- Charge uses pathing-aware movement, refreshes its configurable target stun during the charge and exits cleanly on timeout/contact.
- Thunderclap emits thunder, AoE knockback and slow. Slash defaults to three rapid half-strength equipment hits. Arcane Missiles defaults to a three-pulse interruptible Arcane channel.
- Periodic bleed/DoT/HoT effects tick on the server tick path independently of target-selection cadence.

### Migration / compatibility
- NPC definition schema `17 -> 18`.
- Network protocol `116 -> 117` because the NPC editor ability payload format and combat/stat fields changed.
- Existing schema <=17 NPCs migrate old behavior speed into Walking/Running speed; old manual attack/armor/toughness values are retired; existing abilities are mapped onto the new channel/shape semantics.
- NPC placement schema remains `4`; Community Statistics schema remains `1`.
- See `docs/NPC-COMBAT-1.9.0-dev3.39.md` and `docs/TESTING-1.9.0-dev3.39.md`.

# Simple Server Utilities 1.9.0-dev3.38.3

## SSU GUI scale duplicate backdrop fix

- Fixed the still-visible centered dark rectangle around reduced-scale SSU screens. The dev3.38.2 suppression happened too late: Minecraft extracts its own screen background before the concrete SSU screen calls `SsuGuiScale.fullscreenDim(...)`, so the previous per-frame "managed backdrop already drawn" check could never suppress that earlier vanilla pass.
- At SSU GUI scales below 100%, Minecraft's `extractTransparentBackground` / `extractMenuBackground` layer is now suppressed up front for every SSU screen. This prevents any fullscreen vanilla background from ever being captured inside the centered SSU scale transform.
- Normal SSU menu screens continue to render their explicit `SsuGuiScale.fullscreenDim(...)` edge-to-edge, leaving exactly one uniform dim layer plus the scaled SSU panel.
- Legacy screens that previously relied on Minecraft's vanilla background receive a reduced-scale-only SSU fullscreen fallback: Mail Compose, World Map, Claim Map and Claim Tax Delete. Their 100% behavior is unchanged.
- Region Snapshot Preview and the World Edit compact overlay intentionally remain transparent at reduced scale, matching their existing no-background behavior.
- Dashboard avatar scaling/input fixes from dev3.38.1 remain unchanged. No layout coordinates were altered.

### Compatibility
- Network protocol remains `116`.
- NPC definition schema remains `17`; NPC placement schema remains `4`.
- Community Statistics persistence remains schema `1`.
- No server/world migration is required.

# Simple Server Utilities 1.9.0-dev3.38.2

## SSU GUI scale background cleanup

- Fixed the remaining dark/translucent rectangle around reduced-scale SSU screens. This was Minecraft's own default transparent/menu screen background being extracted inside SSU's centered scale transform after SSU's dedicated fullscreen dim had already been drawn.
- At SSU GUI scales below 100%, the redundant vanilla `extractTransparentBackground` / `extractMenuBackground` layer is suppressed only after a screen has drawn SSU's managed fullscreen dim. The SSU-owned backdrop remains edge-to-edge and becomes the only darkening layer for those screens.
- Overlay/map screens that do not use `SsuGuiScale.fullscreenDim(...)` keep Minecraft's normal background path, avoiding a global behavior change.
- At 100% the vanilla screen-background path is deliberately left untouched, preserving the existing full-size appearance.
- No SSU panel geometry, widget coordinates, avatar scaling or input mapping changed.

### Compatibility
- Network protocol remains `116`.
- NPC definition schema remains `17`; NPC placement schema remains `4`.
- Community Statistics persistence remains schema `1`.
- No server/world migration is required.

# Simple Server Utilities 1.9.0-dev3.38.1

## SSU GUI scale visual hotfix

- Fullscreen black/dim backdrops are no longer part of the centered SSU scale transform. At reduced SSU scale they keep the screen's original opacity and always cover the complete viewport instead of becoming a smaller dark rectangle behind the panel.
- Centralized fullscreen dim rendering through `SsuGuiScale.fullscreenDim(...)`; existing screen-specific dim alpha values are preserved.
- Removed the temporary extra edge-dim pass from the global scale mixin because each SSU screen's own backdrop now renders correctly unscaled.
- Dashboard `PlayerSkinWidget` portrait is a special 3D widget that does not inherit the normal screen pose transform consistently. Its position and dimensions are now explicitly mapped from the original logical portrait opening into physical scaled coordinates.
- Portrait mouse-look continues to use logical coordinates, so rotation remains aligned with the scaled avatar.
- No SSU layout coordinates, panel geometry or page composition changed.

### Compatibility
- Network protocol remains `116`.
- NPC definition schema remains `17`; NPC placement schema remains `4`.
- Community Statistics persistence remains schema `1`.
- No server/world migration is required.

# Simple Server Utilities 1.9.0-dev3.38

## Independent SSU GUI scale

### Client-only SSU scaling
- Added a dedicated **SSU GUI Scale** setting under `Dashboard -> Settings -> Interface`.
- `100%` is exactly the pre-dev3.38/current SSU size. Players can reduce SSU screens to `90%`, `80%`, `70%` or `60%` without changing Minecraft's own GUI Scale option.
- Scaling is client-only and persists in the SSU client config as `ssuGuiScalePercent`; servers do not control a player's preferred SSU screen size.
- Existing SSU layouts are intentionally unchanged. Screen width/height, panel calculations, columns, widget coordinates and editor geometry continue to operate in the original 100% logical coordinate space.
- A central screen render transform scales the final SSU UI around the screen centre, so future SSU screens inherit the setting automatically instead of each screen maintaining separate scale math.
- Mouse click/drag/release coordinates are inverse-mapped to the same logical coordinate space so buttons, text fields, custom hitboxes, map widgets and container interactions stay aligned with their scaled visuals.
- Hover/render mouse coordinates and normal Screen scrolling are mapped as well; custom SSU scroll handlers that inspect pointer position use the same logical mapping.
- Minecraft's global GUI Scale value is never modified, so inventories/chat/vanilla menus outside SSU retain the player's normal size.
- A subtle unscaled edge dim remains behind shrunken SSU screens so scaling a full-screen backdrop does not leave visually harsh undimmed borders.

### Compatibility
- Network protocol remains `116`.
- NPC definition schema remains `17`; NPC placement schema remains `4`.
- Community Statistics persistence remains schema `1`.
- No server/world migration is required.

# Simple Server Utilities 1.9.0-dev3.37

## Community statistics foundation + website analytics API

### Curated community statistics
- Added a new built-in **Community Statistics** service on top of the existing Content Event Core. It does not duplicate gameplay hooks and therefore stays aligned with achievements, quests and administrator-defined statistics.
- Community statistics are collected automatically when `enableCommunityStatistics=true` (default) and do not require admins to create statistic definitions first.
- Every tracked player and the server aggregate now maintain separate **Lifetime, Day, Week, Month and Season** buckets. Day/week/month use stable UTC keys; the current season is controlled by `communityStatsSeasonId`. Changing the season id starts a fresh season bucket while archiving the previous one.
- Completed daily snapshots are retained for 90 days by default (`communityStatsHistoryDays`, configurable 7-730). Weekly history retains 104 buckets, monthly 36 and season history 16.
- Current buckets retain bounded breakdowns such as target/block/entity/minigame id, dimension, movement mode, role and team when the source event supplies that metadata. This lays the foundation for pages such as top mined blocks, popular minigames and travel-by-dimension.
- Derived engagement metrics include Active days, Active players, Player-active-days, Unique biomes and Unique dimensions.
- Initial automatic metric catalog includes sessions/playtime, blocks, combat, crafting/consumption, travel/exploration, claims, auctions, quests, NPC interactions, achievements, minigames and dungeons.
- Metric metadata exposes display name, category, unit, raw-value scale and a `leaderboardSafe` hint. Easily farmed raw counters such as block breaking remain useful for community goals without automatically being treated as trusted competitive leaderboards.
- Large teleport/correction jumps remain excluded by the existing Content Core distance sampler.
- Durable Content Events keep per-player event-id dedupe protection to avoid duplicate counting when a durable source retries an event.
- Fixed an older Content Core issue where login/logout events were published both by `ContentCoreEvents` and `ContentGameplayEvents`; the gameplay adapter now owns movement/exploration state only, preventing new session/login stats from double-counting.

### Website analytics API
- Web API v1 remains read-only and gains community-stat endpoints without exposing Minecraft collections to HTTP worker threads. The statistics service publishes an immutable analytics snapshot on the server thread.
- Added `statistics` to `/api/v1/capabilities`.
- Added endpoints:
  - `GET /api/v1/stats/catalog`
  - `GET /api/v1/stats/server`
  - `GET /api/v1/stats/players?period=week&limit=100`
  - `GET /api/v1/stats/player/<uuid-or-name>`
  - `GET /api/v1/stats/leaderboard?metric=minigame_wins&period=week&limit=10`
  - `GET /api/v1/stats/history?metric=play_time_seconds&period=day`
- Leaderboards are generated from the immutable web snapshot, not by reading player files/world state on HTTP threads.
- Raw values are intentionally lossless integers. Metric catalog `scale=100` marks values such as damage/auction money that should be divided by 100 for display.
- Remote actions remain disabled. This build is analytics/read-only only.

### Persistence / compatibility
- New Community Statistics persistence schema: `1`, stored under `simpleserverutilities/statistics/community/`.
- Existing administrator-defined custom-statistics schema remains unchanged (`definitions=2`, player values `2`).
- Network protocol remains `116`; NPC definition schema remains `17`; NPC placement schema remains `4`.
- Existing worlds start collecting community history from the moment dev3.37 is first run; SSU does not invent historical data that was never tracked.

# Simple Server Utilities 1.9.0-dev3.36

## Advanced boss phase actions + website API foundation

### Boss encounter phase actions
- Boss phases can now contain up to 8 ordered **entry actions** that execute once when an active encounter enters that phase.
- Added a dedicated in-game **Phase actions** editor reachable from the Boss page; no JSON editing is required.
- Initial action set:
  - **Announce**: custom encounter overlay text (blank uses the phase name).
  - **Trigger ability**: deterministically starts an existing NPC ability when the phase begins.
  - **Spawn adds**: spawns 1-16 dynamic SSU NPCs from another NPC template around the boss.
  - **Heal %**: heals the boss by a percentage of maximum health.
  - **Reset threat**: clears the current encounter threat table.
  - **Fixate random player**: forces the boss onto one valid nearby player for 1-60 seconds.
  - **Despawn adds**: removes adds previously spawned by the encounter.
- Every phase also has a **Taunt immune** toggle. External tank/taunt hooks are ignored during that phase while scripted Fixate still works. Scripted Fixate is encounter-owned and does not require normal Threat/Aggro to be enabled.
- Spawned adds are encounter-owned runtime NPCs. They are not persisted as normal placements and are automatically removed when the boss resets or dies.
- Phase-entry state is encounter-aware: the initial phase actions do not fire merely because a boss exists in the world; they begin when SSU combat actually starts.
- Existing phase announcements remain as a compatibility fallback when a changed phase has no explicit Announce action.
- Ability renames/deletes update phase-action references in the editor, and server validation rejects missing scripted ability references.

### Website integration foundation
- Added an opt-in **read-only HTTP API v1** intended for websites, dashboards and status pages.
- The API is disabled by default and defaults to `127.0.0.1:8765` for reverse-proxy use.
- Authentication uses an `Authorization: Bearer <token>` header. SSU refuses to start the API when the configured/effective token has fewer than 16 characters.
- `SSU_WEB_API_TOKEN` may be supplied as an environment variable so production servers do not have to store the token in a committed config file.
- Minecraft state is copied into an immutable snapshot on the server thread once per second; HTTP worker threads never read live world/player collections directly.
- Initial endpoints:
  - `GET /api/v1/health`
  - `GET /api/v1/status`
  - `GET /api/v1/players`
  - `GET /api/v1/capabilities`
- Status includes online players, player UUID/name/dimension and a compact NPC runtime summary.
- Optional exact-origin CORS support is available through `webApiAllowedOrigin`; blank keeps cross-origin browser access disabled.
- **Remote commands/admin actions are intentionally not exposed yet.** API capabilities report `remoteActions=false`; the read-only authenticated/versioned bridge is the foundation for a later permission-scoped action layer.

### Compatibility
- Network protocol is `116` (was `115`) because boss phase actions are synchronized through the NPC editor payload.
- NPC definition schema is `17` (was `16`) for persisted phase actions. Older NPCs migrate with empty phase-action lists.
- NPC placement schema remains `4`; spawn-profile/dialogue/quest schemas remain `1/2/2`.

# Simple Server Utilities 1.9.0-dev3.35

## NPC AI-family polish + free-form role styling

### Cosmetic role/title editor
- Replaced the old fixed NPC role selector with a free-form **Role / occupation** text field (up to 64 characters). The role remains cosmetic metadata and no longer implies NPC functionality.
- Added a compact **2 x 8 palette** beside the role field with all 16 classic Minecraft formatting colors. The selected color is used by the overhead role/title label and the generated NPC service menu.
- Schema-15 and older NPCs migrate their legacy role IDs once to the same human-readable labels (`guard` -> `Guard`, `merchant` -> `Merchant`, etc.) and default to the existing gray role color.
- A blank role is valid and simply removes the role/title line from the SSU overhead label.
- NPC shop controls are no longer gated by the old `Merchant` role. Any NPC can link/create a shop independently of its cosmetic title.

### Species-family AI polish
- Added a higher-level runtime `NpcAiProfile` layer on top of dev3.34.4 locomotion. SSU now differentiates Humanoid ground, Ground creature, Hopping, Flying, Aquatic, Amphibious and Native-special families.
- The profile is derived automatically from the actual NPC shell/model; it is not another setting admins have to maintain.
- Each family has its own route/combat repath cadence and arrival tolerances so a Slime/Rabbit, Vex, fish and Villager are no longer evaluated as if they were the same walking body. Special shells such as Breeze/Enderman/Shulker/Warden stay in a conservative native-special family.
- Flying/swimming combat chasers aim around the target body rather than always steering at the target's feet.
- Wander is now movement-family aware: flying/aquatic NPCs can choose 3-D destinations, aquatic families prefer water targets, while normal ground NPCs retain 2-D ground wandering.
- The navigation controller now permits the faster family-specific combat repath intervals while keeping patrol/schedule profiles conservative enough to avoid the old every-few-ticks path thrashing.
- Patrol, schedule and wander share species-aware route-arrival logic, accepting a valid finished native path near the requested point instead of requiring every model to hit one exact XYZ.

### Combat return + diagnostics
- Combat cleanly interrupts patrol/schedule movement without discarding the logical patrol index or active schedule slot. When combat ends, SSU clears the chase route once and resumes the ambient route from the correct state.
- The NPC Editor Behavior page now shows a compact **AI family** and **runtime state** snapshot (Patrol point, Schedule slot, Combat/return state, pathing state and route speed where relevant) to make movement debugging substantially easier.
- Existing Threat/Aggro, Attack Pattern and Boss state remains compatible with the new AI-family routing.

### Compatibility
- Network protocol is `115` (was `114`) because role color and NPC editor/runtime diagnostic fields are now synchronized.
- NPC definition schema is `16` (was `15`) for the free-form role/title + role color migration.
- NPC placement schema remains `4`; NPC Spawn Profile schema remains `1`; NPC dialogue schema remains `2`; Quest definition schema remains `2`.

# Simple Server Utilities 1.9.0-dev3.34.4

## NPC label + locomotion polish

- Native Player NPCs now suppress Minecraft's own entity/type nametag at renderer level. SSU's role/name/faction/quest stack is the only intended overhead identity display.
- SSU NPC overhead labels now use the entity's partial-tick interpolated render position instead of its last tick position, preventing role/name/faction text from visibly trailing behind a smoothly moving NPC.
- Added automatic runtime locomotion profiles derived from the actual entity shell. This is not a second editor setting and requires no migration.
- Ground walkers (including the native Player NPC and normal ground mobs) continue through their native `PathNavigation`/`MoveControl`.
- Slime and Magma Cube shells keep their native hopping controller instead of receiving Player-style physical steering.
- Vex/Ghast/Phantom/Bat/Wither-style free-flight shells are driven through their specialised native `MoveControl`; Allay/Bee/Parrot-style flying path navigators keep native path navigation.
- Aquatic/amphibious shells retain native water-oriented navigation where available. Modded mobs get a conservative runtime fallback based on their native navigation/control implementation.
- Native flying shells keep airborne/no-gravity semantics automatically even if the generic `Can fly` override is off. The explicit `Can fly` option remains available for intentionally making an otherwise-grounded shell fly.
- Stopping an SSU route also puts specialised native MoveControls into `WAIT`, preventing old Vex/Ghast-style wanted positions from continuing to pull an NPC after arrival.
- The shared smooth-path rule from dev3.34.1 remains: SSU chooses the destination, while the shell's own native movement controller owns how it gets there and unchanged paths are not rebuilt every behavior tick.
- This build is a locomotion/pathfinding foundation, not a claim that every Minecraft species already has bespoke SSU decision-making/brain behavior. Species-specific higher-level behaviors can now be layered on top without forcing every model through Player movement.
- Network protocol stays `114`; NPC definition schema stays `15`; placement schema stays `4`.

# Simple Server Utilities 1.9.0-dev3.34.3

## Patrol arrival / route-state hotfix

- Fixed patrols that visually reached waypoint 1 but never advanced because SSU required an overly strict exact 3-D distance to the stored waypoint coordinate.
- Patrol arrival now uses horizontal/vertical route tolerances appropriate for a living mob instead of one exact floating-point sphere.
- When vanilla `PathNavigation` has completed and the NPC is already close to the requested waypoint, that completed native path is accepted as arrival. This prevents a valid final path node from leaving the logical patrol index stuck forever.
- Waypoint transitions now clear both SSU route bookkeeping and the completed native navigation path before installing the next segment in the same behavior update. Entity velocity is deliberately preserved for smooth movement.
- Unreachable-point recovery immediately starts the replacement waypoint instead of waiting for another behavior update.
- Loop/Ping-pong/Random all share the corrected transition path.
- Network protocol stays `114`; NPC definition schema stays `15`; placement schema stays `4`.

# Simple Server Utilities 1.9.0-dev3.34.2

## Patrol continuation hotfix

- Fixed native Player NPC patrols being able to reach the current waypoint and then remain on the completed native navigation segment instead of reliably starting the next point.
- Every patrol waypoint advance now updates the active patrol index and invalidates the old SSU navigation cache as one operation. The following move call immediately supplies the next destination to Minecraft `PathNavigation`.
- The transition intentionally does not zero velocity or hard-stop the entity, preserving the smoother dev3.34.1 steering behavior.
- `Loop` continues from the final point back to point 1; Ping-pong and Random use the same safe segment transition.
- New GUI/world-editor patrol points now default to zero seconds pause. Existing configured pauses are unchanged.
- Network protocol stays `114`; NPC definition schema stays `15`.

# Simple Server Utilities 1.9.0-dev3.34.1

## NPC movement / native Player NPC hotfix

- Player NPC base `MOVEMENT_SPEED` is fixed at the SSU native default `0.25`; admins now use the Behavior route-speed multiplier instead of accidentally multiplying two separate speed settings.
- Player-mode Stats no longer exposes the duplicate movement-speed attribute.
- Route-speed UI now explicitly describes `1.0` as normal speed; slower decimal values such as `0.5` remain valid.
- `NpcNavigationController` keeps an existing vanilla path stable instead of requesting a new path every four ticks.
- Moving targets only trigger a re-path after meaningful destination drift and a short cooldown; true stalls still force recovery.
- Patrol points with `0` seconds pause no longer hard-stop the NPC between nodes.
- Manual static/no-AI gravity physics now backs off whenever a Mob's native AI is active, preventing SSU from zeroing velocity while `MoveControl` is steering.
- Added the `entity.simpleserverutilities.player_npc` English translation as a safe fallback for contexts outside SSU's own NPC label.
- Network protocol stays `114`; NPC definition schema stays `15`.

# Simple Server Utilities 1.9.0-dev3.34

## Advanced combat patterns
- Added optional ordered **Attack Patterns** for combat NPCs and bosses. Existing NPCs keep the dev3.33 random/legacy ability scheduler until the feature is explicitly enabled.
- A pattern can contain up to 24 enabled/disabled steps and can mix normal **Melee** actions with a specific configured **Ability**.
- Every step can be restricted by target distance, the NPC's own health percentage and an optional boss phase.
- Pattern state is encounter-local, advances after completed melee/actions or intentionally skipped unavailable/chance-failed ability steps, and resets cleanly on boss phase changes, encounter reset, definition edits or runtime removal.
- Ability and boss-phase renames now update matching pattern references; deleting either clears stale references instead of leaving broken editor data.

## Threat / aggro
- Added opt-in per-NPC threat tables with configurable range, damage multiplier, healing multiplier, decay per second and target-switch ratio.
- Confirmed incoming damage adds threat only after SSU's damage protection/authorization checks have passed.
- Target selection uses switch hysteresis so a tiny threat difference does not make an NPC rapidly bounce between players.
- Creative/spectator players, dead/removed entities, friendly targets and targets outside the configured threat range are rejected/pruned.
- Added a bounded taunt hook for future tank/role mechanics. A taunt raises the target above current threat and can force it for a limited duration.
- Added source-attributed healing-threat support for SSU systems that know the healer. The existing SSU Self Heal ability now reports its actual healed amount through this hook. Generic vanilla/NeoForge heals are not guessed when no healer source exists.
- Boss reset clears threat, casts/cooldowns and pattern state so a restarted encounter never inherits stale aggro.

## NPC Editor / compatibility
- Added a dedicated **Tactics** page to the NPC Editor for Threat and Attack Pattern setup, including step browsing, action/ability/phase selection and range/HP conditions.
- Server-side payload bounding and validation reject missing ability/phase references and cap pattern/threat values.
- Network protocol is `114` (was `113`) because NPC editor payloads carry threat and attack-pattern settings.
- NPC definition schema is `15` (was `14`). Existing definitions migrate with both new systems disabled, preserving previous combat behaviour by default.
- NPC placement schema remains `4`; NPC Spawn Profile schema remains `1`; NPC dialogue schema remains `2`; Quest definition schema remains `2`.

---

# Simple Server Utilities 1.9.0-dev3.33

## Native Player NPC runtime
- Replaced the Player visual mode's mannequin runtime shell with SSU's own registered `simpleserverutilities:player_npc` entity.
- The native Player NPC extends `PathfinderMob`, so schedule, patrol, wander, chase, flee and recovery movement can use Minecraft's normal mob collision and `PathNavigation` instead of the non-Mob fallback movement path.
- The entity deliberately registers no vanilla goals. SSU remains authoritative for behavior, schedules, patrols, reactions, combat and boss/ability control, avoiding competing AI controllers.
- Existing Player NPCs migrate at runtime without rewriting their saved definitions: reconciliation notices the old shell type, discards it and recreates the same managed placement on the native SSU entity.
- The saved fallback `entityType` is retained for data compatibility and is not exposed as the physical Player runtime.

## Player rendering
- Added a dedicated dependency-free SSU Player NPC renderer and 64x64 model with **Wide / Steve** and **Slim / Alex** arm geometry plus normal skin overlay parts (hat, jacket, sleeves and pants).
- Player NPC skins continue to use the existing server-authoritative local/HTTPS validation, hashing, caching and client texture sync.
- Wide/Slim choice is also synchronized for Player NPCs using the default Steve/Alex texture, so Slim geometry no longer depends on having a custom PNG. This reuses the existing texture-sync packet shape and does not require a protocol bump.
- Removed the client mannequin skin mixin and the mannequin-specific `PlayerSkin` cache path; dynamic NPC texture registration is now shared cleanly by Player and Entity appearance modes.
- Added basic walk, look, crouch and SSU melee-swing animation to the native Player model.
- The internal `simpleserverutilities:player_npc` runtime type is hidden from the normal Entity model picker.

## Compatibility
- Network protocol remains `113`.
- NPC definition schema remains `14`; NPC placement schema remains `4`; NPC Spawn Profile schema remains `1`; NPC dialogue schema remains `2`; Quest definition schema remains `2`.
- No GeckoLib/custom-geometry dependency is introduced.
- The dev3.30.1 safe melee fallback for living shells without `minecraft:attack_damage` remains unchanged; the native Player NPC itself registers the combat/navigation attributes SSU requires.

---

# Simple Server Utilities 1.9.0-dev3.32

## Native NPC appearance & custom textures
- Reduced the active NPC visual system to **Entity** and **Player**. Custom geometry is parked and no external renderer dependency is used.
- Entity NPCs can now use optional server-local or HTTPS PNG overrides per NPC template while retaining the selected Minecraft living entity's native model, animation, pose, hitbox, equipment and AI/runtime shell.
- Added a per-entity render-state texture bridge plus a narrow `LivingEntityRenderer` base-texture redirect; shared vanilla renderer instances are never globally mutated.
- Player NPCs retain Wide/Steve and Slim/Alex support with strict 64x64 PNG validation.
- Entity PNGs accept normal Minecraft texture dimensions up to the existing safety bounds and should follow the selected entity's vanilla UV layout.
- Legacy dev3.31 `Custom model` definitions normalize back to Entity using their persisted fallback entity type. Legacy custom-geometry fields remain serialized only for safe development-data compatibility and are not exposed in the editor.
- Removed the unused optional external-renderer support helper and all active GeckoLib planning from SSU.
- Added `docs/NPC-TEXTURES.md` and a dev3.32 test checklist.

## Compatibility
- Network protocol is `113` (was `112`).
- NPC definition schema is `14` (was `13`).
- NPC placement schema remains `4`; NPC Spawn Profile schema remains `1`; NPC dialogue schema remains `2`; Quest definition schema remains `2`.
- The dev3.30.1 safe melee fallback for shells without `minecraft:attack_damage` remains intact.

---

# Simple Server Utilities 1.9.0-dev3.31

## NPC custom-model & animation foundation
- Added explicit NPC visual modes: **Entity**, **Player skin**, and **Custom model**. Existing entity/player-skin definitions migrate automatically.
- Custom-model templates now persist a provider-neutral model resource, texture resource and animation resource while retaining a normal living entity as a safe physical fallback shell.
- Added a dedicated **Animations** NPC editor page with mappings for Idle, Walk, Attack, Cast/Ability, Hurt and Death.
- Added strict resource-ID validation and safe normalization for `.geo.json`, `.animation.json` and `.png` references.
- Added `NpcAnimationBridge`, a renderer-independent semantic state layer. SSU melee combat now publishes ATTACK and ability casts publish CAST; WALK/IDLE/HURT/DEATH are derivable from the living runtime shell.
- Added `NpcCustomModelSupport` as an optional-provider boundary. Core SSU contains no direct GeckoLib references, so a missing animation library cannot stop SSU or an existing world from loading.
- Custom model mode deliberately keeps rendering the configured fallback living entity until an animated renderer provider is registered; all AI, collision, combat, schedules, natural spawning and boss logic continue to function on that shell.
- Added `docs/CUSTOM-NPC-MODELS.md` and a dedicated dev3.31 migration/runtime test checklist.

## Compatibility
- Network protocol is `112` (was `111`) because NPC editor payloads now carry visual-mode, model-resource and animation mapping data.
- NPC definition schema is `13` (was `12`). Existing custom player skins migrate to Player skin mode; other definitions migrate to Entity mode.
- NPC placement schema remains `4`; NPC Spawn Profile schema remains `1`; NPC dialogue schema remains `2`; Quest definition schema remains `2`.
- The dev3.30.1 safe melee fallback for mob shells without `minecraft:attack_damage` is retained.

---

# Simple Server Utilities 1.9.0-dev3.30.1

## NPC combat crash hotfix
- Fixed a server-tick crash when an SSU combat NPC uses a vanilla/modded `Mob` shell that does not expose the `minecraft:attack_damage` attribute.
- SSU now uses vanilla `Mob#doHurtTarget` only when the shell actually has `ATTACK_DAMAGE`; otherwise it performs a safe `MOB_ATTACK` fallback using the NPC template's configured attack damage (or 1 damage when inheriting from a shell with no attack attribute).
- This prevents persistent world-load crash loops caused by the same NPC immediately retrying the invalid melee attack after the world is opened.

## Compatibility
- Network protocol and all persistence schemas are unchanged.
- No world/NPC data migration is required.

---

# Simple Server Utilities 1.9.0-dev3.30

## NPC Abilities
- Added reusable per-template NPC abilities. The same ability loadout works for persistent placements and dev3.29 dynamic natural/spawner NPCs.
- First built-in ability types: **Power Strike**, **Ranged Blast**, **Shockwave**, **Self Heal**, and **Leap**.
- Abilities support enabled state, unique ID/display name, cooldown, wind-up, recovery, use chance, minimum/maximum range, damage, radius, knockback, healing and an optional boss-phase binding.
- Ability casting temporarily owns combat movement when required, uses a visible End Rod telegraph during wind-up, and resumes normal chase/melee flow after recovery.
- Ability damage respects SSU combat authorization/faction hostility rather than blindly damaging every nearby living entity.
- Saving an NPC definition clears in-flight casts/cooldowns from the previous definition so edited abilities cannot finish with stale configuration.

## Boss / bossfight foundation
- Added optional **Boss encounter** configuration directly to NPC templates; bosses reuse the normal NPC navigation, factions, reactions, combat profiles and ability engine rather than using a separate AI stack.
- Added a real server boss bar with configurable visibility/range and live health progress.
- Added up to 8 health-threshold boss phases. Each phase can independently scale movement speed, attack/ability cooldown cadence and ability damage.
- Abilities can be bound to one phase or left unbound to remain available in every phase.
- Phase transitions are announced to nearby players through an overlay message.
- Added encounter reset/leash behaviour: configurable reset distance and idle timeout, return to the placement/spawn anchor, optional full heal, ability cooldown reset and restoration of configured stationary/no-AI state.
- Healthy idle bosses are not forcibly pinned to their anchor, so scheduled/patrol bosses remain possible outside active/reset encounters.

## NPC Editor
- Added dedicated **Abilities** and **Boss** pages while keeping the existing compact editor footprint.
- Ability page supports create/delete/cycle, ability type selection and all timing/range/effect/phase parameters.
- Boss page supports encounter/bossbar/reset toggles, arena/reset settings and phase creation/editing with health and combat multipliers.
- Phase renames update linked ability phase IDs; deleting a phase safely clears links that referenced it.

## Compatibility
- Network protocol is `111` (was `110`) because NPC editor payloads now carry ability and boss configuration.
- NPC definition schema is `12` (was `11`). Existing definitions migrate with no abilities and boss encounters disabled.
- NPC placement schema remains `4`; NPC Spawn Profile schema remains `1`; NPC dialogue schema remains `2`; Quest definition schema remains `2`.
- Existing placements, schedules, patrols, spawn profiles, dialogue and quest data require no migration.

---

# Simple Server Utilities 1.9.0-dev3.29.1

## Compile hotfix
- Fixed `NpcSpawnManager` importing `Heightmap` from the obsolete package; 26.2 uses `net.minecraft.world.level.levelgen.Heightmap`.
- Replaced removed `ServerLevel#getDayTime()` usage with `Level#getDefaultClockTime()` for spawn-profile day/night checks.

## Compatibility
- Network protocol remains `110`.
- NPC Spawn Profile schema remains `1`.
- NPC definition/placement/dialogue/quest schemas remain `11/4/2/2`.
- No persistence migration is required.

---

# Simple Server Utilities 1.9.0-dev3.29

## Dynamic NPC Spawning
- Added persistent **NPC Spawn Profiles** under `npcs/spawn_profiles`, separated from reusable NPC templates and persistent NPC placements.
- Added natural spawning with exact dimension selection, optional biome allow-list, day/night filtering, Y/light ranges, chance/cycle/attempt controls, group sizes, player-distance bands, nearby caps, global caps and despawn distance.
- Added vanilla-Spawner-backed NPC population. Profiles bind to an actual Spawner block and support cooldown, radius, activation range, group/cap rules and the shared environmental filters.
- Bound enabled SSU spawners suppress their original vanilla mob spawn through the NeoForge spawner position-check path, preventing duplicate vanilla + SSU population from the same block.
- Dynamic NPCs reuse the normal NPC template runtime (appearance, faction, labels, loot, stats, reactions/combat) but are not written as persistent placements.
- Dynamic population is removed when its profile is deleted/renamed, when its runtime entity disappears, or when no player remains within its configured despawn distance.

## NPC Manager / editor
- Added **Spawning** as a third NPC Manager tab beside Templates and Placements.
- Added Spawn Profile create/edit/test/delete actions, live-population counts and source/location summaries.
- Added dedicated Spawn Profile GUI with Natural/Spawner source selection, template cycling, environmental filters, population limits and source-specific controls.
- Added **Rebind looked-at spawner** for moving a profile to another physical vanilla Spawner.
- NPC template renames update linked spawn profiles; deleting a template is blocked while spawn profiles still use it.

## Compatibility
- Network protocol is `110` (was `109`) for the new spawn-profile editor payloads and NPC Manager mode.
- NPC Spawn Profile schema is `1`.
- NPC definition schema remains `11`; NPC placement schema remains `4`; NPC dialogue schema remains `2`; Quest definition schema remains `2`.
- Existing NPC templates, placements, schedules, patrols and dialogue/quest data need no migration.

---

# Simple Server Utilities 1.9.0-dev3.28

## NPC Combat & Reactions foundation
- Added independent NPC combat reactions so **attitude** (friendly/neutral/hostile) no longer directly dictates behaviour.
- New **When attacked** reactions: Ignore, Flee, Fight back, and Fight + call allies.
- New **When friendly attacked** reactions: Ignore, Assist, and Assist + call allies. Friendly assistance works against otherwise-neutral attackers and respects configured faction friendliness.
- New **Hostile seen** reactions: Ignore, Avoid, and Attack. A HOSTILE relation can therefore be used for avoidance/fear behaviour without forcing combat.
- Direct retaliation/flee reactions have priority over ally assistance and normal hostile sight acquisition.
- Recent attacks are remembered for a bounded 10 seconds; schedules/patrols pause while SSU owns an active combat/flee state and resume afterwards.

## Combat profiles
- Added reusable first-generation combat profiles: **Passive**, **Melee**, **Defender**, and **Aggressive**.
- Passive NPCs never initiate/perform SSU attacks; Melee uses the baseline chase/cadence; Defender chases more cautiously; Aggressive closes distance faster and attacks more frequently.
- Added configurable **assist range**, **flee distance**, and base **attack cooldown**.
- Managed melee attacks may now hit explicit retaliation/assist targets even when those attackers were not already HOSTILE, while SSU still blocks arbitrary friendly/neutral damage.
- Stationary/look-at Mob shells temporarily enable AI while SSU combat owns them and restore their configured AI state when combat ends.
- Non-Mob shells can participate in flee/avoid movement through the dev3.27 collision-aware navigation layer; active melee attacks still require a Mob-backed shell.

## NPC Editor
- Added a dedicated **Combat** page to the NPC editor for combat profile, self-defense reaction, friendly-defense reaction, hostile-sight reaction, assist range, flee distance, and attack cooldown.
- Repacked the editor tabs into two compact six-column rows so the new page does not increase the editor height.

## Compatibility
- Network protocol is `109` (was `108`) because NPC editor payloads carry the new combat configuration.
- NPC definition schema is `11` (was `10`). Existing definitions migrate with Fight back / Assist / Attack, Melee profile, 16-block assist range, 12-block flee distance, and 20-tick attack cooldown defaults.
- NPC placement schema remains `4`; schedule, patrol, dialogue and quest data formats are unchanged.
- NPC dialogue schema remains `2`; Quest definition schema remains `2`.
- No other persistence schema changes.

---

# Simple Server Utilities 1.9.0-dev3.27

## NPC AI foundation — navigation, routes and schedules
- Added a shared `NpcNavigationController` used by schedule, patrol and wander movement instead of maintaining separate movement loops.
- Mob-backed NPCs continue to use Minecraft `PathNavigation`; non-Mob living shells (including custom player-skin mannequins) no longer use incremental `snapTo()` for normal ground travel and now move through collision-resolving entity movement.
- Added progress/stuck detection with bounded recovery. Wander chooses a new destination when unreachable, patrol skips an unreachable waypoint instead of freezing the whole route, and schedules stop/retry rather than phasing through geometry.
- Fixed relation/combat updates cancelling normal navigation every 10 ticks when no hostile target existed.
- Hostile combat now temporarily interrupts schedule/patrol movement and the normal route resumes after the managed target is gone.
- Fixed scheduled no-AI shells dragging their persistent placement/home anchor along while walking their schedule.

## In-world route editing
- Patrol editor now draws route segments between waypoints instead of only isolated End Rod markers.
- Added session undo: sneak + right-click air restores the previous patrol edit; normal right-click air still finishes and reopens the NPC editor.
- Added an in-world **Schedule destination editor**. Right-click blocks to add destinations, sneak-right-click near a destination to remove it, sneak-right-click air to undo, and right-click air to finish.
- New in-world schedule destinations use the current in-game time; if that exact minute is already occupied SSU picks the next free 30-minute slot. Exact times remain editable afterwards.

## Schedule UX and arrival actions
- Schedule **Add** now creates a destination at the administrator's current location, facing and in-game time instead of a generic origin/default time.
- Added quick **Use my location** and **Now** controls.
- Reworded activity as **On arrival** and added explicit `Work / use main hand` and `Guard area` activities while preserving legacy `chop_tree` schedules.
- Arrival actions run after the NPC reaches its active schedule point and remain active until the next schedule time.

## Compatibility
- Network protocol remains `108`; no payload shape changed.
- NPC definition schema remains `10`.
- NPC placement schema remains `4`; existing schedules/patrols remain compatible.
- NPC dialogue schema remains `2`; Quest definition schema remains `2`.
- No other persistence schema changes.

---

# Simple Server Utilities 1.9.0-dev3.26.2.1

## Compile hotfix
- Added the missing `NpcManager.syncAll()` API used by the dev3.26.2 NPC/Quest workflow changes.
- `syncAll()` performs a full NPC runtime refresh and immediately resends NPC labels/quest markers.
- Fixes the six `cannot find symbol: method syncAll()` Java compile errors reported from `NpcCommands`, `NpcAdminService`, `NpcEditorService`, `NpcQuestWorkflowService`, `QuestEditorService`, and `QuestManager`.
- No gameplay, protocol, persistence-schema, or data-format changes.

---

# Simple Server Utilities 1.9.0-dev3.26.2

## NPC Quest workflow UX
- Added **Manage quests…** directly to the NPC Interaction editor. Admins can search/select a quest and set the NPC relationship with one click: Offer, Turn-in, Both, or Unlink.
- Added a simple per-quest NPC dialogue editor for Available / Accept / In Progress / Ready / Turn-in / Completed text plus the `!`, `•`, and `?` markers.
- SSU now generates and maintains the underlying `quest_ready` / `quest_available` / `quest_active` / `quest_completed` condition routing, fallbacks, `quest_offer`, and `quest_turn_in` services automatically for simple NPC quest links.
- Multiple linked quests on the same NPC receive an automatic player-specific quest selector with paging. Simple workflow links are bounded to 12 quests per NPC to remain inside the existing dialogue-node limits.
- Deleting a linked quest rebuilds generated dialogue; deleting an NPC placement from the editor, admin browser or NPC command clears simple quest links to that placement.
- The existing full graph editor is preserved as **Advanced dialogue** for custom branches, AND/OR/NOT conditions, services, and complex conversations.

## Quest access
- Quest access now supports **Quest Menu**, **NPCs**, or **Both** instead of an exclusive Menu/NPC toggle.
- When the first NPC quest is linked while access is still Quest Menu only, SSU explicitly asks whether to enable **NPCs only** or **Both** before saving the link.
- The same three-way access mode is available from SSU Settings and the NPC quest workflow.

## Compact Quest Definition Editor
- Reduced the editor from `720×474` to `550×344`, approximately 24% narrower and 27% shorter, fixing the editor extending outside the visible game area at common GUI scales.
- Reorganized editing into **General**, **Objectives**, **Rewards**, and **NPC Integration** tabs.
- Added clear labels throughout and removed raw entry fields from the normal path wherever a guided control is possible.
- Objective event, prerequisite type, and reward type now use searchable option pickers instead of long click-to-cycle/raw syntax workflows.
- Quest icons, item/block objective targets, and item rewards use inventory-style registry pickers.
- Quest prerequisites use a searchable quest picker; NPC integration uses searchable placement pickers.
- Objective IDs are generated automatically and collision-safe after add/delete operations.
- New quest IDs follow the title automatically and avoid existing quest IDs until the admin manually edits the ID.
- Raw objective metadata and uncommon/custom condition/reward parameters remain behind explicit advanced controls for compatibility.

## NPC integration from Quest Editor
- Quests can directly select a **Quest giver** and a separate **Turn-in NPC**.
- Added quick marker controls for Available `!`, Active `•`, and Ready `?`.
- Added a simple six-state NPC dialogue text editor directly from the quest.
- Selecting a turn-in NPC automatically requires turn-in semantics server-side.

## Compatibility
- Network protocol: `108` (was `107`).
- Quest definition schema: `2` (was `1`). Existing schema-1 quests migrate with no NPC links and retain their existing objectives/rewards/prerequisites.
- NPC dialogue schema remains `2`.
- NPC definition schema remains `10`.
- NPC placement schema remains `4`.
- Other persistence schemas are unchanged from dev3.26.1.

# Simple Server Utilities 1.9.0-dev3.26.1

## NPC Dialogue condition editor hotfix
- Fixed the Dialogue Editor 2.1 condition `Type` cycle getting stuck when the next registered type was `not` but the selected condition did not have exactly one child.
- Invalid direct `not` transitions are now skipped automatically; `Wrap NOT` remains the intended way to negate an existing condition tree.
- Quest condition types such as `quest_available`, `quest_active`, `quest_ready` and `quest_completed` are reachable normally again from the condition editor.
- No network protocol or persistence schema changes versus dev3.26.

# Simple Server Utilities 1.9.0-dev3.26

## NPC Dialogue, Interaction & Quest integration
- Added **player-specific dialogue node gates**. A dialogue node can now have its own Content Condition, not just individual choices.
- Added an optional **fallback node** per dialogue node. When the selected node is not available for the interacting player, SSU follows the configured fallback chain without executing the blocked node's entry actions.
- Fallback routes are bounded and cycle-safe. Missing/self/cyclic fallback references are rejected by both editor validation and authoritative normalization.
- Added the new `quest_available` condition, which uses the real NPC quest-access/start validation including prerequisites, repeatability and cooldowns.
- Existing `quest_active`, `quest_ready` and `quest_completed` conditions can now be used on whole dialogue nodes as well as choices, enabling state-dependent conversation branches.
- Dialogue Editor 2.1 can switch its Conditions page between the selected **Node** and **Choice**, exposes the node fallback route, and provides a server-backed quest picker for quest conditions.
- Dialogue node text can now be edited with SSU's reusable **rich-text editor** (16 colours plus bold/italic/underline/strikethrough), and the player dialogue/preview screens render that formatting.
- Quest offer/turn-in choices continue to use the existing server-authoritative `quest_offer` / `quest_turn_in` NPC services and their live quest target catalogue.
- Added **player-specific quest markers above NPCs**:
  - `!` = at least one linked quest can currently be started from this NPC
  - `?` = at least one linked turn-in quest is ready
  - `•` = a linked quest is active
- Quest markers are inferred from the NPC's configured quest services and linked dialogue conditions, so no duplicate quest-link storage has to be maintained.
- Marker priority is `?` > `!` > `•`, respects NPC quest permissions/access policy, updates through the existing bounded label sync, and scales with the NPC's live SCALE attribute.
- Quest markers can remain visible even when the normal NPC identity label is hidden.

## Compatibility
- Network protocol: `107` (was `106`) because NPC label snapshots now include the player-specific quest marker.
- NPC dialogue schema: `2` (was `1`) for node conditions and fallback routing.
- NPC definition schema remains `10`.
- NPC placement schema remains `4`.
- Quest persistence schemas and all other schemas are unchanged from dev3.25.

# Simple Server Utilities 1.9.0-dev3.25

## NPC Editor, Appearance, Behaviour & Patrols
- Split NPC configuration into clearer **Behavior** and **Movement** pages without removing the existing editor workflows.
- Added persisted NPC behaviour modes: Native AI, Stationary, Look at players, Wander and Patrol. Legacy schema-9 definitions migrate automatically from the former No AI flag.
- Added configurable look-at range/body rotation, wander radius/retarget interval/speed and patrol movement speed.
- Added placement-specific patrol routes with up to 32 waypoints. Each waypoint stores X/Y/Z, facing yaw and pause time; traversal can Loop, Ping-Pong or Random.
- Added an in-world patrol route editor. Right-click blocks to add waypoints, sneak-right-click near a waypoint to remove it, and right-click air to finish and reopen the NPC editor. End Rod particles mark existing waypoints while the edit session is active.
- Added a searchable local NPC skin browser for files found under `<world>/simpleserverutilities/npcs/textures/`, including bounded recursive discovery.
- Local PNG selection is validated synchronously during NPC Save, so missing/invalid/wrong-size local skins return an editor error immediately.
- Behaviour updates run at a bounded 5 Hz and prefer vanilla Mob navigation; schedule routes continue to override normal behaviour.
- Linked NPC placement copies keep independent world-space patrol routes and shift route coordinates with the copied placement.

## Compatibility
- Network protocol: `106` (was `105`) due to expanded NPC editor payloads.
- NPC definition schema: `10` (was `9`).
- NPC placement schema: `4` (was `3`).
- NPC dialogue schema remains `1`; NPC Shop schema remains `4`; other persistence schemas are unchanged from dev3.24.2.

# Simple Server Utilities 1.9.0-dev3.24.2

## NPC custom skin renderer hotfix
- Fixed both Local server PNG and HTTPS custom NPC skins resolving to Minecraft's magenta/black missing texture on mannequin NPCs.
- `PlayerSkin` now receives a `ClientAsset.ResourceTexture` whose explicit texture path is the exact identifier registered by SSU's `DynamicTexture`; the former one-argument constructor derived a different resource-pack path.
- Dynamic texture IDs are now definition-specific (`npc_skin/<definition>/<hash>`) so identical skin bytes used by multiple NPC definitions cannot be released out from under each other.
- Wide/Slim-only changes reuse the installed pixels while rebuilding the `PlayerSkin` model metadata.
- Client skin decode/install failures now emit diagnostic log warnings instead of silently falling back to the missing texture.
- Local/URL validation rules are unchanged: PNG, exactly 64x64, maximum 512 KiB; local files remain sandboxed under `<world>/simpleserverutilities/npcs/textures/`.
- No network protocol or persistence schema changes versus dev3.24.1.


## NPC label and remote-skin hotfix
- Removed the vanilla CustomName from managed NPC runtime entities so targeting an NPC can no longer reveal a second large vanilla nameplate over SSU's own label.
- Doubled the base size of NPC role/name/faction overhead text and scale both text size and label spacing with the NPC's live SCALE attribute.
- Remote HTTPS NPC skins now use image/browser-compatible request headers and a same-origin referrer for CDN/hotlink compatibility.
- Failed remote texture loads are no longer negatively cached forever; SSU retries a failed source after a bounded 30-second cooldown.
- No network protocol or persistence schema changes versus dev3.24.


## Dashboard icons and minimap marker
- Added the supplied `achievements.png` as the dedicated Achievement dashboard icon for player and admin views.
- Added the supplied `games.png` as the dedicated Minigames dashboard icon for player and admin views.
- Added a visible player-dashboard **Cosmetics** tile using `cosmetics.png`. The tile currently opens a local Coming Soon placeholder only; no cosmetic actions, storage, permissions or networking are introduced yet.
- Replaced the old procedural minimap player pointer with the supplied `arrow.png` texture. Existing north-up/rotating-map orientation logic is retained.
- The minimap arrow is centered on the effective map content area, including the inset textured square map.

## Compatibility
- Network protocol remains `105`.
- Player UI Preferences schema remains `13`.
- All persistence schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.23.3

- Hotfix: enlarged the rectangular textured minimap render area by 5 texture pixels on every side.
- New effective square frame insets: left/top 32 px, right 33 px, bottom 34 px (previously 37/37/38/39).
- Fixes the square textured minimap appearing slightly too small inside the frame after dev3.23.2.
- No protocol or schema changes versus dev3.23.2.


- Hotfix: textured rectangular minimap now clips terrain and markers to the actual inner opening of the 256×256 square frame.
- Fixes the issue where the square minimap could render outside the visible frame border.
- Round textured minimap behavior is unchanged.
- No protocol or schema changes versus dev3.23.1.


- Hotfix: replaced the minimap textured frame assets with the user-provided 256×256 final square and round textures.
- No code, protocol or schema changes versus dev3.23; this is a texture/asset integration hotfix only.

## Achievement item picker fixes
- Replaced the Achievement icon text-button registry list with a scrollable inventory-style catalogue using real item icons, translated-name/registry-ID filtering, selection highlighting and an explicit `Select item` confirmation.
- Fixed the old icon-picker return flow: confirmed choices now update the achievement draft itself before the editor is rebuilt, so the chosen icon is no longer discarded.
- Reworked Item rewards to choose from the editing administrator's own 36-slot live inventory. The selected stack is copied as an exact ItemStack template without moving or consuming the real item; Count remains independently editable.
- Exact ItemStack component data is serialized through the existing registry-aware Mail ItemStack codec and remains preserved when the achievement is saved/reopened.
- Registry target picker callbacks now write directly to the objective draft as well, avoiding the same transient-field loss on return.

## Server Operations labels
- Activity now visibly labels logging retention, rollback player/UUID, look-back hours and rollback radius, with explicit units and supporting tooltips.
- Scheduler now labels task name, action, schedule and optional payload/message; schedule syntax explains interval minutes, `daily@HH:mm` and `once@yyyy-MM-ddTHH:mm`.
- Chat now labels slow mode, duplicate/flood windows, flood message limit, caps threshold/minimum length, blocked phrases and mute player/duration/reason controls.

## Region Tool input fix
- Region Tool controls are now deterministic: left-click a targeted block sets Point 1; right-click a targeted block sets Point 2; right-click in the air opens the Region GUI.
- Block-target right-click is consumed by Point 2 and an additional server ray-pick guard prevents a follow-up item-use path from opening the GUI unless the hit result is actually `MISS`.
- Updated Region Tool help text to match the corrected controls.

## Optional textured minimap frames
- Added the supplied square and circular minimap frame textures as SSU resources.
- Added persistent `Frame: CLASSIC / TEXTURED` to Minimap Settings. Existing players migrate to Classic so their visual style does not change unexpectedly.
- Rectangle and Circle shapes automatically select their matching texture.
- The textured circular minimap uses bounded strip-based circular clipping so terrain does not leak through the transparent outer corners of the frame.

## Compatibility
- Network protocol increased `104 -> 105` because minimap payload/menu settings now carry the textured-frame preference.
- Player UI preference schema increased `12 -> 13` for the persistent minimap-frame choice.
- All other persistence schemas remain unchanged from dev3.22.

# Simple Server Utilities 1.9.0-dev3.22

## Achievement UX & administration polish
- Reduced both the player Achievement browser and the admin Achievement browser/editor by roughly 25%.
- Reworked the admin Achievement editor into collapsible General / Objectives / Rewards sections with guided, human-readable controls instead of exposing internal event/action syntax in the normal workflow.
- Added searchable registry pickers for achievement icon items, reward items, block/item/entity targets and registry tags; advanced raw metadata/custom-event fields remain available only when needed.
- Achievement money and damage/healing amounts are now entered in normal human units and converted internally.
- Replaced manual UUID achievement comparison/admin lookup with the existing searchable known-player picker; online and previously known offline players can be selected by name while UUIDs remain internal.
- Achievement rewards are rendered as effective rewards. Item rewards show their real item icon and a friendly `count × Item name`; money, permission, temporary permission, cosmetic, title and claim-chunk rewards use human-readable text.
- Existing exact ItemStack rewards are preserved safely by the guided editor, and their base item/count can be shown in the browser without discarding custom stack data.
- Added the vanilla challenge-complete advancement sound when an achievement is earned.

## GUI compactness and clarity
- Hologram Editor: removed the rich-text/color overlap, moved the rich-text/source/scoreboard area down, halved coordinate field widths, and replaced the generic Coordinates label with explicit X / Y / Z labels.
- Mail Composer: moved inventory upward, pulled the hotbar much closer, moved Back/Send Mail upward, and reduced total window height by about 19%.
- Server Operations > Backups: added visible labels for backup name, automatic backups, interval and retention plus explanatory tooltips.
- Server Operations > Worlds: added labels for world-border center/size, pregeneration radius, chunks-per-tick and auto-pause MSPT; `Save throttle` is now `Save pregen settings` with explanatory tooltips.
- Server Operations > Health now leads with a color-coded `GREAT / GOOD / NEUTRAL / BAD / VERY BAD` summary; TPS/MSPT/heap/cache/module details are hidden behind an optional Technical details control.

## Compatibility
- Network protocol increased `103 -> 104` because Achievement menu/editor payloads now carry structured reward presentation data and economy formatting metadata.
- Achievement definition/player schemas, Content Reward Ledger, Statistics, Server Operations and all other persistence schemas are unchanged.

# Simple Server Utilities 1.9.0-dev3.21.1

## Compile hotfix
- Fixed the remaining rich-text compatibility imports after the dev3.21 `SsuRichTextDocument` generalization: `Format`, `CharacterStyle` and `Segment` now use their canonical declaring type.
- Replaced the unavailable Minecraft 26.2 `ChatFormatting.isColor()` calls with an explicit vanilla 16-color predicate in `SsuRichTextComponents`.
- No gameplay, network payload, protocol, persistence, achievement/statistics schema, or reward-ledger behavior changed. Network protocol remains `103`.

# Simple Server Utilities 1.9.0-dev3.21

## Achievement system
- Added a server-authoritative custom Achievement system with persistent definition schema `1` and per-player progress schema `1`.
- Admins can create/edit/delete achievements from the Admin Dashboard. Definitions include an immutable ID, rich-text title and info, category, icon item, enabled/hidden/announcement flags, sort weight, multiple objectives and multiple rewards.
- Player Dashboard now contains Achievements. Players can browse all visible achievements, filter All/Earned/Unearned, inspect objective progress and rewards, and compare a selected achievement against another player.
- Achievement chat announcements are clickable and open the selected achievement comparison directly. Hidden achievement details are only revealed to viewers who have already earned that hidden achievement themselves; everyone else sees a generic hidden-achievement announcement.
- Admin testing supports `Reset progress` and `Reset + allow reward again`. The latter advances the reward generation so a deliberate re-test remains idempotent.
- Achievement completion is persisted before rewards execute. Offline event progress is supported and pending reward/announcement delivery resumes at the player's next login.

## Shared objective/event foundation
- Moved generic vanilla gameplay publication out of the Quest module into module-independent `ContentGameplayEvents`; disabling Quests no longer disables shared progression events.
- Added reusable Content objectives with target modes `ANY`, `EXACT`, `LIST`, `TAG` and aggregators `COUNT`, `SUM`, `MAX`, `UNIQUE`, plus exact metadata filters. TAG matching resolves vanilla block/item/entity registry tags directly and still accepts publisher-provided custom tag metadata.
- Added/standardized Content events for login/logout, play time, block break/place, deaths/kills, damage dealt/taken, crafting, item use/consume, distance travelled, dimension/biome visits, claim-group creation/chunk additions, auction purchases/sales/revenue, achievement completion, and the existing Quest/NPC/Minigame/Dungeon events.
- Distance tracking samples movement without counting large teleport/correction deltas and records movement metadata such as foot/sprint/swim/vehicle/elytra.
- Damage events include main-hand item, dimension and damage-source metadata, enabling objectives such as damage with a specific equipped item.
- Minigame healing events now expose self/non-self metadata for teammate-healing objectives.
- Claim creation and interactive/batch chunk claiming publish Content events, enabling compound achievements such as create a claim group and claim at least N chunks.
- Auction COMMITTED purchases publish deterministic durable buyer/seller events, including net seller revenue, so offline sellers can progress auction achievements without duplicate counting after recovery.

## Rewards and permissions
- Added persistent Content Reward Ledger schema `1`. Content reward lists write a durable PREPARED journal before side effects; interrupted or rollback-incomplete attempts fail closed instead of being replayed automatically and risking duplicate rewards.
- Cleanly rolled-back normal transaction failures remove their PREPARED ledger entry and may retry normally.
- Added persistent Temporary Permission overlay schema `1`; timed grants are resolved above base player permissions without overwriting the administrator's underlying permission configuration.
- Added shared reward actions for money, exact or normal ItemStacks, claim-chunk capacity, temporary permissions, generic cosmetic unlocks and title unlocks; existing permanent permission Content actions remain supported. `unlock_cosmetic` also bridges `minigame:<cosmetic-id>` into the existing minigame cosmetic entitlements.
- Item rewards can preserve serialized ItemStack components. If inventory delivery cannot fit and Mail is active, SSU delivers the reward as an idempotent system-mail attachment.

## Statistics
- Custom Statistics definition/player schemas increase to `2` for future-schema protection, durable event IDs and the expanded Content-event model.
- Statistics now consume the shared Content Event Bus instead of duplicating vanilla event handlers.
- Minigame direct statistic increments were removed where equivalent Content events already exist, preventing double-counting.
- Player statistic files are indexed at startup and loaded lazily during ordinary gameplay; global leaderboard/rank/total operations still intentionally load the indexed records they query.
- Future-schema statistic files are write-protected instead of being silently normalized and overwritten by an older build.

## World Edit snapshot preview safety
- Snapshot palette data is now segmented into small bounded packet chunks instead of placing the entire palette in the first preview packet.
- Client preview assembly no longer recopies the growing palette/block collection on every incoming packet; immutable completed data is built once after the stream finishes.
- Completed previews are spatially indexed into 16x16x16 sections. Rendering culls section AABBs first, avoiding a full million-block scan every frame for large snapshots.

## Entity Insight
- FLEEING now requires a recent player-caused hit (5-second TTL), preventing old attackers from keeping unrelated mobs cyan later.
- Entity Insight sync cadence is reduced to every 10 ticks and unchanged per-viewer snapshots are suppressed to reduce repeated entity payload traffic.

## Rich text and cleanup
- Extracted reusable rich-text code into `SsuRichText`, `SsuRichTextDocument` and `SsuRichTextComponents`. `HologramRichTextDocument` remains as a deprecated compatibility facade, while shared editors/renderers no longer depend on a Hologram-specific document type.
- Quest gameplay event handling is reduced to Quest-specific lifecycle persistence; generic block/combat/gameplay publication has one shared owner.
- New achievement/network/storage paths are bounded and future-schema guarded. Existing achievement IDs are immutable after creation to avoid orphaning player progress.

## Compatibility
- Network protocol increases from `102` to `103` for Achievement menu/editor payloads and the revised snapshot-preview stream payload.
- Achievement definition schema: `1`.
- Achievement player-progress schema: `1`.
- Content Reward Ledger schema: `1`.
- Temporary Permission overlay schema: `1`.
- Custom Statistics definition/player schemas increase to `2`.
- Auction purchase journal schema increases to `2` for durable Content-event publication state.
- Player UI preferences schema remains `12`.
- Minigame definition/recovery/progression/history schemas remain `21/4/3/1`.
- NPC definition/placement/dialogue schemas remain `9/3/1`; NPC Shop remains `4`.
- Player Claim storage remains `3`; Region storage remains `5`; portable snapshot format remains `1`.
- Mine schema remains `3`; physical Jail schema remains `2`; Server Operations schema remains `3`.

# Simple Server Utilities 1.9.0-dev3.20.1

Compile hotfix above dev3.20.

- Entity Insight: explicitly restores/validates the `FLEEING` member on `EntityInsightPayload.Attitude` used by `EntityInsightService`.
- `FLEEING` keeps wire id `3` and cyan color `0x55FFFF`; encode/decode and client color rendering remain aligned.
- No gameplay, permission, persistence, schema, or network-protocol change. Network protocol remains `102`.

# Simple Server Utilities 1.9.0-dev3.20

## World Edit in-world workflow
- Reworked the dedicated World Edit Tool input flow: **left-click a block = Point 1**, **right-click a block = Point 2**, and **right-click in the air = open the full World Edit GUI**.
- Added a normal rebindable Minecraft key mapping named **World Edit: compact tools**, defaulting to `W`. While the SSU World Edit Tool is held, this toggles a transparent bottom-right editing palette instead of covering the world with the full editor.
- The compact palette exposes the operations that benefit from continuous world visibility: one-block X/Z arrow movement, `+Y`/`-Y`, rotate left/right/180, mirror X/Z and vertical flip. Buttons are deliberately 28x18 pixels and use tooltips rather than long labels.
- Clipboard, Fill, Replace, Snapshots and the complete editor remain available in the normal World Edit GUI. Existing server-authoritative action payloads and bounded Undo/Redo are reused by the compact controls.
- Cleaned the World Edit Snapshots page layout so its explanatory text, Preview/Load controls and selected-snapshot label no longer overlap.

## Real snapshot ghost preview
- Replaced the old sampled coloured-cube preview with a chunked preview stream containing the **full snapshot block-state palette and every snapshot block entry**.
- Snapshot preview now submits Minecraft's actual baked block-model quads and resource-pack textures through a translucent moving-block render layer, producing a ghost-like copy instead of placeholder boxes.
- Biome/block tint sources are applied per previewed block where available. Air remains invisible; no blocks are actually placed until confirmation.
- Removed the filled preview cuboids and removed the preview screen's vanilla blur/dim background extraction. The world remains visually readable behind both preview controls and Free mode.
- Snapshot preview controls were compacted to bottom-right arrow/axis/transform buttons, with Place/Cancel and Free inspection retained.

## Entity Insight polish
- Armor Stands are now completely excluded from Entity Insight, preventing their misleading `20/20 HP` label.
- Added **Fleeing** as a fourth dynamic Entity Insight attitude, rendered cyan. A non-hostile mob that is actively moving away from a player who hurt it is shown as fleeing; active targeting remains higher-priority hostile/red and the label automatically reverts when the flee condition ends.

## Compatibility
- Network protocol increases from `101` to `102` for the full chunked snapshot-preview wire format and the added Entity Insight attitude semantic.
- Player UI preferences schema remains `12`.
- Minigame definition schema remains `21`; NPC definition schema remains `9`; Mine schema remains `3`; physical Jail schema remains `2`; Server Operations schema remains `3`.
- Portable selection snapshot format remains `1`; saved snapshots are not migrated.

# Simple Server Utilities 1.9.0-dev3.19

## Rich-text palette polish
- Reflowed every shared 16-colour rich-text palette into exactly two rows of eight colors instead of one long row. This applies to Mail Compose, Floating Hologram, the reusable Rich Text editor and Rank Prefix editor.
- Enlarged the Floating Hologram inline text-colour swatches from 7x7 to 14x14 pixels so the actual colors are clearly visible.
- The tooltip label for the **Black** swatch is now rendered in white for readability on Minecraft's dark tooltip background. Selecting Black still applies the real black text color.

## World Edit structural-entity transform fix
- Full-snapshot transforms now rotate/mirror the persisted vanilla `Facing` direction used by hanging entities in addition to transforming position and yaw.
- Item frames, glow item frames and paintings therefore keep the correct wall/floor/ceiling attachment when a World Edit snapshot is rotated 90/180 degrees, mirrored, or vertically flipped.
- Snapshot format remains `1`; existing saved snapshots remain compatible because the fix is applied in memory while transforming them.

## NPC overhead-name fix
- Suppressed Minecraft's vanilla custom-name nameplate for entities tagged `ssu_npc`. This prevents the NPC name from appearing a second time when the player targets the NPC.
- SSU's own Role / Name / Faction overhead label remains unchanged and is still the single visible NPC identity layer.

## Compatibility
- Network protocol remains `101`.
- Player UI preferences schema remains `12`.
- Minigame definition schema remains `21`; NPC definition schema remains `9`; Mine schema remains `3`; physical Jail schema remains `2`; Server Operations schema remains `3`.
- No persistence migration is required.

# Simple Server Utilities 1.9.0-dev3.18.2

## Compile hotfix
- Fixed the Entity Insight integer dashboard slider for the Minecraft 26.2 `AbstractSliderButton` contract by implementing `applyValue()` instead of the non-existent `apply()` override.
- Fixed the Minecraft 26.2 `TamableAnimal` import to `net.minecraft.world.entity.TamableAnimal`.
- Fixed SSU NPC exclusion in Entity Insight by using the current `Entity#entityTags()` accessor instead of removed `Entity#getTags()`.
- No gameplay behavior, network packet shape, permissions, or persistence formats changed.

## Compatibility
- Network protocol remains `101`.
- Player UI preferences schema remains `12`.
- All other schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.18.1

## Player Dashboard polish
- Added the supplied dedicated Player Dashboard icons:
  - `ticket.png` for **Support**.
  - `kits.png` for **Kits**.
  - `mines.png` for **Mines**.
- The admin dashboard keeps its existing administration icons; the new assets are used specifically by the player-facing tiles.

## Entity Insight GUI-first cleanup
- Removed the newly introduced `/ssu settings entity_insight ...` mutation command branch.
- Entity Insight remains fully configurable from the player GUI: enabled, health display, range (0-32 blocks), and maximum rendered entities (1-50).
- Existing legacy settings commands for unrelated older features remain unchanged for compatibility.

## Compatibility
- Network protocol remains `101`.
- Player UI preferences schema remains `12`.
- No persistence or gameplay-format migration is required from dev3.18.

# Simple Server Utilities 1.9.0-dev3.18

## Entity Insight
- Added **Entity Insight**, a player-configurable overhead nametag overlay for nearby non-player living entities.
- Entity Insight renders the entity name and, optionally, live current/max health (for example `Sheep  10/10 HP`).
- Nametags are attitude-coloured: green for friendly entities, yellow for neutral entities and red for hostile entities. Neutral vanilla mobs switch to red while actively targeting a player and return to yellow when that hostility ends; tamed animals remain friendly unless they actually target a player.
- Invisible entities, players and SSU-managed NPCs are excluded so Entity Insight does not leak invisibility or collide with SSU's existing player/NPC overhead identity renderers.
- Players can enable/disable the overlay, independently enable/disable health, choose an exact render range from `0` to `32` blocks and cap the nearest rendered entities from `1` to `50`. Defaults are ON, health ON, 16 blocks and 20 entities.
- Added default-granted permission key `ssu.entity_insight.use`. The server remains the hard gate and selects the bounded nearest entity set for each viewer.
- Added `/ssu settings entity_insight` fallback controls for enabled/health/range/max_entities in addition to Settings > Combat.

## Compatibility
- Network protocol increases from `100` to `101` for the bounded Entity Insight viewer payload and expanded dashboard settings snapshot.
- Player UI preference schema increases from `11` to `12` for Entity Insight enable/health/range/count preferences. Existing player preference files migrate in place with conservative defaults.
- Minigame definition schema remains `21`; NPC definition schema remains `9`; Mine schema remains `3`; physical Jail schema remains `2`; Server Operations schema remains `3`; all other persistence schemas are unchanged.

# Simple Server Utilities 1.9.0-dev3.17.1

## Compile hotfix
- RegionSelectionEditScreen compile hotfix: restored the missing `setNotice(String, boolean)` helper used by the new World Edit GUI.
- No gameplay, protocol, schema, or persistence changes.

## King of the Hill fixes and editor polish

- Fixed active King of the Hill combat being blocked by the containing Region's normal PvP rule. KOTH now participates in the same narrowly scoped active-match Region-PvP bypass as the existing combat minigames; outside an active match the Region rule still applies.
- KOTH friendly fire remains independent: `Friendly fire: No` blocks only teammate damage while enemy PvP remains enabled during the match.
- Reworked all three KOTH editor tabs into a smaller, denser layout. Numeric fields are sized for their actual values, text fields retain practical width, time fields show `(sec)`, and the title/helper/label rows no longer occupy the same vertical space.

## Dedicated World Edit Tool

- Added a standalone **SSU World Edit Tool** to Admin Tools. It uses the same two-left-click Point 1/Point 2 selection state as Regions and opens its editor with right-click.
- The Region Setup Tool is now focused on Region-specific work (Region creation/bounds, protection, access/rent, auto-reset and browsing); the generic Selection/build tab is no longer exposed there.
- Added a dedicated World Edit GUI with separate Clipboard, Fill, Replace, Snapshots and Transform tabs.
- Clipboard operations now include Copy, Cut, Paste, Clear to air, Fill water, Fill lava, Undo and Redo.
- Weighted fill palettes are inventory-backed and no longer limited to six entries. The client editor supports up to 64 authored fill entries and the payload/backend accepts the same bounded maximum; unused percentage can intentionally become air.
- Added multi-source block replacement with a separately authored weighted target palette. Source and target blocks are chosen from the admin inventory without consuming items.
- Added rotate left/right/180, mirror east/west, mirror north/south, vertical flip and X/Y/Z offset/move operations.
- World Edit transforms and moves use the existing full portable snapshot infrastructure so block entities/inventories and supported structural entities are preserved instead of degrading to block-state-only copies.
- Existing portable selection snapshots are integrated directly into World Edit: save, list/load, ghost preview and confirmed placement reuse the existing snapshot/preview system.
- Added bounded per-admin session Undo/Redo history using full snapshots. History keeps at most 8 entries and trims aggregate stored volume to a bounded limit; large edits continue to use the existing batched job scheduler.
- Snapshot preview confirmation now records undo history before placement.

## Mine Setup Tool input fix

- Mine Setup Tool selection now follows the shared SSU convention: first left-click sets Point 1, second left-click sets Point 2, and the next left-click starts a fresh selection.
- Right-click is now reserved exclusively for opening Mine Administration, removing the previous Point 2/menu input conflict.
- Actionbar feedback explicitly tells the admin which point was set and what the next input does.

## Block Party editor polish

- Removed the long comma-separated registered block-ID field from Block Party round configuration.
- Block Party palettes are now authored through a 16-slot inventory-backed ghost palette. Real block icons and vanilla hover tooltips are shown; left-click selects/replaces a palette slot and right-click removes an individual entry. Admin inventory items are never consumed.
- Duplicate palette blocks are rejected, the existing minimum of 2 valid blocks remains enforced, and the maximum is explicitly 16.
- Added clear labels and units to every round-rule field, including initial/minimum round time, per-round speedup, drop duration, tile size and elimination depth; numeric fields were compacted accordingly.

## Compatibility

- Network protocol remains `100` because no packet wire shape changed; the existing Region selection action payload only accepts a larger bounded list.
- Minigame definition schema remains `21`; Block Party and KOTH reuse existing schema-21 fields.
- NPC definition schema remains `9`; Mine schema remains `3`; physical Jail schema remains `2`; Server Operations schema remains `3`; all other persistence schemas are unchanged.

# Simple Server Utilities 1.9.0-dev3.16

## King of the Hill v2

- Every KOTH arena now explicitly uses exactly one objective mode: `STATIC` or `ROTATING`. The mode is stored per arena, so different arenas of the same KOTH minigame may use different modes while the existing arena-rotation system remains responsible for choosing the next arena.
- **STATIC** uses a persistent tug-of-war control bar: 40% red territory, 20% neutral white territory and 40% blue territory. A yellow control marker moves toward the team with the greater live presence inside the hill. The HUD shows the movement direction. Once the marker enters a team's coloured 40% section, that team starts receiving score until the marker is pushed back into neutral or the opposite side.
- Static control movement is deliberately slower than the original instant-control implementation. `Neutral push (sec)` controls the approximate time for a one-player advantage to move the marker from the centre to the edge of the neutral section.
- **ROTATING** has no control bar. The Setup Tool supports up to 16 authored hill points; an arena requires at least two. The team with the stronger current presence on the active hill scores, ties score nothing. `Rotate every (sec)` and `Warning (sec)` control relocation timing.
- The active rotating point advances sequentially and broadcasts a warning before moving. Arena rotation and hill-point rotation remain separate systems.
- Added a dedicated live KOTH HUD with both team scores, score target, current presence, clear `YOU ARE INSIDE THE HILL` feedback, static control bar/direction, or rotating point/countdown state. Players no longer need to open the match overview to see live KOTH scoring.
- Added a translucent in-world KOTH half-dome. It is white while neutral, changes to the controlling/scoring team's configured colour, previews in setup, and follows the currently active rotating hill point.
- The KOTH Setup Tool now places a visible white physical banner at static/rotating hill centres while editing. KOTH team spawn setup banners now use the nearest vanilla banner colour to the configured team colour instead of the generic yellow banner.
- KOTH setup supports a hill-point slot selector for rotating arenas. Existing v20 KOTH arenas load as `STATIC` by default.
- KOTH editor input fields now have explicit visible labels and units. Arena pages can cycle through existing arenas, allowing each arena to choose its own STATIC/ROTATING mode.
- The detailed in-match game overview has been reduced from 720x430 to 576x344 logical pixels (about 20% smaller) and its layout was compacted accordingly.

## Compatibility

- Network protocol increases from `99` to `100` for the new KOTH dome visual payload.
- Minigame definition schema increases from `20` to `21` for per-arena KOTH mode and rotating hill points.
- NPC definition schema remains `9`; Mine schema remains `3`; physical Jail schema remains `2`; Server Operations schema remains `3`; all other persistence schemas are unchanged.

# Simple Server Utilities 1.9.0-dev3.15.4

## Jail community-reward hotfix

- Fixed the prisoner who completed a community task sentence being included in the recipient pool for the resulting community-reward mail and item distribution.
- The completing prisoner is now always excluded from community contribution recipients, even if they qualify as a recently active player.
- If no other recently active players qualify, no community reward mail/items are generated; the punishment still completes and the prisoner is released normally.
- No protocol or persistence schema changes.

# Simple Server Utilities 1.9.0-dev3.15.3

## Jail task mining hotfix

- Fixed jailed task prisoners only being able to physically remove Mine blocks that were still needed by the punishment task.
- While a prisoner has an active task punishment, is inside the configured Jail Task Area, and has both `ssu.mines.use` and the Mine-specific permission, **every block inside an overlapping Mine can now be mined and stays removed until the Mine resets**.
- Only block types configured in the punishment requirements advance Jail task progress; unrelated Mine blocks are removed normally for the Mine but do not count toward the sentence.
- Blocks whose required quota is already complete may still be mined from the overlapping Mine, but no additional punishment progress is awarded.
- Jail-task Mine breaks continue to produce **no physical block drops**, regardless of the Mine's normal drop mode.
- Mine mined-block statistics are updated for every Mine block physically removed by the prisoner.
- Physical AIR replacement now succeeds before Mine statistics or Jail task progress are committed.
- Outside an overlapping Mine, the dedicated Jail Task Area remains requirement-only as before.
- No protocol or persistence schema changes.

# Simple Server Utilities 1.9.0-dev3.15.2

Compile hotfix on top of dev3.15.1.

- Fixed `MinigameSetupToolService#setHillCenter`: added the missing local `locationInsideRegion(MinigameLocation, Region, double)` helper used to validate that a King of the Hill center lies inside its arena Region.
- The helper intentionally matches the existing `MinigameManager` containment semantics, including dimension validation and optional vertical margin.
- Network protocol remains 99; NPC schema remains 9; Minigame definition schema remains 20; no persistence or gameplay format changes.

## Previous: 1.9.0-dev3.15.1

# Simple Server Utilities 1.9.0-dev3.15.1

Compile hotfix on top of dev3.15.

- Fixed `MinigameManager#tickObjectiveTime` for King of the Hill: arena lookup now uses the existing `arena(MinigameDefinition, String)` helper with the active match arena ID.
- Removed the invalid `MinigameLocation#configured()` call for the KOTH hill center; `MinigameLocation` has no such API and KOTH arena validation already requires a valid hill-center location.
- Network protocol remains 99; NPC schema remains 9; Minigame definition schema remains 20; no persistence or gameplay format changes.

## Previous: 1.9.0-dev3.15

## NPC redesign
- Reorganizes the NPC editor around clear Identity, Appearance, Interaction, Behavior, Relations, Stats, Loadout, Schedule and Respawn pages while retaining the existing definition/placement persistence model. Existing schema-8 NPCs migrate in place.
- Merchant NPCs now expose a direct NPC-managed shop workflow. `Create NPC shop`/`Edit NPC shop` opens the Shop Editor as a child of the NPC editor; technical shared-shop identity/navigation is hidden in this embedded flow, while `Shared shop...` remains available for intentionally reused shops.
- Embedded shop offer authoring uses the complete administrator inventory without consuming it: left-click copies the complete exact stack, right-click copies one item, and `Save & back` returns to the NPC editor.
- Adds optional custom player-style skins. `LOCAL` resolves only relative files below the server's `simpleserverutilities/npcs/textures` folder; `URL` accepts HTTPS only. Assets are capped at 512 KiB and must be 64x64 PNG.
- Custom textures are server-authoritative: the server asynchronously loads/validates/caches the PNG, hashes it and sends only changed visible-NPC assets to clients through a bounded payload. Clients register dynamic textures and override only SSU-managed mannequin skins; missing/malformed assets safely fall back to the vanilla mannequin skin.
- Supports Wide and Slim skin models, removal/tombstone sync when a custom skin is disabled, cache invalidation on NPC save/delete and safe reload when an administrator replaces an asset at the same configured source.
- NPC definition schema increases from `8` to `9`.

## King of the Hill
- Adds a fully implemented two-team King of the Hill mode with configurable score target, hill radius, score interval, points per interval, team names/colours, weapon and friendly-fire rule.
- The Setup Tool can place the hill center and team spawns. The runtime tracks neutral, contested and controlled states, awards team score only while one team is uncontested on the hill and credits individual objective contribution only to players actually standing on it.
- Integrates normal SSU preparation, combat needs, death/respawn, results, rewards, victory effects, objective-time statistics, validation and arena snapshot/recovery flow.

## Block Party
- Adds a fully implemented 2–32 player free-for-all Block Party mode with configurable block palette, starting/minimum round time, per-round speedup, drop duration, tile size and fall-elimination depth.
- The Setup Tool manages the playfloor plus one spawn per maximum player. Each round paints the floor, announces a non-repeating safe block when possible, eliminates players on the wrong block, removes every unsafe floor block, waits through the drop phase and repaints for the next round.
- Supports same-round draw handling, last-player-standing victory, fall elimination, per-round survivor score and normal SSU region snapshot restoration after the match.
- Minigame definition schema increases from `19` to `20`.

## Compatibility
- Network protocol increases from `98` to `99` because NPC editor/label payloads changed and NPC texture synchronization was added. Client and server must use the exact same dev3.15 build.
- Server Operations schema remains `3`; Mine definition schema remains `3`; physical Jail definition schema remains `2`; Moderation/Jail sentence schema remains `2`; all unrelated persistence schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.14.1

Compile/runtime hotfix on top of dev3.14.

## Jail task mining
- Fixed valid Jail task blocks inside a Mine visually/physically reappearing immediately after being mined.
- Root cause: jailed block breaks are deliberately cancelled to suppress vanilla drops and unrelated break behavior, but the task handler set the block to air inside that cancelled event. Vanilla then resynchronised the original block state after cancellation.
- Validated Jail task breaks are now coalesced and applied on the next server tick, after the cancelled vanilla event has fully completed.
- Progress, Mine statistics and task completion are committed together with the actual delayed block removal, so one physical block cannot be counted repeatedly while waiting for removal.
- Mine permissions remain mandatory (`ssu.mines.use` plus the Mine-specific key); no Jail bypass was introduced.
- No schema or network protocol changes.

# Simple Server Utilities 1.9.0-dev3.14

## Jail/Mines nesting, administration and permission polish

- Makes dedicated Mines structurally Region-bound. Applying or saving Mine bounds now requires the complete 3D Mine volume to fit inside an existing SSU Region; SSU automatically records the smallest containing Region and invalid/outside Mines no longer operate, teleport, reset or expose status holograms. `Region -> Jail -> Mine` nesting remains valid and Mines stay independent from Jail.
- Changes the automatic permission convention for newly created Mines to `ssu.mines.use.<mine-id>`. Existing non-empty/custom Mine permission keys are preserved so live servers are not silently broken; the Create Mine editor tracks the entered ID while its permission remains automatic.
- Removes manual Jail Parent selection. Jail Administration now derives the containing Region from the actual Jail bounds and rejects bounds that are not fully inside a Region. The parent is retained only as internal integrity metadata.
- Removes `Cell radius` from Jail definitions, GUI and confinement. Solitude prisoners spawn in a configured physical cell; the built cell controls normal movement while the complete Jail bounds remain the anti-escape safety boundary. Legacy schema-1 `cellRadius` data is safely ignored during normalization.
- Adds dedicated Jail/Task Area 3D border visualization while an admin inspects/edits a Jail, with visually distinct Jail and Task Area colours. Closing Jail Administration clears this editor layer.
- Replaces all-or-nothing cell maintenance with individual cell management. Admins can select a stable cell entry, inspect dimension/XYZ, move that cell to their current location or delete it. Cells actively assigned to solitude prisoners cannot be moved/deleted; later cell assignments are shifted safely after a deletion.
- Clarifies punishment time fields with explicit units, including `Time sentence (hours)`, `Task deadline (hours)` and `Share period (days)`.
- Cleans Mine Administration layout: notices wrap inside the panel, the New Mine action no longer crowds the title, palette previews no longer sit behind buttons and the setup-tool button is removed. Mine and Jail Setup Tools are now obtained centrally from Admin Tools.
- Fixes nested Jail-task mining permissions. A jailed task prisoner receives no Mine bypass: if the task block lies in a Mine, both `ssu.mines.use` and the Mine-specific `ssu.mines.use.<mine-id>` permission are evaluated through a narrow jail-safe resolver while all unrelated jailed permissions/features remain blocked.
- Adds `Remove holo` for generated Mine status holograms. Removing one disables its stored generated-hologram state and deletes the generated world hologram rather than leaving an orphan.
- Adds an administrator Prisoner Overview to Jail Administration with active prisoners, online state, facility, sentence/path, start/remaining time, task progress, buyout state, solitude cell and reason, plus online/path filters, paging, refresh, teleport-to-prisoner, punishment details and release actions.
- Raises network protocol from `97` to `98` because Jail editor border/category state and administration flows changed. Mine definition schema rises from `2` to `3` for the persisted containing Region; physical Jail definition schema rises from `1` to `2` for parent auto-derivation/cell identity and removal of cell-radius semantics. Server Operations remains schema `3`; Moderation player/Jail sentence remains schema `2`; all unrelated persistence schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.13

## Dedicated Jail system redesign

- Rebuilds Jail as a dedicated SSU subsystem with persistent physical Jail definitions, its own Admin Center page and a two-corner `SSU Jail Setup Tool`. Every Jail must fit completely inside one existing SSU Region; independent Mines may be nested inside a Jail and remain separate systems.
- Adds clear Jail facility setup for outer bounds, an optional task-work area, intake spawn, task spawn, release exit and up to 32 solitude-cell spawnpoints. Structural bounds/cell deletion is locked while prisoners are active, and time-only prisoners are distributed across the least-used configured cells.
- Reworks sentencing into three explicit modes: `Buyout or Task`, `Task only`, and `Time / solitude`. Reasons use the shared rich-text editor; task requirements/tools use a real inventory-backed ghost-slot editor instead of JSON fields.
- `Buyout or Task` gives the prisoner 30 seconds in a forced Jail dashboard. A successful buyout releases immediately; insufficient funds immediately select and lock Task; no choice within 30 seconds also selects and locks Task automatically.
- Task punishments have a configurable absolute completion deadline (GUI default 168 hours / one week). Missing the deadline produces a durable permanent ban with the exact reason `failed to complete punishment`, including while the prisoner is offline. Completing every configured block requirement now completes/releases the punishment automatically.
- Time-only punishments place prisoners in solitude. Multiple cell spawns are supported and a configurable cell radius allows normal movement inside the physical cell while enforcing return if the prisoner leaves that cell/dimension.
- Jailing an online player immediately cancels pending SSU teleportation, exits active minigames/dungeons, closes non-inventory containers, snapshots the restored normal player state, equips only Jail task tools where applicable, teleports to the selected Jail and opens the Jail dashboard.
- Jail restrictions are enforced continuously: commands, item/block/entity interaction, combat, damage, pickup/toss exploits, normal block breaking, unrelated dashboard pages, SSU teleports and normal permission/admin bypasses are disabled while jailed. Even an operator serving a sentence loses SSU administrator capability until release. All shared dashboard entry points are server-gated back to the Jail dashboard rather than relying only on client navigation.
- Jail supersedes an existing Freeze state so the old freeze anchor cannot fight the Jail confinement position; trying to freeze an already jailed player is rejected as a conflicting/redundant restriction.
- Releasing an offline prisoner now keeps the pre-Jail snapshot in a durable pending-restore state. Their inventory/effects/gamemode are restored and they are moved through the configured Jail release exit/fallback on the next valid login instead of losing the backup. Expired time sentences are likewise completed immediately when the player reconnects.
- Prisoners cannot remain teleported outside their Jail/cell: direct/admin/vanilla teleports are corrected by the confinement tick. Admins instead get a `Teleport to prisoner` action, and Jail Administration has `Teleport to jail`.
- Jail task mining is independent from Mines. Required blocks are counted only inside the Jail's own work area and produce no normal drops. If an independently configured Mine happens to overlap that area, its block-mined progress is notified so nested-zone bookkeeping stays consistent without making the Jail depend on a Mine.
- Preserves legacy dev3.12 active Jail records safely: old Region-backed task sentences retain their old task-region mining route, do not gain a surprise one-week deadline, and legacy location fallback remains available only for those older saves.
- Adds Jail definitions to Server Operations configuration profiles.
- Adds `ssu.jails.admin`.
- Raises Player Moderation record/Jail sentence persistence from schema `1` to `2`; new physical Jail definition schema starts at `1`. Moderation settings and inventory snapshot schemas remain `1`.
- Raises network protocol from `96` to `97` for the redesigned sentencing payload and dedicated Jail Administration payloads. Server Operations schema remains `3`; Mine definition schema remains `2`; all unrelated persistence schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.12

## Dedicated Mines completion pass

- Expands the standalone Mines module beyond the dev3.11 foundation with a real inventory-backed nine-slot reset-palette editor. Admins select a ghost palette slot, click any block item in their inventory to copy it, assign an independent weight and can clear slots without consuming inventory items.
- Adds a dedicated Mining Rules screen with `NORMAL`, `NONE` and `CUSTOM` drop modes, configurable XP multiplier, independent Fortune/Silk Touch allowance, reset warning output (`ACTIONBAR`, `CHAT`, `TITLE`) and optional warning sound.
- Custom mine drops are authored from real inventory items into up to nine ghost drop slots. Each slot has independent minimum count, maximum count and percentage chance; custom drops are server-validated and split safely to the item's normal maximum stack size.
- `NORMAL` drop mode can suppress Fortune and/or Silk Touch for mine drops without altering the player's actual tool. `NONE` suppresses item drops; the XP multiplier remains independently configurable in every mode.
- Adds generated per-mine status holograms managed from Mines. They can use an automatic mine/spawn position or a custom `Hologram here` position, configurable view range and live mine tokens for name, remaining/mined percentage, block progress, resets and next reset. Mine tokens resolve even when the Custom Statistics module is disabled.
- Adds dedicated Mine Statistics with current-cycle progress, lifetime blocks mined, teleport/use count, total/manual/automatic resets, last mining/reset timestamps, top miners and most-mined block types with real block icons/tooltips.
- Threshold-triggered resets now use the configured warning countdown instead of resetting immediately. Timed and mined-threshold reset triggers share the earliest due time; empty-only resets retry safely after 30 seconds while players remain inside.
- Reset interval edits now restart the interval from the newly saved value instead of retaining a stale timer from the previous interval. Threshold/warning edits safely restart any runtime threshold countdown.
- Adds paging to the Mines catalogue so more than the first admin/player page of mines remains reachable.
- Hardens Mine teleport access so the global `ssu.mines.use` permission is enforced server-side even if a client tries to send a teleport action directly.
- Player/admin Mine payloads now expose the effective next reset due time, including an active mined-threshold countdown, so the Mines UI and statistics screen show the live countdown consistently.
- Failed/cancelled reset jobs clear the resetting state and use a 30-second safe retry delay instead of immediately re-submitting every tick. Disabled mines also remove their generated status hologram until re-enabled.
- Mine definition schema rises from `1` to `2` for mining rules, custom drops, hologram settings and lifetime statistics. Legacy schema-1 mines normalize in place and preserve their current mined count as the minimum lifetime counter.
- Network protocol remains `96`; Server Operations schema remains `3`; all non-Mine persistence schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.11.1

Compile hotfix built on dev3.11.

- Fixed `HologramEditorScreen` compilation by capturing the palette loop index in an effectively-final local before using it in the rich-text colour tooltip style lambda.
- Updated Mine permission-denial feedback to the Minecraft 26.x `ServerPlayer#sendOverlayMessage(Component)` API.
- Updated Mine reset warnings to use `sendOverlayMessage` for ACTIONBAR mode and `sendSystemMessage` otherwise, replacing the removed `displayClientMessage(Component, boolean)` call.
- No network payload, persistence/schema, Mine gameplay, GUI layout or rich-text behavior changes beyond these compile/API corrections.
- Network protocol remains `96`; Server Operations schema remains `3`; Mine definition schema remains `1`.

# Simple Server Utilities 1.9.0-dev3.11

## GUI polish, support workflow and dedicated Mines foundation

- Compacts the Mail/Wallet/Profile/Kit/Support/Floating Hologram workflows requested during the final Minecraft 26.2 polish pass, while keeping existing gameplay/data semantics unless explicitly noted below.
- Mail status/feedback notices now wrap inside the mailbox panel instead of running beyond the GUI.
- Replaces rich-text colour dropdowns with a shared direct 16-colour Minecraft swatch palette. Swatches have no permanent labels; hovering a swatch shows that colour's name rendered in the same colour. Mail compose, shared rich-text editors, Floating Holograms and rank-display rich text use the same reusable palette.
- Compacts the Floating Hologram editor by roughly 20%, shortens numeric inputs, displays coordinates with two decimals, replaces the custom background hex field with a compact background palette and reduces oversized controls.
- Reworks Kit Administration and Player Kits into smaller layouts with labelled fields, real item-stack previews and vanilla hover tooltips. The Kit Contents ghost editor draws its inventory/hotbar grids, supports full-stack left click and one-at-a-time right click behaviour, keeps feedback readable and uses Back to return to Kit Administration.
- Reworks player Support & Reports into a compact ticket overview. New tickets are composed in a separate category/description screen; replies use the shared rich-text editor; closing requires a reason which is retained in ticket history. Closed tickets are automatically removed after a configurable retention period (default 24 hours), independent of read/unread state.
- Raises Server Operations persistence schema from `2` to `3` for closed-ticket timestamp/reason and retention settings. Legacy schema-2 threaded tickets continue to normalize safely.
- Compacts Wallet & Transactions by roughly 20%, shortens Search, labels payment Player/Amount fields, adds a server-paged known-player picker that searches the merged permission/economy/online identity set, and repositions transaction details/loading text to avoid overlap.
- Compacts Profile by roughly 20%, moves Choose title beside the selected title with safe spacing, and removes the redundant minimap/title-help lines.
- Adds the first dedicated **Mines** module foundation, separate from generic Regions while reusing SSU's bounded jobs/permissions/storage patterns: persistent mine definitions, admin/player GUIs, two-corner Mine Setup Tool, per-mine access permission, weighted reset palette, manual/timed/mined-threshold reset triggers, countdown warnings, safe empty-only or player-evacuation reset handling, remaining/mined progress, teleport spawn/exit, bounded reset jobs and basic use/reset counters. Mine definition schema starts at `1`.
- The first Mines phase deliberately does **not** yet claim the full advanced roadmap: slot-based palette authoring, custom/no-drop and XP/Fortune/Silk rules, integrated mine holograms and richer dedicated mine statistics remain follow-up work.
- Adds Mines to configuration-profile data and exposes `ssu.mines.use`, `ssu.mines.admin` plus per-mine `ssu.mines.<id>.use` keys.
- Raises network protocol from `95` to `96` for the Mines payloads.

# Simple Server Utilities 1.9.0-dev3.10.1

Compile hotfix built on dev3.10.

- Added the missing `ServerOperationsScreen#formatTicketTime(long)` helper used by the threaded Support/Reports conversation renderer.
- Ticket timestamps now render through the existing `TICKET_TIME` formatter (`dd/MM HH:mm`) and safely fall back to `-` for missing/non-positive timestamps.
- No network payload, persistence/schema, ticket workflow, rich-text or gameplay behavior changes.
- Network protocol remains `95`; Server Operations schema remains `2`.

# Simple Server Utilities 1.9.0-dev3.9.1

Compile hotfix built on dev3.9.

- Fixed Minecraft 26.2 `ServerChatEvent#getRawText()` handling: the event already returns a `String`, so the invalid extra `.getString()` call was removed.
- Fixed scheduler task creation compilation by replacing the lambda capture of a reassigned local task ID with a direct duplicate-ID loop.
- No network payload, storage/schema, gameplay or Server Operations behavior changes beyond these compile corrections.
- Network protocol remains `94`; Server Operations schema remains `1`.

# Simple Server Utilities 1.9.0-dev3.9

## Performance-first public-server operations

- Adds `Server Operations` to Admin Center and `Support` to the player dashboard.
- Adds an intentionally lightweight player activity log for block break/place only. Records are memory-bounded and batch-written to JSONL; minigame/dungeon activity is ignored. Rollback is capped at 5,000 matching changes, runs in bounded batches and restores only the recorded block type's default state when the current block still matches the recorded post-change block.
- Adds manual world ZIP backups plus optional automatic backups, retention, progress/status, last-backup protection and staged restore. Backup creation flushes SSU storage and best-effort world saving first; restore stops the server, retains a pre-restore world safety directory and attempts rollback if extraction fails.
- Adds Scheduler tasks with `INTERVAL`, `DAILY` (`daily@HH:mm`) and `ONCE` (`once@yyyy-MM-ddTHH:mm`) schedules. Supported actions are `BACKUP`, `BROADCAST`, `MAINTENANCE_ON`, `MAINTENANCE_OFF`, `SAVE_SSU`, `SSU_RELOAD` and `STOP_SERVER`. Automatic backups are represented by a protected system scheduler task.
- Adds Maintenance Mode with configurable disconnect text, optional kick of current players and `ssu.maintenance.bypass`.
- Adds opt-in chat moderation: permanent/temporary mute, slow mode, duplicate suppression, burst/flood limit, caps threshold, link policy, blocked phrases, capped memory-only recent chat and permission-gated `#` staff chat. `ssu.chat.moderation.bypass` bypasses automatic filters but never an explicit mute.
- Adds a persistent bounded staff-audit JSONL stream and integrates high-value Server Operations, dashboard permission/rank/economy changes, moderation, live/offline inventory edits, kits, managed dimensions, onboarding, region changes/resets and minigame arena administration.
- Adds a lightweight Health page using the existing `SsuPerformanceMonitor` for TPS/MSPT, heap, online players, active jobs, permission/cache metrics, region lookups and top module timings, avoiding a second continuous profiler.
- Adds player Support/Report tickets with a maximum of three open tickets per player and admin assignment, staff notes, resolve/reopen/close workflow.
- Adds per-dimension world-border editing and one bounded chunk-pregeneration job at a time. Pregeneration is limited to 1–4 chunks/tick and automatically pauses above the configured MSPT limit.
- Reuses and improves the existing Player Info & Profile permission table instead of creating a duplicate Effective Permission Inspector. Permission rows now show the winning personal override/wildcard or rank inheritance path and matched key.
- Adds on-demand Economy analytics without duplicating Economy Admin: total supply/accounts, loaded transactions, 24h loaded volume, richest accounts, loaded volume by type and configurable large-transaction alerts.
- Adds configuration-only profile ZIP export/import for ranks/permissions, dimensions, spawns/onboarding, moderation settings, kits, region definitions/templates, economy/Auction/claim-tax settings, titles, holograms, statistics, NPC definitions, quests, minigames, dungeons, visualization and Server Operations settings/tasks. Player balances, mail, moderation player records/inventories, progression and other player-owned data are intentionally excluded.
- Every configuration-profile import first creates a persistent `pre-import-*` safety profile before copying/reloading configuration.
- Adds permissions `ssu.server_operations.admin`, `ssu.maintenance.bypass`, `ssu.chat.moderation.bypass`, `ssu.chat.staff` and `ssu.reports.use`, plus their appropriate wildcard catalogue entries.
- Automatic backups and automatic chat filtering default to OFF; the lightweight block activity log defaults to ON with a 20,000-entry / 14-day cap.
- Raises network protocol from `93` to `94` for the new Server Operations payloads.
- Adds Server Operations persistence schema `1`; all previously existing storage/schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.8

## GUI-first completion, minigame polish and Region Tool workflow

- Dynamically exposes every configured kit permission key in the rank/player Permission Editor, even before that permission has been assigned. Built-in kit access/admin permissions and the `ssu.kits.*` wildcard are also in the catalogue. Conventional stale kit keys remain visible with an explicit obsolete-key description so admins can unset them cleanly.
- Reworks the Minigame Results table to use fixed shared column anchors for headers and row values, keeping player/team/role/combat/objective/impact statistics aligned.
- Adds a permanent `Decline & leave` control to mandatory onboarding with a client-side confirmation step. Declining disconnects the player without accepting rules or completing onboarding, so the flow restarts on the next join.
- Fixes Spleef temporary projectiles under the global minigame inventory lock. Authorized grants update the lock baseline, and vanilla projectile consumption is accepted on the next server tick before lock enforcement so Power projectiles remain finite and usable.
- Adds `Restore snapshot` to the Minigame Setup Tool. The server only schedules the existing bounded arena-reset job when the selected arena has no active match/reservation, is not already resetting, and has a valid saved arena snapshot.
- Clarifies the Region Setup Tool with shorter task-oriented tabs (`Region`, `Protection`, `Access & rent`, `Auto reset`, `Selection`, `Browse`), more explicit teleport/spawn/bounds labels, clearer selection build/create actions, and distinct wording for per-region reset snapshots versus portable selection snapshots.
- Renames the generic bottom action to `Save settings` so editable region pages more clearly communicate when changes are persisted.
- Network protocol remains `93`; all storage and schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.7.1

Compile hotfix built on dev3.7.

- Updated newly-added Minecraft 26.2 server access from removed `ServerPlayer#getServer()` calls to the existing `player.level().getServer()` pattern.
- Updated vanilla/shared spawn lookups from removed `getSharedSpawnPos()` to the current respawn-data API (`getRespawnData()` + world-border adjustment).
- Fixed warning/onboarding sound delivery to use the current sound packet API. The moderation Call horn is now sent only to the warned player.
- No network payload, storage, schema, gameplay-balance, onboarding-flow, moderation-policy, jail, inventory-admin or kit behavior changes beyond these API/compile corrections.
- Network protocol remains 93.

# Simple Server Utilities 1.9.0-dev3.7

## Dimension-aware spawns, onboarding, moderation, jail, live inventories and kits

- Adds a safe `Teleport` action to the Dimensions manager for every selected dimension that is currently loaded.
- Upgrades the persistent SSU location file to schema `2`, retaining one server-wide spawn and adding one first-join Lobby spawn. Both locations may be stored in any loaded dimension.
- Adds dimension-aware death fallback: valid personal bed/respawn-anchor destinations remain first, SSU Server Spawn is used when no valid personal destination exists, and vanilla Overworld spawn remains the final fallback.
- Adds configurable first-join onboarding with Lobby teleport, welcome title/firework presentation, a delayed SSU-key prompt, rich-text Rules with two-step acceptance, and a compact optional/skippable rich-text introduction.
- Locks movement, inventory containers, commands, combat, item transfer and all normal world interactions until onboarding is completed. Pressing the SSU menu key opens the onboarding flow instead of the normal dashboard.
- Adds `Onboarding & Spawns` administration for enabling the flow, setting/clearing Server Spawn and Lobby Spawn, editing rich-text Rules and introduction pages, and resetting/completing individual player onboarding states.
- Adds `Manage` to Player Info & Profile with rich-text warnings, kicks, temporary/permanent bans, unban, freeze/unfreeze, custom whitelist administration, moderation history and recorded name changes.
- Warning presentation uses a large title/subtitle for a configurable duration and the vanilla Call goat horn.
- Adds a Jail dashboard and persistent jail sentences with optional time, economy buyout or a community mining task in a configured region.
- Community tasks support up to sixteen block requirements and nine configured tools. Jail tools are continuously restored, kept effectively unbreakable and cannot be moved or retained outside their assigned slots; mined task blocks are counted virtually instead of entering the prisoner's inventory.
- Completing a community task distributes the configured block contribution through system mail among players seen within the configured lookback period, restores the prisoner's original inventory and releases them to onboarding or Server Spawn as appropriate.
- Adds live online/offline administration of player inventory, armor/offhand and ender chest, while retaining a fully usable administrator inventory in the same container screen. Offline changes are applied on the player's next login.
- Adds permission-aware nine-slot Kits with exact item stacks, previewable contents, cooldown, price, one-time claim, enabled/locked state and a configurable per-kit permission key.
- Adds player Kits to the dashboard and compact Kit Administration with a ghost inventory editor backed by the administrator inventory.
- Adds dedicated `KIT_PURCHASE` and `JAIL_BUYOUT` economy transaction types.
- Raises the network protocol from `92` to `93` for the new payloads and menus. Existing Player Claim, Region, Minigame, UI preference, Title and Player Identity schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.6.5

## Minigame capture HUD spacing

- Moves the shared CTF/Domination cast instruction (`Do not move, attack, use items, or take damage.`) out of the vanilla action bar and into the custom minigame cast HUD.
- Renders the instruction as its own centered line 16 logical pixels above the existing capture label, while leaving the capture label and progress bar at their previous positions.
- Applies the same layout to taking an enemy CTF flag and claiming a Domination base.
- Raises the network protocol from `91` to `92` because `MinigameCastBarPayload` now carries a bounded instruction line.
- All storage, snapshot and schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.6.4

## Team-specific objective capture sounds

- Capture the Flag now plays the vanilla Ponder goat horn (`minecraft:item.goat_horn.sound.0`) only for the team that successfully returns the enemy flag and scores.
- The opposing CTF team receives the non-horn loss cue `minecraft:block.beacon.deactivate` instead.
- Domination now uses the same Ponder team celebration whenever a base finishes capturing.
- Every opposing Domination player receives the same non-horn loss cue, including when the captured base was previously neutral.
- Existing sounds for starting a flag theft, starting a Domination claim and defending/interruption events remain unchanged.
- Network protocol remains `91`; all storage, snapshot and schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.6.3

- Removes the duplicate `Create region from selection` button from All regions.
- Makes `Save full snapshot` react immediately to the typed name and shows live validation feedback.
- Reflows the snapshot page so explanations, fields and buttons no longer overlap.
- Moves ghost-preview controls into a minimal world-visible placement screen without a background panel.
- Adds Free mode for walking/flying around the stationary preview; left-click returns to edit controls.
- Blocks world interactions, attacks, block edits, item pickup/drop, containers and commands while a preview is active.
- Safely clears preview state on cancel, placement, logout, death or dimension change.
- Network protocol remains `91`; all storage and schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.6.2

## Compact Region Setup layout and direct region selection

- Reduces the Region Setup Tool from 840×480 to 690×400 logical pixels and compacts all six pages, controls, lists and footers into the smaller footprint.
- Reorganizes labels, status text, action rows, block-mix editors, inventory grids, snapshot controls and region rows so explanatory text no longer renders behind buttons or input fields.
- Changes reset/selection block mixes to a compact two-column layout and reduces snapshot/region rows per page where required to preserve readability.
- Adds `Select region` / `Unselect region` to the General page of an existing region. Selecting copies the exact region bounds and dimension into the active Region Tool selection and immediately shows its selection border.
- Unselecting clears the active Region Tool selection and hides its border. The action is server-authoritative and works for remotely opened regions as well.
- Network protocol remains `91`; all storage, snapshot and schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.6.1

## Compile hotfix

- Fixes the claim-role Permission Editor lambda capture in `SsuMenuService` by resolving the selected role into one final value before stream lookups.
- Removes two stale `@Override operationCount()` methods from full selection snapshot capture/paste jobs; `SsuJob` does not define that method and the counters are not used by the scheduler.
- Network protocol and all storage/schema versions remain unchanged.

# Simple Server Utilities 1.9.0-dev3.6

## Unified Region Setup Tool, remote administration and full selection snapshots

- Removes the duplicate `Set point 1/2 to current position` controls from Region Setup. Selection corners remain an in-world Region Tool action.
- Keeps automatic detection of the region at the administrator's position while adding an `All regions` browser for remote editing.
- Adds `Teleport to region` from both the active editor and remote region browser. Configured region spawns are preferred; otherwise SSU searches the region and its immediate upper area for a safe destination.
- Adds a permanent Selection tab to Region Setup with current point/dimension/volume status, Create region from selection, Clear selection points, Clear to air, Fill water, Fill lava and weighted inventory block mixes.
- Adds portable full selection snapshots in format `1`. These preserve block states, block-entity NBT/inventories and structural entities such as item frames, paintings and armor stands.
- Adds a non-destructive ghost preview workflow for full selection snapshots. A loaded snapshot appears five blocks in front of the administrator and can be moved on all three axes, rotated, mirrored, cancelled or confirmed before server-authoritative placement.
- Bounds preview packets to 4,096 sampled non-air blocks while keeping the eventual placement complete and exact.
- Updates the active Region Tool selection to the placed snapshot bounds after confirmation.
- Clears server/client preview state on screen close and logout.
- Raises the network protocol from `90` to `91`. Region storage remains schema `5`; Player Claim remains `3`; Minigame definition/recovery/progression/match-history remain `19/4/3/1`; Player UI preferences remain `11`; Title catalogue and Player Identity remain `1`.

# Simple Server Utilities 1.9.0-dev3.5

## GUI completion and Player Claim access roles

- Adds Rank Management controls for editing priority and adding/removing inherited parent ranks, including cycle-safe server validation.
- Adds non-destructive Add/Remove rank controls to the player Permission Editor so one player can hold multiple ranks.
- Adds a Claim roles mode to the Permission Editor for server-wide claim-role defaults.
- Replaces the simple trusted-player list with explicit Member and Co-owner assignments. Legacy trusted players migrate to Member.
- Adds per-claim role permission overrides directly under Claim Settings > Claim access > Manage. Co-owner, Member and Visitor permissions can inherit the server default or be overridden for one claim.
- Protects block placement/breaking, item frames and armor stands, containers, doors/trapdoors/fence gates, buttons/levers/pressure plates, item pickup/drop, claim homes, living-entity damage and living-entity interaction through the new claim-role context.
- Keeps Claim Settings strictly owner-only. Co-owners receive only the in-claim actions granted to their role.
- Exposes player/admin Region Rental cancellation refund percentages in the Rent Journal GUI.
- Adds live minigame score Add/Set controls to Minigame Administration using the existing server-authoritative score action.
- Extends Travel with read-only shared claim homes when the active claim role permits their use.
- Raises network protocol from `89` to `90` and Player Claim storage schema from `2` to `3`. Region schema remains `5`; Minigame definition/recovery/progression/match-history remain `19/4/3/1`; Player UI preferences remain `11`; Title catalogue and Player Identity remain `1`.

# Simple Server Utilities 1.9.0-dev3.4

## Damage Indicator Drop style and GUI-first Region Setup Tool

- Removes the exclamation mark from Pop indicators and square brackets from Burst indicators.
- Adds the `Drop` Damage Indicator style: the value spawns tightly from the affected entity, briefly pops upward and then falls downward.
- Replaces the Region Tool's fragmented right-click workflow with a full Region Setup Tool that edits the region at the player's position or guides creation from a two-point selection when no region is present.
- Integrates region identity, priority, borders, welcome/leave messages, spawn, redefine/delete, all protection flags, context permission overrides, rental configuration and manager/member access into the same editor.
- Adds persistent scheduled resets per region. Administrators can select a saved snapshot or a weighted inventory-built block preset, configure the interval and optionally postpone resets while players remain inside.
- Adds immediate snapshot capture and Reset now actions. All capture/reset/fill operations use bounded SSU jobs and existing region resource locks.
- Adds a region overload to the existing weighted-fill system and treats percentages below 100% as air, preserving the established inventory-preset workflow.
- Makes CREATE submissions transactional by validating rent, interval and inventory preset data before creating the region.
- Raises network protocol from `88` to `89` and region storage schema from `4` to `5`. Minigame definition/recovery/progression/match-history remain `19/4/3/1`; Player UI preferences remain `11`; Title catalogue and Player Identity remain `1`.

# Simple Server Utilities 1.9.0-dev3.3.3

- Damage/healing indicators are approximately twice as large, remain fully opaque for most of their lifetime and now offer five styles: Floating, Hearts, Compact, Pop and Burst.
- Player titles no longer render for the local player and use partial-tick entity interpolation so they move smoothly with remote players.
- Immutable match inventories are now a definition-level default for every minigame. CTF, Domination, Spleef and Generic editors expose an `Inventory lock` exception toggle; when enabled, inventory slots, armor, offhand and cursor stacks are restored server-side every tick. CTF flag banners remain an intentional temporary helmet replacement.
- Minigame definition schema increases from `18` to `19`. Network protocol remains `88`; all other schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.3.2

## Rank/title GUI and chat-format hotfix

- Replaces the sixteen plain colour-name buttons in the Rank Prefix Editor with real colour swatches while keeping each palette name readable with automatic contrasting text.
- Shortens the Rank Management rename field to approximately half its former width, moves Title Manager and Refresh beside it without overlap, and clarifies that the field supplies the new name used by the row-level Rename button.
- Doubles the administrator Title display-name field width and reorganizes the Title Administration controls and preview so the larger field remains clear and non-overlapping.
- Fixes duplicated player names in chat by cancelling the vanilla wrapped chat line and broadcasting one complete server-authoritative SSU line in the form `RankPrefix PlayerName: message`.
- Ensures exactly one normal space separates the styled rank prefix from the unstyled player name in chat.
- Network protocol remains `88`; Player UI preference schema remains `11`; Title catalogue and Player Identity schemas remain `1`; Minigame definition/recovery/progression/match-history remain `18/4/3/1`.

# Simple Server Utilities 1.9.0-dev3.3

## Global titles, styled rank prefixes and damage indicators

- Changes the Tank Defensive Field/AOE impact sound to the vanilla lightning-bolt impact sound for a stronger, more lively ability cue.
- Promotes minigame titles into a global player-identity system. Players now select their active title from the normal SSU Profile instead of the Minigame Profile.
- Adds a server-owned Title Administration screen. Administrators can create, edit, enable/disable and delete titles, choose one of the fixed sixteen Minecraft colours, and define acquisition through free access, minigame level, minigame wins, rank, permission or manual grant.
- Migrates the original Rookie, Contender, Veteran, Champion, Elite and Legend title progression into the global catalogue and migrates each player's former minigame title selection on first load.
- Adds personal Settings toggles for overhead title visibility and overhead rank-prefix visibility.
- Adds a rich Rank Prefix editor to Rank Management using the same hidden formatting model as Mail and Floating Text, including multi-colour text and Bold/Italic/Underline/Strikethrough across the fixed sixteen-colour palette.
- Shows the styled primary-rank prefix before the normal unstyled player name in overhead nameplates and chat. Disabling overhead rank visibility does not remove the authoritative chat prefix.
- Adds damage/healing indicators around living entities with per-player enablement and three display styles: Floating, Hearts and Compact. Damage is red and healing is green.
- Adds permission key `ssu.damage_indicators.use`, with a default fallback of `true`.
- Network protocol increases from `87` to `88`.
- Player UI preference schema increases from `10` to `11`; the new title catalogue and per-player identity records use schema `1`. Minigame definition/recovery/progression/match-history schemas remain `18/4/3/1`.

# Simple Server Utilities 1.9.0-dev3.2.1

## Minigame manager API compile hotfix

- Restores eight established `MinigameManager` runtime methods that were accidentally deleted together with the obsolete Ready-check block in dev3.2: `matchView`, `finishMatch`, `addScore`, `setScore`, `eliminate`, `onPlayerDeath`, `onPlayerRespawn` and `onLogin`.
- Fixes the resulting unresolved-method errors in `TeleportPolicy.java`, `MinigameEvents.java`, `MinigameCommands.java` and `MinigameManager.java` itself.
- Keeps the dev3.2 preparation-only startup flow intact; no Ready/Unready state or ready timer has been reintroduced.
- Network protocol remains `87`. Minigame definition schema remains `18`; recovery remains `4`; progression remains `3`; match-history remains `1`; Player UI preference schema remains `10`.

# Simple Server Utilities 1.9.0-dev3.2

## Minigame progression, preparation countdown and in-match overview

- Adds administrator-configurable weekly cosmetic challenge settings to every minigame definition: challenge enablement, matches required/reward XP, wins required/reward XP and contribution required/reward XP.
- Keeps the weekly counters shared per player while applying the active minigame definition's configured thresholds and rewards. Disabling weekly challenges stops both progress and weekly XP for that minigame.
- Removes the Ready/Unready button, ready player counter, ready-check setting and maximum ready wait. A preparing match now starts automatically when its configured preparation time reaches zero, provided the minimum player composition remains valid.
- Displays the final ten preparation seconds as large center-screen numbers with a synchronized sound on every second, and displays `GO!` when the match enters RUNNING.
- Extends important end/cancellation messages to five seconds and duplicates the reason in chat so opponent-left, forfeit, cancellation, draw and overtime outcomes remain readable.
- Routes the `U` key through a server-authoritative match check. Active participants and spectators receive a detailed current-match screen with phase/time, team scores, roster/statistics, objectives, status, rules and a confirmed Leave match action; players outside a match still open the normal SSU dashboard.
- Fixes the Victory effect section title overlapping the effect selector in the Minigame Profile.
- Replaces the former mining-outline colour cycle with the fixed sixteen-colour Minecraft palette and migrates existing Treecapitator/Veinminer colours to their nearest equivalent.
- Network protocol increases from `86` to `87`.
- Minigame definition schema increases from `17` to `18`; Player UI preference schema increases from `9` to `10`; recovery remains `4`, progression remains `3`, and match-history remains `1`.

# Simple Server Utilities 1.9.0-dev3.1

## MVP validator, boosts, borders and dashboard polish

- Arena validation now permits lobby locations, spectator spawns and spectator bounds outside the playable arena region. Spectator bounds are still checked for a valid dimension.
- Manual boost validation no longer treats a boost as a player spawn, and temporary End Rod setup markers are treated as empty space. Boosts still require their real position to be inside the arena, free above, and supported by a solid floor.
- The validation report now explains that errors block readiness while warnings are advisory.
- Adds the supplied `questbook.png` to the Questbook dashboard tile and restores `portal.png` to My Warps.
- Changes the minigame scoreboard/HUD keybind from `L` to `J` so it no longer conflicts with Advancements.
- Reworks Tank Defensive Field knockback into an explicit radial server velocity with a direct motion packet to the affected enemy player, while retaining enemy-only targeting, Slowness and the configured strength.
- Adds administrator-configurable Regeneration Boost healing per second. The default is `2.0` health points per second; the supported range is `0.1` to `40.0`.
- Compacts the CTF and Domination Boosts tabs, removes the former right-side explanatory paragraphs, and adds a dedicated regeneration-heal field without overlapping controls.
- Adds clear labels and concise descriptions to all six Match Flow values and all four Progression & Integration values.
- Makes the Minigame border and Spectator border toggles apply to both active-match overlays and Setup Tool overlays immediately.
- Displays Treecapitator and Veinminer outline colors by readable names instead of raw hexadecimal values, while preserving RGB storage internally.
- Network protocol increases from `85` to `86`.
- Minigame definition schema increases from `16` to `17`; recovery schema remains `4`; progression schema remains `3`; match-history schema remains `1`; Player UI preference schema remains `9`.

# Simple Server Utilities 1.9.0-dev3.0.2

- Fixed system/minigame reward money claims that could fail with `Insufficient funds` when a legacy or interrupted system-mail escrow deposit was missing. Only server-generated SYSTEM, MINIGAME and RECOVERY mail can use the idempotent repair path; player and Auction House mail remain strictly escrow-backed.
- Game and spectator borders now use the same stroke width, translucent fill and depth-aware region-box rendering style as normal region borders, both during setup and in live matches.
- Reduced the maximum Mailbox dimensions from 900x500 to 675x375 and the Auction House from 920x510 to 690x384 while keeping both centered.
- Added the supplied claim-land, travel, wallet, mail and minigame dashboard icons and render dashboard tile icons at approximately twice their previous size.
- Network protocol remains 85; all storage and definition schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev3.0.1

## Minecraft 26.2 results, HUD and runtime-border compile hotfix

- Replaces both remaining `Minecraft#setScreen(null)` calls in the results payload handler and `MinigameResultsScreen#onClose` with the project-supported Minecraft 26.2 `setScreenAndShow(null)` API.
- Replaces the removed `Options.hideGui` field in the minigame kill-feed renderer with `minecraft.gui.hud.isHidden()`.
- Corrects the active-match reconnect path to call the existing `syncRuntimeBorders(ServerPlayer)` method instead of the undefined `sendRuntimeBorders(ServerPlayer)` name.
- Scans the full Java tree for the removed screen/HUD APIs and the stale runtime-border method name; no occurrences remain.
- Network protocol remains `85`. Minigame definition schema remains `16`; recovery schema remains `4`; progression schema remains `3`; match-history schema remains `1`; Player UI preference schema remains `9`.

# Simple Server Utilities 1.9.0-dev3.0

## Minigame Experience, validation, diagnostics and transactional progression

- Adds a complete post-match experience layer with detailed results, compact kill/objective feed, three scoreboard HUD modes, reconnect grace, AFK handling, ready checks, overtime, post-game voting and spectator target cycling.
- Adds rating- and preferred-role-aware team balancing while preserving configured role minimums and maximums.
- Adds a Minigame Profile with cosmetic-only XP, levels, per-game ratings, badges, selectable titles, victory effects and bounded weekly challenges.
- Adds an Arena Validator with errors/warnings, issue teleporting, safe arena cloning and bounded JSON export/import.
- Adds Minigame System Health diagnostics, integrity checks and conservative orphan cleanup.
- Introduces a shared safe ability definition/effect foundation for future role spells without commands or arbitrary scripts.
- Publishes minigame events and custom statistics for quest, hologram and other existing SSU integrations; existing participation and winner reward packages remain the authoritative reward path.
- Fixes settlement ordering: result-screen XP is preview-only until every configured reward is committed. Progression, ratings, weekly challenges and match history are then applied once, stored with a bounded match-settlement ledger, and both progression/history files are flushed and byte-verified before cleanup continues.
- Match-summary mail is delivered only after the durable settlement and uses an idempotent correlation key. A partial mail failure pauses cleanup and safely retries without duplicating rewards, progression or previously delivered summaries.
- Non-transactional statistic and level-up events are emitted at most once per live settlement after durable progression storage.
- Network protocol increases from `84` to `85` for the new result, profile, diagnostics, validation, spectator and kill-feed payloads.
- Minigame definition schema increases from `15` to `16`; Minigame recovery schema remains `4`; Minigame progression schema is `3`; match-history schema is `1`; Player UI preference schema remains `9`.

# Simple Server Utilities 1.9.0-dev2.9.3.1

## Minecraft 26.2 Tank knockback compile hotfix

- Updates the Tank Defensive Field to Minecraft 26.2's five-argument `LivingEntity#knockback` signature.
- Supplies the Tank's player-attack `DamageSource` as knockback context while keeping the ability damage-free with `0.0F` damage.
- Preserves the configured radial strength, enemy-only targeting, knockback resistance handling and existing Slowness effect.
- Scans the complete source tree for other legacy three-argument `knockback(...)` calls; none remain.
- Network protocol remains `84`. Minigame definition schema remains `15`; Minigame recovery schema remains `4`; Player UI preference schema remains `9`.

# Simple Server Utilities 1.9.0-dev2.9.3

## Delayed team-mode respawns and healer-controlled sustain

- Adds an administrator-configurable respawn delay to Capture the Flag and Domination. The allowed range is `1` to `300` seconds and the default is `5` seconds.
- Defeated players enter spectator mode at the configured arena spectator location and receive a large center-screen `Respawning` countdown.
- The respawn destination is chosen when the player is defeated: CTF uses a team spawn, while Domination retains the controlled-node respawn logic.
- Pending respawns are excluded from active-player checks, combat, role targeting, objective casts, boost pickup and arena-return enforcement.
- CTF carriers drop their flag before entering the respawn delay, and active objective casts are interrupted safely.
- Keeps the food bar visually full while canceling `LivingHealEvent` for active CTF and Domination participants, eliminating vanilla hunger-based natural regeneration.
- Food use remains unavailable through the protected, server-owned minigame loadout.
- Healer abilities continue to use controlled direct health updates. Regeneration boosts now use their own controlled one-health healing pulse every `50` ticks for the configured duration while retaining the vanilla regeneration visual effect.
- Match cleanup clears pending countdown titles and controlled regeneration runtime state.
- Network protocol increases from `83` to `84`. Minigame definition schema increases from `14` to `15`; Minigame recovery schema remains `4`; Player UI preference schema remains `9`.

# Simple Server Utilities 1.9.0-dev2.9.2

## Single minigame queue action and Tank defensive knockback

- Replaces the overlapping global and selected-game queue controls with one dynamic primary button in the Minigame Lobby.
- The button reads **Join queue** when the player is free, **Leave queue** while queued, and **Leave match** during COUNTDOWN or RUNNING.
- Joining always targets the currently selected minigame; leaving is global and does not depend on which game is selected in the list.
- Preferred-role controls remain available only before joining a queue and are hidden while the player is queued or in a match.
- Adds a defensive server-side consistency check that rejects queue joins when the player already exists in any concrete minigame queue, even if the queue index ever became stale.
- Extends the Tank Defensive Field with enemy-only radial knockback in addition to Slowness.
- Adds an administrator setting for Tank knockback strength in both the Capture the Flag and Domination role editors. The allowed range is `0.0` to `5.0`, defaults to `1.0`, and `0` disables the push while retaining the slow.
- Network protocol remains `83`. Minigame definition schema increases from `13` to `14`; Minigame recovery schema remains `4`; Player UI preference schema remains `9`.

# Simple Server Utilities 1.9.0-dev2.9.1

## Minigame arena block-placement hotfix

- Fixes **Edit arena blocks** allowing block breaking but rejecting every attempted block placement with `You cannot interact with blocks here.`
- Recognizes the `RightClickBlock` stage that precedes Minecraft's actual `EntityPlaceEvent` when an administrator holds a `BlockItem` inside the selected idle arena.
- Suppresses the clicked block's own interaction during that placement attempt, so containers, doors, buttons and similar blocks are not opened or activated as a side effect.
- Keeps the final placement authorization in `EntityPlaceEvent`; blocks outside the selected arena and arenas with active queues or matches remain protected.
- Network protocol remains `83`. Minigame definition schema remains `13`, Minigame recovery schema remains `4`, and Player UI preference schema remains `9`.

# Simple Server Utilities 1.9.0-dev2.9

## Open preparation matches, role polish, mining HUD and compact Auction House

- CTF and Domination role abilities now activate whenever their individual cooldown is ready, even when no valid target is found. A missed or empty activation still consumes its cooldown.
- Tank Defensive Field radius and Healer AOE Heal radius are separately configurable from 1 to 16 blocks and default to 3 blocks.
- DPS bow use is explicitly permitted through the protected minigame interaction pipeline. Its named arrow is replenished server-side and the configured effect remains enemy-only.
- COUNTDOWN is now an open preparation stage: additional players may join an existing preparing match until its configured maximum is reached. RUNNING matches remain closed.
- The minigame lobby now exposes a global Leave queue / Leave match action without requiring the active game entry to be selected first.
- Added a client keybind, default `L`, to show or hide the active minigame scoreboard HUD.
- Role abilities received clearer glinting ability items, denser particles and louder spatial sounds.
- Temporary role weapons, abilities, cosmetic team armor and the Tank shield are server-owned and restored to their fixed slots when a player attempts to move them.
- Treecapitator and Veinminer previews can now show a two-line target panel below the crosshair with the active outline color, selected block name, block count and a translucent black background.
- Added independent personal settings for the Treecapitator information panel and Veinminer information panel. Both default to enabled.
- The Auction House main window is smaller and remains centered. Its lower frames align, the obsolete admin-only Sale tax label is removed, and the search area plus right-side controls are compacted.
- Network protocol: `83`.
- Minigame definition schema: `13`.
- Minigame recovery schema: `4`.
- Player UI preference schema: `9`.

# Simple Server Utilities 1.9.0-dev2.8.2

## Runtime objective labels and player-controlled minigame borders

- Adds a fitted semi-transparent black background behind live Domination node labels and capture timers. The background shares the objective text's camera-facing billboard plane and always-on-top behavior.
- Reuses the existing claim/region border payload and renderer for two independent active-match layers: the arena game region and the configured spectator bounds.
- Adds **Minigame border** and **Spectator border** toggles to the player Borders settings. Both are enabled when schema-3 preferences migrate to player border preference schema 4.
- Adds dedicated `MINIGAME_GAME_AREA` and `MINIGAME_SPECTATOR_AREA` color categories, defaulting to cyan and purple.
- Runtime border payloads are cached per player and refreshed only when the match, dimension, preference or border settings revision changes. They are cleared immediately when the player leaves or is returned from the match.
- Increases network protocol from **81** to **82** because the dashboard snapshot and border-layer enum changed. Minigame definition schema remains **12** and Minigame recovery schema remains **4**.

# Simple Server Utilities 1.9.0-dev2.8.1

## Minecraft 26.2 role compile and objective-cast interaction hotfix

- Fixes the leather cosmetic armor color component by supplying Minecraft 26.2's required `DyedItemColor` value instead of a raw integer.
- Updates the moved Minecraft 26.2 `AbstractArrow` import to `net.minecraft.world.entity.projectile.arrow.AbstractArrow`, restoring DPS special-arrow impact handling.
- Removes the unavailable `Blocks.WHITE_BANNER` fallback and safely falls back to white dye color when a configured team banner block cannot be resolved.
- Fixes CTF and Domination objective casts being canceled immediately by the continuation stages of the same physical right-click that started them.
- Main-hand objective activation is now processed before generic action interruption only when no cast is active. The same-tick offhand/item/entity continuation is consumed without canceling the newly created cast.
- Any later right-click, attack, block action, item use, entity interaction, movement or incoming damage still interrupts the active cast and cancels the triggering action.
- Network protocol remains **81**. Minigame definition schema remains **12**. Minigame recovery schema remains **4**.

# Simple Server Utilities 1.9.0-dev2.8

## Optional tactical roles for Capture the Flag and Domination

- Adds an optional DPS, Tank and Healer role system to Capture the Flag and Domination while preserving the existing non-role loadouts when the feature is disabled.
- Players select a preferred role before joining the queue. Final roles are assigned server-side per team: configured minimums are satisfied first, preferences are honored where possible and configured maxima are never exceeded.
- Adds administrator-configurable per-team minimum and maximum counts for every role. Minimums may be zero; role-composition validation prevents impossible minimum-player and maximum-player settings.
- Adds configurable base maximum health, armor and armor toughness for every role. All CTF and Domination players wear a full team-colored leather set with its item armor modifiers removed, so the visible armor is cosmetic and the real combat values come from the assigned role.
- DPS receives a Diamond Sword, Bow and automatically replenished special arrow. Its enemy hit effect, level and duration are configurable; the default is Poison I.
- Tank receives a Stone Sword, team-colored logo Shield and a cooldown-visible Defensive Field item. The field applies Slowness I only to active enemy players inside a true two-block radius for the configured duration.
- Healer receives a Stone Sword, an eight-block straight single-target healing beam, a weaker four-block ally AOE heal and a self-heal that instantly restores 25% of maximum health. Heal values and all three cooldowns are configurable.
- Uses native item cooldown overlays for Tank and Healer ability items, server-authoritative target/team validation, match-only particles and sounds, and protected interaction routing so abilities and the Tank shield cannot be used to interact with arena blocks or entities.
- Removes durability wear from the temporary role weapons, Bow, Shield and cosmetic leather so a long match cannot destroy part of a role loadout.
- Rebalances assigned roles if someone leaves during the countdown, preserves preferred roles when a cancelled countdown returns players to the queue and supports safe role assignment for any future late-join-enabled two-team definition.
- Extends recovery state with original maximum-health and armor-toughness base values in addition to armor, ensuring role attributes are restored after normal exit, logout or crash recovery.
- Network protocol increases from **80** to **81**. Minigame definition schema increases from **11** to **12**. Minigame recovery schema increases from **3** to **4**.

# Simple Server Utilities 1.9.0-dev2.7.2

## Minigame setup area overlays and strict objective-cast interruption

- Adds independent in-world setup overlays for the selected arena/game border, configured spectator border and configured Spleef playfloor while the administrator holds the Minigame Setup Tool.
- Renders the game border in cyan, spectator bounds in purple and the Spleef floor in amber, with always-visible outlined cuboids and large labeled billboards; spectator bounds and the Spleef floor also receive a subtle translucent fill.
- Uses the selected arena region directly for the game border, so the minigame overview no longer depends on the separate Region border visibility setting.
- Extends the administrator-only setup visual payload with at most three normalized area bounds. The network protocol increases from **79** to **80**; definition schema remains **11** and recovery schema remains **3**.
- Objective casts in CTF and Domination are now canceled before an attempted attack or other gameplay action can execute.
- Attacking entities or blocks, breaking/placing blocks, using items, interacting with blocks/entities and dropping items immediately clears the castbar and consumes/cancels that attempted action.
- Incoming damage and movement continue to interrupt casts, while a server tick fallback also catches an active item-use state.

# Simple Server Utilities 1.9.0-dev2.7.1

## Enlarged minigame setup labels

- Enlarges every Minigame Setup Tool world label to roughly 3.6 times its former size, including lobby, spectator, team/player spawns, CTF flags, Domination nodes and boost spawns.
- Adds one fitted semi-transparent black camera-facing background behind each setup label.
- Keeps the background locked to the same billboard orientation and visual plane as its text, with only a tiny depth bias to prevent flicker.
- Raises the label center slightly so the larger panel remains clearly above the physical setup object.
- Network protocol remains **79**. Minigame definition schema remains **11** and recovery schema remains **3**.

# Simple Server Utilities 1.9.0-dev2.7

## Boost presentation, Spleef projectile polish and physical boost markers

- Changes the built-in boost icons to Golden Boots, Golden Apple, Diamond Chestplate and Rabbit Foot for Speed, Regeneration, temporary Armor and Jump.
- Adds spatial vanilla pickup sounds: firework launch without an explosion for Speed, beacon power-select for Regeneration, diamond armor equip for Armor and wind-charge throw for Jump.
- Applies the native per-item cooldown overlay to the infinite Spleef Snowball and keeps one visible copy in the inventory while the cooldown runs.
- Resynchronizes denied early Snowball clicks so the client cannot show a ghost-empty slot while the server still owns the item.
- Forces both Spleef projectiles onto a gravity-free, zero-spread trajectory aligned exactly with the player's look direction.
- Places temporary End Rods at every configured manual boost spawn while editing CTF or Domination arenas and removes them before match start and snapshot capture.
- Network protocol remains **79**. Minigame definition schema remains **11** and recovery schema remains **3**.

# Simple Server Utilities 1.9.0-dev2.6.2

## Remaining boost editor compile hotfix

- Fixed the remaining `Integer.toString(double)` calls for temporary armor points in both `CaptureTheFlagMinigameEditorScreen` and `DominationMinigameEditorScreen`.
- Both double-backed, integer-edited boost values (`minimumSpacing` and `armorPoints`) are now rounded before being rendered in their integer-only text fields.
- Audited every `Integer.toString(...)` call in both editor classes; all remaining arguments are integer expressions.
- Network protocol remains 79.
- Minigame definition schema remains 11.
- Minigame recovery schema remains 3.

# Simple Server Utilities 1.9.0-dev2.6.1

## Compile hotfix

- Fixed `CaptureTheFlagMinigameEditorScreen` and `DominationMinigameEditorScreen` passing the `double` boost minimum spacing value to `Integer.toString(int)`.
- The editor now rounds the normalized spacing value to the integer format already required by its input field and save parser.
- Network protocol remains 79.
- Minigame definition schema remains 11.
- Minigame recovery schema remains 3.

# Simple Server Utilities 1.9.0-dev2.6

## Spleef projectiles and CTF/Domination boost system

- Adds an optional infinite Spleef Snowball projectile that unlocks after a configurable delay, breaks one configured playfloor block and is protected by a server-authoritative per-player cooldown.
- Adds an optional finite, stackable Power Egg that enters the match after a configurable delay and is awarded one at a time to a random active player at a random configured interval. Its stack is capped and each impact removes a horizontal five-block cross from the configured Spleef floor.
- Adds a shared boost system for Capture the Flag and Domination with manual Setup Tool spawn slots or safe automatic placement. CTF searches random arena ground; Domination searches around linked node respawns.
- Supports configurable maximum simultaneous boosts, initial delay, random respawn range, minimum spacing, allowed boost types, per-type duration and per-type RGB mist color.
- Adds Speed, Regeneration, temporary Armor and Jump boosts. Boost items float without glowing; colored dust creates a soft mist around them and proximity pickup consumes the item.
- Adds a dedicated **Projectiles** page to the Spleef editor and a dedicated **Boosts** page to both the CTF and Domination editors.
- Adds **Set boost spawn** to the Minigame Setup Tool with up to 64 manual locations and administrator-only cyan world labels.
- Validates manual locations, duplicate blocks, arena containment and enabled boost types server-side. Automatic and manual locations require clear feet/head space and sturdy ground.
- Cleans orphan boost entities on startup and persists the player's original base armor value in recovery data so temporary armor can never survive a logout or crash.
- Keeps network protocol **79**. Minigame definition schema increases from **10** to **11**; Minigame recovery schema increases from **2** to **3**.

# Simple Server Utilities 1.9.0-dev2.5.3

- Replaces the separately moving CTF carrier armor-stand banner with vanilla captain-style equipment: the configured banner item is placed directly in the carrier's head equipment slot, exactly like a pillager captain.
- The carried flag is now part of the player entity and therefore follows position, rotation, animation and network interpolation without lagging behind.
- Preserves and restores any temporary match head equipment when the flag is dropped, returned, scored or the match ends.
- Retains the existing team-colored carrier glow and removes the now-unused armor-stand invoker mixin. Legacy orphan carrier stands from older builds are still cleaned on startup.
- Keeps network protocol **79**, Minigame definition schema **10**, and Minigame recovery schema **2** unchanged.

# Simple Server Utilities 1.9.0-dev2.5.2

- Replaces the remaining holographic lobby, spectator, player/team-spawn and Domination-node-respawn cloth previews with actual temporary standing banner blocks. Client setup rendering now supplies only the centered labels above those physical banners.
- Uses green for the lobby, purple for spectators, yellow for Spleef player spawns, configured team banners for CTF/Domination team spawns and orange for linked Domination respawns.
- Removes all temporary setup banners before countdown preparation, before arena snapshot capture, on server startup and during shutdown. Spawn/lobby/spectator coordinates remain unchanged and continue working while the marker blocks are absent.
- Recreates the banners after a completed snapshot only when the same administrator still targets that arena with the Setup Tool. Actual CTF base flags and Domination node banners are explicitly preserved.
- Keeps network protocol **79**, Minigame definition schema **10**, and Minigame recovery schema **2** unchanged.

# Simple Server Utilities 1.9.0-dev2.5.1

## Domination editor compile fix and dropped CTF flags

- Adds the missing shared `ACCENT` GUI color used by the Domination node-respawn label, fixing the unresolved-variable compilation error.
- A CTF carrier who dies now drops the physical configured banner at the death position instead of returning it directly to base.
- A carrier can voluntarily drop the flag by starting to crouch; picking a flag up while already crouched is latched until sneak is released once, preventing an immediate accidental re-drop.
- A teammate can right-click the dropped enemy flag to pick it up immediately without a new cast. The flag's original team can right-click it to return it instantly to base.
- The CTF HUD now reports each flag as at base, carried or dropped, and scoring requires the carrier's own flag to be physically back at base.
- Dropped banner blocks are cleaned on match finish, reload and shutdown; if a safe dropped-banner position cannot be found, the flag falls back to its base rather than becoming lost.
- Keeps network protocol **79**, Minigame definition schema **10**, and Minigame recovery schema **2** unchanged.

# Simple Server Utilities 1.9.0-dev2.4.1

## Minecraft 26.2 CTF compile hotfix

- Replaces orphan CTF back-flag detection through the unavailable `Entity#getTags()` call with a hidden persistent custom-name marker on the temporary armor stand.
- Updates carrier glow teams to Minecraft 26.2's `Optional<TeamColor>` scoreboard API and calculates the nearest vanilla `TeamColor` directly from its RGB value.
- Removes the obsolete `ChatFormatting#getColor()` / `isColor()` path used only by the carrier-glow mapper.
- Keeps network protocol **78**, Minigame definition schema **9**, and Minigame recovery schema **2** unchanged.

# Simple Server Utilities 1.9.0-dev2.4

## Physical CTF flags, carrier cast and team glow

- Places the configured red/blue physical banner blocks before a newly selected CTF arena snapshot is captured, keeps existing configured flags physically visible whenever the Minigame Setup Tool previews an idle arena, and restores them after snapshot resets made by older builds.
- Replaces the old custom square setup flags for lobby, spectator and spawn positions with lightweight cross/vertical guides and labels; CTF flag positions and Domination nodes continue to use their actual banner blocks.
- Replaces instant enemy-flag pickup with a configurable stationary **Flag take cast**. The player must right-click the enemy banner and remain still and within four blocks; movement, leaving the flag/arena or incoming damage interrupts the castbar.
- Plays Sing to the successful carrier's team and Seek to the opposing team when the flag pickup cast completes.
- Removes the old carried-flag cuboid and upward beam renderer. A zero-hitbox invisible marker armor stand now follows behind the carrier and displays the configured real banner item in the same style as a captain banner.
- Gives the carrier a scoreboard-colored glowing outline visible to all players, mapped to the nearest vanilla team color from the configured CTF RGB color, and restores the player's previous scoreboard team/glowing state when the flag returns or scores.
- Cleans temporary carrier entities and castbars on score, death, departure, match finish, reload and shutdown.
- Reverts the unnecessary `INTERACT` bypass from **Edit arena blocks**; the already-working BREAK/PLACE bypass remains unchanged.
- Increases network protocol from **77** to **78** and Minigame definition schema from **8** to **9** for the new CTF cast duration. Minigame recovery remains schema **2**.

# Simple Server Utilities 1.9.0-dev2.3.3

## Setup-world markers and arena placement hotfix

- Adds an administrator-only in-world setup visualization while the SSU Minigame Setup Tool is held. Lobby, spectator and spawn positions are shown as collision-free colored flag markers with clear labels; configured CTF flags and Domination nodes receive centered labels.
- Ensures CTF and Domination setup actions place or refresh the actual configured physical banner block immediately, and removes the old physical banner when a flag or node is moved.
- Keeps the preview purely client-side for lobby/spectator/spawn markers, so these markers do not obstruct teleport positions or become part of the arena snapshot.
- Fixes **Edit arena blocks** placement: the managed-arena bypass now permits the prerequisite right-click interaction as well as the resulting place event. Admins can therefore both break and place blocks while the selected arena is idle.
- Increases network protocol from **76** to **77** for the setup visualization payload. Minigame definition schema remains **8** and recovery schema remains **2**.

# Simple Server Utilities 1.9.0-dev2.3.2

## Physical two-tone Domination banners and capture-complete feedback

- Removes the crossed blue/red cuboid assault overlay. An assaulted Domination base now changes the physical banner itself: the previous owner or neutral color remains the banner base and a vanilla half-horizontal pattern paints the claiming team color across the top half.
- Restores a clean single-color physical banner whenever an assault is defended, interrupted or completed.
- Enlarges the world-space base label exactly tenfold, from scale 0.025 to 0.25, and centers it on the standing banner block at a higher non-overlapping position.
- Adds distinct non-horn completion sounds: the capturing team hears beacon activation when ownership becomes final, while the team that definitively loses the base hears beacon deactivation. Neutral captures only play the positive capture sound.
- Keeps network protocol **76**, Minigame definition schema **8**, and recovery schema **2** unchanged.

# Simple Server Utilities 1.9.0-dev2.3.1

## Compile hotfix

- Fixes Minecraft 26.2 compilation: `ServerPlayer#playNotifySound(...)` no longer exists.
- Domination team horns are now sent per recipient with `ClientboundSoundPacket`.
- Keeps network protocol **76**, Minigame definition schema **8**, and recovery schema **2** unchanged.

# Simple Server Utilities 1.9.0-dev2.3

## Interruptible Domination claims, delayed captures and live base presentation

- Replaces proximity-based Domination capture with an explicit physical-banner interaction. A player must right-click a node banner to start a configurable claim cast.
- Adds a bottom-center colored cast bar. The cast is interrupted immediately when the claimant moves, leaves the flag, leaves the arena or receives any incoming attack/damage event.
- Adds a separate configurable capture-delay timer after a successful cast. During this assault period the base produces no resource points and changes owner only when the visible timer reaches zero.
- Shows active claim timers to every match participant in the scoreboard and directly above the physical base. Node names remain visible above their banners and use the controlling or claiming team's configured RGB color.
- Renders assaulted banners as two-tone flags: the claiming team's color occupies the top half while the former owner's color, or neutral white, remains on the lower half. The marker becomes the full owning-team banner only after capture completes.
- Lets the former owning team right-click an assaulted flag to defend it immediately, cancel the pending transfer and resume control/scoring without a second cast.
- Plays the vanilla Sing horn for the claiming/defending team and Seek horn for the opposing team whenever a base is assaulted or successfully defended.
- Adds clear Domination editor fields for **Claim cast** and **Capture delay**, replacing the obsolete proximity capture time/radius controls and updating all in-editor guidance.
- Increases network protocol from **75** to **76** for the cast-bar and live Domination world-visual payloads.
- Migrates the Minigame definition schema from **7** to **8** for the new Domination timing settings. Minigame recovery remains schema **2** and unrelated SSU schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev2.2

## Player/admin separation and in-world Minigame Setup Tool

- Separates minigame use from administration. The normal Dashboard **Minigames** tile now opens a player-only lobby containing game information, queue state and only Join/Leave plus Refresh/Close controls; administrator edit, force, finish, delete, score and creation controls are no longer sent into that workflow.
- Adds a dedicated **Admin Center → Minigames** section for disabled and enabled definitions, mode-specific settings, live-match control, blocked-arena recovery and access to the Minigame Setup Tool.
- Removes the misleading generic **Create minigame** route. New supported games are created only after the Minigame Setup Tool selects real arena bounds, so an unusable generic definition can no longer be produced from the player lobby or Region Tool.
- Adds a dedicated server-authoritative golden-hoe **SSU Minigame Setup Tool**. Right-click opens the target/action selector; left-click performs the chosen world action. The tool is available from Admin Tools and the Minigame Administration screen.
- Supports in-world actions for new arena bounds, managed-arena resizing, protected physical block editing, verified snapshot recapture, lobby position, spectator spawn, spectator movement bounds, team/player spawn slots, Spleef playfloor, Capture the Flag bases and Domination capture nodes.
- Arena edit mode temporarily disables the game and arena, archives the previous reset snapshot and grants a narrow build bypass only inside the selected managed arena. **Save arena snapshot** captures and verifies the new physical state before restoring the previous enabled state.
- Managed arena resizing updates the existing hidden SSU region, clamps dependent locations safely, archives the old snapshot and captures a new verified reset source. Active queues or matches block setup mutations.
- Adds schema-backed spectator movement cuboids and Spleef playfloor cuboids. Eliminated spectators are confined to the configured box; Spleef breaking and elimination depth use the configured floor volume instead of the complete arena region when present.
- Existing Spleef definitions migrate safely with an optional playfloor; newly created Spleef arenas default the selected arena volume as their initial playfloor and can be refined with the Setup Tool.
- Removes minigame creation from the Region Tool menu and directs region editing and minigame setup through their separate purpose-built tools.
- Increases network protocol from **74** to **75** for the separate administrator lobby view and Setup Tool payloads.
- Migrates the Minigame definition schema from **6** to **7** for spectator bounds and Spleef playfloor bounds. Minigame recovery remains schema **2** and unrelated SSU schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev2.1

## Compact Minigame Lobby, focused rewards and first playable Domination

- Reduces the Minigame Lobby from 720×468 to 540×351 GUI pixels, exactly 25% smaller in both dimensions, and rearranges the player/admin controls into a compact non-overlapping footer.
- Limits direct minigame rewards to meaningful account/progression effects: player unlocks, reputation, permissions and personal claim-capacity bonuses. Item and money rewards remain in their dedicated Mail-backed controls; player/server flags, counters, server unlocks and legacy give-item/give-money actions are hidden from the minigame selector without breaking older stored definitions.
- Adds the reversible `add_claim_chunks` Content Core reward action. `amount=5` permanently adds five personal claim chunks above the player's current configured capacity and remains transaction/idempotency safe.
- Adds the first runnable two-team **Domination** implementation inspired by five-node resource battlegrounds. Teams capture and hold physical banner nodes, accumulate configurable resource points and win when they reach the score limit or lead when time expires.
- Adds a dedicated six-tab Domination editor: General, Arena, Team spawns, Rewards, Nodes and Rules. Administrators can configure 3–9 physical nodes, team names/colors/banners, capture time/radius, score interval, points per node, score limit, temporary weapon and friendly fire.
- Extends the Selection Tool wizard with Domination. A selected arena of at least 15×15 blocks receives two opposing team-spawn groups and five initial Farm, Lumber Mill, Blacksmith, Mine and Stables nodes before the verified reset snapshot workflow completes.
- Adds live node ownership, neutralization/capture progress, contested-node pausing, up-to-four-player capture acceleration, team resource HUD, out-of-bounds return, death respawn, team winner titles and team-colored winner fireworks.
- Increases network protocol from **73** to **74** for the new editor/runtime contract.
- Migrates the Minigame definition schema from **5** to **6** for Domination rules and arena control points. Minigame recovery remains schema **2** and unrelated SSU schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev2

## Winner presentation, drop-free Spleef and first playable Capture the Flag

- Clears the reward ghost cursor when an administrator right-clicks anywhere outside the real inventory and nine reward-mail slots; no empty inventory slot is required anymore.
- Replaces the free-text direct-action type field with a server-supplied cycling selector covering every registered Content Core action type, while keeping the parameters field and contextual usage help.
- Shows a large winner title and subtitle to every online match participant, and launches two colored star fireworks at each winning player. Capture the Flag fireworks use the winning team color.
- Makes Spleef floor removal permanently drop-free. Allowed floor blocks are removed directly on the server, tool durability is still consumed, and no transient item entity is created or rendered.
- Adds the first runnable two-team **Capture the Flag** implementation with its own five-tab editor, automatic team balancing, team spawns, scores, configurable captures-to-win, match weapon and friendly-fire setting.
- Adds two physical configurable standing-banner flags per arena. Players take the enemy flag by right-clicking its banner and score by returning to their own base while their own flag is present.
- Renders the carried flag on the carrier's back together with an upward team-colored beam. Flags return automatically when a carrier dies, disconnects or leaves the arena.
- Adds a live CTF HUD, respawn-at-team-base death handling, time-limit winner resolution, winner rewards, protected managed-arena interactions and verified snapshot reset integration.
- Extends Selection Tool creation to Spleef or Capture the Flag, requires a minimally usable CTF footprint and creates opposite physical bases plus initial team spawns along the arena's longest horizontal axis.
- Increases network protocol from **72** to **73** for CTF live visuals and the expanded editor payload.
- Migrates the Minigame definition schema from **4** to **5** for Capture the Flag rules and arena flag points. Minigame recovery remains schema **2** and unrelated SSU schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev1.1.1

## Compact Spleef editor and true ghost-inventory rewards

- Reduces the dedicated Spleef editor panel from 860×470 to 660×360 pixels, about 25% smaller in both dimensions, while preserving all five mode-specific tabs.
- Rebuilds the field layout with bounded wrapped help text, shorter labels and fixed vertical spacing so descriptions no longer run through neighboring fields, buttons or footer messages.
- Replaces the click-to-copy reward list with a real ghost-inventory workflow. Clicking an administrator inventory stack holds a visible non-consuming cursor copy; left-clicking any of the nine mail slots copies the complete current stack into that exact slot, while right-clicking adds exactly one matching item.
- Keeps reward slots spatially stable, including empty gaps, instead of compressing later stacks to the left. With an empty ghost cursor, right-clicking a reward slot clears it.
- Performs every item placement server-side against the administrator’s current inventory. Right-click addition rejects different item/components and full stacks; the real inventory is never changed.
- Increases network protocol from **71** to **72** for the reward placement operation flag.
- Migrates the Minigame definition schema from **3** to **4** so nine reward-slot positions, including intentional empty gaps, remain persistent. Minigame recovery remains schema **2** and unrelated SSU schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev1.1

## Dedicated Spleef editor, mail-backed rewards and spectator containment

- Replaces the raw shared minigame form for Spleef with a dedicated five-tab **Spleef Editor**: General, Arena, Player spawns, Rewards and Spleef rules. The generic framework keeps a separate fallback editor so future concrete game modes can receive their own screens without mixing mode-specific settings.
- Adds visible field labels and administrator guidance throughout the Spleef editor. Internal team/victory fields are no longer exposed for Spleef, player positions are named clearly, and coordinates are shown with two decimals instead of long floating-point values.
- Renames Spleef team spawns to **Player spawns** in the editor and automatically keeps their internal one-player-per-slot numbering consistent.
- Adds structured participation and winner reward packages. Administrators can copy up to nine exact item stacks from their complete inventory into ghost reward slots without consuming the real items, and can configure a normal formatted Economy money amount.
- Delivers all minigame item and money rewards through SSU Mail with a subject and explanation identifying the minigame, arena, match and whether the reward came from participation or winning. Players claim physical/economy attachments safely from Mail.
- Applies permissions, unlocks, flags, counters, reputation and other direct Content Core actions immediately, while still sending a reward mail that lists the account changes. Legacy `give_item` and `give_money` minigame actions are routed through Mail during migration and runtime.
- Makes reward delivery retry-safe: direct Content Core actions retain idempotency keys, Mail uses a per-match/player/reward correlation key, and match cleanup pauses instead of marking a failed reward package as delivered.
- Requires the configured spectator position to remain close to the arena region. During a running match, eliminated spectators who leave the arena dimension or move more than 24 blocks beyond its horizontal footprint (or 32 blocks vertically) are returned to the spectator point.
- Increases network protocol from **70** to **71** for the extended editor-open data and server-authoritative inventory reward capture payload.
- Migrates the Minigame definition schema from **2** to **3** for structured mail-backed rewards. Minigame recovery remains schema **2** and all unrelated SSU schemas remain unchanged.

# Simple Server Utilities 1.9.0-dev1

## Minigame Core expansion and first playable Spleef mode

- Adds an explicit minigame game-type layer above the existing queue, arena, reward and reset framework. **Spleef** is implemented first; King of the Hill, Capture the Flag, Domination, Team Deathmatch, Parkour Race and Prop Hunt remain non-runnable planned types.
- Extends the Region Tool right-click GUI with **Create minigame arena**. The server validates the selection and permission, creates a reserved `ssu_mg_*` region, locks interactions, captures a reset snapshot and leaves the new minigame disabled until an administrator reviews it.
- Adds Spleef-specific settings for allowed floor blocks, configured tool, required-tool enforcement, PvP, item/drop cleanup and elimination depth. Selection-created arenas receive one spawn per configured maximum player plus editable lobby and spectator locations.
- Implements the Spleef runtime: isolated countdown inventory, Survival match inventory with the configured tool, server-side block validation, boundary/fall elimination, spectator mode after elimination, last-player-standing/tied-survivor resolution and automatic snapshot reset.
- Adds a compact server-authoritative HUD scoreboard showing game mode, lifecycle state, countdown or remaining time, alive players and personal spectator state.
- Persists complete minigame player recovery state before any inventory is cleared. Restoration occurs before rewards and before the return teleport. Inventory encoding/decoding fails closed, legacy schema-1 recoveries preserve the player's current inventory, and failed restores retain their recovery record instead of overwriting data.
- Durably flushes and byte-verifies the exact minigame recovery file before countdown/late-join inventory replacement. If that critical path cannot be verified, no live player state is changed and new match starts pause for the session.
- Moves generic `give_item` and `give_money` reward handlers into Content Core so Minigame rewards remain available even when the Quest module is disabled. Per-action reward idempotency keys distinguish multiple reward steps of the same type.
- Cancels normal SSU teleports for active participants so leaving must pass through the minigame lifecycle. Temporary lobby/match players also cannot use blocks, items or ordinary entities, while configured same-match PvP remains possible. Outsiders cannot break or place inside active arenas, and managed arenas remain protected while idle.
- Forced administrator starts are reward-free. Countdown disconnects/deaths cancel safely when the minimum player count is lost, restore all available players and return them to the queue.
- Changes blocked-arena administration from unsafe release to actual snapshot restoration. Interrupted reset-enabled arenas remain unavailable until restoration succeeds.
- Protects managed region ownership against client-edited JSON: only the server selection workflow can create managed metadata, managed region IDs cannot be retargeted or removed in the editor, deletion only cleans reserved managed regions, and region IDs are unique across minigames.
- Increases network protocol from **69** to **70** for the Minigame HUD, selection-creation flow and extended lobby payload.
- Minigame definition schema is **2** and Minigame recovery schema is **2**. Every unrelated SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.6

## Per-claim tax cycles, permanent collateral and crash-safe enforcement

- Replaces the initial aggregate Player Claim-tax cycle with an independent tax cycle stored on every player claim. A newly created claim starts its timer at creation; existing dev18.5 claims receive a fresh full cycle during migration.
- Records each claim's highest chunk count during the active cycle. The taxable peak rises immediately when chunks are added, never falls when chunks are removed and is repaired on load if legacy/corrupt data would place it below the live claim size.
- Snapshots the rate, interval, reminder lead and dimension multiplier per cycle. Administrator changes apply to new/next cycles and cannot retroactively alter an already running cycle.
- Sends a per-claim reminder as an explicit estimate. The mail explains that expansion can increase the final amount and permanent collateral, while shrinking the claim does not lower the recorded peak.
- Makes normal deletion of a taxable claim a GUI settlement: **Pay tax & delete** atomically debits the current full-cycle amount, while **Forfeit capacity & delete** permanently confiscates that claim's taxable peak. Both routes use a second confirmation and remove linked Homes.
- Blocks final-chunk, Claim Map batch and legacy-command deletion from bypassing the settlement choice. Administrators retain an explicit management bypass, but no claim mutation may race an already journaled tax settlement.
- Groups simultaneously due claims for one owner into one idempotent Economy debit. A successful debit starts a new cycle for only those paid claims with their current size as the new peak.
- Treats only Economy's explicit `insufficient_funds` result as non-payment. Technical failures, disabled dependencies, failed journals or uncertain storage never delete claims or confiscate capacity.
- On confirmed automatic non-payment, journals and removes all current claims and linked Homes, leaves world blocks untouched, then permanently confiscates exactly the summed unweighted peaks of the claims whose tax was due. Dimension multipliers affect money only, never the number of confiscated claim slots.
- Stores permanent confiscations separately from ranks and permissions, keyed by settlement UUID. Effective claim capacity is always `permission limit - confiscated chunks`, so rank changes cannot erase the penalty and duplicate recovery cannot apply it twice.
- Adds a schema-2 settlement ledger with explicit payment/removal/penalty phases, claim and linked-Home recovery snapshots, per-claim progress and retained penalty-bearing audit records. Startup recovery safely resumes after crashes between any two steps.
- Verifies exact claim, Home and limit files after critical flushes before advancing the journal. Missing/conflicting recovery data enters a persistent fail-closed safety halt instead of guessing.
- Adds `player_claims/tax_safety_halt.json` as a restart-persistent administrator repair marker. A damaged/ambiguous ledger cannot silently become active taxation again after reboot; the marker must only be removed after restoring or repairing the underlying records.
- Increases network protocol from **68** to **69** for the taxed-claim deletion payload and extended Claim Map tax details.
- Player Claim storage schema is **2**, Player Claim-tax settings schema is **2**, and the new settlement-ledger schema is **2**. Warp storage remains schema **2**; all unrelated SSU schemas remain unchanged.

# Simple Server Utilities 1.8.0-dev18.5

## Selection transforms, Player Claim tax foundation and player-warp rentals

- Extends Region Selection Fill Mix to accept block items plus water/lava buckets. Percentages may total 0–100%; every unused percentage becomes air and inventory items are not consumed.
- Adds 90-degree left/right rotation, 180-degree rotation, east/west and north/south mirrors and vertical flipping with compatible block-state transforms and safe old/new-footprint cleanup.
- Adds the first disabled-by-default Player Claim-tax administration under Economics with an exact base rate, configurable cycle/reminder and default Overworld x1.0, Nether x1.2 and End x1.5 dimension multipliers.
- Adds GUI-only player-warp rentals with `ssu.warps.rent` and `ssu.warps.rent.max`, prepaid creation, automatic renewal, public/private visibility, My Warps management and Travel integration for public rentals.
- Migrates warp storage to schema **2**. Network protocol remains **68**.

# Simple Server Utilities 1.8.0-dev18.4

## GUI-first region selection editor

- Removes the player-facing **Rent**, **Extend** and **Unrent** actions from the Admin Center **Regions** list. Region configuration remains available through each region's existing **Settings** page.
- Removes the overlapping **Region Maintenance** tile from the Admin Center while retaining the underlying legacy actions and commands for compatibility and recovery use.
- Rebuilds the Region Tool workflow: left-click point 1 and point 2, then right-click to open a focused action menu with **Create server region**, **Edit selected blocks** and **Clear selection**.
- Simplifies region creation to a name-only GUI. Protection flags, priority, messages and rental settings are intentionally configured afterwards under **Admin Center → Regions → Settings**.
- Adds a dedicated selection editor with temporary server clipboard copy/paste, safe block clearing, inventory-driven weighted fill mixes and explicit destructive-action confirmations.
- The fill-mix editor accepts only current `BlockItem` inventory slots, supports up to six block types, requires percentages from 1–100 and requires an exact 100% total. Inventory items are not consumed.
- Adds portable selection templates with a clear **SERVER / CLIENT** storage choice. Server templates are shared with administrators; client templates are stored in the local Minecraft installation and are revalidated by the server before loading.
- Selection templates contain only block states. Block entities, container inventories and entities are deliberately excluded to prevent item or entity duplication. Destination container contents are discarded without drops for GUI paste, load, fill and selection-clear operations.
- Runs copy, paste, fill and clear operations through the existing job scheduler with cuboid and intersecting-region locks, a 1,000,000-block safety limit, world-height/world-border checks and bounded template decoding.
- Saves server and client template files atomically. The internal `.ssusel` template format starts at version 1; no existing SSU storage schema is changed.
- Network protocol is **68** because the region selection workflow introduces five new client/server payloads and extends its open payload. Every existing SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.3.1

## Auction House maintenance compile hotfix

- Restores `AuctionHouseManager#maintenanceTick()`, which was accidentally removed while moving Auction House tax administration into the protected Economics menu.
- Restores startup and scheduled maintenance for purchase recovery, pending seizures, expired/empty listings, session cleanup and old purchase-journal cleanup.
- Network protocol remains **67** and every SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.3

## Economics administration and transaction UX

- Adds **Admin Center → Economics** as the single protected entry point for economy accounts, the complete transaction journal, Auction House tax, Player Claim tax status and the region-rent journal.
- Rebuilds the administrator transaction page with a scrollable known-player selector, a retained exact player-name/UUID compatibility field, independent free-text transaction search, details and history-retention controls.
- Renames the administrator page from **Wallet & Transactions** to **Transactions**. The player-facing Wallet remains separate and continues to provide payments and personal history.
- Removes the obsolete **Apply refund policy** controls and their menu action route. Existing stored region-rental refund settings and refund behaviour remain unchanged.
- Moves Auction House sale-tax editing out of the Auction House screen. Players may still see the active seller tax, while only authorized economy administrators can change it under Economics.
- Adds a **Player Claim tax** submenu that clearly reports that no claim tax is active. The current claim system has no purchase price, recurring billing schedule or defined insufficient-funds policy, so this build deliberately does not create a non-functional or ambiguous charge.
- Removes the secondary gray dimension/coordinate text from player Travel rows so destination text cannot appear behind the search field. Admin Travel Management keeps the technical location details.
- Network protocol remains **67** and every SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.2.1

## Travel compile and claim-chunk Home cleanup hotfix

- Fixes the Java effectively-final compilation error in both Travel pages by keeping the mutable destination accumulator separate from the filtered and sorted visible list.
- Deletes every Home physically located in a successfully removed claim chunk, for individual unclaims and valid Claim Map batch removals.
- Performs ownership and connected-area validation before Home cleanup, so rejected chunk changes never remove Homes.
- Network protocol remains **67** and every SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.2

## Claim cleanup, focused map navigation and Travel separation

- Deletes every home physically located inside a player claim when that claim is deleted through the Claim Map, dashboard administration or legacy command path. Cleanup runs centrally in `PlayerClaimManager` before the claim chunks are removed, so all deletion routes stay consistent.
- Makes the Claim Map previous/next buttons request an authoritative viewport centered on the selected owned claim. Remote owned-claim viewports are validated against the claim bounds before normal map actions are accepted.
- Keeps Claim Map notices inside the bottom panel by left-aligning and width-clipping the message; duplicate disconnected-selection wording is shortened.
- Rebuilds player **Travel** as a read-only shortcut directory for permitted claim-linked homes, warps and server spawn. It includes All, Homes, Warps and Other filters, name/dimension search, teleport actions and pending-teleport cancellation.
- Removes all warp/spawn mutation controls from player Travel. Adds **Admin Center → Travel Management** with All, Warps and Spawn filters, search, normal test teleports, warp create/move/delete, server-spawn set/clear and permission-aware disabled controls.
- Existing `/homes`, `/warps`, `/spawn` and claim commands remain available and continue to use the same server managers and policies.
- Increases network protocol from **66** to **67** for the extended Claim Map request payload. Border visualization preferences remain schema **3** and every other SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.1.1

## Minecraft 26.2 screen API compile hotfix

- Replaces the two invalid `Minecraft#setScreen(...)` calls introduced by the claim-specific Homes navigation with the Minecraft 26.2 `Minecraft#setScreenAndShow(...)` method.
- Fixes compilation in `ClaimMapScreen` and `PropertySettingsScreen`.
- Network protocol remains **66**, border visualization preference schema remains **3**, and every other SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.1

## Per-claim borders and claim-bound Homes

- Replaces the old one-shot claim border controls with a persistent per-owned-claim **Show / Hide** toggle.
- Splits personal claim-border settings into a master **Enable claim borders** gate and **Show other claims**.
- Moves Homes management into each claim's settings and filters the page to homes physically located inside that claim.
- Requires new or moved homes to be inside the selected owned claim and revalidates `homes.use`, `homes.set`, teleport/delete permissions and the permission-derived total home limit server-side.
- Existing home records and legacy commands remain available; homes outside claims are not deleted automatically.
- Network protocol is **66**, border visualization preference schema is **3**, and every other SSU storage/schema version remains unchanged.

# Simple Server Utilities 1.8.0-dev18.0

## GUI-first command migration

- Adds dedicated **Homes** management under **Claims & Homes**. Players can save/update, teleport to and safely delete homes without commands.
- Keeps direct claim teleport administrator-only in both GUI and the legacy `/claims tp` command route; players navigate to their land through homes.
- Expands **Travel** with warp creation, relocation and deletion, server-spawn management and pending-teleport cancellation.
- Adds **Admin Center → Player Claims** for cross-player claim inspection, safe administrator teleport and confirmed deletion.
- Adds visual rank creation, rename, default-rank selection, deletion and player reset while preserving the existing permission editor and legacy commands.
- Adds **Region Maintenance** for snapshots, reset, clear, redefine, deletion, selection points/coordinates/fill, rental time, pause/resume and the global renting switch.
- Adds a complete Utility Mining administration page for Treecapitator/Veinminer limits and custom/disabled block lists.
- Adds a shared Maintenance page for safe SSU reload, border colors/refresh, hologram refresh/move-here, NPC refresh, NPC-shop buy-back retention and runtime diagnostics.
- Adds administrator score adjustment controls to the Minigame Lobby and an administrator **Advance stage** control to the Dungeon Lobby.
- Routes `/ssu reload` and the GUI through the same reload lifecycle, which now includes Auction House, map markers, NPC shops, quests, minigames and dungeons. Managed dimensions remain restart-bound.
- Existing commands remain available as compatibility, console and recovery routes. GUI actions reuse the existing managers, policies, storage and job locks.
- Network protocol is **65** because of the new minigame score action payload. All SSU storage/schema versions remain unchanged.

# Simple Server Utilities 1.8.0-dev17.3

## Managed-dimension datapack recovery hotfix

- Rewrites generated dimension-type JSON to the Minecraft 26.2 format.
- Removes legacy dimension-type fields that were deleted before 26.2, including `effects`, `fixed_time`, `piglin_safe`, `natural`, `ultrawarm`, `bed_works`, `respawn_anchor_works` and `has_raids`.
- Maps those settings to the current `attributes`, `skybox`, `cardinal_light`, `has_fixed_time`, `default_clock` and `timelines` fields.
- Generates current 26.2 bed, raid, piglin, respawn-anchor, water/lava, fog, sky and fixed-time attributes for Overworld, Nether, End, Flat and Empty presets.
- Keeps network protocol **64** and every SSU storage/schema version unchanged.
- Existing worlds that already contain the malformed generated datapack require the one-time recovery steps in `docs/RECOVERY-1.8.0-dev17.3.md` before Minecraft can load the world.

# Simple Server Utilities 1.8.0-dev17.1

## Compile hotfix

- Fixed `SsuMenuService.permissionEditorData(...)`: the requested dimension is now normalized into a separately assigned final/effectively-final value before it is referenced by a stream lambda.
- Resolves the Java compile error that `selectedDimension` must be final or effectively final.
- Network protocol remains **64** and all storage/schema versions remain unchanged.

# Simple Server Utilities 1.8.0-dev16.5

## System-account isolation and entity combat information

- Marks SSU Mail Escrow and Auction House Tax as internal economy system accounts instead of player accounts.
- Migrates the two existing deterministic account IDs automatically, without changing their balances or transaction history.
- Excludes system accounts from Trusted Players, player permission/profile selectors, region member resolution, player payments and player mail recipients.
- Automatically removes an internal system account from a claim's trusted set if it was accidentally added by an earlier build.
- Keeps system accounts visible only in dedicated administrator economy accounting where their balances are relevant.
- Block Information now shows health/current maximum health for living entities.
- Armor is shown only when the inspected entity currently has armor points; armor toughness is shown only when greater than zero.
- Entity stats are available in normal and debug Block Information views and do not reveal unavailable fields.
- Network protocol remains **63** and all public storage schemas remain unchanged. Economy account records internally normalize to schema 2 for the system-account marker.

# Simple Server Utilities 1.8.0-dev16.4

## Dedicated trusted-player manager

- Replaces the cramped Claim Settings trusted-player summary plus separate Trust/Untrust controls with one **Trusted players → Manage** entry.
- Adds a dedicated server-authoritative trusted-player GUI per owned claim.
- The **Trusted players** tab lists every currently trusted player, supports local name/UUID filtering and removes access with one explicit button.
- The **Add player** tab searches online and previously known players and adds them without manually typing names or UUIDs.
- Online players are visually marked and sorted before offline known players.
- Candidate results are capped at 100 per search response; the GUI reports the full match count and asks the user to narrow broad searches.
- Claim ownership and `claims.trust` permission are revalidated server-side for every add/remove action.
- The old property-setting `trust_player` and `untrust_player` actions are removed from the Claim Settings page.
- Network protocol is **63**; all storage schemas remain unchanged.

# Simple Server Utilities 1.8.0-dev16.2

## World marker beacon-beam refinement

- Replaced the former thin two-line marker beam with a beacon-like three-layer vertical column.
- The beam now uses a broad translucent marker-colour glow, a brighter inner column and a narrow luminous core.
- The lower end starts at the first free block above the active `WORLD_SURFACE` column instead of extending to the dimension minimum Y.
- Surface height is cached for 40 ticks per marker and refreshed when the marker column changes, avoiding a heightmap lookup every rendered frame.
- Unloaded marker columns safely fall back to the marker's persisted Y until the chunk becomes available.
- Beam visibility distance, marker colours, map icons, labels, network protocol and every storage schema remain unchanged.

# Simple Server Utilities 1.8.0-dev16.1

## Compile hotfix

- Fixed `ContentProgressionManager.markAccess(UUID)`: `MinecraftServer#getTickCount()` returns an `int`, while `lastAccessTick` stores `Long` values.
- The tick count is now explicitly widened to `long` before insertion.
- No network, storage, schema, or runtime-behaviour changes.

# Simple Server Utilities 1.8.0-dev15.4

## NPC and Dialogue Editor usability hotfix

- Keeps the NPC Manager on the **Templates** tab after deleting or spawning a reusable template instead of forcing it back to Placements.
- Replaces the wide Role button on NPC Identity with compact `<`, role and `>` controls.
- Adds a dedicated upper-right close button to the NPC editor.
- **Save** now saves an existing NPC without closing the editor. The manager refreshes when the editor is closed; successful deletion still closes and refreshes immediately.
- Makes the Template ID read-only while editing an existing NPC so repeated saves continue to target the same reusable template safely.
- Renames Dialogue Editor tabs to clearer workflow terms: **On open** and **On choose**.
- Adds a contextual explanation on every Dialogue Editor page and a complete Help window explaining Node, Conditions, On open, Choice and On choose.
- Adds a selectable **Parameter guide** for every known condition and action type. It lists required/optional keys, descriptions and insertable examples while preserving the free-form `key=value` field for custom and modded handlers.
- Adds recommended defaults for built-in item and money actions alongside the existing progression, reputation and permission actions.
- Network protocol remains **61** and every storage schema remains unchanged.

# Simple Server Utilities 1.8.0-dev15.3

## Shop Editor usability hotfix

- Keeps **Save shop** inside the editor and shows a compact saved confirmation instead of closing the screen.
- Adds a dedicated close button in the upper-right corner.
- Replaces the previous/next shop labels with compact `<` and `>` buttons.
- Fits the Count field, Infinite toggle and Down button completely inside the Offers panel.
- Adds a framed selected-item slot with the normal Minecraft item tooltip.
- Removes the redundant green price/catalog and copied-item explanatory text from Offers and Trade rules.
- Adds **View: All / View: Added / View: Not added** filtering to Trade rules so administrators can immediately inspect only rules already present in the active whitelist or blacklist.
- Preserves missing legacy item/tag rules in the added-only view, even when a mod or tag is no longer available.
- Network protocol remains **61** and all storage schemas remain unchanged.

# Simple Server Utilities 1.8.0-dev15.2

## Compile hotfix

- Fixed Java generic type inference in `NpcEditorService.shopChoices()`.
- The shop comparator now explicitly uses `NpcShopDefinition`, so `displayName` and `id` resolve correctly.
- The faction comparator now explicitly uses `NpcDefinition` as a preventive matching fix.
- No network or storage format changes.

# Simple Server Utilities 1.8.0-dev15.1

## Compile hotfix

- Replaced the removed `BuiltInRegistries.ITEM.getTagNames()` call in `NpcShopEditorScreen` with the Minecraft 26.2 registry API `BuiltInRegistries.ITEM.getTags()`.
- Tag identifiers are now read from each `HolderSet.Named<Item>` through `tag.key().location()`.
- Restores compilation of the visual whitelist/blacklist tag selector without changing shop data, schemas, or network payloads.
- Network protocol remains **61**.
- NPC Shop schema remains **4**.

## Previous dev15 changes

## Administrator interface

- Rebuilt **Admin Center → Admin Tools** as a true scrollable list with mouse-wheel support and visible up/down controls. Tool cards no longer collide with the fixed footer buttons on shorter screens.
- Reduced the complete NPC Shop Editor from **760×480** to **570×360**, exactly 25% smaller in both dimensions, and reflowed every page for the compact window.

## Shop availability

- Replaced the shared offer time window with seven independent weekday rows.
- Every weekday now has its own enabled switch, **All day** switch, start time and end time.
- Removed the redundant **Every day** control. Administrators explicitly enable the weekdays they need.
- Overnight windows remain supported per day, for example Sunday 22:00–Monday 02:00.
- Migrated schema-3 schedules without changing their meaning: the former empty day mask remains every day, and legacy equal start/end times remain all day.

## Visual trade rules

- Replaced the manually typed whitelist and blacklist editors with visual selectors.
- Administrators can switch between **Items** and **Tags**, search the active registry, page through results and click a row to add or remove it from the selected list.
- Every vanilla and modded item is sourced from the active item registry and is shown with its item icon and registry ID.
- Every item tag known to the connected client is selectable without memorising tag IDs. Existing exact-item and tag rules remain compatible.

## NPC editor usability

- Removed the Basic interaction text field from Identity. NPC conversations now remain the responsibility of Dialogue definitions; existing legacy text is preserved invisibly for compatibility.
- Replaced the manually typed linked-shop field with a searchable selector containing all existing shared shops.
- Replaced manually typed faction-relation targets with a searchable selector containing all factions currently defined by NPC templates.
- Unknown legacy references remain visible as missing references instead of being silently discarded.

## Compatibility

- Mod version: **1.8.0-dev15**.
- Network protocol increased from **60** to **61** for the NPC editor shop/faction choice lists.
- NPC Shop schema increased from **3** to **4** for independent weekday hours.
- NPC definition schema remains **7**, placement schema remains **3**, dialogue schema remains **1**, Item Price Catalog schema remains **1**, and Player UI preference schema remains **8**.
- Client and dedicated server must use the exact same dev15 build.

# Simple Server Utilities 1.8.0-dev14

## NPC identity

- Moved **Role / occupation** from Functions to the Identity tab. Role is now descriptive metadata only and never creates, replaces or changes NPC services.
- Removed the **Apply preset** button and all role-to-service preset logic.
- Added a synchronized three-line NPC identity label: smaller role above the NPC name and faction name below it.
- Faction labels use the NPC attitude toward players: hostile red, neutral yellow and friendly green.
- Added a player-facing faction display name while retaining the stable faction ID for reputation and relation logic. Empty display names migrate to a readable form of the faction ID.
- Disabled the vanilla custom-name plate for managed NPCs while the SSU label is active, preventing duplicate names. The existing **Name visible** option controls the complete SSU identity label.

## Central shop administration

- Added **Shop Manager** to Admin Center → Admin Tools as the single place to create, browse, edit and delete shared shops.
- Removed Shop Library, Use for NPC and Edit linked shop navigation from the NPC editor.
- NPC Editor → Functions now contains only a **Linked shop ID** reference for shops. Shop content cannot be edited from an NPC.
- Migrated legacy generic shop functions into the explicit linked-shop field and removed them from advanced functions during definition normalization.
- Linked-NPC counts and the Shop Editor's linked-NPC page now read the explicit shop ID.

## Compatibility

- Mod version: **1.8.0-dev14**.
- Network protocol increased from **59** to **60** for the NPC editor fields and overhead-label synchronization payload.
- NPC definition schema increased from **6** to **7**.
- NPC placement schema remains **3**, dialogue schema remains **1**, NPC shop schema remains **3**, Item Price Catalog schema remains **1**, and Player UI preference schema remains **8**.
- Client and dedicated server must use the exact same dev14 build.

# Simple Server Utilities 1.8.0-dev13.2

- Fixed misleading NPC shop affordability feedback.
- Shop hover now distinguishes right-click one-item price from left-click offered-stack total.
- Added the raw synchronized player balance to the shop snapshot so unaffordable one-item and stack prices render in red.
- Insufficient-funds messages show required amount, current balance and the right-click fallback.
- Applied the same detailed feedback to buy-back purchases.
- Network protocol increased from 58 to 59 because `NpcShopDataPayload.Entry` now carries the formatted full-stack price.
- NPC shop schema remains 3; item price catalog schema remains 1; player UI preference schema remains 8.

# Simple Server Utilities 1.8.0-dev13.1

- Removed the live weekday/time line from the player-facing NPC Shop.
- Added a live weekday/time line to the SSU dashboard header.
- Added a personal `Day & time below map` minimap setting, disabled by default and synchronized with the normal minimap payload.
- Minimap coordinates and the optional weekday/time now render below the minimap for every minimap position.
- Reduced the Item Price Catalog window from 820×500 to 615×375 (25% smaller in both dimensions).
- Reflowed the catalog controls and reduced catalog pages from 18 to 12 rows so the compact window remains fully visible.
- Player UI preference schema increased from 7 to 8.
- Network protocol increased from 57 to 58.

## 1.8.0-dev13

### Added

- Added a searchable, paged **Item Price Catalog** to **Admin Center → Admin Tools**. It is built from the live Minecraft item registry, so every active vanilla and modded item appears automatically without a hard-coded compatibility list.
- Added independent global base prices for what a player pays to buy one item and what a player receives when selling one item. Zero disables that direction. The sparse catalog is persisted in `npcs/item_prices.json` with item-price schema **1**.
- Added per-shop **Trade rules** with sale whitelists and blacklists. Rules accept exact item IDs such as `minecraft:wheat` and item tags such as `#c:crops`; an empty whitelist accepts every globally priced item and the blacklist always wins.
- Added a reusable seven-day Minecraft calendar from Monday through Sunday. Calendar days change at midnight, repeat every seven world days and share the existing Minecraft clock conversion.
- Added per-offer **Availability** settings in the Shop Editor: selected weekdays, start time and end time. Overnight windows are supported, including Sunday 22:00 through Monday 02:00.
- Added the current weekday and time to the player-facing NPC Shop header; it keeps advancing from the synchronized client world clock while the server remains authoritative for availability.
- Added a central purchase-quote pipeline for future reputation and temporary server-event discounts. Multiple discounts are summed as percentages of the unchanged catalog base price, so discounts never compound on already discounted prices.

### Changed

- Players can now sell any inventory item with a configured global sale price to a shop that permits it through its whitelist/blacklist. The item no longer needs to be one of that shop's purchase offers.
- Shop offers now define which exact items the NPC sells, their offered stack size, stock/restocking and schedule. Buy and sell prices are no longer edited per offer.
- Existing schema-2 offer prices are migrated once into the global item catalog and then cleared from the shop entries. An existing catalog value takes priority, allowing administrators to disable a price later by setting it to zero.
- NPC Shop storage migrates from schema **2** to schema **3** for shop sale filters and scheduled offers. NPC definitions remain schema **6**, placements remain schema **3**, dialogues remain schema **1**, and the Item Price Catalog starts at schema **1**.
- The network protocol is now **57** for the item-catalog editor payloads, player-shop weekday/time snapshot and per-inventory-slot sale quotes.
- Client and dedicated server must use the exact same `1.8.0-dev13` build.

### Safety

- Purchase availability and inventory-sale filters are revalidated server-side at transaction time. A stale client screen cannot buy an offer outside its configured day/time window or sell an item rejected by the shop rules.
- The player shop now receives a bounded sale quote for every occupied inventory slot, showing the current catalog sale price or the exact shop-rule reason that prevents the sale before the player clicks.

## 1.8.0-dev12.1

### Fixed

- Replaced the remaining **Use held item** workflow in the visual NPC Shop Editor with a complete 36-slot inventory and hotbar picker.
- Clicking a non-empty inventory slot now asks the server to copy that exact stack into the selected shop offer. The original player stack is never moved, reduced or removed.
- Exact item data is preserved, including count, damage, custom name, enchantments and other data components supported by the existing `NpcItemCodec`.
- Empty slots are rejected without changing the current offer, and the server independently validates the clicked slot before accepting the item.
- New offers now instruct administrators to click an inventory stack rather than hold an item in the main hand.

### Changed

- The shop editor submission payload now carries a bounded inventory-slot index, so the network protocol is **56**.
- NPC Shop storage remains schema **2**. NPC definitions remain schema **6**, placements remain schema **3**, and dialogues remain schema **1**.
- Client and dedicated server must use the exact same `1.8.0-dev12.1` build.

## 1.8.0-dev12

### Added

- Added a shared-shop workflow based on permanent shop IDs. Multiple NPC functions can point to the same shop ID, and every linked NPC reads the same live offers, prices, stock and buy-back behavior.
- Added **Shop library** to the NPC Functions editor. It opens the visual shared-shop library without changing the NPC until an administrator selects **Use for NPC**.
- Added **Use for NPC** to the shop library when it was opened from an NPC editor. The selected shop ID is written directly into that NPC's shop function without manual copying.
- Added **Edit linked shop** beside shop functions so an administrator can open the currently assigned shared shop directly from the NPC editor.
- Added previous/next shop browsing and a Shop list shortcut inside the Shop Editor. Browsing saves the current draft first, preventing edits from being lost while moving between shared shops.
- Added a **Linked NPCs** Shop Editor page showing every NPC template that exposes the current shop, how many shop functions reference it and how many placed NPC instances use that template.
- Added NPC-reference totals to every Shop Library row.

### Changed

- The visual shop administration model is now intentionally reusable rather than transitional: a shop ID is the single shared source for all NPCs linked to it.
- Shop deletion is blocked while any NPC template still references the shop ID. The library and server both enforce this guard and report the number of linked templates and placed NPCs.
- Shop Editor save messages explicitly confirm that linked NPCs use the update immediately.
- The network protocol is now **55** because the shop manager rows carry NPC-reference totals and the editor payload carries shop navigation state, notices and bounded linked-NPC records.
- NPC Shop storage remains schema **2**. NPC definitions remain schema **6**, placements remain schema **3**, and dialogues remain schema **1**.
- Client and dedicated server must use the exact same `1.8.0-dev12` build.

### Scope

- Dev12 keeps the dev11 click-only player buying, inventory selling and timed nine-entry buy-back behavior unchanged. This slice focuses on reusable shop IDs, remote editing, NPC assignment and reference visibility.

## 1.8.0-dev11

### Added

- Added a compact click-only player NPC Shop screen with eighteen shop slots per page and the complete 36-slot player inventory. Shop items never attach to the cursor and no drag-and-drop path exists.
- Added direct mouse transactions: left-clicking a shop item buys its configured offered stack, right-clicking buys one item, left-clicking an inventory stack sells the complete clicked stack, and right-clicking sells one item from that exact slot.
- Added a per-player **Buy-back** tab for the latest nine sales to the current NPC shop. Left-click buys back the remaining sold stack and right-click buys back one item at the exact unit price originally paid to the player.
- Added reserved buy-back stock. Recently sold items remain reserved until bought back or expired; expired and evicted records are safely committed to finite shop stock.
- Added the common config value `npcShopBuybackMinutes`, defaulting to 5 minutes and bounded from 1 to 1440 minutes. Administrators can inspect or change it at runtime with `/ssu npc shop buyback-minutes [minutes]`.
- Added dedicated Economy Core transaction types and rollback handling for NPC shop buy-back purchases.

### Changed

- NPC Shop prices, finite stock and restock amounts now use individual item units. The configured `itemCount` only determines the stack bought by a normal left-click.
- NPC Shop storage migrates from schema **1** to schema **2**. Legacy offer prices are converted to per-item prices and legacy finite offer stock/restock counts are expanded to item counts.
- Inventory sales are resolved server-side from the clicked slot and exact item components. When duplicate exact offers exist, the valid offer with the highest sell price is selected.
- The network protocol is now **54** because the player shop snapshot includes eighteen shop entries and nine bounded buy-back entries. NPC definition schema remains **6**, placement schema remains **3**, and dialogue schema remains **1**.
- Client and dedicated server must use the exact same `1.8.0-dev11` build.

### Scope

- Dev11 implements the new player transaction behavior and buy-back foundation. The next shop slice can remove the reusable/shared Shop Manager architecture and integrate each NPC's shop editor directly into the NPC editor.

## 1.8.0-dev10.1

### Added

- Added a visual **NPC Shop Manager** with search, paging, creation, editing, guarded two-click deletion, enabled/disabled status and offer counts. It opens from the new **Shops** button in the existing NPC Manager or through `/ssu npc shop manage`.
- Added a two-page **NPC Shop Editor** for identity and offers. Administrators can create reusable shops, edit display name and enabled state, add, duplicate, delete and reorder offers, and preview the selected exact item, prices and stock configuration.
- Added server-authoritative held-item capture from the editor. The server copies the administrator's current main-hand stack including components, enchantments, damage, trims, dyes, custom names and custom model data, then returns the updated draft without persisting it until Save.
- Added visual controls for items per offer, buy/sell prices in the configured economy decimal format, infinite/finite stock, current/maximum stock and persisted restock amount/interval.
- Added `/ssu npc shop edit <shop>` as a direct visual-editor shortcut while retaining all dev10 command administration for automation and recovery.

### Changed

- Existing shop IDs become immutable after first save so NPC Function and dialogue targets cannot silently break through a rename. New shops choose their ID before the first save.
- Visual-editor saves are fully revalidated on the dedicated server for administrator permission, unique normalized IDs, exact valid ItemStacks, at least one enabled transaction direction, stack-size bounds, stock bounds, entry count and serialized size.
- Network protocol is now **53** for the six bounded shop-manager/editor payloads. NPC Shop schema remains **1**; NPC definition schema remains **6**, placement schema remains **3**, and dialogue schema remains **1**.
- Client and dedicated server must use the exact same `1.8.0-dev10.1` build.

### Scope

- Dev10.1 replaces normal shop administration with a visual workflow while preserving the command layer. Categories, search/filtering in the player shop, per-player purchase limits, reputation/rank discounts and generic paid NPC services remain planned dev10 follow-up work.

## 1.8.0-dev10.0.1

### Fixed

- Removed the private `NpcShopScreen#rebuildWidgets()` helper that collided with the inherited Minecraft 26.2 `Screen#rebuildWidgets()` method and caused Java to reject the reduced visibility. `acceptData(...)` now uses the inherited screen rebuild implementation directly.

### Changed

- Network protocol remains **52**. NPC Shop schema remains **1**; NPC definition schema remains **6**, placement schema remains **3**, and dialogue schema remains **1**.
- Client and dedicated server must use the exact same `1.8.0-dev10.0.1` build.
- This is a source compilation hotfix only; the dev10 shop feature scope and stored data are unchanged.

## 1.8.0-dev10

### Added

- Added the first persistent **NPC Shop** foundation with independent schema-1 shop definitions stored under `npcs/shops`. A shop can contain up to 128 exact ItemStack offers and is referenced through the extensible NPC service target `shop`.
- Added a server-authoritative player shop screen with eight offers per page, item rendering/tooltips, current balance, exact buy/sell prices, stock visibility, quantity entry, paging and refresh.
- Added bidirectional fixed-price transactions. NPCs can sell items to players, buy exact matching items from players, or support only one direction by setting the other price to zero.
- Added finite or infinite stock, maximum stock, persisted wall-clock restock intervals and catch-up restocking after server downtime without tick-by-tick loops.
- Added exact inventory planning and rollback snapshots across the 36 storage/hotbar slots. Custom names, components, enchantments, trims, damage, dyes and custom-model data must match when selling.
- Added Economy Core journal types and idempotency keys for NPC shop purchases, sales and both rollback directions.
- Added administrator commands under `/ssu npc shop` for listing, creating, deleting, renaming and enabling shops; capturing the held exact item as an offer; removing offers; and configuring finite stock/restock.
- Added permissions for using, buying from and selling to NPC shops, plus the NPC-service shop gate.
- Merchant and Blacksmith role presets now use the registered `shop` service. Dialogue Editor target browsing and validation include current NPC shops.

### Changed

- Network protocol is now **52** for the new shop page, refresh and transaction payloads.
- NPC definition schema remains **6**, NPC placement schema remains **3**, and NPC dialogue schema remains **1**. The new NPC Shop schema is **1**.
- Shop interactions revalidate the NPC instance, dimension, distance, reputation, permissions, module state, session, shop, offer, stock, inventory capacity/content and current economy result on every request.
- Client and dedicated server must use the exact same `1.8.0-dev10` build.

### Scope

- This first dev10 slice provides the fixed-price shop runtime, storage, player GUI and safe command-based administration. A visual shop editor, categories, per-player limits, discounts and generic paid NPC services remain planned dev10 follow-up work.

## 1.8.0-dev9.3

### Added

- Added a safe client-only dialogue preview that can start from the currently selected node, follow graph choices, reset to the configured start node and show which entry/choice actions or NPC services would run without executing any side effects.
- Added a paged Dialogue Editor validation report with separate errors and warnings for missing/duplicate IDs, missing graph targets, unreachable nodes, trapped graph cycles, blank player-facing text, ignored condition children, unknown registered handlers and incomplete service routing.
- Added server-synchronised target catalogues for warp, quest-offer, quest-turn-in, minigame-queue and dungeon-queue services. The Choice page can now browse actual current server targets while retaining manual input for extensible custom services.
- Added shared side-effect-free `NpcDialogueValidation` used by both the client editor and authoritative server save path.

### Changed

- Dialogue saves are blocked client-side when validation contains errors and are independently revalidated on the dedicated server. Warnings remain visible but do not prevent intentionally unusual dialogue graphs.
- Service selection automatically chooses the first available known target when switching to a targeted service whose current target is invalid or empty.
- Preview mode assumes conditions pass and exposes condition/action/service summaries through tooltips; it never mutates player data, executes actions, teleports, starts quests or opens live services.
- Network protocol is now **51** because the dialogue-editor open payload carries bounded service-target entries. NPC definition schema remains **6**, NPC placement schema remains **3**, and NPC dialogue schema remains **1**.
- Client and server must use the exact same `1.8.0-dev9.3` build.

### Scope

- This completes the planned Dialogue Editor 2.0 preview, graph validation and first module-aware target-browser slice. Fully typed parameter widgets and copy/duplicate conveniences can be added later; the next major planned phase is NPC Shops & Paid Services.

## 1.8.0-dev9.2

### Added

- Added the visual nested-condition page for Dialogue Editor 2.0. Every choice condition is displayed as a bounded pre-order tree with the selected node, depth and stable path visible in the editor.
- Added selection navigation, child creation, deletion and deterministic sibling reordering across the full condition tree.
- Added one-click AND, OR and NOT wrappers. Adding a child to a leaf safely converts that leaf into an equivalent AND group containing the original condition plus a new `always` child.
- Added registered condition-type selection and per-type starter-parameter restoration for every selected tree node rather than only the root condition.
- Added compact tree previews with indentation, parameter summaries and explicit warnings when legacy data stores children below a non-composite handler.

### Changed

- The Dialogue Editor now has five pages: Node, Conditions, Entry actions, Choice and Choice actions. Choice keeps graph/service routing compact and opens the dedicated condition tree for availability logic.
- Condition editing now preserves arbitrary existing `all`/`any`/`not` nesting while enforcing the shared Content Core maximum depth and node limits during visual mutations.
- `not` structures are guarded both client-side and during the server-authoritative save: exactly one child is required. Manually modified invalid dialogue JSON is rejected safely.
- Network protocol remains **50** because no payload changed. NPC definition schema remains **6**, NPC placement schema remains **3**, and NPC dialogue schema remains **1** because the existing condition-tree data model is reused unchanged.
- Client and server should use the exact same `1.8.0-dev9.2` build.

### Scope

- This slice completes visual nested-condition composition. Rich typed parameter widgets, dialogue preview/testing and module-specific target browsers remain follow-up Dialogue Editor 2.0 work.

## 1.8.0-dev9.1

### Added

- Began **Dialogue Editor 2.0** with four compact pages: Node, Entry actions, Choice and Choice actions.
- Added full GUI editing for all node-entry actions and all choice actions instead of exposing only the first choice action. Each bounded action list supports selection, addition, deletion and deterministic reordering.
- Added server-synchronised catalogues for every currently registered content condition, content action and NPC service. Editor buttons now cycle valid registered IDs rather than requiring administrators to remember or type them manually.
- Added visual graph-target selection across the dialogue's current node IDs and visual service selection including a safe `none` option.
- Added context-sensitive starter parameters for built-in conditions/actions and service-target guidance for warps, quests, minigames and dungeons.
- Added dialogue enabled/disabled editing and safe cleanup of choice links that target a deleted node.

### Changed

- Network protocol is now **50** because the dialogue-editor open payload carries the registered condition/action/service catalogues.
- NPC definition schema remains **6**, NPC placement schema remains **3**, and NPC dialogue schema remains **1** because this build only exposes capabilities already supported by the existing dialogue data model.
- Existing dialogue JSON remains compatible. Existing nested `all`/`any`/`not` condition children are retained when their parent condition is edited; a visual nested-condition builder remains later Dialogue Editor 2.0 work.
- Dialogue saves remain server-authoritative and continue to reject unknown action, condition and service IDs.
- Client and server must use the exact same `1.8.0-dev9.1` build.

### Scope

- This slice completes multi-action editing and registered-type selection. Visual nested condition trees, dialogue preview/testing and richer target pickers remain follow-up work.

## 1.8.0-dev9

### Added

- Added the first **NPC Functions** foundation with a dedicated eighth NPC editor page. Every reusable NPC template can now store a descriptive role, an interaction mode and up to eight named functions.
- Added fourteen role presets: Citizen, Quest Giver, Merchant, Auctioneer, Postmaster, Healer, Banker, Warp Master, Minigame Host, Dungeon Master, Guard, Trainer, Blacksmith and Innkeeper.
- Added three player interaction modes: the existing dialogue/fallback-text route, one direct service and a generated service-selection menu.
- Added a server-synchronised NPC function menu. Each entry is preflight-validated against the existing extensible NPC service registry and unavailable entries are disabled with their validation reason.
- Added editor-side service selection from the currently registered built-in and module-provided services. Role presets may create a sensible first function when their service is available.
- NPC Manager searches now also match NPC role IDs.

### Changed

- NPC definition schema is now **6** for roles, interaction modes and function lists. Existing definitions migrate to `citizen` plus `dialogue`, preserving their current dialogue link or one-line fallback behavior exactly.
- Network protocol is now **49** for the expanded NPC editor payloads and the new function-menu/use payloads.
- NPC placement schema remains **3**. NPC dialogue, Quest, Minigame, Dungeon and Content Progression schemas remain unchanged.
- Direct function execution remains server-authoritative: current module state, NPC state, dimension, distance, reputation, service permissions and service targets are revalidated at use time. Client requests are accepted only for NPCs currently configured in service-menu mode.
- Client and server must use the exact same `1.8.0-dev9` build.

### Scope

- This dev9 slice establishes roles and service routing. The larger Dialogue Editor 2.0 work, richer role-specific configuration and the planned fixed-price NPC Shop remain follow-up work.

## 1.8.0-dev8.3

### Added

- Rebuilt the NPC administrator interface into a compact seven-page editor sized for smaller GUI scales: Identity, Behavior, Relations, Stats, Loadout, Schedule and Respawn. The dialogue editor is now two compact pages, and the model browser uses a searchable three-column grid with paging.
- Added a real server-synchronised NPC loadout container. Six visual equipment slots and nine loot slots are edited against the player's live inventory with normal pickup/hotbar/shift-click interactions while configuration copies never consume the player's items.
- Added a compact remote NPC Manager with separate Placements and Templates tabs, search, paging, spawn-from-template, remote edit, delete, teleport-to, bring-to-player, copy and force-respawn actions.
- Added explicit NPC attitudes toward players and up to sixteen per-faction relations (`friendly`, `neutral`, `hostile`). Hostile managed mobs acquire players or NPCs from configured factions, path toward them and use a basic server-authoritative melee loop even when their vanilla model has no suitable combat goal.
- Added per-placement respawn settings: enabled state, durable wall-clock delay, dimension, coordinates and yaw. Automatic respawn survives a server restart; administrators can also force an immediate respawn remotely.
- Added manual gravity simulation for static No-AI NPC shells. Gravity-enabled, non-flying NPCs settle onto terrain and persist the landed position instead of being snapped back to their old floating coordinate.
- The NPC Tool now resolves entity interaction before block/item fallback. Right-clicking a managed NPC opens its editor directly, while normal empty-space use opens the NPC Manager and sneak-use retains linked copy/paste.

### Changed

- Every managed NPC now uses only its configured SSU nine-slot loot table. The former custom-loot toggle is removed from the editor; an empty table deliberately drops nothing and native entity loot is always suppressed.
- Visual equipment is always excluded from death loot and remains cosmetic only. Equipment copies retain visual stack identity but do not contribute armor, health, attack, blocking, gliding or other gameplay behavior.
- Reusable NPC definitions are the template library automatically: creating an NPC creates one template plus one placement, linked copies and template spawns reuse it, and unused templates may be deleted from the manager.
- NPC definition schema is now **5** for player/faction attitudes. NPC placement schema is now **3** for durable respawn configuration and state.
- Network protocol is now **48** for the compact editor, relation, respawn, loadout-container and remote-manager payloads.
- NPC dialogue, Quest definition/journal, Minigame definition/recovery, Dungeon definition/recovery and Content Progression schemas remain unchanged.
- Existing NPC definitions/placements migrate with neutral relations and their current position as the default respawn anchor. Existing schema-4 equipment, loot and schedules are preserved.
- Client and server must use the exact same `1.8.0-dev8.3` build.

## 1.8.0-dev8.2

- Compile-only Minecraft 26.2 API hotfix for the NPC editor service and NPC tool.
- Replaced the removed `ServerPlayer#serverLevel()` calls with the covariant `ServerPlayer#level()` accessor in `NpcEditorService` and `NpcToolManager`.
- Declared the registry lookup explicitly as `HolderLookup.Provider`, which also resolves the cascading `NpcItemCodec.encode(...)` and `decode(...)` type errors after the invalid level accessor.
- Network protocol remains 47 and all storage schemas remain unchanged.
- Client and server must use the exact same `1.8.0-dev8.2` build.

## 1.8.0-dev8.1

- Compile-only Minecraft 26.2 clock API hotfix for NPC schedules.
- Replaced removed `Level#getDayTime()` calls with `Level#getDefaultClockTime()` in the NPC editor and runtime manager.
- Network protocol remains 47 and all storage schemas remain unchanged.
- Client and server must use the exact same `1.8.0-dev8.1` build.

## 1.8.0-dev8

### Added

- Rebuilt the NPC administrator editor into six focused tabs: Identity, Behavior, Stats & Faction, Equipment, Loot Table and Schedule.
- Replaced typed NPC model IDs with a searchable server-validated model picker containing registered vanilla and modded living entity types. Unsafe native boss shells remain excluded.
- Replaced typed equipment item IDs with six visual ghost item slots for main hand, offhand, head, chest, legs and feet. Administrators drag-copy items from their live inventory; the player item is never consumed. Exact stack components such as names, dyes, trims, custom models and visual enchantment glint survive save/reload.
- NPC equipment is now strictly visual. Runtime display copies remove vanilla attribute, weapon, attack-range, blocking, glider and death-protection behavior while retaining cosmetic identity and glint. All mob equipment drop chances are forced to zero and exact-stack death-drop filtering provides an additional safety layer. Equipment never contributes armor, health or damage and never becomes NPC loot.
- Added an optional nine-slot custom NPC loot table. Each filled slot stores item/count plus an independent 0.01%-100.00% drop chance, and the complete table can be rolled 1-100 times per death so several entries and repeated copies may succeed together.
- Added normal-gravity, swimming and flying behavior controls. Swimming NPCs retain air and can use water movement; flying schedules use direct three-dimensional travel and ignore gravity while airborne. Static gravity-enabled NPCs may settle downward naturally and persist their settled placement instead of repeatedly snapping back into the air.
- Added per-placement schedules with up to sixteen sorted time entries. Every entry defines a Minecraft clock time, world target, yaw, speed, walk/teleport travel and an arrival activity (`idle`, `look_around` or visual `chop_tree`).
- Linked NPC copies now shift schedule targets relative to the pasted placement instead of reusing the source NPC's absolute route coordinates.

### Compatibility

- NPC definitions migrate from schema 3 to schema **4**. Existing typed equipment IDs migrate as visual slots; their legacy equipment drop chance is discarded. Every definition receives nine stable loot-slot positions with custom loot disabled by default.
- NPC placements migrate from schema 1 to schema **2** for optional per-placement schedules. Existing placements receive an empty disabled schedule.
- Network protocol is now **47** because the NPC editor payloads carry movement, exact ItemStacks, fixed loot-slot, schedule and model-picker data. Empty slots and client submissions use the bounded optional-untrusted ItemStack stream codec.
- NPC dialogue schema, Quest definition/journal schemas, Minigame definition/recovery schemas, Dungeon definition/recovery schemas and Content Progression remain unchanged.
- Existing claims, regions, economy, mail, Auction House, quest, minigame and dungeon data remain unchanged.
- Client and server must use the exact same `1.8.0-dev8` build.

## 1.8.0-dev7

### Added

- Added the independent, data-driven **Customized Dungeon Framework** without hard NPC, Quest or Minigame dependencies. Dungeon Core depends on Content Core, storage, permissions and the existing Regions module.
- Added persistent dungeon definitions with player limits, countdown/time-limit/post-run timing, lives, prerequisites, transactional participation/completion/failure rewards, ordered stages and one or more reusable region-backed arena slots.
- Added four generic ordered stage types: administrator/manual progression, entity kill counts, proximity checkpoints and timed survival.
- Added required SSU region ownership for every dungeon arena. Kill-count progression only accepts defeated entities inside that configured region, preventing out-of-arena farming.
- Added server-authoritative queues that form runtime parties, automatic and administrator-forced starts, optional late joining, arena reservation, run countdown/running/post-run/reset phases and live stage/life status.
- Added lobby, start, spectator and named checkpoint locations; deaths consume configurable lives, respawn at the latest reached checkpoint and eliminate exhausted players to spectator position. Zero configured lives means unlimited lives.
- Added completion/failure handling, party-wide announcements, Content Core events and at-most-once transaction keys for rewards.
- Added optional SSU region snapshot restoration after a run, persistent player return-location recovery and durable unsafe-arena markers. Interrupted or failed resets stay blocked after restart until safely restored or explicitly released.
- Added a player-facing Dungeon Lobby and four-page administrator Dungeon Editor for general lifecycle, arenas/checkpoints, ordered stages and Content Core requirements/rewards.
- Added optional NPC dialogue services `dungeon_lobby` and `dungeon_queue` without adding an NPC dependency to Dungeon Core.
- Added `/ssu dungeon` player/admin commands, Admin Center/dashboard integration, `ssu.npcs.service.dungeons` and enforcement of the existing dungeon use, queue and admin permissions.
- Added `dungeon_queue_joined`, `dungeon_queue_left`, `dungeon_started`, `dungeon_stage_completed`, `dungeon_completed` and `dungeon_failed` Content Core events for independent Quest integration.
- Compacted the Admin Tools page when six tools are present so the Dungeon Editor and footer controls remain inside the dashboard panel.

### Compatibility

- Added dungeon-definition schema **1** under `simpleserverutilities/dungeons/definitions` and dungeon recovery schema **1** in `simpleserverutilities/dungeons/recovery.json`.
- Network protocol is now **46** for the bounded dungeon lobby/editor payloads.
- Minigame definition/recovery schemas remain 1; NPC definition schema remains 3; NPC placement/dialogue, Quest definition/journal and Content Progression schemas remain 1.
- Existing claims, regions, snapshots, economy, mail, Auction House, NPC, Quest and Minigame data remain unchanged.
- Client and server must use the exact same `1.8.0-dev7` build.
- This build supplies region-based instances and generic stage lifecycle. Dynamic copied instances, generated structures, configured waves/bosses, puzzle scripting, invitation parties and cutscenes remain follow-up dungeon work.

## 1.8.0-dev6

### Added

- Added the independent, data-driven **Minigame Framework** with no hard NPC, Quest or Dungeon dependency. The module depends on Content Core, storage, permissions and the existing Regions module.
- Added persistent minigame definitions containing player limits, team counts, countdown/match/post-game timing, victory mode, prerequisites, participation rewards, winner rewards and one or more reusable arena slots.
- Added arena configuration for lobby, spectator and team spawn locations, optional SSU region snapshots and independent free/reserved/resetting/blocked lifecycle states.
- Added server-authoritative queues, automatic and administrator-forced match starts, round-robin team assignment, optional late joining, countdown, running and post-game phases, score mutation, elimination and highest-score, last-team-standing or manual completion modes.
- Added a player-facing Minigame Lobby and a three-page administrator Minigame Editor for general settings, arenas/team spawns and Content Core requirements/rewards.
- Added persistent player return-location recovery. Reset-enabled arenas receive durable unsafe markers while in use; an interrupted or failed reset keeps the arena blocked after restart until it is reset successfully or explicitly released by an administrator.
- Added transactionally delivered participation and winner rewards plus `minigame_queue_joined`, `minigame_queue_left`, `minigame_started`, `minigame_won` and `minigame_completed` Content Core events for optional Quest and future Dungeon integration.
- Added optional NPC dialogue services `minigame_lobby` and `minigame_queue` without adding an NPC dependency to the minigame module.
- Added `/ssu minigame` player/admin commands, Admin Center access, the dedicated `ssu.npcs.service.minigames` permission and enforcement of the existing minigame use, queue and admin permissions.
- Voluntary match leavers are now removed from the live roster, returned safely and cannot receive abandoned-match rewards. Disconnects remain recoverable and rejoin the active match as eliminated spectators.

### Compatibility

- Added minigame-definition schema **1** below `simpleserverutilities/minigames/definitions` and minigame recovery schema **1** in `simpleserverutilities/minigames/recovery.json`.
- Network protocol is now **45** for the bounded lobby and editor payloads.
- NPC definition schema remains 3; NPC placement/dialogue schemas, Quest definition/journal schemas and Content Progression remain schema 1.
- Existing claims, regions, economy, mail, Auction House, NPC and quest data remain unchanged.
- Client and server must use the exact same `1.8.0-dev6` build.
- This build provides the generic queue/arena/match framework. Concrete rule handlers such as Spleef, parkour or team deathmatch are intentionally separate follow-up content implementations.

## 1.8.0-dev5.1

- Compile-only Minecraft 26.2 API hotfix for Advanced NPC equipment.
- `NpcManager` now applies per-slot equipment drop chances only when the runtime shell is a `Mob`; `LivingEntity` itself no longer exposes `setDropChance(...)`.
- Non-mob living shells still receive configured equipment, while unsupported per-slot drop chances are safely ignored.
- Network protocol remains 44 and all storage schemas remain unchanged.
- Client and server must use the exact same `1.8.0-dev5.1` build.

## 1.8.0-dev5

### Added

- Started the Advanced NPC phase with schema-3 reusable NPC templates while preserving independent placements, dialogue graphs and optional Quest Core integration.
- Added optional server-authoritative attribute overrides for maximum health, movement speed, attack damage, armor, armor toughness, follow range, knockback resistance and entity scale. Blank values inherit the selected living entity model's native attributes.
- Added persistent equipment for main hand, offhand, head, chest, legs and feet, with validated vanilla/modded item registry IDs and a configurable shared drop chance. Blank slots inherit native equipment; `minecraft:air` explicitly clears a slot.
- Added faction and reputation interaction gates backed by the independent Content Progression Core, including a configurable denied message and reputation loss when a player attacks an NPC.
- Added a configurable home radius for native-AI NPCs. A zero radius disables the leash, while static NPCs remain tightly anchored to their saved placement.
- Replaced the former single-page NPC editor with separate Identity & Placement, Stats & Faction and Equipment pages. Invulnerability is now editable instead of being forced on by the foundation build.
- Runtime attribute and equipment changes safely respawn linked model shells so returning a field to inherit restores the model's true native value or loadout. Ordinary reconciliation never repeatedly heals damaged NPCs.

### Compatibility

- NPC definitions migrate from schema 2 to schema **3**. Existing definitions receive empty factions/equipment, native inherited attributes, a 16-block home radius and retain their prior invulnerability and dialogue behaviour.
- Network protocol is now **44** because the bounded NPC editor payloads carry the new advanced template fields.
- NPC placement schema remains 1, NPC dialogue schema remains 1, quest definition/journal schemas remain 1 and Content Progression remains schema 1.
- Client and server must use the exact same `1.8.0-dev5` build.
- Custom inventories, dedicated combat-goal profiles, custom loot tables, routines, animations and external skin rendering remain scheduled for later Advanced NPC iterations.

## 1.8.0-dev4

### Added

- Added the first fully independent **Quest Core**. Quest definitions and player journals use isolated schema-1 storage and do not require NPC Core.
- Added an exclusive administrator-selected quest entry route: quests are opened through either the SSU dashboard or NPC dialogue services, never both. When NPC Core is disabled, the existing safe effective-mode fallback exposes the questbook through the SSU menu.
- Added persistent quest lifecycle states for available, active, ready-to-turn-in, completed and abandoned quests, including repeatability, completion counts and bounded cooldowns.
- Added event-driven objectives for generic Content Core events, plus Minecraft adapters for block breaking/placement, entity kills, player deaths and damage dealt/taken. Existing NPC and dialogue events can be used when NPC Core is enabled without creating a hard module dependency.
- Added prerequisite conditions for completed, active and ready quests, dependency-cycle validation and reference-safe quest rename/delete behaviour.
- Added transactionally delivered quest rewards. Built-in item and exact-money rewards join the existing shared permission, unlock, flag, counter and reputation actions.
- Added a server-authoritative paged Questbook with tracking, abandon, start, turn-in, reward preview, objective progress, completed history and administrator edit/delete access.
- Added a structured administrator Quest Editor for basic information, prerequisites, repeatability/cooldowns, objectives and rewards, plus `/ssu quest` management commands.
- Added optional NPC dialogue services `questbook`, `quest_offer` and `quest_turn_in`; these are available only while Quest Core is active and still enforce the exclusive access mode and all NPC/quest permissions.
- Added dedicated `ssu.npcs.service.quests` permission while retaining separate `ssu.quests.use`, `ssu.quests.track`, `ssu.quests.abandon` and `ssu.quests.admin` controls.

### Compatibility

- Added quest-definition storage schema **1** under `simpleserverutilities/quests/definitions` and player quest-journal schema **1** under `simpleserverutilities/quests/players`.
- Network protocol is now **43** for the bounded questbook and quest-editor payloads.
- NPC definition schema remains 2, NPC placement schema remains 1, NPC dialogue schema remains 1 and Content Progression remains schema 1.
- Existing NPCs, dialogues, claims, regions, mail, Auction House, economy and all other stored data remain compatible.
- Client and server must use the exact same `1.8.0-dev4` build.

## 1.8.0-dev3

### Added

- Added the first Dialogue & Services slice of NPC phase 3 while keeping NPC Core independent from the future quest module.
- Added persistent reusable graph-based dialogue definitions with bounded nodes, choices, conditions, transactional actions, node-entry actions, next-node routing, hidden locked choices and explicit close behaviour.
- Added a server-authoritative player dialogue screen with five-minute sessions, distance checks, replay-resistant request IDs and safe session cleanup on close, logout, module stop and reload.
- Added a dedicated administrator dialogue graph editor linked from the existing NPC editor. Linked NPC copies continue to share the same reusable dialogue definition.
- Added an extensible NPC service registry with side-effect-free preflight validation and built-in services for Mail, Auction House, SSU Menu, healing, server spawn and named warps. Existing module and destination permissions remain authoritative.
- Added `dialogue_opened`, `dialogue_choice` and `npc_service_used` Content Core events for future quests, statistics, minigames and dungeons without introducing hard module dependencies.
- Added separate NPC dialogue and service permissions, including mail, Auction House, SSU menu, healing and teleport services.
- Added persistent dialogue reload/save participation in `/ssu reload` and the NPC module lifecycle.

### Compatibility

- NPC definitions migrate from schema 1 to schema **2** for the optional reusable `dialogueId` link. Existing NPCs without a dialogue retain their original one-line interaction text.
- Added NPC dialogue schema **1** below `simpleserverutilities/npcs/dialogues`. NPC placement schema remains 1 and Content Progression remains schema 1.
- Network protocol is now **42** for the six bounded dialogue runtime/editor payloads.
- Client and server must use the exact same `1.8.0-dev3` build.
- External skin folders, URL/UUID skins, recolouring, advanced aura rendering and animation/routine expansion are intentionally not part of this first phase-3 test slice.

## 1.8.0-dev2.2

- Minecraft 26.2 NPC Foundation compile/API cleanup hotfix.
- Removed unreachable `ReflectiveOperationException` catches around `EntityType.loadEntityRecursive`; the current API only requires runtime-failure handling.
- Replaced deprecated `LevelReader#hasChunkAt(BlockPos)` usage in `NpcManager` with `Level#isLoaded(BlockPos)`.
- Replaced the removed `Entity#moveTo(double, double, double, float, float)` call with the current `Entity#snapTo(double, double, double, float, float)` positioning API.
- No NPC storage, protocol, permission or gameplay schema changes.
- Client and server must use the exact same `1.8.0-dev2.2` build.

## 1.8.0-dev2.1

- Fixed Minecraft 26.2 source compilation in `NpcManager`.
- Vanilla entity constants now use `EntityTypes.PLAYER`; Minecraft 26.2 moved registry object constants out of `EntityType`.
- Runtime NPC command-tag checks now use `Entity#entityTags()` instead of the removed `Entity#getTags()`.
- NPC behaviour, network protocol 41 and both NPC storage schemas remain unchanged.
- Client and server must use the exact same `1.8.0-dev2.1` build.

## 1.8.0-dev1.1

- Fixed the startup failure caused by `content_core` declaring the nonexistent module dependency `transaction`.
- The dependency now correctly targets the existing shared module ID `transactions`.
- No gameplay behaviour, network payloads or storage schemas changed.
- Network protocol remains 40; client and server must use the exact same `1.8.0-dev1.1` build.

## 1.8.0-dev1

### Added

- Added the module-independent Content & Progression Core with persistent per-player and world flags, counters, unlocks and reputation.
- Added extensible condition, reversible action/reward, content-event and dependency-validation foundations for independent NPC, quest, minigame and dungeon modules.
- Added exclusive quest access-mode configuration for SSU-menu or NPC delivery, including safe menu fallback while the NPC module is disabled.
- Added dedicated player and administrator permissions for NPC, quest, minigame, dungeon and shared content access.

### Compatibility

- Added Content Progression storage schema 1.
- Network protocol is 40. Existing Auction House, mailbox, economy, claim, region and all other stored data remain compatible.
- Client and server must use the exact same `1.8.0-dev1` build.

## 1.7.0-dev1.5.1

- Fixed a Java compilation error in Auction House blacklist item-ID validation.
- Registry lookup lambdas now use a distinct `registeredItem` parameter instead of redeclaring the enclosing local variable `item`.
- Applied the same unambiguous lambda naming to the blacklist display and client preview paths.
- Network protocol remains 39 and all storage schemas remain unchanged.
- Client and server must use the exact same `1.7.0-dev1.5.1` build.

## 1.7.0-dev1.5

### Changed

- Replaced the held-item blacklist shortcut with a proper inventory picker inside the administrator Blacklist page. The page renders the player's 9×3 inventory and hotbar; selecting a non-empty slot only transmits its slot index, and the server rechecks the current item before adding its base registry identifier.
- Added manual item-ID entry alongside inventory selection. The client shows a live visual preview for registered item IDs, while the server parses the identifier and rejects malformed, missing or empty items before persistence. Successfully added entries remain visible with their real item icon and name in the blacklist results.
- Administrator cancellation and seizure now require a non-empty reason through a dedicated confirmation dialog. The reason is included in seller mail and server logs.
- Seizure reasons are stored durably with pending seizures so crash/restart mail recovery preserves the exact administrative explanation.

### Compatibility

- Auction House listings migrate from schema 2 to schema **3** for the durable seizure reason. Auction House settings remain schema 2 and purchase journals remain schema 1.
- Network protocol is now **39** because Auction House action text can carry the required administrative reason.
- Existing listings, blacklist entries, purchases, mailbox data and all non-Auction-House schemas remain compatible.
- Client and server must use the exact same `1.7.0-dev1.5` build.

## 1.7.0-dev1.4

### Added

- Added a searchable administrator-only **Admin Overview** containing every active Auction House listing.
- Administrators with `ssu.auction_house.admin` can cancel any active listing and return its unsold items to the seller by system mail.
- Administrators can seize an active listing. The unsold items are delivered to the administrator's mailbox and the seller receives a system-mail notice naming the administrator.
- Added a persistent item blacklist managed from an administrator-only **Blacklist** page. Administrators can add the item held in either hand, remove existing entries, or blacklist the item represented by a selected active listing.
- New listings are rejected server-side when their base item registry identifier is blacklisted. Existing listings remain active so an administrator can deliberately return or seize them.
- Administrative cancellation, seizure and blacklist changes are written to the server log.

### Compatibility

- Auction House settings migrate from schema 1 to schema **2** for the persistent blacklist. Listings migrate from schema 1 to schema **2** for durable seizure ownership/recovery; purchase journals remain schema 1.
- Network protocol is now **38** because Auction House entry/action identifiers can carry full modded item registry identifiers and the administrator modes are synchronized to the client.
- Existing listings, purchases, economy journals, mailbox data and every non-Auction-House schema remain compatible.
- Client and server must use the exact same `1.7.0-dev1.4` build.

## 1.7.0-dev1.3

- Fixed the dashboard portrait-sidebar frame using an absolute bottom coordinate as an outline height. The frame now derives its height from the responsive dashboard panel and remains contained at every in-game GUI scale.
- Expanded and reorganized the Create Auction container screen.
- Added a clearly highlighted auction-offer slot and removed the redundant “Drop one stack here” text.
- Added individual slot backgrounds plus framed 9×3 inventory and 9×1 hotbar grids.
- Spaced the price, quantity, duration, tax and action controls more clearly without changing listing behaviour.
- Network protocol remains 37 and all Auction House/storage schemas remain unchanged.
- Client and server must use the exact same `1.7.0-dev1.3` build.

## 1.7.0-dev1.2

- Source compilation hotfix for Java generic type inference in `AuctionSort`.
- Added explicit `AuctionListingView` lambda parameter types to the ID, quantity, price and expiry comparators so Java no longer infers `Object` and rejects `listing()`.
- Sorting behaviour is unchanged. Network protocol remains 37 and all Auction House/storage schemas remain unchanged.
- Client and server must use the exact same `1.7.0-dev1.2` build.

## 1.7.0-dev1.1

- Source compilation hotfix for Minecraft/NeoForge 26.2: removed the unavailable `BlockTags.SAPLINGS` field from Auction House category classification.
- Saplings remain classified as Plants through the existing localized item-name/registry-path matcher.
- Network protocol remains 37 and all Auction House/storage schemas remain unchanged.
- Client and server must use the exact same `1.7.0-dev1.1` build.

## 1.7.0-dev1

### Added

- Added the first complete **Auction House** module with persistent schema-1 listings, purchase journals and global settings under the world `simpleserverutilities/auction_house` folder.
- Added a permission-aware Auction House dashboard tile. `ssu.auction_house.dashboard` controls only the dashboard entrance, while `ssu.auction_house.access` controls actual use from every trusted server entry point so future NPC access can remain available independently.
- Added searchable and category-filtered browsing for Weapons, Armor, Tools, Building Blocks, Plants, Seeds, Food, Enchants, Potions, Ores, Metals, Logs, Machines and Miscellaneous.
- Added display-name substring search and server-side sorting by item name, quantity, unit price and remaining time.
- Added long selectable listing rows with the real item stack, normal item hover information, name, unit price, remaining quantity, seller and time remaining. The result area supports paging and mouse-wheel navigation.
- Added a two-step purchase flow with a manual quantity field, exact formatted total price and a final **Buy now** action.
- Added an inventory-backed selling screen. Players can drag a representative item into the offer slot, see the total matching quantity in their inventory, enter quantity and unit price, select 12/24/48 hours and create the listing.
- Added **My Auctions** with remaining quantities and cancellation. Cancelled and expired listings return their remaining items through system mail.
- Added mailbox delivery for purchases. Items are split by their real maximum stack size, limited to nine stacks per mail and automatically continued across multiple mails.
- Added immediate seller proceeds through a pre-funded Auction House mail containing item, buyer, date, quantity, gross value, historical tax, tax amount and net proceeds.
- Added a configurable global sale tax with 0.1% precision for players with `ssu.auction_house.admin`.
- Added `ssu.auction_house.max_active` for rank/player-specific simultaneous listing limits and the `ssu.auction_house.*` wildcard.
- Added a client purchase confirmation sound using the note-block pling after a transaction fully commits.

### Reliability and security

- All searches, listing creation, inventory extraction, price calculations, balance checks, quantity reservations, tax calculations, purchases, cancellations and deliveries are server-authoritative.
- Direct Auction House packets require a short-lived server-granted session. Dashboard sessions require both access permissions; the public trusted server/NPC entry requires only general Auction House access.
- Purchase processing is journaled and idempotent across listing reservation, buyer capture, seller-mail funding, seller delivery and buyer delivery. Recovery consults the economy idempotency journal before retrying or rolling back a step.
- Listing and purchase reservation files must be persisted before items or money can be committed. Failed listing persistence restores the player's inventory and failed purchase preparation takes no money.
- Completed/rolled-back purchase journals are retained for thirty days before maintenance cleanup.
- Auction House operation pauses when Mail or Economy is unavailable; stored listings remain intact.

### Compatibility

- Network protocol is now **37** for the Auction House payloads and dashboard snapshot extension.
- Auction House listing, purchase and settings storage start at schema 1.
- Existing claims, regions, mailboxes, economy, permissions, holograms, statistics, map markers, player preferences and aerial map cache data are unchanged.
- Client and server must use the exact same `1.7.0-dev1` build.

## 1.6.0-dev12.11

### Fixed

- Filled the horizontal and vertical gaps inside the World Map marker context panel so terrain no longer shows through between Edit/Delete/Add and Close.
- Corrected the yellow World Map legend swatch from **Markers** to **Player**. The player indicator is always shown independently of the personal marker layer toggle.

### Compatibility

- Network protocol remains 36. Player UI preferences remain schema 7, map markers remain schema 1 and the aerial map cache remains format 5 under `map-cache-v4`.
- No server data, marker data, payload or client-cache migration is required. Client and server should use the exact same dev12.11 build.

## 1.6.0-dev12.10

### Added

- Added a personal **Live terrain** radius setting under Settings → World map. It cycles through 1, 2, 4, 6, 8, 12, 16, 24 and 32 chunks and defaults to 8 chunks.
- The validated live-update radius is included in both the menu settings snapshot and recurring minimap state, so it is restored after reconnecting without requiring the dashboard to be opened first.

### Changed

- Confirmed and retained the existing asynchronous, server-isolated client disk cache for explored aerial-map tiles under `map-cache-v4` (cache format 5).
- Only loaded chunks inside the player's configured live-update radius are recaptured. Cached terrain outside that radius remains available from disk but is not continuously refreshed.
- Off-radius and inactive-dimension atlas tiles are evicted from client RAM after ten seconds without use. Tiles temporarily loaded for a distant World Map view remain in memory while actively viewed, then return to disk-only storage after the view is closed.
- Raised the hard transient atlas ceiling to support a complete large World Map viewport without LRU thrashing; proximity and idle eviction remain the normal memory boundary.
- Strengthened local, broad and macro topographic shading and expanded the ground-light range so hills, terraces and valleys read more clearly. The renderer fingerprint is now `atlas-topographic-v6`, causing explored tiles to be rebuilt once with the improved relief while keeping the same cache format and folder.

### Fixed

- Reworked the World Map marker context frame so its dark gutter and double outline are rendered after the map widget. The map can no longer paint over the frame around Add/Edit/Delete/Close.

### Compatibility

- Network protocol is now 36 because the validated map-update radius is synchronized to clients.
- Player UI preferences migrate from schema 6 to schema 7 with an 8-chunk default.
- Map markers remain schema 1 and the aerial cache remains format 5 under `map-cache-v4`.
- No server world, claim, region, marker or other gameplay-data migration is required. Client and server must use the exact same dev12.10 build.

## 1.6.0-dev12.9

### Fixed

- Shifted the complete left map toolbar panel three pixels to the right on both the World Map and Claim Map.
- The panel background now moves together with its controls, restoring equal three-pixel padding on both sides of every 28-pixel toolbar button.
- Kept the outer map shell, map viewport, top bar, information panels and right-side Close controls unchanged.

### Compatibility

- Network protocol remains 35. Player preferences remain schema 6, map markers remain schema 1 and the aerial map cache remains format 5 under `map-cache-v4`.
- No server data, marker data, payload or client-cache migration is required. Client and server should use the exact same dev12.9 build.

## 1.6.0-dev12.8

### Changed

- Doubled the aimed in-world marker name/distance text again.
- Tightened the translucent label background around the enlarged text, especially above the glyphs, while retaining enough lower space for descenders.

### Compatibility

- Network protocol remains 35 and all persistent schemas remain unchanged.

## 1.6.0-dev12.7

### Changed

- Halved the in-world marker-disc diameter again while preserving the filled billboard style, colour and dark contrast rim.
- Increased the aimed marker name/distance label to 2.25 times its former text and panel size so it remains clearly readable at long range.
- Moved the World Map and Claim Map Back controls into the bottom of their left tool columns.
- Moved the Close controls to the top-right corner of both map shells.
- Shifted every left-column map control three pixels to the right for improved alignment.

### Compatibility

- Network protocol remains 35. Player preferences remain schema 6, map markers remain schema 1 and the aerial map cache remains format 5 under `map-cache-v4`.
- No server data, marker data, payload or client-cache migration is required. Client and server should use the exact same dev12.7 build.

## 1.6.0-dev12.6

### Changed

- Added bounded distance compensation to in-world marker discs and aimed labels so their apparent screen size remains stable beyond sixteen blocks.
- Replaced distance-capped world-space aim tolerance with a fixed angular tolerance for reliable long-range marker labels.

### Compatibility

- Network protocol remains 35 and all persistent schemas remain unchanged.

## 1.6.0-dev12.5

### Added

- Looking directly at an in-world marker now reveals a compact camera-facing label containing the marker name and live distance. Only the best marker under the crosshair is labelled, avoiding world clutter.
- World Map cursor information now includes the mapped surface biome and block alongside X/Y/Z in both the right information panel and compact bottom status bar.
- Surface block and biome registry ids are persisted per explored atlas column so the information remains available for cached terrain outside currently loaded chunks.

### Changed

- Upgraded the client-only aerial map cache from format 4 to format 5 and moved it to `map-cache-v4` for the new biome/block metadata. Existing terrain is rebuilt automatically as it is explored or loaded from chunks.

### Compatibility

- Network protocol remains 35. Player preferences remain schema 6 and map markers remain schema 1.
- No server/world data migration is required. The only migration is the disposable client aerial cache. Client and server should use the exact same dev12.5 build.

## 1.6.0-dev12.4

### Changed

- Replaced the large hollow in-world marker ring with a solid camera-facing coloured disc.
- Halved the marker icon diameter from approximately 1.16 blocks to 0.58 blocks while preserving a thin dark contrast rim.
- Kept the independent full-height marker beam, map markers, minimap markers and all visibility/range settings unchanged.

### Compatibility

- Network protocol remains 35. Player preferences remain schema 6, map markers remain schema 1 and the aerial map cache remains format 4.
- No server data, marker data, payload or client cache migration is required. Client and server should use the exact same dev12.4 build.

## 1.6.0-dev12.3

### Changed

- Replaced the horizontal in-world marker ring with a camera-facing circular marker icon centred on the saved marker coordinate. The vertical beam remains independent and still spans the active dimension height.
- Expanded the marker editor from twelve mixed presets to the complete sixteen Minecraft legacy colours.
- Marker colour buttons now render their actual colour across the button face, with a bright selection outline and named/hex hover tooltips.
- Added a larger double-outline frame around the complete World Map marker context menu so Add/Edit/Delete and Close visually belong to one compact panel.

### Compatibility

- Network protocol remains 35. Player preferences remain schema 6, map markers remain schema 1 and the aerial map cache remains format 4.
- No server data, marker data, payload or client cache migration is required. Client and server should still use the exact same dev12.3 build.

## 1.6.0-dev12.2

### Fixed

- Fixed the World Map marker context-menu buttons not responding to left clicks.
- Marker context actions are now resolved by the parent screen before the underlying map widget receives the event.
- **Add marker**, **Edit**, **Delete/Confirm** and **Close** now use explicit modal hitboxes that match the rendered button bounds.
- Clicking outside the marker menu closes it and consumes the click, preventing accidental click-through to the map or toolbar.

### Compatibility

- Network protocol remains 35. Player preferences remain schema 6, map markers remain schema 1 and the aerial map cache remains format 4.
- No server data, marker data, payload or client cache migration is required. Client and server should still use the exact same dev12.2 build.

## 1.6.0-dev12.1

### Fixed

- Restored World Map right-click marker actions by opening the marker context menu directly on the right-button press instead of waiting for the former right-drag release path.
- World Map and Claim Map panning now use the held middle mouse button. This keeps right-click exclusively available for marker create/edit/delete actions on the World Map.
- Added screen-level middle-drag forwarding so dragging remains active even when Minecraft transfers focus between the map widget and its parent screen during the gesture.
- Raised the bottom Back/Close controls on both full-screen maps by 5 pixels.
- Removed the duplicate World Map/Claim Map switch button from the bottom control row; the existing left toolbar switch remains.
- Renamed the World Map marker-manager tooltip to `Manage markers` and the terrain rebuild tooltip to `refresh`.
- Updated both map status hints to describe middle-button panning.

### Compatibility

- Network protocol remains 35. Player preferences remain schema 6, map markers remain schema 1 and the aerial map cache remains format 4.
- No server data, marker data or client cache migration is required. Client and server should still use the exact same dev12.1 build.

## 1.6.0-dev12

### Added

- Added persistent personal map markers with name, colour, dimension and editable X/Y/Z coordinates. Markers are stored per player in isolated schema-1 JSON records below `simpleserverutilities/map_markers/players` and are limited to 256 per player.
- Added a World Map right-click workflow. Right-clicking terrain opens a compact context menu to create a marker; right-clicking an existing marker opens Edit/Delete actions with delete confirmation.
- Marker creation derives the initial Y coordinate as the first free block directly above the clicked mapped surface. The server verifies the height from `WORLD_SURFACE` whenever the clicked chunk is loaded and otherwise retains the cached mapped height supplied by the client.
- Added a paged remote marker manager for editing or deleting markers without travelling to their position.
- Added coloured marker symbols to the World Map and minimap, plus a coloured in-world circle at the saved coordinate.
- Added optional distance-limited vertical marker beams spanning the active dimension's complete build height. Personal beam range defaults to 128 blocks, is adjustable from 16 through 512 blocks and is clamped server-side.
- Added independent personal settings for World Map markers, minimap markers, in-world marker circles and marker beams.
- Added shared map day/night treatment. World Map, Claim Map and minimap now darken with the client world's sky-darkness while cached block light keeps surface light sources visible at night.
- Added block-light data to the persistent client aerial atlas cache and upgraded its on-disk format to version 4 under `map-cache-v3`.

### Changed

- Redesigned the World Map as a near-full-height professional map workspace with compact icon controls, hover tooltips, a dedicated layer/location/selection panel, a compact status bar and direct marker management.
- Redesigned the Claim Map with the same visual shell, compact tooltips and a clearer right-side claim/action panel while preserving the existing server-authoritative claim workflows.
- Strengthened local, terrace, hill and broad-scale topographic contrast so height transitions are more legible without changing terrain colours or map coordinates.
- Minimap rendering now includes personal markers and uses the same day/night and illuminated-surface treatment as the larger maps.
- Player UI preference schema increases from 5 to 6 for marker visibility and beam settings. Existing players migrate with all marker views enabled and a 128-block beam distance.
- Network protocol increases from 34 to 35 for marker actions/synchronization and the expanded UI-settings snapshot. Client and server must use the exact same dev12 build.

### Compatibility

- Existing claims, regions, permissions, mail, economy, hologram, statistics and Block Information data remain compatible.
- Region records remain schema 4, holograms schema 4, mailboxes schema 3, statistics schema 1 and border preferences schema 2.
- The aerial atlas cache is client-only and is rebuilt automatically in the new version-4 cache format; no world-save migration is required.

## 1.6.0-dev11.4

- Separates the compact Block Information indicator from the recommended-tool hint.
- The green/red vertical bar now answers only whether the looked-at block can be harvested correctly with the currently held item: green when drops are obtainable (or no correct tool is required), red when a correct tool is required but not held, and red for unbreakable blocks.
- The tool icon remains an independent minimum required/recommended tool hint: optional efficiency recommendations such as a wooden shovel for dirt no longer turn the bar red when the player uses an empty hand.
- Required minimum tiers remain unchanged, for example diamond pickaxe for obsidian; recommended categories still use their lowest-tier icon to communicate that any tier is suitable.
- Debug output continues to distinguish `Required` from `Recommended`.
- Network protocol remains 34 and all stored-data schemas remain unchanged.

## 1.6.0-dev11.2

- Extends Block Information with server-authoritative content previews for accessible containers and inventory-bearing objects.
- Adds strict permission `ssu.block_information.inventory`; operators do not bypass it without an explicit rank/player/wildcard grant.
- Adds integer permission `ssu.block_information.inventory.max_items` with a default of `1` and a hard range of `0` through `54` shown non-empty item stacks.
- Adds optional `ssu.block_information.inventory.full`, which overrides the numeric preview limit up to the hard 54-stack cap.
- Shows preview stacks as real Minecraft item icons with stack counts below the compact block/entity title; empty supported targets show `Empty` and additional hidden stacks are indicated with an ellipsis.
- Supports flower pots, the viewing player's own ender chest, ordinary and double chests, vanilla `Container` block entities, lecterns, campfires, item frames, armor stands, container entities and compatible modded block/entity item capabilities.
- Resolves the looked-at target again on the server, enforces reach, loaded-chunk state, SSU claim/region interaction protection and container locks, and never exposes another player's inventory.
- Refuses to inspect unopened random-loot containers so the preview cannot generate or reveal loot before legitimate access.
- Sanitizes preview stacks to item type and count only; nested container contents, books, maps and other per-stack metadata are not transferred.
- Refreshes previews four times per second but sends a packet only when the effective target or bounded contents change.
- Raises network protocol from 33 to 34; client and server must use the exact same dev11.2 build. Stored data schemas remain unchanged.

## 1.6.0-dev11.1

- Compacts the ordinary Block Information HUD to the translated block/entity name plus an optional real Minecraft item icon for the minimum required harvesting tool.
- Retains the green/red vertical tool-validity indicator.
- Moves registry ID, hardness, required-tool text and blockstate properties into a separate personal debug mode.
- Adds strict permission `ssu.block_information.debug`; operators do not bypass this gate unless their rank/player/wildcard permissions explicitly grant it.
- Adds entity names to the compact overlay and entity registry IDs to debug mode.
- Raises Player UI Preferences schema from 4 to 5; debug mode migrates OFF.
- Raises network protocol from 32 to 33; client and server must use the exact same dev11.1 build.

## 1.6.0-dev11

- Added the server-controlled **Block Information** module. Players can enable or disable it under Personal Settings, while the module switch and strict `ssu.block_information.use` permission remain the hard server gates.
- Added a lightweight top-centre block overlay showing the translated block name, full registry identifier, readable namespace/mod name, hardness, correct-tool status and bounded sorted block-state properties. All block inspection is local client state; the server only synchronizes the effective permission/toggle state and sends no per-look packets.
- Added persistent administrator-defined **Custom Player Statistics** for blocks broken, blocks placed, entities killed, player deaths, damage dealt, damage taken and online play time. Registry-aware event types support either `*` or one exact block/entity identifier.
- Added a paged Statistics administration page with search plus create, edit, pause, resume, reset and double-confirmed delete actions. The dedicated editor validates IDs, display names, event types, filters and units on both client and server.
- Added indexed event routing: enabled statistic definitions are grouped by event type and exact/wildcard target so gameplay events do not scan every definition. Damage is stored in exact hundredths; ordinary counters and play time use whole units.
- Added isolated statistic storage below `simpleserverutilities/statistics`: schema-1 `definitions.json` and per-player schema-1 value records. Dirty player records are queued in batches every five seconds, on logout, on module disable/reload and during shutdown through the existing single-writer storage service.
- Added corruption handling and bounded operation: malformed definition/player files are archived, definition IDs are unique and limited to 128, leaderboard output is bounded, additions saturate at `Long.MAX_VALUE`, and renamed/deleted/reset statistics migrate or remove their player values safely.
- Integrated custom statistics into Floating Text. Rich text/link titles accept `{{stat:<id>}}` and `{{rank:<id>}}`; scoreboard holograms accept objective `ssu:<id>` and support both personal SELF values and formatted TOP leaderboards using the hologram's existing row count and independent refresh interval.
- Added `ssu.statistics.admin`, the Statistics module switch and Block Information/Statistics descriptions to the permission and module editors.
- Increased player UI preference schema from `3` to `4` for the personal Block Information choice. Existing players migrate with the personal choice enabled, while server module and permission gates remain authoritative.
- Increased network protocol from `31` to `32` for the new dashboard fields, statistics records and editor/block-information payloads. Client and server must use the same dev11 build. Region schema remains `4`, hologram schema remains `4`, mailbox schema remains `3` and border preferences remain schema `2`.

## 1.6.0-dev10.2

- Corrected the Regions-page visibility model. Its per-region `Show`/`Disable` control is now a persistent server-owned setting instead of a personal pin belonging only to the administrator who clicked it.
- Added `borderVisible` to region records. Existing and newly created regions default to hidden until an administrator explicitly enables their border. Region storage schema is now `4`.
- Region overview payloads now contain only regions whose server-owned border setting is enabled. The player's personal Region borders switch and the strict `ssu.borders.regions.view` capability gate are still applied afterwards.
- Retired the separate per-player `REGION_FOCUS` rendering path for Regions-page actions and clear that legacy layer during synchronization, removing both duplicate rendering and the case where Disable merely returned a region to the automatic overview.
- The Regions page now reports the server-owned state, uses `Show`/`Disable`, and `Disable all` switches every server region off. `/regions show`, `/regions hide <name>` and `/regions hide` now manage the same server-owned state and require region-edit administration.
- Region redefinition preserves the visibility setting and changes are saved and synchronized to all online players immediately.
- Network protocol remains `31`; client/server payload layouts are unchanged. Border player-preference schema remains `2`; region record schema increases from `3` to `4`.

## 1.6.0-dev10.1

- Fixed the server region-border capability gate so an explicit `ssu.borders.regions.view = false` also applies to operators; operator bypass can no longer force region borders visible while the server has denied them.
- Applied the same hard-gate semantics to claim borders for consistency: the server permission is the maximum capability, while each player's personal border toggle remains the final visibility choice.
- Border synchronization now compares the freshly resolved effective capability with the previous client state every refresh cycle, so module or permission changes clear stale overlays even when no settings revision was raised.
- Region overview, pinned/focused region borders and the temporary region-selection layer are all cleared when the effective region-border gate is off.
- Region `Show` actions now fail explicitly when the server disallows region borders instead of appearing successful while no legal overlay should be sent.
- Network protocol remains `31`; payloads and stored-data schemas are unchanged from dev10.

## 1.6.0-dev10

- Fixed duplicate in-world claim and region borders when an automatically visible overview entry was also selected as a focused claim or pinned region. Focused/pinned entries are now excluded from the overview layer and rendered exactly once.
- Made personal border switches the final visibility decision. Claim and region overview, focused claims and individually selected regions are sent only when the relevant server module is enabled, the server permission allows viewing and that player has enabled the corresponding border setting.
- Updated dashboard border capability flags so disabled server modules also disable the matching personal border control. Permission and rank changes now refresh online border visibility immediately.
- Centered the textured Back button inside the portrait sidebar.
- Moved Profile out of the wide dashboard tile grid and placed a dedicated Profile button directly below the portrait details and balance. Compact layouts without a portrait sidebar retain the Profile tile.
- Network protocol remains `31`; no payload or stored-data schema changed. Existing border preferences, selected regions and other world/player data remain compatible.

## 1.6.0-dev9

- Added selection-based rich text to Mail Compose with the 16 Minecraft colours plus bold, italic, underline, strikethrough and clear-style controls. Inbox and Sent render the stored formatting while legacy plain-text mail remains compatible.
- Removed the obsolete editable global text-colour control from the hologram editor while retaining old stored base colours as a compatibility fallback.
- Renamed scoreboard `Lines` and `Ticks` to `Score rows` and `Refresh sec`, and fixed scheduling so each scoreboard hologram follows its own refresh interval.
- Increased network protocol from `30` to `31` for the rich-mail payload changes. Mailbox schema remains `3`; hologram schema remains `4`.

## 1.6.0-dev8.2.1

- Fixed the Java compilation error in `PropertySettingsScreen.addPlayerAction`: the stored dropdown value is now captured through an unchanged local variable before the selected fallback value may be reassigned.
- No gameplay, payload or stored-data behaviour changed. Network protocol remains `30`; client and server should use the same dev8.2.1 build.

## 1.6.0-dev8.2

- Removed claim-specific spawn configuration from the claim settings GUI and `/claims` command tree. Claim teleports now always resolve a deterministic claimed chunk and use the server heightmap for an automatic surface destination.
- Retired the stored claim-spawn fields. Legacy `spawnX`, `spawnY`, `spawnZ`, `spawnYaw` and `spawnPitch` values are ignored when older claim JSON is loaded and disappear when that claim record is saved again; region and global server spawns are unchanged.
- Replaced free-text claim Trust/Untrust fields with server-provided player dropdowns. Trust lists online and previously known players who do not already have access; Untrust lists only players currently trusted in that claim. Actions submit UUIDs and are revalidated server-side.
- Added claim-presence tracking every 10 server ticks. A claim welcome message is now shown when a player actually enters a different claim, without repeating while moving between chunks belonging to the same claim.
- Increased network protocol from `29` to `30` because property-setting entries can now carry bounded value/label option lists. Client and server must use the same dev8.2 build.
- Existing claim ownership, chunks, trust, flags, regions, homes, warps, economy, mail, holograms and player preferences remain compatible. Hologram schema remains `4`, player UI preferences remain schema `3`, and border visualization settings remain schema `2`.

## 1.6.0-dev8.1

- Polished the dashboard and Settings UI: clearer mail-retention labels and hover help, simplified explanatory text, larger centered page titles and removal of redundant grey labels.
- Removed the normal-player Regions dashboard tile while retaining administrative region management.
- Simplified the left profile panel and removed the green Close arrow from the root dashboard; subpages retain a compact Back control.
- Network protocol and stored-data schemas remained unchanged from dev8.

## 1.6.0-dev8

- Added **Admin Center → Module settings** as the operational control surface for Player Claims, Homes, Warps, Server Regions, Treecapitator, Veinminer, Crop Harvesting, Floating Text / Media, Mail, Permissions and remote hologram images. Existing NeoForge config values remain the persistence/backwards-compatibility layer.
- Added runtime module activation. A disabled lifecycle-owned module is not initialized or loaded at server startup; disabling it at runtime first cancels owned work, saves pending state, releases its in-memory managers and makes its commands/tools inert. Re-enabling loads the unchanged stored data without requiring a restart.
- Hardened `/ssu reload` so it cannot reload disabled module data. Claims, homes, warps, regions, mail and hologram command roots now also evaluate their module switch dynamically.
- Added a configurable global hologram render/load distance of 8–512 blocks, default 64. The server now synchronizes only holograms in the same dimension and within the effective player range; each hologram may still request a shorter range.
- Replaced the former periodic full hologram broadcast with one-second per-player proximity snapshots. Unchanged snapshots are not resent, scoreboard lines keep their own update interval and image sources leaving the active snapshot are released from the client cache.
- Remote image definitions already stored on disk are now omitted from synchronization when remote images are disabled.
- Added separate configurable claim-border and region-border render distances of 16–512 blocks, both defaulting to 128. The old chunk-distance setting migrates once into both block values; border settings schema is now 2.
- Sent the authoritative border distance to clients and removed the hidden fixed claim/region renderer limits. Pinned region borders and focused claim borders now obey the configured range; long claim edges and large region-box edges are clipped at the player radius so a far face cannot remain visible merely because the player is inside the same large region. Region selections are cleared when the region module is disabled.
- Added module ownership to long-running region snapshot/reset/world-edit jobs so disabling Regions cancels those jobs before region runtime state is released. Permission-profile login creation, region-rent ticking, mail login maintenance and Treecapitator placement provenance now also stop touching their module data while the corresponding module is disabled.
- Increased network protocol from `28` to `29` because dashboard and border payloads changed. Hologram storage remains schema `4`, player UI preferences remain schema `3`, and existing world/module data remains compatible. Client and server must use the same dev8 build.

## 1.6.0-dev7

- Implemented actual client-side image holograms instead of the former text placeholder. PNG, JPG/JPEG and animated GIF sources are decoded, cached and rendered as camera-facing billboards.
- Added asynchronous two-thread image loading so HTTP requests and image decoding do not block the render thread. Synced image holograms are preloaded and inactive cache entries expire automatically.
- Added animated GIF frame composition, frame delays and disposal handling with a 180-frame safety limit.
- Added local resource support such as `simpleserverutilities:textures/holograms/example.png` and remote direct HTTP(S) image sources, including extensionless endpoints when their returned bytes decode as PNG, JPG or GIF.
- Added remote-source protections: 8 MiB download cap, connect/read timeouts, at most three redirects, credential rejection and rejection of loopback, link-local, site-local, multicast and carrier-grade NAT targets on every redirect.
- Added decoded-size limits of 4096x4096 / 16,777,216 pixels, a maximum 64x64 render sample, a bounded 64-source client cache and an animated-image complexity cap. Equal colour runs are merged into larger billboard rectangles to control Gizmo cost.
- Added in-world loading and concise error placeholders, image-aware right-click hitboxes, F3+T/resource-reload cache clearing and correct see-through behaviour.
- Tightened editor and command validation to the supported PNG/GIF/JPG/JPEG formats. New common configs enable remote hologram images by default; an existing config that still contains `allowRemoteHologramImages=false` must be changed manually.
- Network protocol remains `28` and hologram storage schema remains `4`; the image fields already existed in the dev6 payload and save format. Use the same dev7 build on client and server.

## 1.6.0-dev6.2

- Fixed the remaining Treecapitator wood-family bug: player-placement provenance no longer rejects matching trunk blocks before family resolution.
- Natural canopy validation remains authoritative, while normal logs, stripped logs, wood, stripped wood, stems and hyphae of the same namespace-qualified species are now actually selected and broken together.
- A stripped natural trunk segment or a matching bark-on-all-sides wood segment can be used as the targeted origin.
- Player-placed leaves remain excluded from natural canopy validation, so ordinary constructed log piles still do not qualify as trees.
- Network protocol remains 28 and hologram schema remains 4.

## 1.6.0-dev6.1

- Replaced the broken visible legacy-format-code editing path with a dedicated rich-text document model. The editor now stores only plain visible text in `MultiLineEditBox`; colors and B/I/U/S are retained separately per selected character and encoded only when saving.
- Added a client-only styled content pass for the hologram text box. Applying a color, bold, italic, underline or strikethrough to a selection is visible immediately in the editor without exposing `§` control codes.
- Retained the selected range while toolbar buttons and the color palette take focus, allowing several effects to be applied consecutively to the same selection.
- Reworked world rendering into independently positioned rich-text segments. The 16 colors render per segment; bold uses a compact overdraw pass; underline and strikethrough use camera-plane decoration quads; stored/editable text remains unchanged.
- Updated hologram hit testing to use the styled rendered width and the same camera-up multiline plane as the renderer.
- Strengthened Treecapitator remnant discovery: one missing vertical trunk block is bridged in either direction, a canopy-backed one-log crown is no longer rejected, and conventionally named modded log/wood/stem/hyphae variants are accepted even when a mod omitted them from the logs tag.
- Normal, stripped and bark-on-all-sides variants still normalize to the same namespace-qualified wood family, while different species and mod namespaces remain separated.
- Network protocol remains `28` and hologram storage schema remains `4`; dev6.1 is payload- and data-compatible with dev6, but using the same build on client and server remains recommended.

## 1.6.0-dev6

- Replaced one-background-per-line hologram rendering with one fitted camera-facing background around the complete multiline text block.
- Recalibrated background width against the rendered font, including bold glyphs, and added a scale-aware rear depth offset so text remains in front at both short and long distances.
- Added an authoritative 40-visible-character limit per line. Formatting codes do not consume the limit; the editor moves overflow to the next line automatically and the server repeats the same normalization.
- Replaced whole-hologram Bold/Italic/Underline/Strikethrough editor toggles with compact selection tools. Selected text can independently receive bold, italic, underline, strikethrough or any of the 16 standard Minecraft colours, and Clear style removes formatting only from the selected range.
- Formatting is stored inline and carried across automatically wrapped TextGizmo lines. Existing schema-3 whole-text style flags migrate once to equivalent inline formatting.
- Added editable X, Y and Z fields to local and remote hologram editing. The server validates finite Minecraft-bound coordinates while retaining the hologram's current dimension.
- Increased hologram storage schema from 3 to 4 and network protocol from 27 to 28; client and server must both use dev6.
- Included the Minecraft 26.2 `GameRenderer.mainCamera()` correction and the missing `Block` import in `UtilityMiningResolver`.
- Existing non-hologram storage remains unchanged.

## 1.6.0-dev5.1

- Fixed Treecapitator rejecting a natural tree remnant after a lower or middle trunk log had already been removed. Natural-tree validation now relies on the remaining vertical trunk section plus its natural canopy instead of requiring the connected remnant to still touch solid ground.
- Grouped normal logs, stripped logs, bark-on-all-sides wood, stripped wood, stems and hyphae by wood family. Treecapitator can now continue through those variants of the same species while still refusing a neighbouring different wood type.
- Updated runtime revalidation so mixed valid variants of the selected wood family remain eligible throughout the automatic break chain and still receive their own per-block permission/protection/tool checks.
- Reworked floating-text backgrounds to use the main camera's exact forward/left/up quaternion basis, matching Minecraft's `TextGizmo` billboard rotation instead of independently aiming the quad at the camera position.
- Reduced the background depth bias to a minimal anti-z-fighting offset, keeping the text and background visually on one moving billboard layer.
- Network protocol remains `27`; payloads and stored hologram schema remain unchanged from dev5.

## 1.6.0-dev5

- Fixed Treecapitator natural-leaf cleanup when mining begins above the bottom log. Completion is measured against the selected upward trunk section, while lower logs intentionally remain.
- Added persistent ARGB backgrounds for floating text, links and scoreboard holograms. Existing holograms migrate to schema 3 with a transparent default background.
- Added separate 16-colour Minecraft preset palettes for text and background colours, while retaining direct RGB/ARGB hexadecimal input and a transparent-background action.
- Added a camera-facing background quad behind each visible hologram line with the same scale, view range and see-through policy as its text.
- Increased the network protocol from `26` to `27`; client and server must both use dev5 or dev5.1.

## 1.6.0-dev4

- Recalibrated floating-text scale so the former visual size at scale `8` is now the useful baseline scale `1`; the editor and commands expose the new `1` through `8` range.
- Migrated hologram definitions and the hologram container to schema 2. Existing schema-1 scales are converted once and queued for persistence so old floating text does not become eight times larger after updating.
- Replaced the broad spherical hologram selector with a narrow camera-facing rectangle per rendered text line. Nearby holograms are now selected only when the view ray intersects their visible line area, with the nearest matching plane winning.
- Treecapitator now builds both its preview and break selection from the exact targeted log upward. Logs below the target are excluded, and only the same exact log block/species as the target is accepted.
- Separated natural-canopy validation from conservative leaf ownership. A touching tree of another species no longer disables the selected tree, while shared leaves near another trunk are left intact during automatic cleanup.
- Added one normal durability attempt for every automatically mined Treecapitator log and Veinminer ore. The real main-hand tool is damaged through `hurtAndBreak`, so Unbreaking and normal tool-break handling remain involved; automatically removed leaves remain durability-free.
- Added server-authoritative **Crops Harvesting**. Right-clicking a mature supported crop gives its normal block loot and resets its growth property to the first planted stage instead of removing the crop.
- Added broad crop compatibility for vanilla crop classes, `minecraft:crops`, `c:crops`, legacy `forge:crops`, conventional `age`/`growth`/`stage`/`maturity` properties and configurable custom/disabled block identifiers. Mature double-height age-based crops are reset without a second loot roll; berry bushes and melon/pumpkin stems keep their native behaviour.
- Added progression permission `ssu.crops_harvesting.use`, enabled for the migrated default rank, plus wildcard `ssu.crops_harvesting.*`.
- Added a global **Crop harvesting: ON/OFF** control to Admin Center → Admin Tools and the matching server config value `enableCropsHarvesting`.
- Increased the network protocol from `25` to `26` because the dashboard snapshot now carries the global crop-harvesting state; client and server must both use 1.6.0-dev4.
- Existing claims, regions, economy, mail, permissions, player preferences and utility-mining tracking remain compatible. Holograms migrate in place from schema 1 to schema 2.

## 1.6.0-dev3

- Reworked the Hologram Tool around direct right-click use. Right-clicking normally opens a fresh editor and stores a server-authoritative placement point exactly one block along the player's view direction.
- Added in-world hologram selection: right-clicking visible floating text, links, scoreboard holograms or image placeholders with the Hologram Tool opens that existing definition for editing instead of creating a duplicate. The server rechecks the exact named main-hand tool and hologram-admin permission before opening local edits.
- Expanded the hologram editor to preload every persisted setting, support ID changes, save existing definitions and provide a double-confirmed local **Delete hologram** action.
- Added a dedicated **Holograms** page directly in the Admin Center, plus a shortcut from Admin Tools. The searchable, paged list supports remote **Edit**, safe **Teleport** and double-confirmed **Delete** actions for every stored hologram.
- Remote hologram teleports reuse SSU's existing safe-destination resolver, load the target chunk and refuse the teleport when no collision-free two-block standing position can be found nearby.
- Improved in-world selection width so long floating-text lines can be clicked across their visible text instead of only near their centre point.
- Treecapitator now requires an item in the main hand that belongs to Minecraft's axe item tag. Veinminer likewise requires the main-hand item to belong to the pickaxe item tag.
- Rechecks the required tool before resolving a preview, before starting a multi-block break chain and before every extra log, ore or natural-leaf break, so switching tools or breaking the final durability point stops the chain safely.
- Incorporated the Minecraft 26.2 client-message API correction in both hologram and region editor result screens by using `LocalPlayer.sendSystemMessage(Component)`.
- Removed the unintended `src/test` JUnit source set from the distributable source archive, keeping `gradlew.bat clean build` aligned with the project's dependency-free build configuration.
- Increased the network protocol from `24` to `25`; client and server must both use 1.6.0-dev3.
- Existing claims, regions, economy, mail, permissions, player preferences, utility-mining tracking and hologram schema-1 storage remain compatible.

## 1.6.0-dev2

- Fixed floating text being rendered twice by removing the second offset grey `TextGizmo`. Holograms now use one centered colour pass only.
- Retired the misleading hologram shadow control for this renderer. Minecraft 26.2 `TextGizmo.Style` exposes colour, scale and alignment but no native single-pass shadow option; the stored compatibility field is normalized off.
- Added an **Admin Tools** page to the permission-aware Admin Center. Each tool has a purpose tooltip and a server-validated **Get Tool** action.
- Added the **Hologram Tool**. Left-clicking a block records a temporary face-relative placement point; right-clicking opens a custom editor for floating text, website links, image definitions and scoreboard holograms, including colour, scale, formatting, visibility range and type-specific settings.
- Added the **Region Tool** to the same Admin Tools page. Two left-clicks select positions 1 and 2; right-click opens an initial region creation GUI with name, priority, protection flags and rental/reset settings.
- Added server-authoritative hologram and region editor payloads. Submitted coordinates are never trusted: both editors use the server's current temporary anchor or region selection and recheck module state and permissions.
- Treecapitator previews and outlines now contain logs only. Leaves are no longer included in the selected-block outline or the permission-limited mining chain.
- Added `treecapitatorBreakNaturalLeaves`, enabled by default, plus `/ssu utilitymining tree break_leaves <true|false>`. When enabled, naturally connected leaves are removed instantly only after the complete detected trunk has been felled.
- Added persistent tracking of player-placed logs and leaves below `simpleserverutilities/utility_mining/player_placed_tree_blocks.json`. Tracked blocks are excluded from Treecapitator origins, trunk discovery and natural-leaf cleanup.
- Natural-tree validation now requires a supported vertical trunk and a non-persistent, non-decaying canopy near its top. Leaf ownership is bounded by the configured search range and rejects a canopy when another unselected trunk is at least as close.
- Existing hand-built structures from before this version cannot be identified historically with certainty; structural, canopy and vanilla leaf-state checks reject ordinary builds, while all new player placements are tracked exactly.
- Increased the network protocol from `23` to `24`; client and server must both use 1.6.0-dev2.
- Existing claims, regions, economy, mail, permissions, player preferences and hologram storage remain compatible. The new player-placed-tree tracking file is isolated.

## 1.6.0-dev1.1

- Source compilation hotfix for Minecraft/NeoForge 26.2.
- Corrected `net.minecraft.util.Util` import for clickable hologram links.
- Removed the `CustomPacketPayload.type()`/record-component name collision by using `miningType`.
- Added `UtilityMiningTarget.isEmpty()`.
- Replaced unavailable 26.2 ore-tag constants with explicit vanilla ore families while retaining `c:ores` and custom-block support.
- Made Treecapitator ordering comparators use explicitly typed `BlockPos` lambdas.
- Network protocol remains `23`; payload wire format and stored data are unchanged.

## 1.6.0-dev1

- Added separate Core 2.0 modules for Utility Mining and persistent Holograms.
- Added Treecapitator and Veinminer as server-authoritative multi-block mining systems. The server resolves every selected block, rechecks protection immediately before breaking and uses the normal player game mode so ordinary drops, tool use and other block-break hooks remain involved.
- Added player-selectable Sneak or Keybind activation for both systems. Treecapitator defaults to Left Alt and Veinminer to V in Minecraft Controls; activation mode, personal enable state, outline colour and outline brightness are available in Player Settings and fallback `/ssu settings` commands.
- Added a merged outer structure outline that removes internal shared block-grid edges and renders a configurable glow/core pass around the complete selected tree or ore vein.
- Added global Treecapitator/Veinminer toggles, default block limits, Treecapitator leaf range, custom log/ore lists and explicit disabled-block lists. Vanilla and modded log-tagged blocks are included by default; vanilla ore tags and the common `c:ores` tag are supported.
- Added `ssu.treecapitator.use`, `ssu.treecapitator.max_blocks`, `ssu.treecapitator.blocks`, exact `ssu.treecapitator.block.<namespace>.<path>` permissions, `ssu.veinminer.use`, `ssu.veinminer.max_blocks`, per-vanilla-ore permissions and exact `ssu.veinminer.block.<namespace>.<path>` permissions.
- Default-rank migration enables Treecapitator and coal Veinminer, while iron, copper, gold, redstone, emerald, lapis and diamond remain explicitly locked for later rank, personal-permission or quest upgrades.
- Added `/ssu utilitymining` administration for runtime status, module toggles, limits, leaf range and custom/disabled block-list maintenance. Administration requires `ssu.utility_mining.admin` unless run from the server console or by an operator.
- Added persistent dimension-aware floating text, clickable website-link and scoreboard holograms stored below `simpleserverutilities/holograms/holograms.json`.
- Added hologram colour, scale, bold, italic, underline, strikethrough, shadow, see-through, enabled state, view distance, move, edit, delete and refresh controls through `/ssu hologram`; administration requires `ssu.holograms.admin`.
- Added TOP and SELF scoreboard holograms backed by ordinary Minecraft scoreboard objectives, with configurable maximum lines and refresh interval.
- Added image-hologram definitions with validated internal resource identifiers, optional validated HTTP(S) sources, width/height metadata and safe persistence. Remote image sources remain disabled by default. The actual textured billboard renderer is intentionally not enabled in this first slice; image entries currently render a visible source placeholder until the dedicated 26.2 custom-geometry/texture pipeline is completed.
- Added a 512-definition hologram safety limit and only synchronizes holograms for the player's current dimension.
- Migrated player UI preferences from schema 2 to schema 3 without changing existing settings.
- Increased the network protocol from `22` to `23`; client and server must both use 1.6.0-dev1.
- Existing claim, region, home, warp, spawn, economy, rent, permission and mailbox storage remains compatible.

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
