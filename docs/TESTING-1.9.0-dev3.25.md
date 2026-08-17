# SSU 1.9.0-dev3.25 — NPC Phase 1 Runtime Test Checklist

Baseline: `1.9.0-dev3.24.2`

This build combines the first two planned NPC work blocks: **Editor/Appearance** and **Behaviour/Patrols**.

## Compatibility

- Network protocol: `106`
- NPC definition schema: `10`
- NPC placement schema: `4`
- NPC dialogue schema: unchanged (`1`)
- NPC Shop schema: unchanged (`4`)

Client and server must use the exact same dev3.25 build.

## 1. Existing NPC migration

1. Start a world that already contains dev3.24.2 NPCs.
2. Verify every old NPC still exists, has the expected model/skin/equipment/labels and can still be edited.
3. Old NPCs with the former **No AI = ON** should open as **Stationary**.
4. Old NPCs with the former **No AI = OFF** should open as **Native AI**.
5. Restart the server/world and confirm the migrated behaviour persists.

## 2. NPC Editor layout

1. Open an existing NPC.
2. Verify the pages are available: Identity, Appearance, Interaction, Behavior, Movement, Relations, Stats, Loadout, Schedule, Respawn.
3. Switch repeatedly between pages, edit values, return to the previous page and verify unsaved in-screen values are preserved until Save.
4. Verify existing Loadout and Schedule workflows still open and save normally.

## 3. Local skin browser

1. Put several valid 64×64 PNG skins in:
   `<world>/simpleserverutilities/npcs/textures/`
2. Also test a subfolder, for example `guards/guard01.png`.
3. Reopen the NPC editor and choose **Texture source: Local**.
4. Click **Browse local…** and verify files are searchable/selectable.
5. Save a selected skin and verify it renders.
6. Manually enter a missing file and verify Save returns a clear local-skin error.
7. Try a wrong-size PNG and verify Save rejects it.
8. Recheck an HTTPS skin to ensure dev3.24.2 URL rendering still works.

## 4. Stationary

1. Set Behavior to **Stationary**.
2. Verify the NPC does not wander under native AI.
3. Verify gravity still behaves correctly when enabled.
4. Verify labels, custom skin and equipment still render.

## 5. Look at players

1. Set Behavior to **Look at players**.
2. Test a small and large look-at range.
3. Walk around the NPC and verify the head follows the nearest player in range.
4. Toggle **Rotate body** and verify body rotation follows the setting.
5. Leave the configured range and verify it stops tracking you.
6. Test with two players if possible and confirm the nearest eligible player is selected.

## 6. Wander

1. Set Behavior to **Wander**.
2. Test radius values such as 2, 6 and 16 blocks.
3. Test different retarget intervals and movement speeds.
4. Verify the NPC remains around its placement/home position.
5. Verify the NPC can recover after bumping into simple terrain/obstacles.
6. Test Can Swim and Can Fly separately where applicable.

## 7. Manual patrol list

1. Set Behavior to **Patrol**.
2. Open Movement.
3. Add several points with X/Y/Z/Yaw/Pause values.
4. Save and reopen the NPC.
5. Verify all route values persist.
6. Test **Loop**, **Ping-Pong** and **Random**.
7. Verify pause time and facing yaw are applied at reached points.
8. Test an empty patrol: the NPC should remain stopped without producing an error.

## 8. In-world patrol route editor

1. Open an existing NPC and go to Movement.
2. Click **Edit route in world**.
3. Verify the GUI closes only after the current NPC changes save successfully.
4. Right-click blocks to add waypoints.
5. Verify End Rod particles show the persisted waypoints while editing.
6. Sneak + right-click near a waypoint to remove it.
7. Verify a normal right-click on a block does not also finish the editor.
8. Right-click genuine air to finish.
9. Verify the NPC editor reopens and contains the edited route.
10. Restart and verify the route persists.

## 9. Schedule precedence

1. Give an NPC Wander or Patrol behavior.
2. Enable a non-empty Schedule.
3. Verify Schedule movement takes precedence over the normal behavior.
4. Disable/clear the Schedule and verify the configured behavior resumes.

## 10. Linked placement copy

1. Create/copy a linked NPC placement with a patrol route.
2. Paste it at another location.
3. Verify the copied patrol coordinates shift with the new placement.
4. Edit the copied placement's patrol and verify the original placement's route is unchanged.
5. Verify normal shared definition edits still affect linked placements as before.

## 11. Regression checks

- NPC custom Local skin still works.
- NPC HTTPS skin still works.
- No duplicate vanilla nameplate returns.
- Name/role/faction labels still scale with NPC Scale.
- Merchant/shop links still work.
- Dialogue interaction still works.
- Faction hostility still works for appropriate AI-enabled NPCs.
- Death/respawn still works.
- NPC Tool copy/paste still works when no patrol edit session is active.
- No duplicate runtime NPC entities after restart/chunk reload.

## Log information useful for a bug report

If something fails, include the relevant client/server log lines plus:

- Behavior mode
- Entity model (for example `minecraft:villager`)
- NPC scale
- Can Swim / Can Fly / Gravity state
- Schedule enabled/disabled
- Patrol mode and point count
- Whether it happened before or after a restart
