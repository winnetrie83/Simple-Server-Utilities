## Current development build: 1.9.0-dev3.40.6.1

- dev3.40.4.1 is an emergency client-render recovery build: the new Player-NPC equipment renderer from dev3.40.4 was rolled back because it caused a black screen during client startup. The movement and NPC Manager fixes from dev3.40.4 remain active.
- dev3.40.4 fixes the actual NPC Tool Manager duplicate-widget cause: constructor-time payload handling no longer builds a pre-init widget set before the normal screen initialization.
- NPC runtime sync no longer snaps active NPCs back to their placement during combat/movement; combat recovery returns through pathfinding to home, patrol or the active schedule destination.
- Player-model NPC held main/off-hand items are now visible again on the proven safe renderer path for Wide/Steve and Slim/Alex models.
- Arcane Missiles has received a first-pass premium VFX upgrade: purple sinus-wave beam, visible arcane orb projectiles, richer charge-up telegraph and brighter impact burst.
- dev3.40.2's canonical 4-column Dashboard layout remains unchanged.
### Shared Ability Library + smarter NPC casting

- NPC abilities are now reusable server-wide definitions rather than copies owned by individual NPC templates.
- Open **Dashboard -> Admin Tools -> Ability Library** to create/edit abilities without the NPC Tool. One shared edit applies to every assigned NPC.
- NPC Editor -> Abilities is now an assignment flow: open the library, choose **Assign**, optionally restrict the assignment to a boss phase, and keep using the same stable ability ID in Attack Patterns / phase actions.
- Existing dev3.39 NPC abilities migrate automatically into the shared library without accidentally merging unrelated NPC-local copies.
- Smarter ability eligibility adds **Requires stationary** and **Min targets**. Arcane Missiles can stop its old navigation before channeling; Thunderclap only becomes eligible when real enemies are inside its AoE instead of when a far-away enemy is merely seen.
- Around-self AoE and cone selection use shape-aware target validation; channel interruption now reacts to actual post-cast displacement/damage.
- Equipment-driven combat, walking/running combat movement, attack channels, boss encounters, GUI scaling, community statistics and the read-only website API remain intact.
- Network protocol `118`; NPC definition schema `19`; NPC Ability Library schema `1`; NPC placement schema `4`; Community Statistics schema `1`.
- Hotfix dev3.40.1 repairs stale/missing NPC-specific boss-phase gates on shared ability assignments instead of blocking NPC saves. Blank/invalid gates safely fall back to **All phases**.
- See `docs/NPC-ABILITIES-1.9.0-dev3.40.md` and `docs/TESTING-1.9.0-dev3.40.1.md`.


## Previous development build: 1.9.0-dev3.39

### Equipment-driven NPC combat + Ability Workshop

- NPC armor/toughness/ordinary weapon damage come from equipped items and gameplay enchantments; managed equipment remains unbreakable and non-dropping.
- Separate Walking/Running speed and Melee/Ranged/Magic damage multipliers/channels remain the combat-stat foundation.
- dev3.39 introduced the editable combat-ability presets and workshop that dev3.40 now promotes into a shared server-wide library.
- Network protocol `117`; NPC definition schema `18`.


## Previous development build: 1.9.0-dev3.38.3

### SSU GUI scale visual hotfix

- Fullscreen menu dim/backdrop now remains unscaled and always covers the complete viewport at 60-100%.
- Dashboard PlayerSkinWidget avatar now explicitly follows the centered SSU scale for both position and rendered size.
- The underlying SSU layout remains unchanged; 100% is still identical to the pre-scaling layout.
- Inherits the independent client-only SSU GUI scale from dev3.38.

### Independent SSU GUI scale

- Added a client-only `SSU GUI Scale` under Dashboard -> Settings -> Interface.
- 100% preserves the exact existing SSU size; players can select 90%, 80%, 70% or 60% without changing Minecraft's global GUI Scale.
- SSU keeps its original logical screen dimensions/layout and applies one centered final render transform, with matching inverse mouse/input mapping.
- The preference persists in the client config as `ssuGuiScalePercent`; no network protocol or world/persistence migration is required.
- Community Statistics and the read-only Website Analytics API from dev3.37 remain unchanged.
- See `docs/TESTING-1.9.0-dev3.38.3.md`.


## Previous development build: 1.9.0-dev3.37

### Community statistics + website analytics foundation

- Automatic curated community stats track Lifetime, Day, Week, Month and administrator-controlled Season buckets without requiring custom statistic definitions.
- Rolling histories and Web API statistic/leaderboard/history endpoints remain the analytics foundation.
- Community Statistics schema `1`; network protocol `116`; NPC definition schema `17`.
- See `docs/TESTING-1.9.0-dev3.37.md` and `docs/WEB-API-1.9.0-dev3.37.md`.


## Previous development build: 1.9.0-dev3.36

### Advanced boss encounters + website foundation

- Boss phase actions, encounter-owned adds, Fixate/Taunt immunity and the initial authenticated read-only Web API v1 remain the foundation inherited by dev3.37.
- Network protocol `116`; NPC definition schema `17`.


## Previous development build: 1.9.0-dev3.35

### NPC AI-family polish + free-form role styling

- Free-form cosmetic Role / occupation text plus the 2 x 8 Minecraft color palette.
- Species-family AI profiles for humanoid/ground/hopping/flying/aquatic/amphibious/native-special movement.
- Species-aware patrol/schedule arrival and clean combat return to ambient movement.
- Behavior-page AI family/runtime diagnostics.
- Network protocol `115`; NPC definition schema `16`.


## Previous development build: 1.9.0-dev3.34.4

### Smooth labels + model-aware locomotion

- Player NPC renderer suppresses the native Minecraft/type nametag so only SSU's role/name/faction/quest identity stack should be visible.
- NPC overhead text follows the entity's interpolated render position, removing the visible tick-lag behind a moving model.
- SSU now derives a locomotion family from the selected physical entity shell and delegates movement to that shell's native navigation/control style:
  - Player/Villager/normal ground shells: native ground path navigation.
  - Slime/Magma Cube: native hopping movement.
  - Vex/Ghast/Phantom/Bat/Wither families: specialised free-flight MoveControl.
  - Allay/Bee/Parrot-style fliers: native flying path navigation.
  - Fish/Guardian/etc.: native water navigation where the shell supplies it.
  - Amphibious shells keep their water/ground-capable native controller.
- SSU remains responsible for route/schedule/combat destinations; it no longer tries to make every model physically move like the Player NPC.
- Native fliers retain no-gravity flight automatically; the editor's `Can fly` remains an explicit override for ground shells.
- This is the locomotion foundation for later species-specific AI/decision behavior, not a one-size-fits-all replacement for vanilla species brains.
- Network protocol remains `114`; NPC definition schema remains `15`; no persistence migration is required.
- See `docs/TESTING-1.9.0-dev3.34.4.md`.


## Previous development build: 1.9.0-dev3.34.3

### dev3.34.3 patrol arrival / route-state hotfix

