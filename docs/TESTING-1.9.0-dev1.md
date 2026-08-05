# SSU 1.9.0-dev1 test checklist

Back up the test world before testing arena resets or player-state recovery. Client and dedicated server must use the exact same build and network protocol 70.

## Build and migration

1. Build with Java 25: `gradlew.bat clean build`.
2. Start a dedicated NeoForge 26.2 server and a separate client.
3. Confirm protocol 70 connects without a mismatch.
4. Confirm dev18.6 claims, claim tax, Homes, warps, regions, economy, NPCs, quests and dungeons load unchanged.
5. Confirm old generic minigame definitions migrate to schema 2 with `gameType=generic` and remain editable.
6. Confirm an old schema-1 minigame recovery entry returns the player to its saved location without clearing the current inventory.
7. Test Minigame rewards once with Quests enabled and once with Quests disabled; `give_item` and `give_money` must remain registered through Content Core.

## Create a Spleef arena from the Region Tool

1. Select both corners of a small disposable Spleef floor with the Region Tool.
2. Right-click and confirm **Create minigame arena** appears only for a Minigame administrator.
3. Enter an unused ID, display name, minimum players and maximum players.
4. Click **Create arena** once and confirm the button remains locked while snapshot capture is running.
5. Confirm SSU creates one hidden `ssu_mg_<id>` server region and a verified reset snapshot.
6. Confirm the definition opens disabled in the Minigame Editor after capture completes.
7. Confirm failed snapshot capture rolls back the draft and managed region.
8. Attempt a duplicate ID and a selection overlapping a player claim; both must fail without leaving partial data.
9. Confirm the selected points and temporary selection border are cleared after successful creation.

## Managed-region ownership and protection

1. Confirm the managed `ssu_mg_*` region is absent from the normal Admin Center Regions list.
2. Attempt to break, place, interact or PvP inside the idle arena as a normal player and as an administrator; all must remain blocked.
3. Attempt normal region delete, redefine, snapshot-save or clear operations; SSU must direct the administrator to Minigames.
4. Edit the minigame JSON through the GUI and try to change/remove the managed region ID; saving must fail.
5. Reference the same physical region from another minigame; saving must fail.
6. Delete the minigame while idle and verify only its server-owned managed region/snapshot is cleaned up. A manually referenced ordinary region must never be deleted by Minigames.

## Arena configuration and validation

1. Set a waiting-lobby location outside the floor.
2. Set a spectator location with a safe overview.
3. Confirm there is one distinct player spawn per configured maximum player.
4. Attempt to enable Spleef with too few spawns, duplicate spawn blocks, missing snapshot, unknown tool or unknown breakable block; saving must fail clearly.
5. Configure the allowed floor blocks, tool, tool requirement, PvP, drop cleanup and elimination depth.
6. Confirm future game types shown in code/data remain non-runnable and cannot be enabled accidentally.

## Queue, lobby and countdown

1. Open the player Minigame Lobby and join the Spleef queue.
2. Confirm the game type, queued count, free/blocked arenas and requirements are visible.
3. Join with the minimum number of players and confirm automatic start reserves one arena.
4. Confirm players are moved to the configured waiting lobby in Adventure mode with an empty temporary inventory.
5. Confirm the countdown HUD displays mode, state, timer and alive count.
6. Leave or disconnect during countdown until below minimum players; verify all available players are safely restored and returned to the queue.
7. Confirm a forced administrator start works with fewer players but grants no configured rewards.

## Player-state restoration

Before joining, give every test player distinctive:
- hotbar/main inventory contents;
- armor and offhand items;
- health and hunger values;
- experience level/progress;
- status effects;
- Survival/Creative/Adventure mode and flight state where applicable.

