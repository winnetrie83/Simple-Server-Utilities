# SSU 1.9.0-dev3.33 test checklist

## 1. Upgrade from dev3.32
1. Load a dev3.32 world containing at least one Player NPC.
2. Confirm the NPC reappears at the same placement and retains name, role/faction, Wide/Slim choice and skin.
3. Confirm schedules, patrol points, spawn profiles, dialogue/shop/quest links, loadout, reactions, abilities and boss settings were not reset.
4. Save/reload the world a second time; there must not be duplicate mannequin/native runtime entities.

## 2. Player appearance
1. Create one **Wide / Steve** Player NPC with a local 64x64 skin.
2. Create one **Slim / Alex** Player NPC with a different local 64x64 skin.
3. Verify head/hat, body/jacket, sleeves and pants use the expected skin UVs and Slim arms are visibly 3 px wide.
4. Check looking, walking, crouching and a melee attack for sane model animation.
5. Set Texture source to Default and confirm the corresponding vanilla Steve/Alex fallback renders.
6. Re-test HTTPS skin loading and reconnect/resync.

## 3. Native movement / collision
1. Patrol over slabs/stairs and around solid obstacles.
2. Route through an open doorway and around a closed obstruction; the NPC must not phase through blocks.
3. Test Wander in an uneven area and verify stuck recovery chooses/recovers from unreachable destinations.
4. Test a schedule with at least two destinations on different elevations.
5. Push/collide with the Player NPC and compare its physical behavior with a normal Mob-backed Entity NPC.

## 4. Combat / reactions
1. Test Aggressive chase + melee against a player.
2. Test Fight back after the Player NPC is attacked.
3. Test Flee and hostile Avoid movement around obstacles.
4. Test Assist / call allies with two nearby NPCs.
5. Run Power Strike, Ranged Blast, Shockwave, Self Heal and Leap once where applicable.
6. Test bossbar, phase switching, leash/reset and post-reset navigation.

## 5. Loadout / interaction regression
1. Configure main-hand, off-hand and armor equipment and verify the underlying runtime still receives the exact configured stacks. Visually inspect all six slots; report any Player-renderer-only display regression separately from server loadout persistence.
2. Interact with dialogue, quest giver/turn-in, linked shop and function menus.
3. Kill a non-invulnerable Player NPC with configured SSU loot; visual equipment must still not become unintended death loot.
4. Test configured respawn and manual Respawn Now.

## 6. Dynamic spawning
1. Spawn a Player template through a Natural Spawn Profile.
2. Spawn it through a bound Spawner Profile.
3. Verify dynamic instances use the native `simpleserverutilities:player_npc` shell, inherit the correct skin/model and clean up normally at despawn distance.

## 7. Entity-mode regression
1. Re-test zombie, villager, skeleton and cow/pig Entity NPCs with and without custom texture overrides.
2. Two NPCs using the same vanilla entity type but different PNGs must keep separate textures.
3. Re-test the dev3.30.1 mob-without-ATTACK_DAMAGE case; no server tick crash.