- Patrol waypoint arrival no longer depends on hitting one exact XYZ within a strict 1-block 3-D sphere.
- A native Player NPC that reaches the valid final `PathNavigation` node near a waypoint now advances to the next logical waypoint instead of parking forever on point 1.
- Switching waypoints clears the completed native path as well as SSU route state, then starts the next path in the same behavior update while preserving entity momentum.
- STUCK recovery immediately requests the replacement waypoint.
- Route modes Loop, Ping-pong and Random all use the same corrected transition code.
- Network protocol remains `114`; NPC definition schema remains `15`; no persistence migration is required.
- See `docs/TESTING-1.9.0-dev3.34.3.md`.


## Previous development build: 1.9.0-dev3.34.2

### dev3.34.2 patrol continuation hotfix

- First attempt at invalidating SSU navigation state on waypoint advance. dev3.34.3 supersedes this by also fixing arrival recognition and clearing the completed native path.
- Newly created patrol waypoints default to `Pause = 0`; existing saved pause values are preserved.
- Network protocol remains `114`; NPC definition schema remains `15`.


## Previous development build: 1.9.0-dev3.34.1

### dev3.34.1 movement hotfix

- Native Player NPC movement now keeps a fixed vanilla-like base movement attribute (`0.25`); the Behavior route-speed value is the single user-facing speed multiplier for Player NPC patrol/wander movement.
- The duplicate Player NPC Movement Speed field is removed from Stats. `1.0` means normal speed; values such as `0.5` are valid slower movement multipliers.
- Native `PathNavigation` is no longer rebuilt every four ticks for the same destination. Repathing now happens only for meaningful target drift, a finished path, or stall recovery.
- Zero-pause patrol points flow directly into the next waypoint instead of forcing a stop every node.
- SSU's manual no-AI gravity fallback no longer clears movement from a Mob whose native AI/navigation is currently active.
- Network protocol remains `114`; NPC definition schema remains `15`.
- See `docs/TESTING-1.9.0-dev3.34.1.md`.


### Advanced Combat Patterns + Threat/Aggro

- New optional **Threat targeting** tracks damage aggro with configurable range, multipliers, decay and switch hysteresis.
- SSU systems with a known healer can add healing threat; the built-in Self Heal ability already reports its actual healing through this hook.
- New optional **Attack Patterns** let NPCs/bosses run ordered Melee/Ability sequences instead of only choosing random eligible abilities.
- Pattern steps can be conditioned by target range, own HP percentage and boss phase.
- New **Tactics** page in the NPC Editor exposes both systems without requiring JSON editing.
- Boss phase transitions/reset cleanly reset pattern/threat encounter state, and a taunt API foundation is available for future tank mechanics.
- Existing NPCs keep previous combat behaviour because Threat and Attack Patterns default to OFF.
- Network protocol is `114`; NPC definition schema is `15`; placement/spawn-profile/dialogue/quest schemas remain `4/1/2/2`.
- See `docs/TESTING-1.9.0-dev3.34.md`.

## Previous development build: 1.9.0-dev3.33

### Native Player NPC runtime

- Player NPCs moved from the mannequin shell to the native `simpleserverutilities:player_npc` PathfinderMob runtime.
- Wide/Steve and Slim/Alex skins, native pathfinding, SSU combat/schedules/patrols and the dependency-free player renderer were retained.
- Network protocol `113`; NPC definition schema `14`.

## Previous development build: 1.9.0-dev3.29.1

### NPC spawning compile hotfix

- Fixed the `Heightmap` import in `NpcSpawnManager` for Minecraft/NeoForge 26.2 (`net.minecraft.world.level.levelgen.Heightmap`).
- Replaced the removed legacy `ServerLevel#getDayTime()` call with `getDefaultClockTime()`, matching SSU's existing 26.x clock usage.
- No network, NPC schema, placement schema, dialogue schema, quest schema, or Spawn Profile schema changes.

## Previous development build: 1.9.0-dev3.29

### Dynamic NPC Spawning

- Added reusable **NPC Spawn Profiles**. A normal NPC template can now be used by persistent placements, natural population, or a physical vanilla Spawner without duplicating the NPC definition.
- New **NPC Manager → Spawning** tab with create/edit/test/delete workflows and a dedicated GUI-first Spawn Profile editor.
- Natural profiles support dimension, biome allow-list, day/night, Y range, light range, chance, cycle time, attempts, group size, player-distance band, nearby cap, global cap, and despawn distance.
- Spawner profiles bind to an actual vanilla Spawner block and support group size, cooldown, spawn radius, activation range, nearby/global caps, time/light/Y/biome conditions, and rebinding by looking at another spawner.
- While an enabled SSU profile owns a vanilla Spawner, its old vanilla mob spawn is suppressed; disabling/removing the profile releases the spawner back to vanilla behaviour.
- Spawned population is **dynamic runtime population**, not permanent NPC placements. It still uses the linked template's appearance, labels, faction relations, loot, stats and dev3.28 combat/reaction behaviour, but is automatically cleaned up away from players and does not pollute placement JSON.
- Spawn profiles follow NPC template renames, and a template cannot be deleted while a spawn profile still references it.
- Network protocol is `110`; new NPC Spawn Profile schema is `1`; NPC definition schema remains `11`; NPC placement schema remains `4`; dialogue/quest schemas remain `2`.

## Previous development build: 1.9.0-dev3.28

### NPC Combat & Reactions foundation

- NPC relations now separate **attitude** from **reaction**: being HOSTILE can mean Ignore, Avoid, or Attack instead of always forcing combat.
- Self-defense supports **Ignore / Flee / Fight back / Fight + call allies**.
- Friendly-defense supports **Ignore / Assist / Assist + call allies**, allowing guards/allies to react when a friendly NPC is attacked.
- Added **Passive / Melee / Defender / Aggressive** combat profiles plus assist range, flee distance, and attack cooldown settings.
- Schedules, patrols and wander behaviour yield to active SSU combat/flee states and resume afterwards.
- The NPC editor has a dedicated **Combat** page; tabs remain in the same compact 510×350 editor footprint.
- Network protocol is `109`; NPC definition schema is `11`; NPC placement schema remains `4`; NPC dialogue and Quest schemas remain `2`.

## Previous development build: 1.9.0-dev3.27

### NPC AI foundation — navigation, routes and schedules

- Shared navigation for schedules, patrols and wander behaviour.
- Collision-aware fallback movement for custom-skin mannequin shells instead of incremental `snapTo()` movement through blocks.
- Stuck detection/recovery, in-world patrol and schedule editors with undo, and configurable schedule arrival actions.
- Network protocol remained `108`; NPC definition schema remained `10`; placement schema remained `4`.

## Previous development build: 1.9.0-dev3.26.2.1

**dev3.26.2.1 compile hotfix:** restores the missing `NpcManager.syncAll()` method required by the NPC/Quest workflow refresh paths. Protocol and schemas are unchanged from dev3.26.2.

### Simplified NPC Quest workflow + compact guided Quest Editor

