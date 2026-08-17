# SSU 1.9.0-dev3.29 — NPC Dynamic Spawning test plan

## Build / startup
1. Build the mod and start a dedicated or integrated server with an existing dev3.28 world.
2. Confirm old NPC templates and placements load unchanged.
3. Open NPC Manager and verify **Templates / Placements / Spawning** tabs all work.

## Natural spawn profile
1. Create a reusable NPC template with visible custom name/faction/loot/combat settings.
2. NPC Manager → Spawning → Create. Choose Natural.
3. Start with easy test values: chance 100%, cycle 2s, attempts 8, min distance 8, max distance 24, global cap 4, max nearby 4.
4. Verify NPCs appear on valid ground around the player and never exceed the profile cap.
5. Verify group min/max, Y, light, day/night and biome restrictions independently.
6. Walk farther than despawn distance from the population and verify it is removed.
7. Verify spawned NPCs use the template's label, skin/model, faction reaction/combat, stats and loot.
8. Confirm natural NPCs do not appear in the persistent Placements tab and do not create placement JSON files.

## Physical vanilla Spawner profile
1. Place a vanilla Spawner and note/configure its old vanilla mob.
2. While looking directly at it, create or edit a Spawner profile and bind it.
3. Verify only the SSU NPC population is produced while the profile is enabled; the original vanilla mob must not also spawn from that bound block.
4. Test activation range, cooldown, spawn radius, group size, nearby cap and global cap.
5. Disable the profile and verify the block is released to normal vanilla-spawner behaviour.
6. Re-enable it and verify SSU control returns.
7. Break the bound spawner and verify the profile stops producing NPCs.
8. Place/look at another spawner, use **Rebind looked-at spawner**, save, and verify the new block is used.

## Editor / persistence
1. Restart the server and verify spawn profiles reload from `npcs/spawn_profiles`.
2. Rename an NPC template used by a spawn profile; verify the profile follows the new template ID.
3. Attempt to delete a template still referenced by a profile; deletion should be refused.
4. Delete a spawn profile and verify its live dynamic population is removed.
5. Use **Test** from the Spawning list for both Natural and Spawner profiles.

## Regression
- Persistent NPC placement/edit/delete remains functional.
- Patrols and schedules from dev3.27 remain functional.
- dev3.28 self-defense / assist / flee / hostile-sight reactions still work.
- NPC quests/dialogues/shops still open on persistent NPCs.
- Joining with a mismatched protocol is rejected as expected (`110`).
