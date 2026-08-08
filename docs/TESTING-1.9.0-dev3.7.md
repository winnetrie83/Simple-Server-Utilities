# SSU 1.9.0-dev3.7 — Runtime Test Checklist

This build introduces dimension-aware spawn routing, first-join onboarding, moderation and jail administration, online/offline inventory editing, and permission-aware kits. Test on a real dedicated NeoForge 26.2 server with a separate client before treating the build as stable.

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev3.7 JAR on client and server.
- Confirm a dev3.6.5 client is rejected by protocol 93 rather than connecting with mismatched payloads.
- Start from a copy of the world and persistent SSU data.

## Managed dimensions and teleport

- Open Admin Center → Dimensions and select the Overworld, Nether, End, and a custom enabled dimension.
- Verify `Teleport` is enabled only when the selected dimension is loaded and usable.
- Teleport to each loaded dimension and confirm SSU chooses a safe position near that dimension's shared spawn.
- Verify disabled, missing, or not-yet-registered dimensions fail safely with a readable message.
- Restart after changing managed-dimension registry settings and verify existing dimensions remain intact.

## Server Spawn and respawn priority

- Set Server Spawn in the Overworld and verify `/spawn`/dashboard Spawn use that exact destination.
- Set Server Spawn inside a custom dimension and restart the server.
- Die with a valid bed spawn: the bed must remain the destination.
- Die with a valid respawn anchor: the anchor must remain the destination.
- Break or obstruct the personal respawn point, then die: the player must reach SSU Server Spawn in its configured dimension.
- Die without any personal respawn point: the player must reach SSU Server Spawn.
- Temporarily make the SSU spawn dimension unavailable and verify vanilla Overworld spawn is the final fallback.
- Confirm minigame and dungeon death/recovery logic still takes precedence and does not leak into the global spawn router.
- Confirm only one Server Spawn exists at a time; setting a new one replaces the old destination atomically.

## Lobby Spawn and onboarding administration

- Open `Onboarding & Spawns` and set/clear Server Spawn and Lobby Spawn in different dimensions.
- Enable onboarding, edit rich-text Rules, and create multiple rich-text introduction pages using all supported styles and the fixed 16 colors.
- Verify invalid/empty configuration is rejected without corrupting the last saved settings.
- Confirm Lobby Spawn falls back to vanilla Overworld spawn when it is not configured or cannot be resolved.
- Test the admin actions to reset a player's onboarding and to mark a player complete.

## First-join onboarding flow

Use a genuinely new UUID/player-data entry.

- First login must send the player to Lobby Spawn before ordinary play becomes available.
- Verify the centered welcome presentation and firework effect are visible without damaging blocks/entities.
- After the configured delay, verify the player is prompted to press the actual configured SSU menu key rather than a hard-coded letter.
- Pressing the SSU key must open the onboarding screen instead of the normal dashboard.
- Rules must be readable page by page and require two distinct confirmations.
- Verify the first confirmation cannot accidentally complete acceptance through a double click or stale packet.
- Test introduction Next/Back navigation and optional Skip behavior.
- Complete onboarding, reconnect, and verify the normal dashboard and permissions are restored permanently.
- Restart between Rules acceptance and introduction completion; progress must resume safely.

## Onboarding lock and bypass resistance

Before completion, verify the player cannot:

- move, jump, swim, fly, or be pushed away from the onboarding anchor;
- break/place/interact with blocks;
- attack, damage, or interact with entities;
- use items, containers, inventory menus, pickup, or drop items;
- execute commands or use normal SSU dashboard routes;
- bypass the lock by dying, changing dimension, reconnecting, or sending stale GUI actions.

Also verify administrators can still manage the player and that moderation/jail restrictions correctly take precedence.

## Player management and moderation

From Player Info & Profile, open `Manage` for online and offline players.

### Warnings

- Send a rich-text warning with multiple colors/styles.
- Verify the warning is shown in large text for the configured number of seconds.
- Verify only the warned player hears the vanilla Call goat horn.
- Confirm the warning is recorded once in history with actor, timestamp, and reason.

### Kick, bans, and unban

- Kick an online player with a rich-text reason and verify the disconnect reason is readable.
- Apply a temporary ban, reconnect before expiry, and verify denial includes the reason and remaining/expiry information.
- Reconnect after expiry and verify access is restored.
- Apply a permanent ban to an online and an offline player.
- Unban by UUID/name and verify the history retains both ban and unban records.
- Confirm malformed durations and unknown targets are rejected safely.

### Whitelist