- Added a normal **NPC → Manage quests** workflow. Select a quest and choose **Offer**, **Turn-in**, **Both**, or **Unlink**; SSU generates the quest-state dialogue routing internally.
- Simple NPC quest dialogue is now edited as six friendly texts: Available, Accept, In Progress, Ready, Turn-in, and Completed. The graph/condition editor remains available separately as **Advanced dialogue**.
- Added direct **NPC Integration** to the Quest Definition Editor with searchable Quest Giver and Turn-in NPC pickers, `!` / `•` / `?` marker switches, and a simple dialogue editor.
- Added **Quest Menu / NPCs / Both** access modes. Linking the first NPC quest while NPC access is disabled now opens an explicit choice instead of silently making `quest_available` false.
- Multiple simple quests on one NPC use an automatically generated player-specific quest selector. Generated simple links are capped at 12 per NPC; larger/custom hubs can use Advanced Dialogue.
- Deleting a linked quest rebuilds the managed quest dialogue, and deleting an NPC placement through the editor/admin browser/command clears its simple giver/turn-in links instead of leaving stale references.
- The Quest Definition Editor was reduced from **720×474 to 550×344** (about 24% narrower and 27% shorter) and reorganized into **General / Objectives / Rewards / NPC Integration** tabs.
- Normal quest editing uses labelled pickers and contextual controls instead of raw IDs/parameter fields wherever practical: searchable event/reward/prerequisite selectors, registry item pickers, quest/NPC pickers, automatic objective IDs, and an auto-generated unique Quest ID for new quests until manually overridden.
- Advanced metadata/custom parameters remain available only when an existing/custom definition actually needs them.
- Network protocol is `108` because quest-editor and NPC-quest workflow payloads changed.
- Quest definition schema is `2`; NPC dialogue schema remains `2`; NPC definition schema remains `10`; NPC placement schema remains `4`. Other persistence schemas are unchanged from dev3.26.1.

## Previous development build: 1.9.0-dev3.26.1

### NPC Dialogue condition editor hotfix

- Fixed the Dialogue Editor condition type selector becoming trapped at an invalid `NOT` transition.
- The selector now skips `not` when the selected condition does not contain exactly one child; use **Wrap NOT** to negate an existing condition safely.
- Network protocol remains `107`; NPC dialogue schema remains `2`; all other schemas are unchanged from dev3.26.

## Previous development build: 1.9.0-dev3.26

### NPC Dialogue, Interaction & Quest integration

- Dialogue nodes now support their own **player-specific Content Condition** plus an optional fallback node, allowing one NPC to route players to different conversation states safely.
- Added `quest_available` alongside the existing `quest_active`, `quest_ready` and `quest_completed` conditions. Quest availability uses the real NPC quest access, prerequisite, cooldown and repeatability rules.
- Dialogue Editor 2.1 can edit conditions on either the whole node or a single choice, exposes fallback routing, and includes a live quest-definition picker for quest conditions.
- NPC dialogue text supports SSU rich text through the reusable 16-colour + B/I/U/S editor and renders formatted in both live dialogue and preview.
- Quest offers and turn-ins continue through the existing authoritative `quest_offer` and `quest_turn_in` NPC services.
- NPC overhead labels now support player-specific quest state markers: **`!` available**, **`?` ready to turn in**, **`•` active**. Links are inferred from configured NPC services/dialogue data and the marker scales with NPC scale.
- Network protocol is `107`; NPC dialogue schema is `2`; NPC definition schema remains `10`; NPC placement schema remains `4`. Other persistence schemas are unchanged from dev3.25.

## Previous development build: 1.9.0-dev3.25

### NPC Editor, Appearance, Behaviour & Patrol expansion

- Reorganized the NPC editor into dedicated **Behavior** and **Movement** pages while preserving Identity, Appearance, Interaction, Relations, Stats, Loadout, Schedule and Respawn workflows.
- Added persistent NPC behaviour modes: **Native AI**, **Stationary**, **Look at players**, **Wander** and **Patrol**. Existing schema-9 NPCs migrate from their legacy No AI setting.
- Look-at behaviour supports configurable range and optional body rotation; wander supports radius, retarget interval and speed; patrol supports configurable speed.
- Added placement-specific patrol routes with up to 32 waypoints, per-point yaw/pause and Loop / Ping-Pong / Random traversal. Linked placement copies shift their own route coordinates instead of sharing world waypoints.
- Added an in-world patrol route editor: right-click a block to add, sneak-right-click near a point to remove, and right-click air to finish/reopen the editor. Active route points are marked with End Rod particles while editing.
- Added a searchable local-skin browser backed by `<world>/simpleserverutilities/npcs/textures/`; local PNGs are synchronously validated on Save instead of failing only after the editor closes.
- Behaviour runtime is bounded to 5 Hz and uses vanilla Mob navigation where available, with non-Mob/flying/swimming fallbacks. Schedules retain priority over normal behaviour.
- Network protocol is `106`; NPC definition schema is `10`; NPC placement schema is `4`. Other persistence schemas are unchanged from dev3.24.2.

## Previous development build: 1.9.0-dev3.24.2

### NPC custom skin renderer hotfix

- Fixed Local server PNG and HTTPS NPC skins rendering as Minecraft's magenta/black missing texture.
- Dynamic NPC skin textures are now bound through `ClientAsset.ResourceTexture` with an explicit texture path matching the exact identifier registered in the client `TextureManager`.
- Dynamic skin identifiers are definition-specific, so two NPC definitions using identical PNG bytes no longer share a releasable texture registration.
- Wide/SLIM model-only changes reuse the already installed dynamic texture safely.
- Client-side skin installation failures are now logged instead of being silently swallowed.
- Local texture folder remains `<world>/simpleserverutilities/npcs/textures/`; URL/local source formats and the 64x64 / 512 KiB limits are unchanged.
- Network protocol remains `105`; NPC schema remains `9`; all persistence schemas are unchanged.


### NPC label and remote skin hotfix

- Managed NPC runtime entities no longer carry a vanilla CustomName, preventing Minecraft from showing a second large nameplate while the NPC is targeted.
- NPC role/name/faction overhead labels are approximately twice the previous base size and now scale their text size and spacing with the NPC SCALE attribute.
- Remote HTTPS skin loading uses image/browser-compatible request headers and retries transient failures after a bounded 30-second cooldown instead of caching a failed URL forever.
- Network protocol remains `105`; NPC schema remains `9`; all persistence schemas are unchanged.

### Dashboard icons, Cosmetics placeholder and minimap player arrow

- Added the supplied 16x16 `achievements.png` icon to player and admin Achievement tiles.
- Added the supplied 16x16 `games.png` icon to player and admin Minigame tiles.
- Added a new player-dashboard **Cosmetics** tile using the supplied 16x16 `cosmetics.png`; it currently opens a local Coming Soon placeholder and has no cosmetic backend yet.
- Replaced the procedurally drawn minimap player marker with the supplied 16x16 `arrow.png`, preserving the existing minimap rotation behavior and centering it on the effective map area.
- Network protocol remains `105`; Player UI Preferences schema remains `13`; all persistence schemas are unchanged.

## Previous development build: 1.9.0-dev3.23.3

