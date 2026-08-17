# SSU 1.9.0-dev3.26.2 — Runtime test plan

## 1. Quest Definition Editor size and guided controls

1. Open the admin Quest Definition Editor at the same GUI scale that caused dev3.26.1 to extend outside the screen.
2. Confirm the whole `550×344` editor, footer and Save button are visible.
3. Check all four tabs: **General / Objectives / Rewards / NPC Integration**.
4. Create a new quest and change its title. Confirm the Quest ID follows the title automatically until the ID is manually edited.
5. Confirm prerequisite, objective event and reward type open searchable selectors rather than requiring raw syntax/cycling through every option.
6. Confirm quest icon, item/block objective target and item reward use inventory-style registry pickers.
7. Add/delete/add objectives and confirm generated objective IDs remain unique.
8. Confirm Advanced metadata is hidden until explicitly opened.

## 2. Three-way quest access

1. Open SSU Settings and cycle Quest access through **Quest Menu → NPCs → Both → Quest Menu**.
2. In Quest Menu mode, link a quest to an NPC from either the Quest Editor or NPC `Manage quests…`.
3. Confirm SSU asks whether to enable **NPCs only** or **Both** before saving.
4. Choose Both and verify the Questbook menu and NPC quest route are both usable.
5. Choose NPCs only on a separate test and verify the normal Questbook route is denied while NPC quest interaction works.

## 3. Simple NPC quest workflow

1. Edit an existing NPC → Interaction → **Manage quests…**.
2. Search for a quest.
3. Test **Offer**, **Turn-in**, **Both**, and **Unlink**.
4. Confirm the row immediately reports the saved relationship after the server refresh.
5. Configure the six simple texts and marker switches, save, reopen, and confirm they persisted.
6. Confirm `Advanced dialogue` remains separately available and its existing custom graph is not overwritten by the simple quest workflow.

## 4. End-to-end quest states

Use a non-repeatable quest with turn-in required and one easy objective.

1. Before acceptance: linked giver shows `!`; interaction opens Available text and Accept button.
2. Accept: giver shows `•`; interaction opens In Progress text.
3. Finish objective: turn-in NPC shows `?`; interaction opens Ready text and Turn-in button.
4. Turn in: ready marker disappears; interaction opens Completed text.
5. Repeat the test with separate giver and turn-in NPC placements.
6. Repeat with two players at different quest states and confirm markers/dialogue are player-specific.

## 5. Multiple quests on one NPC

1. Link 2–5 quests to one NPC and verify the generated quest selector only exposes player-relevant choices.
2. Link a 6th quest and verify selector paging (`Next` / `Previous`).
3. Verify ready/available/active/completed states route to the correct generated per-quest dialogue.
4. Verify the 13th simple link is rejected with a clear message; use Advanced Dialogue for larger custom hubs.

## 6. Quest Editor NPC Integration

1. Open a quest → NPC Integration.
2. Pick a giver and a different turn-in NPC with the searchable placement pickers.
3. Toggle `!`, `•`, `?` marker visibility.
4. Edit the simple dialogue text and save the quest.
5. Reopen and confirm all values persist.
6. Confirm choosing a turn-in NPC forces turn-in-required semantics on the server.

## 7. Create quest from NPC

1. NPC → Manage quests… → `+ Create quest`.
2. Confirm the compact Quest Editor opens pre-linked to that NPC as both giver and turn-in.
3. Save the quest.
4. Confirm returning to the NPC workflow refreshes the list and shows the new quest as `Offer + turn-in`.

## 8. Cleanup and persistence

1. Delete a simple-linked quest. Confirm the NPC's generated managed dialogue is rebuilt and no stale quest button remains.
2. Delete an NPC placement through the editor/admin browser/command and confirm simple giver/turn-in links to that placement are cleared from affected quests.
3. Restart the server and confirm all links, dialogue text, marker switches and Quest access mode persist.
4. Load a pre-dev3.26.2/schema-1 quest and confirm it migrates to schema 2 with no NPC link and otherwise unchanged quest content.