- Confirm the custom whitelist is OFF by default.
- Enable it and add entries by exact player name and by UUID.
- Verify listed players can connect and non-listed players are disconnected cleanly.
- Verify authorized administrators/operators retain the intended bypass.
- Disable the whitelist and verify ordinary access resumes.
- Restart and confirm whitelist state and entries persist.

### History and name changes

- Join with a known UUID under one name, then later under another name.
- Verify the name history shows both names without creating a second moderation identity.
- Confirm warnings, kicks, bans, unbans, freeze, jail, release, and inventory administration are timestamped and attributable.
- Test pagination with more records than fit on one page.

### Freeze

- Freeze an online player and verify movement and ordinary actions are blocked.
- Confirm the SSU key does not open an unrestricted dashboard.
- Unfreeze and verify the previous location/inventory/state is unchanged.
- Disconnect/reconnect while frozen and verify the restriction persists until explicitly removed.

## Jail system

### Location and sentence modes

- Set Jail location in a non-Overworld dimension and verify it persists after restart.
- Jail an online and an offline player with a rich-text reason.
- Test indefinite/manual release.
- Test timed release, including server restart while the sentence is active.
- Test economy buyout with insufficient and sufficient funds; verify `JAIL_BUYOUT` ledger entries and no double debit.
- Confirm the jailed player receives the Jail dashboard instead of the normal dashboard and cannot use ordinary commands.

### Community mining task

- Configure an existing region as the task mine.
- Configure several required block IDs/counts and one or more allowed tools.
- Verify the prisoner's normal inventory is backed up before task equipment is applied.
- Confirm task tools are restored when damaged/moved, cannot be dropped, and remain effectively unbreakable.
- Break an allowed configured block inside the task region: the block should be removed, no item should enter inventory, and virtual progress should increase once.
- Break a non-required block, a block outside the region, or use an invalid tool: progress must not increase and no bypass should occur.
- Test progress and dashboard persistence through reconnect/restart.
- Reach all requirements and press the completion button.
- Verify contribution items are divided among players seen within the configured lookback window and delivered by system mail with the prisoner attribution.
- Verify no duplication when recipient count, quantity, or mail capacity creates edge cases.
- Confirm original inventory/armor/offhand is restored exactly once and the player is released to onboarding when incomplete, otherwise Server Spawn.
- Test manual unjail and administrator cancellation paths for safe inventory restoration.

## Online and offline inventory administration

- Open an online player's inventory and confirm all 36 inventory slots, armor, offhand, and ender chest are represented.
- Move items between the target inventory and the administrator's own inventory; the administrator inventory must remain normally usable.
- Close the menu and verify changes apply exactly once without loss or duplication.
- Modify armor/offhand and verify visual/equipment synchronization.
- Open an offline player's saved inventory, edit it, restart, then let the player join; pending edits must apply once.
- Verify offline ender-chest changes persist.
- Attempt to manage your own inventory through the target editor and verify the unsafe self-edit route is refused.
- Test full inventories, empty stacks, stack limits, custom NBT/components, containers, damaged items, and enchanted items.
- Disconnect the target while an online edit screen is open and verify safe conflict handling.

## Kits

### Administration

- Create, rename/update, enable/disable, lock/unlock, and delete kits.
- Fill a kit from the administrator inventory using 1–9 exact ghost stacks; source items must not be consumed.
- Verify a tenth slot cannot be added.
- Test exact preservation of components/NBT, damage, enchantments, custom names, and stack counts.
- Configure price, cooldown, one-time use, and a dynamic permission key.
- Save/restart and verify definitions and contents persist.

### Player use

- Open the Kits dashboard and verify players see only kits allowed by both `ssu.kits.use` and the kit-specific permission.
- Preview contents without receiving items.
- Claim a free kit and a priced kit; verify `KIT_PURCHASE` ledger records and atomic debit.
- Verify insufficient funds, insufficient inventory capacity, locked/disabled status, cooldown, and one-time use are all rejected without partial delivery.
- Reconnect/restart during cooldown and verify remaining time is retained.
- Confirm one-time usage cannot be reset by renaming/reordering unless explicitly intended by the stored kit ID.
- Test two rapid claim requests and verify the kit is delivered/debited only once.

## Regression checks

- Claims, Regions, Region Setup Tool snapshots/preview, minigames, dungeons, NPCs, Auction House, Mail, permissions, titles/ranks, and normal dashboard navigation still open and function.
- Chat rank prefix remains `RankPrefix PlayerName: message` with no duplicate name.
- Existing player data loads without being forced through onboarding unless the configured policy explicitly resets them.
- Existing spawn data migrates to schema 2 without losing Server Spawn.
- Server shutdown/restart produces no pending inventory, onboarding, moderation, jail, or kit write errors.