### Achievement pickers, Server Operations clarity, Region Tool input and minimap frames

- Achievement icon selection now uses a scrollable, searchable inventory-style catalogue of all registered items with real item icons, explicit selection and confirmation. The previous picker bug that lost the chosen value when returning to the editor is fixed.
- Achievement item rewards now copy an exact ItemStack template from the editing administrator's own live inventory, without consuming or moving the real item. Selection is explicit and the reward count remains independently editable.
- Server Operations > Activity, Scheduler and Chat now label the previously bare text/numeric controls with their actual purpose and units, with supporting tooltips where useful.
- Region Tool mouse input is deterministic: left-click a block sets Point 1, right-click a block sets Point 2, and only right-clicking the air opens the Region GUI.
- Minimap Settings adds `Frame: CLASSIC / TEXTURED`. Classic preserves the existing SSU border and remains the migration default; Textured uses the supplied square/round frame automatically for the active minimap shape. The textured round map is circularly clipped.
- Network protocol is `105`; Player UI preference schema is `13`; all other persistence schemas remain unchanged from dev3.22.

## Previous development build: 1.9.0-dev3.22

### Achievement UX, server-health clarity and GUI polish

- Player/admin Achievement screens are roughly 25% smaller; admin editing is guided through collapsible sections, human-readable switches and searchable registry/player pickers.
- Achievement rewards display their effective reward (including real item icons), and earning an achievement plays the vanilla challenge-complete advancement sound.
- Hologram coordinates/rich-text layout and the Mail Composer were compacted and cleaned up.
- Server Operations Backup/Worlds fields now have clear labels/tooltips, and Health leads with a color-coded Great/Good/Neutral/Bad/Very Bad verdict with technical details on demand.
- Network protocol is `104`; persistence schemas remain unchanged from dev3.21.1.

### Achievements + shared progression foundation

- Added a full custom **Achievement system** with rich-text title/info, category/icon, hidden/enabled/announce controls, multi-objective progress, multi-reward support, clickable chat announcements, player comparison and admin create/edit/delete/reset tools.
- Player Dashboard and Admin Dashboard both contain dedicated Achievement entries. Players can filter All/Earned/Unearned; hidden achievements stay secret until the viewer has earned them.
- Generic Content gameplay events are now independent of Quests, with reusable objective matching (`ANY`/`EXACT`/`LIST`/`TAG`, `COUNT`/`SUM`/`MAX`/`UNIQUE`) for blocks, mobs, damage/equipment, crafting/use, travel/dimensions/biomes, claims, auctions, quests, minigames, dungeons, NPCs and more.
- Added a persistent fail-closed **Content Reward Ledger**, exact ItemStack rewards, mail fallback, title/cosmetic rewards and persistent temporary-permission overlays.
- Statistics now consume the shared Content Event Bus, protect future schemas and lazy-load normal player records.
- World Edit snapshot preview now segments palettes, avoids repeated stream copies and spatially culls 16³ preview sections.
- Entity Insight FLEEING now has a 5-second recent-hit TTL; unchanged snapshots are suppressed and normal sync runs every 10 ticks.
- Shared rich text is no longer Hologram-specific.

Version state:

- Network protocol: `104`
- Achievement definition schema: `1`
- Achievement player-progress schema: `1`
- Content Reward Ledger schema: `1`
- Temporary Permission overlay schema: `1`
- Custom Statistics definition/player schema: `2`
- Auction purchase journal schema: `2`
- Server Operations schema: `3`
- Mine definition schema: `3`
- Physical Jail definition schema: `2`
- Moderation settings schema: `1`
- Moderation player record / Jail sentence schema: `2`
- Moderation inventory snapshot schema: `1`
- Server/Lobby spawn storage schema: `2`
- Onboarding settings/player schema: `1`
- Kit definition/player schema: `1`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Portable selection snapshot format: `1`
- NPC definition schema: `9`
- NPC placement schema: `3`
- NPC dialogue schema: `1`
- NPC Shop schema: `4`
- Minigame definition schema: `21`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `12`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.20.1

- World Edit input/compact tools and realistic ghost snapshot preview from dev3.20 remain included.
- Entity Insight excludes Armor Stands and includes the cyan FLEEING attitude; dev3.20.1 restored the missing enum member compile hotfix.
- Network protocol was `102`.

## Previous development build: 1.9.0-dev3.19

- Rich-text palettes use two rows of eight; Floating Hologram swatches are larger and the Black tooltip label is readable.
- World Edit transforms preserve hanging-entity Facing for item frames/glow item frames/paintings.
- SSU NPCs suppress the duplicate vanilla target nameplate.
- Entity Insight added friendly/neutral/hostile overhead labels, optional health, 0-32 block range, 1-50 nearest-entity cap and default-granted `ssu.entity_insight.use`.
- Network protocol was `101`; Player UI preference schema became `12`.

## Previous development build: 1.9.0-dev3.16

### King of the Hill v2
- Added per-arena STATIC/ROTATING KOTH modes, live score HUD, static tug-of-war control bar, rotating authored hill points and a translucent in-world hill dome.
- Added KOTH editor/setup labels, hill-point setup visuals and compacted the detailed in-match overview.
- Network protocol increased to `100`; Minigame definition schema increased to `21`.

## Previous development build: 1.9.0-dev3.15.4

### Jail task/community hotfix line
- Jail task prisoners can physically mine every permitted Mine block without drops while only required block types advance punishment progress.
- The completing prisoner is excluded from their own community reward distribution.
- Network protocol remained `99`; NPC schema remained `9`; Minigame definition schema remained `20`.

## Previous development build: 1.9.0-dev3.14.1

### Jail/Mines nesting and admin polish

- Mines must now be fully contained by an existing Region. Their containing Region is auto-detected/persisted; Mines can still be nested inside a Jail without depending on Jail.
- New Mine permission defaults use `ssu.mines.use.<mine-id>`; existing non-empty permission keys are preserved.
- Jail Parent selection is gone: Jail bounds automatically determine the smallest containing Region.
- `Cell radius` is removed. Physical solitude cells define normal movement while overall Jail bounds remain the escape safety boundary.
- Jail Administration shows dedicated 3D Jail/Task Area editor borders and supports individual cell selection, move and delete with active-prisoner safety.
- Jail punishment time fields show their units explicitly.
- Mine/Jail Setup Tools are obtained from Admin Tools; Mine Administration layout/notice/palette collisions are cleaned up.
- Jail task mining inside a Mine still strictly requires both `ssu.mines.use` and the specific Mine permission; a narrow jail-safe permission resolver fixes the prior false denial without opening other SSU features.
- Mine status holograms can now be explicitly removed.
- Jail Administration includes a paged/filterable active-prisoner overview with reason, facility, path/mode, remaining time, progress, buyout/cell state and quick moderation actions.
- Network protocol: `98`.
- Server Operations persistence schema: `3`.
- Mine definition schema: `3`.
- Physical Jail definition schema: `2`.
- Moderation player/Jail sentence schema: `2`; moderation settings/inventory snapshot schemas remain `1`.

## Previous development build: 1.9.0-dev3.13

