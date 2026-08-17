# SSU 1.9.0-dev3.26 — NPC Dialogue / Quest Integration Testing

Use a client and server running the exact same dev3.26 build (network protocol 107).

## 1. Dialogue migration
1. Load a world containing a dev3.25/schema-1 NPC dialogue.
2. Open and use the dialogue without editing it.
3. Open the dialogue editor and save it.
4. Restart and verify the dialogue still works.
5. Confirm its stored dialogue schema migrated to 2.

## 2. Node conditions and fallback routing
Create four nodes for one quest, for example:
- `ready`: condition `quest_ready`, fallback `active`
- `active`: condition `quest_active`, fallback `offer`
- `offer`: condition `quest_available`, fallback `done`
- `done`: condition `always`

Set the dialogue start node to `ready`.

Verify:
- player with a ready quest enters `ready`;
- player with an active but incomplete quest reaches `active`;
- eligible player without the quest reaches `offer`;
- unavailable/completed player reaches `done`;
- blocked-node entry actions never run while SSU is following a fallback.

Also verify the editor rejects:
- a missing fallback target;
- a node falling back to itself;
- A → B → A fallback cycles.

## 3. Quest offer / turn-in
1. On an offer choice select service `quest_offer` and choose a quest from the server target list.
2. Interact and start the quest.
3. Verify the dialogue can immediately route to its active branch.
4. Complete the objectives.
5. On a turn-in choice select `quest_turn_in` and the same quest.
6. Turn it in and verify rewards are delivered once and the dialogue routes to the expected post-completion branch.

Repeat with:
- prerequisite-locked quest;
- repeatable quest;
- repeatable quest on cooldown;
- player denied NPC quest access.

## 4. Quest markers
For NPCs linked to quest services/dialogue:
- `!` should show only when a linked offered quest can currently start;
- `?` should override `!` when a linked turn-in quest is ready;
- `•` should show for a linked active quest when neither higher-priority state applies;
- marker should disappear after there is no relevant linked quest state.

Verify markers:
- differ correctly for two players with different quest progress;
- update within the normal bounded NPC label-sync interval;
- scale together with NPC SCALE;
- still render when the normal NPC identity label is disabled;
- do not appear for players denied NPC quest access.

## 5. Rich dialogue text
1. Use `Edit rich text` on a dialogue node.
2. Apply multiple colours plus B/I/U/S.
3. Save and reopen the editor.
4. Preview the dialogue.
5. Use it in-world.
6. Restart server/client and verify formatting persists.

## 6. Regression
Retest:
- normal unconditional dialogues;
- choice-level conditions;
- entry actions;
- choice actions;
- service menu/direct-service NPCs;
- NPC local and HTTPS skins;
- look-at/wander/patrol behaviour;
- patrol in-world editor;
- schedules overriding normal behaviour.

## Expected compatibility
- Mod version: 1.9.0-dev3.26
- Network protocol: 107
- NPC definition schema: 10
- NPC placement schema: 4
- NPC dialogue schema: 2
- NPC Shop schema: 4
