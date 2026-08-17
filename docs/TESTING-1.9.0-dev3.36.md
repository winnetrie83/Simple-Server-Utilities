# SSU 1.9.0-dev3.36 testing

## Upgrade
- Start from a world containing dev3.35 NPCs and confirm definitions migrate to NPC schema 17 without losing roles, AI-family settings, abilities, threat, attack patterns, schedules or patrols.
- Reopen and save an old boss with no phase actions; behavior should remain equivalent to dev3.35.

## Boss phase actions
1. Create a boss with at least two phases, e.g. 100% and 60%.
2. On the first phase add an Announce action and verify it fires only when combat starts, not while the boss is idle.
3. On the second phase add Heal %, Reset threat and an Announce action; cross the threshold and verify every action fires once.
4. Keep normal Threat/Aggro OFF, add Fixate random player for 5 seconds and verify the boss still temporarily forces one valid nearby player as target; scripted Fixate is an encounter mechanic and must not depend on Threat being enabled.
5. Enable Taunt immune for a phase and verify external SSU taunt hooks are rejected in that phase while scripted Fixate still works.
6. Create an ability and add Trigger ability to a phase. Verify the scripted cast starts at phase entry and normal combat continues afterwards.
7. Create a separate non-boss NPC template and configure Spawn adds with count 3 and radius 5. Verify three dynamic adds appear around the boss.
8. Reset/leash the boss and verify all encounter-spawned adds are removed.
9. Kill the boss and verify encounter adds are also removed.
10. Add Despawn adds to a later phase and verify previous adds are removed immediately.
11. Rename/delete an ability referenced by a phase action and verify the editor updates/clears the reference safely.

## Regression
- Retest Player/Villager ground pathing, Slime hopping, Vex flight and one aquatic NPC from dev3.35.
- Retest Patrol Loop and schedule return after combat.
- Verify SSU overhead role/name labels remain smooth and no native `Player NPC` nametag appears.

## Website API
- Keep `enableWebApi=false`: server must start without opening the HTTP listener.
- Set `enableWebApi=true` with a token shorter than 16 characters: SSU must refuse to start the API and log an error without crashing Minecraft.
- Use a 16+ character token and query `/api/v1/health`, `/status`, `/players`, `/capabilities` with the Bearer header.
- Verify a missing/wrong token returns 401.
- Verify POST returns 405; there is no remote command endpoint in this build.
- Verify player join/leave and dimension changes appear in the snapshot within roughly one second.
- When using CORS, set one exact `webApiAllowedOrigin` and verify other origins are not allowed.