### Dedicated Jail system redesign

- Introduced dedicated physical Jail facilities nested inside Regions, independent Mine nesting, punishment choice/task/solitude modes, 30-second choice flow, task deadlines, prisoner dashboard restrictions and safe player-state restoration.
- Network protocol: `97`; Server Operations schema: `3`; Mine definition schema: `2`; physical Jail definition schema: `1`; Moderation player/Jail sentence schema: `2`.

## Previous development build: 1.9.0-dev3.12

### Dedicated Mines completion pass

- Completed the standalone Mines phase with inventory-backed weighted reset palettes and custom drop authoring.
- Added `NORMAL`, `NONE` and `CUSTOM` drop modes, XP multipliers, independent Fortune/Silk Touch rules, warning modes/sounds and safer reset countdown/retry behaviour.
- Added generated live Mine status holograms and dedicated Mine Statistics with progress, lifetime mining, use/reset counters, top miners and block breakdowns.
- Added Mine paging and server-side teleport permission hardening.
- Network protocol: `96`; Server Operations schema: `3`; Mine definition schema: `2`.

## Previous development build: 1.9.0-dev3.11.1

### Final 26.2 GUI polish + Support workflow + Mines foundation

- Mail feedback is wrapped inside the panel.
- Shared rich-text editing uses 16 direct Minecraft colour swatches with coloured hover names.
- Floating Hologram, Kits, Support, Wallet and Profile screens are compacted and cleaned up.
- Kits use real item previews/tooltips and a corrected ghost-inventory editor.
- Support uses a dedicated Create Ticket flow, rich-text replies, required close reasons and configurable closed-ticket retention (24h default).
- Wallet adds a known-player picker; Profile title controls are compacted.
- Dedicated Mines phase 1 added persistent mine definitions, setup tool, weighted palettes, progress, permissions, teleport spawn/exit and bounded manual/automatic resets.
- Network protocol: `96`.
- Server Operations persistence schema: `3`.
- Mine definition schema: `1`.

### Performance-first Server Operations suite

SSU now includes a compact, GUI-first Server Operations layer intended to cover the remaining day-to-day needs of a public server without turning normal gameplay into a continuous scan workload.

- Adds a **lightweight player block activity log** for break/place actions only, with bounded retention and a conservative rollback that restores block type/default state only. It intentionally does not log redstone, fluids, pistons, containers or block-entity NBT, and skips active minigame/dungeon gameplay.
- Adds **manual world backups**, optional automatic backups, retention controls, protected last-backup deletion and staged restore with a pre-restore world safety copy. Automatic backups are opt-in and can be driven by the scheduler.
- Adds a central **Scheduler / Task Manager** with interval, daily and one-time schedules for backup, broadcast, maintenance on/off, SSU save/reload and controlled server stop actions.
- Adds **Maintenance Mode** with a custom disconnect message, optional kick of current non-bypass players and `ssu.maintenance.bypass`.
- Adds **chat moderation** with mute/temporary mute, slow mode, duplicate/flood/caps/link/blocked-phrase controls, capped in-memory recent chat and permission-gated `#` staff chat. Automatic chat filtering is opt-in.
- Adds a persistent, bounded **Staff Audit Log** and hooks high-value permission/rank/economy, moderation, inventory, kit, dimension, onboarding, region and minigame administration changes.
- Adds a lightweight **Server Health** dashboard that reuses SSU's existing performance monitor for TPS/MSPT, heap, players, jobs, permission/cache and module timing data instead of running a second profiler.
- Adds player **Support / Report tickets** with player-created tickets and an admin queue for assignment, notes, resolve/reopen/close.
- Adds **World management** for per-dimension world borders and throttled chunk pregeneration with 1–4 chunks/tick and automatic pause above a configurable MSPT threshold.
- Improves the existing **Player Info & Profile permission view** instead of adding a duplicate inspector: effective permission rows now explain the winning personal/rank/wildcard source and inheritance path.
- Adds read-only **Economy analytics** for money supply, loaded transaction counts/24h volume, richest accounts, loaded volume by transaction type and configurable large-transaction alerts. Existing Economy Admin remains the mutation interface.
- Adds **configuration profiles** for exporting/importing selected server configuration without player balances/mail/inventories/progression. Every import first creates a persistent `pre-import-*` safety profile and remains confirmation-gated in the GUI.
- Expensive analytics and filesystem work are on-demand; log writes use one background IO queue; scheduler/health sampling is bounded; no new whole-world/player/entity scans run every tick.
- Network protocol: `94`.
- New Server Operations storage schema: `1`.
- Existing Player Claim, Region, Minigame, UI preference, Title, Identity, Onboarding, Moderation and Kit schemas remain unchanged.

Version state:

- Network protocol: `94`
- Server Operations schema: `1`
- Server/Lobby spawn storage schema: `2`
- Onboarding settings/player schema: `1`
- Moderation settings/player/inventory schema: `1`
- Kit definition/player schema: `1`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Portable selection snapshot format: `1`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.8

### GUI-first completion, minigame fixes and Region Tool clarity

- Kit permission keys are discovered dynamically by the Permission Editor, including custom per-kit keys.
- Minigame results use fixed column anchors.
- Mandatory onboarding offers `Decline & leave`.
- Spleef temporary projectile grants cooperate with the minigame inventory lock.
- Minigame Setup has confirmed manual `Restore snapshot` for idle arenas.
- Region Setup wording/navigation and reset-vs-portable snapshot terminology are clearer.
- Network protocol: `93`; persistent schemas unchanged.

### Server lifecycle, moderation and player onboarding

- Administrators can teleport directly to loaded managed dimensions.
- Server Spawn and first-join Lobby Spawn can be set in any dimension.
- Respawn priority is: valid bed/respawn anchor → SSU Server Spawn → vanilla Overworld spawn.
- New players can be locked into a configurable rich-text Rules and introduction flow before normal play becomes available.
- Player Info now opens a compact management hub for warning, kick, ban, whitelist, history, freeze, jail and live inventory/ender-chest administration.
- Jail sentences support time, buyout and virtual community-mining tasks with system-mail resource distribution.
- Players can view and claim accessible nine-slot kits; administrators can configure kit contents, price, cooldown, one-time use and permission requirements.
- Network protocol is `93`.

Version state:

- Network protocol: `93`
- Server/Lobby spawn storage schema: `2`
- Onboarding settings/player schema: `1`
- Moderation settings/player/inventory schema: `1`
- Kit definition/player schema: `1`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Portable selection snapshot format: `1`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.6.5

### CTF and Domination capture HUD spacing

- Keeps the existing capture label and progress bar in their current correct positions.
- Moves the additional `Do not move, attack, use items, or take damage.` instruction into a separate centered HUD line above them.
- Uses the same corrected layout for CTF flag taking and Domination base claiming.
- Network protocol is `92`; all schemas remain unchanged.

Version state:

- Network protocol: `92`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Portable selection snapshot format: `1`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.6.4


### Team-specific CTF and Domination capture sounds

