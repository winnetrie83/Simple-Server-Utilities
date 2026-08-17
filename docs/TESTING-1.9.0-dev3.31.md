# SSU 1.9.0-dev3.31 test checklist

## Upgrade / migration

1. Back up the test world.
2. Start a world last saved with dev3.30.1.
3. Confirm existing vanilla-model NPCs still render and behave normally.
4. Confirm existing local/URL player-skin NPCs still use their mannequin skin.
5. Open and save both types in the NPC editor and restart the world.

## Appearance editor

1. Open an existing NPC and visit Appearance.
2. Cycle Visual through Entity -> Player skin -> Custom model -> Entity.
3. In Entity mode, select a vanilla/modded living entity with the model picker and save.
4. In Player skin mode, verify Local/URL/Vanilla source controls and Wide/Slim still work.
5. In Custom model mode, select a fallback living entity and enter:
   - model `simpleserverutilities:entity/test_npc`
   - texture `simpleserverutilities:entity/test_npc.png`
6. Invalid resource IDs should be rejected by Save rather than corrupting the definition.

## Animation page

1. Set Custom model mode.
2. Enter animation resource `simpleserverutilities:entity/test_npc`.
3. Edit all six animation names and save.
4. Reopen the editor and verify all values persisted.
5. Restart the world and verify persistence again.

## Runtime safety

1. Save a Custom model NPC without any optional renderer provider.
2. Confirm the NPC still spawns using its fallback entity shell.
3. Test patrol, schedule, collision/pathfinding and combat.
4. Test the dev3.30 boss/ability features on the same NPC.
5. Confirm melee combat with passive shells still uses the dev3.30.1 safe attack fallback and does not reproduce the `attack_damage` crash.

## Natural/spawner NPCs

1. Link a Custom model template to a dev3.29 natural Spawn Profile.
2. Confirm dynamic NPCs spawn and despawn normally using the safe fallback shell.
3. Repeat with a bound vanilla Spawner profile.

## Network

1. Test dedicated server + client both on dev3.31.
2. Confirm editor opening/saving works with protocol 112.
3. Confirm a protocol-111 client is rejected rather than decoding the expanded editor payload incorrectly.