Then verify:
1. The complete state is restored after normal completion.
2. The complete state is restored after voluntary leave during a running match.
3. The complete state is restored after countdown cancellation.
4. Death/respawn does not drop or duplicate real inventory or experience.
5. Disconnect/reconnect restores from the persisted recovery record.
6. Normal SSU Home/Warp/Spawn/Claim/Region teleports are denied while in a live match.
7. While in the countdown lobby or arena, confirm ordinary block/item/entity interactions and attacks on world mobs are denied; configured same-match PvP still follows the Spleef toggle.
8. If the critical recovery file cannot be written, the player remains untouched and the match does not start.
9. After such a failure, new starts remain paused for the session instead of repeatedly risking state.

## Spleef runtime

1. Confirm every participant starts in Survival at a distinct spawn with only the configured tool.
2. Confirm only configured floor blocks can be broken.
3. With tool enforcement enabled, confirm another tool or empty hand is rejected.
4. Confirm players cannot place blocks.
5. Confirm outsiders and eliminated spectators cannot break the arena.
6. Toggle Spleef PvP and verify damage is allowed only when enabled and only between participants in the same running match.
7. Confirm environmental damage during countdown is blocked.
8. Fall below the configured elimination depth and verify spectator mode plus teleport to spectator spawn.
9. Die by damage and verify the respawn path returns the player as spectator rather than as an active participant.
10. Eliminate two final players nearly simultaneously and confirm a draw is possible instead of event-order deciding a winner.
11. Reach the time limit with multiple survivors and verify the configured last-team-standing resolution does not revive eliminated players as winners.
12. Confirm floor drops are removed when configured and never escape the temporary arena inventory lifecycle.

## HUD and spectator behavior

1. Confirm the HUD appears only while the server marks the player in a match.
2. Confirm it updates countdown, running time, alive count and spectator status.
3. Confirm it hides while another screen is open and clears after return/logout.
4. Confirm an eliminated player cannot interact, place or break and can leave only through the Minigame lifecycle.

## Rewards and idempotency

1. Configure participation and winner rewards using `give_item` and `give_money`.
2. Complete a normal match and verify state restoration occurs before rewards are applied.
3. Verify winners receive both participation and winner rewards; non-winners receive participation rewards only.
4. Configure two separate `give_money` actions and confirm both execute once using distinct action keys.
5. Fill a player's inventory and verify an item reward fails clearly without corrupting the restored inventory.
6. Force-start an administrator test match and confirm no rewards are granted.
7. Restart after a committed money reward and verify its durable Economy idempotency key prevents a duplicate credit.
8. Confirm post-reward cleanup pauses if the rewarded inventory cannot be encoded or durably stored; voluntary leave must not restore the old pre-reward inventory during this phase.

## Arena reset and blocked recovery

1. Complete a match after breaking the floor and confirm the snapshot reset restores it before the arena becomes free.
2. Confirm no new match can reserve the arena while resetting.
3. Force a reset job failure in a disposable copy and verify the arena becomes **Blocked**.
4. Use **Restore arena** and confirm it performs a real snapshot reset rather than merely clearing the block flag.
5. Confirm the arena is released only after successful restoration.

## Crash/restart tests

Use a disposable server process and backups.

1. Stop after the recovery file is durably written but before countdown inventory replacement; restart and verify the original state remains recoverable.
2. Stop during countdown after inventories are cleared; reconnect and verify restoration.
3. Stop during an active match after floor damage; reconnect and verify player restoration and arena blocked-until-reset behavior.
4. Stop during spectator state; reconnect and verify original state restoration.
5. Stop after player state restoration but before final return teleport; verify recovery returns the player without duplicating inventory.
6. Stop during arena reset; restart and verify the arena remains unavailable until **Restore arena** succeeds.
7. Corrupt a copied recovery entry and verify SSU does not partially clear or overwrite the player's live inventory.

## Regression checks

1. Region selection copy/paste/fill/templates/transforms still work.
2. Normal Regions settings and snapshots still work for non-managed regions.
3. Claim tax dev18.6 payment, forfeiture and permanent-capacity safeguards still work.
4. Player Travel, Homes and rented warps still work.
5. Quests and Dungeons still load and their shared Content Core actions remain available.
6. Admin score Add/Set still works for a running generic minigame.