- A completed CTF flag score plays the vanilla Ponder goat horn only for the scoring team.
- The opposing CTF team hears a beacon-deactivation loss cue instead of another horn.
- A completed Domination base capture uses the same Ponder celebration for the capturing team and the loss cue for every opposing player.
- Claim-start, flag-theft and defense sounds are unchanged.
- Network protocol remains `91`; all schemas remain unchanged.

Version state:

- Network protocol: `91`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Portable selection snapshot format: `1`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.6.3

### Region Setup Tool polish and world-visible snapshot placement

- Removes the duplicate region-creation button from the `All regions` page. Region creation remains available from the dedicated Selection workflow.
- Updates `Save full snapshot` immediately while the name is typed and shows live name validation.
- Reflows the Full snapshots page so fields, buttons, validation feedback and explanatory text do not overlap.
- Replaces the large in-menu ghost-preview editor with compact transparent controls rendered over the world.
- Adds preview movement on all three axes, rotation, mirroring, explicit placement confirmation and explicit cancellation.
- Adds `Free mode`: the preview stays fixed while the administrator walks or flies around it. Left-click returns to edit controls.
- Blocks normal world actions, inventory containers, item pickup/drop and commands while a preview session is active.
- Clears preview state safely after placement, cancellation, logout, death or dimension change.
- Network protocol remains `91`; all schemas remain unchanged.

Version state:

- Network protocol: `91`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Portable selection snapshot format: `1`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.6.2

### Compact Region Setup Tool and region selection toggle

- Uses a compact 690×400 Region Setup panel and reflows all six pages.
- Keeps labels and controls in separate layout areas to avoid overlap.
- Adds `Select region` / `Unselect region` to copy exact region bounds into the active Region Tool selection.
- Network protocol remains `91`; all schemas remain unchanged.

## Previous development build: 1.9.0-dev3.6.1

### Compile hotfix

- Fixes the claim-role Permission Editor lambda capture in `SsuMenuService`.
- Removes stale `operationCount()` overrides from selection snapshot jobs.
- Network protocol remains `91`; all schemas remain unchanged.

### Unified Region Setup, remote editing and snapshot previews

- The Region Setup Tool no longer duplicates point-selection buttons. Administrators mark both corners directly in the world with the bound Region Tool.
- A permanent `All regions` tab lists every editable region, identifies the region at the player's current position and allows remote editing or safe teleportation.
- `Teleport to region` uses the configured region spawn when available and otherwise searches for a safe destination inside or immediately above the region.
- The integrated Selection tab can create a region from the current selection, clear it to air, fill it with water/lava or fill it from a weighted mix of inventory block items. Unused percentage becomes air.
- Full portable selection snapshots preserve blocks, container/block-entity data and structural entities. Snapshots are stored separately from scheduled region-reset snapshots.
- Loading a full selection snapshot first creates a translucent ghost preview five blocks in front of the administrator. It can be translated along X/Y/Z, rotated or mirrored before placement is confirmed.
- Preview packets are sampled and bounded for safety; confirmation still places the complete snapshot through the bounded SSU job scheduler and existing cuboid/region resource locks.

Version state:

- Network protocol: `91`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Portable selection snapshot format: `1`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.5

### GUI completion, advanced ranks and Player Claim access roles

- Adds GUI controls for rank priority and rank inheritance to Rank Management.
- Changes the player Permission Editor from destructive single-rank assignment to explicit Add/Remove controls, allowing multiple simultaneous ranks per player.
- Adds a server-wide Claim roles mode to the Permission Editor for Owner, Co-owner, Member, Visitor and no-claim contexts.
- Expands Claim Settings > Claim access > Manage into one place for assigning Members/Co-owners and configuring per-claim role permission overrides. Owner always has full control and remains the only role that can open Claim Settings.
- Adds contextual Player Claim permissions for block break/place, non-living entity modification, containers, doors/trapdoors/fence gates, buttons/levers/pressure plates, item pickup/drop, claim-home use, living-entity damage and living-entity interaction.
- Makes homes linked to a claim available as read-only travel destinations when the player's assigned role allows it. Visitors may use them only while physically inside that claim and when explicitly allowed.
- Adds Region Rental cancellation refund percentages to the Rent Journal GUI for player cancellations and administrator cancellations.
- Adds live minigame score Add/Set controls to Minigame Administration.
- Raises the network protocol to `90` for the expanded dashboard and Claim access payloads.
- Raises the Player Claim storage schema to `3`; legacy trusted players migrate safely to the Member role.

Version state:

- Network protocol: `90`
- Player Claim storage schema: `3`
- Region storage schema: `5`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.4

### Region Setup Tool and scheduled region restoration

- Removes punctuation decorations from Pop and Burst damage indicators. Every damage/healing style now shows only the signed value, except Hearts which retains its heart symbol.
- Adds a sixth Damage Indicator style, `Drop`: the number originates tightly from the affected entity, pops upward briefly and then falls under a gravity-like curve.
- Right-clicking the bound Region Tool now opens one server-authoritative Region Setup Tool. Standing inside an editable region opens its full editor; otherwise the same screen guides point selection and region creation.
- The Region Setup Tool exposes general settings, all existing protection flags, contextual permission overrides, rental/access settings, region spawn/border/messages, redefine/delete controls and scheduled restoration.
- Adds scheduled region resets with a configurable interval, optional wait-until-empty policy and either a saved region snapshot or a weighted block preset as source.
- Weighted reset presets are assembled by clicking up to six block items or water/lava buckets in the player's inventory. Percentages may total up to 100%; any remainder becomes air.
- Scheduled and manual resets run through the bounded SSU job system, respect region/minigame/rental recovery locks and avoid unsafe container drops during preset fills.
- Region creation validates all submitted values before persisting the new region, preventing partially created regions after invalid rent or preset input.

Version state:

- Network protocol: `89`
- Region storage schema: `5`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.3.3

### Damage indicators, smooth titles and universal match inventory locking

- Damage/healing indicators are approximately twice as large and stay fully opaque for most of their lifetime.
- Settings > Combat now offers five display styles: Floating, Hearts, Compact, Pop and Burst.
- The local player never sees their own selected title above themselves. Remote titles use partial-tick interpolation and move smoothly with their player entity.
- Match inventory locking is now a definition-level rule that defaults to enabled for every minigame. The CTF, Domination, Spleef and Generic editors expose an `Inventory lock` toggle for explicit exceptions.
- When inventory locking is enabled, SSU restores the exact server-owned inventory, armor and offhand layout every tick and clears cursor-carried duplicates. A carried CTF flag remains an intentional temporary helmet replacement.

Version state:

- Network protocol: `88`
- Minigame definition schema: `19`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.3

### Global player identity and combat feedback

- The Tank Defensive Field now uses the vanilla lightning impact sound.
- Titles are global player-profile cosmetics rather than Minigame Profile selections. The server title catalogue supports the fixed sixteen Minecraft colours and acquisition by free access, minigame level/wins, rank, permission or manual administrator grants.
- Players can independently show/hide their selected title and styled rank prefix above their name from Settings > Identity.
- Rank Management includes a rich prefix editor with multi-colour text plus Bold, Italic, Underline and Strikethrough. Rank prefixes appear before the standard player name in nameplates and chat.
- Settings > Combat now contains personal damage-indicator enablement and Floating, Hearts or Compact styles. Damage is red, healing is green, and `ssu.damage_indicators.use` defaults to allowed.

Version state:

- Network protocol: `88`
- Minigame definition schema: `18`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `11`
- Title catalogue schema: `1`
- Player identity schema: `1`

## Previous development build: 1.9.0-dev3.2.1

### Minigame manager API compile hotfix

- Restores the public runtime methods accidentally removed from `MinigameManager` while the Ready system was deleted.
- Repairs the unresolved calls used by `TeleportPolicy`, `MinigameEvents`, `MinigameCommands` and internal Spleef/disconnect handling.
- Restored methods: `matchView`, `finishMatch`, `addScore`, `setScore`, `eliminate`, `onPlayerDeath`, `onPlayerRespawn` and `onLogin`.
- The Ready/Unready system remains removed; preparation-time-only match startup from dev3.2 is unchanged.

Version state:

- Network protocol: `87`
- Minigame definition schema: `18`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `10`

## Previous development build: 1.9.0-dev3.2

### Minigame progression, preparation and match-overview polish

- Makes all three weekly minigame challenges configurable per definition, including enable/disable, required progress and XP rewards.
- Removes the complete Ready/Unready flow. COUNTDOWN is now solely the configured preparation time and starts automatically when it expires.
- Shows a large synchronized `10` through `1` preparation countdown with one sound per second, followed by `GO!`.
- Keeps important cancellation and match-end reasons visible longer and also writes them to chat.
- Replaces the normal `U` dashboard while participating in a match with a detailed live match overview and confirmed Leave match action.
- Fixes the Minigame Profile Victory effect label/button overlap.
- Expands Treecapitator and Veinminer outline selection to the fixed sixteen-colour Minecraft palette: White, Light Gray, Gray, Black, Brown, Red, Orange, Yellow, Lime, Green, Cyan, Light Blue, Blue, Purple, Magenta and Pink.

Version state:

- Network protocol: `87`
- Minigame definition schema: `18`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `10`

## Previous development build: 1.9.0-dev3.1

### MVP validator, boosts, borders and dashboard polish

- Removes Arena Validator false positives for external lobby/spectator areas and temporary End Rod boost markers.
- Adds reliable Tank radial knockback and configurable Regeneration Boost health-per-second balancing.
- Rebuilds the CTF/Domination Boosts tabs into a compact, non-overlapping layout.
- Labels every Match Flow and Progression & Integration numeric field.
- Makes Minigame/Spectator border visibility affect both runtime and Setup Tool overlays.
- Uses readable Treecapitator/Veinminer color names, changes the minigame HUD keybind to `J`, and installs the supplied Questbook and My Warps icons.

Version state:

- Network protocol: `86`
- Minigame definition schema: `17`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `9`

## Previous development build: 1.9.0-dev3.0.2

### Reward, border and compact-GUI update

- Repairs missing escrow only for idempotent server-generated SYSTEM, MINIGAME and RECOVERY money attachments, fixing valid reward claims that could report `Insufficient funds` without weakening player/Auction House escrow guarantees.
- Routes game and spectator bounds through the exact region-border renderer, including clipping, configurable colors, translucent fill and live-match visibility toggles.
- Reduces the Mailbox and Auction House maximum dimensions by approximately 25% while keeping both centered.
- Adds the supplied claim-land, travel, wallet, mail and minigame dashboard textures and renders dashboard tile icons at approximately twice their former size.
- Network protocol remains `85`; Minigame definition schema remains `16`; recovery schema remains `4`; progression schema remains `3`; match-history schema remains `1`; Player UI preference schema remains `9`.

## Previous development build: 1.9.0-dev3.0

### Minigame Experience Update

- Detailed match results, kill/objective feed, compact/expanded/hidden HUD modes, reconnect grace, AFK handling, ready checks, overtime, voting and spectator navigation.
- Rating- and preferred-role-aware team balancing within every configured role minimum and maximum.
- Cosmetic-only progression with XP, levels, ratings, badges, titles, victory effects and weekly challenges.
- Arena validation, issue teleporting, safe cloning, JSON export/import and Minigame System Health diagnostics.
- Shared safe ability components plus integrations with existing quests/events, custom statistics, holograms, mail and configured minigame rewards.
- Rewards settle first. Progression/history then commit through a bounded idempotency ledger and a verified two-file storage barrier before players are returned. Summary mail is retried with correlation-based duplicate protection.

Version state:

- Network protocol: `85`
- Minigame definition schema: `16`
- Minigame recovery schema: `4`
- Minigame progression schema: `3`
- Minigame match-history schema: `1`
- Player UI preference schema: `9`

## Previous development build: 1.9.0-dev2.9.3.1

### Minecraft 26.2 Tank knockback compile hotfix (1.9.0-dev2.9.3.1)

- Tank Defensive Field now calls Minecraft 26.2's required five-argument knockback API.
- The Tank is supplied as the player-attack source while the knockback itself deals zero damage.
- Enemy-only radial push strength, Slowness, role cooldowns and knockback resistance remain unchanged.

Version state:

- Network protocol: `84`
- Minigame definition schema: `15`
- Minigame recovery schema: `4`
- Player UI preference schema: `9`

## Previous development build: 1.9.0-dev2.9.3

### Delayed respawns and controlled minigame healing (1.9.0-dev2.9.3)

- Capture the Flag and Domination now use an administrator-configurable respawn delay from `1` to `300` seconds; the default is `5` seconds.
- A defeated player waits in spectator mode at the arena spectator location and sees a large center-screen countdown before returning to the selected team respawn.
- Respawning players are excluded from combat, role abilities, objectives and boost collection until the delay finishes.
- The food bar remains visually full, but hunger-based natural regeneration is canceled throughout active CTF and Domination matches.
- Normal food use remains blocked by the server-owned minigame loadout and interaction policy.
- Healer abilities remain authoritative direct heals. Regeneration boosts use controlled SSU healing ticks, so these are the only intended sources of restored health during combat.
- Death while carrying a CTF flag still drops the flag before the respawn wait begins.

Version state:

- Network protocol: `84`
- Minigame definition schema: `15`
- Minigame recovery schema: `4`
- Player UI preference schema: `9`

## Previous development build: 1.9.0-dev2.9.2

### Single queue action and Tank knockback (1.9.0-dev2.9.2)

- The Minigame Lobby now has one primary queue/match control: **Join queue**, **Leave queue**, or **Leave match**, based on the player's global minigame state.
- The selected game only determines the destination of a new queue join. A player cannot queue for another game until the current queue or match has been left.
- The server also checks every concrete queue map before accepting a join, preventing duplicate queue membership if state ever becomes inconsistent.
- Tank Defensive Field still slows enemies inside its configured AOE and now also pushes them radially away from the Tank.
- CTF and Domination administrators can configure Tank knockback strength from `0.0` to `5.0`; the default is `1.0` and zero disables knockback.

Version state:

- Network protocol: `83`
- Minigame definition schema: `14`
- Minigame recovery schema: `4`
- Player UI preference schema: `9`

## Previous development build: 1.9.0-dev2.9.1

### Minigame arena block-placement hotfix (1.9.0-dev2.9.1)

- **Edit arena blocks** now permits normal block placement inside the selected idle arena.
- The preliminary right-click no longer gets rejected by managed-region interaction protection.
- The clicked block's own interaction is denied while the held block item is allowed to place, preventing accidental container, button, door or other block activation.
- The actual placement event still enforces arena bounds and active-runtime safety.
- Network protocol remains 83; Minigame definition schema remains 13; Minigame recovery schema remains 4; Player UI preference schema remains 9.

### Open preparation matches, role polish, mining HUD and compact Auction House (1.9.0-dev2.9)

- Tactical role abilities in CTF and Domination always activate when ready and consume their cooldown even on a miss or with no valid targets.
- Tank slow AOE and Healer healing AOE each have an admin-configurable radius, defaulting to 3 blocks.
- The DPS bow is supported by the minigame interaction guard and retains its replenished configurable effect arrow.
- Players may join an existing match while it is in COUNTDOWN/preparation, up to the configured player maximum. Joining remains disabled after RUNNING begins.
- A global Leave queue / Leave match control is available from the lobby regardless of the selected minigame.
- The active match scoreboard can be shown or hidden with the configurable keybind that defaults to `L`.
- Role ability presentation is more visible and audible, while temporary role equipment is restored to its intended server-owned slots.
- Treecapitator and Veinminer can independently show an outline-colored, two-line block/count panel below the crosshair.
- The Auction House main browser is shorter and narrower, remains centered, uses aligned lower frames and no longer shows the Sale tax footer label.

Version state:

- Network protocol: `83`
- Minigame definition schema: `13`
- Minigame recovery schema: `4`
- Player UI preference schema: `9`

## Previous development build: 1.9.0-dev2.8.2

### Runtime objective labels and minigame borders (1.9.0-dev2.8.2)

- Live Domination objective labels now use the same fitted semi-transparent black billboard background as setup labels, with text and background locked to the same camera-facing plane.
- Active matches can show the arena game border and configured spectator border through the existing claim/region border renderer, including distance clipping and translucent-safe world rendering.
- Players can independently toggle **Minigame border** and **Spectator border** under Settings → Borders. Existing player preference files migrate both options to enabled.
- Adds dedicated server-configurable colors for minigame game and spectator areas.
- Network protocol is 82; Minigame definition schema remains 12; Minigame recovery schema remains 4; player border preference schema is 4.

### Role compile and objective-cast hotfix (1.9.0-dev2.8.1)

- Corrects the Minecraft 26.2 `DyedItemColor` component, relocated arrow-entity package and missing white-banner constant used by the first tactical-role build.
- CTF flag and Domination node casts no longer cancel themselves because NeoForge continues one right-click through main-hand, offhand and item interaction stages.
- Only continuation stages from the exact server tick that created the cast are absorbed; every genuinely new gameplay action still interrupts the cast.
- Network protocol remains 81; Minigame definition schema remains 12; Minigame recovery schema remains 4.

### Optional CTF and Domination tactical roles (1.9.0-dev2.8)

- Capture the Flag and Domination can optionally use server-composed DPS, Tank and Healer teams. Players choose a preferred role before joining, but the final assignment follows each role's configured per-team minimum and maximum.
- Every role has configurable maximum health, armor and armor toughness. Full team-colored leather armor is cosmetic only; role attributes provide the actual defense and health.
- DPS uses a Diamond Sword, Bow and replenished configurable effect arrow, defaulting to Poison I.
- Tank uses a Stone Sword, team-colored logo Shield and an enemy-only two-block AOE slow with configurable duration and cooldown.
- Healer uses a Stone Sword, an eight-block straight single-target heal, a weaker nearby-team AOE heal and a 25% maximum-health self-heal, each with configurable balancing values and cooldowns.
- Role ability items display Minecraft's normal cooldown overlay. Server-side checks protect team targeting, distance, match state and arena interactions; temporary role equipment does not wear down during a match.
- Network protocol is 81; Minigame definition schema is 12; Minigame recovery schema is 4.

### Setup area overlays and action-locked objective casts (1.9.0-dev2.7.2)

- Holding the Minigame Setup Tool now shows the selected game/arena border in cyan, spectator bounds in purple and the configured Spleef floor in amber, independently of the normal Region border switch.
- Every configured area uses a large labeled outline; spectator bounds and the Spleef floor also use a subtle translucent fill for an immediate layout overview.
- CTF flag-take and Domination node-claim casts now cancel immediately when the caster attacks, breaks or places blocks, uses an item, interacts with a block/entity or attempts to drop an item; the triggering action is canceled as well.
- Movement, leaving the objective and incoming damage remain interrupt conditions.
- Network protocol is 80; Minigame definition schema remains 11; Minigame recovery schema remains 3.

### Enlarged setup labels (1.9.0-dev2.7.1)

- All Minigame Setup Tool labels are approximately 3.6 times larger and sit slightly higher above their physical marker.
- Lobby, spectator, spawn, flag, node and boost labels now receive a fitted semi-transparent black billboard background for much stronger contrast.
- Text and background share the same camera-facing orientation and visual layer; a minimal depth bias prevents z-fighting.
- Network protocol remains 79; Minigame definition schema remains 11; Minigame recovery schema remains 3.

### Boost presentation, Spleef projectile polish and setup markers (1.9.0-dev2.7)

- Boost visuals now use Golden Boots for Speed, a Golden Apple for Regeneration, a Diamond Chestplate for temporary Armor and the existing Rabbit Foot for Jump.
- Boost pickup sounds use the firework launch, beacon power-select, diamond armor-equip and wind-charge throw sounds respectively.
- The infinite Spleef Snowball now uses Minecraft's visible item cooldown overlay, remains visibly present after a valid throw and resynchronizes safely when clicked too early.
- Both Spleef projectiles are converted to fast, gravity-free, zero-spread shots that travel in the exact look direction.
- Manual CTF and Domination boost positions are shown by temporary upright End Rods during arena setup. They are removed before matches and snapshot capture just like the existing setup banners.
- Network protocol remains 79; Minigame definition schema remains 11; Minigame recovery schema remains 3.

### Spleef projectiles and match boosts (1.9.0-dev2.6.2)

- Spleef now supports a delayed infinite single-block Snowball with cooldown and delayed random awards of finite stackable five-block Power Eggs.
- Capture the Flag and Domination now share configurable floating Speed, Regeneration, temporary Armor and Jump pickups with colored mist, manual or automatic placement, active limits, spacing and random respawn timers.
- The Spleef, CTF and Domination editors expose the new settings; the Minigame Setup Tool registers up to 64 manual boost spawn slots.
- Boost visuals and temporary armor are cleaned and recovered safely across match finish, logout, restart and crash recovery.
- Network protocol remains 79; Minigame definition schema is 11; Minigame recovery schema is 3.

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
