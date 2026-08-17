# Runtime test checklist — SSU 1.9.0-dev3.40.4

## 1. NPC Manager: exactly one widget set
1. Give yourself the SSU NPC Tool and stand in the world.
2. Set **SSU GUI Scale** to 60% and right-click empty air once.
3. Verify exactly one NPC Manager is visible. There must be no second set of tabs, Search field, row buttons or page arrows behind the dark panel.
4. Close/reopen the manager at least five times and verify no stale controls accumulate.
5. Repeat at 70%, 80%, 90% and 100% SSU GUI Scale.
6. On every scale, test Placements/Templates/Spawning, Search, paging and at least one row action. Input hitboxes must match the visible controls.
7. Trigger an action that refreshes the manager list and verify the existing screen updates in place without creating another widget set.
8. Right-click a block/entity with the NPC Tool and verify that interaction does not accidentally open a second manager.

## 2. NPC movement ownership / snap-back regression
Prepare several NPC placements with visible home positions so movement is easy to observe.

### Stationary / Look at players
1. Create a hostile or otherwise combat-capable STATIONARY NPC with Home Radius/Wander Radius = 0.
2. Pull it away from its placement during combat.
3. While combat is active, watch for at least one full reconcile interval (>2 seconds). It must not flash/teleport back to the placement.
4. End combat by killing/leaving the target.
5. Verify the NPC **walks/pathfinds** back to the placement. It must not snap back.
6. On arrival, verify stationary behavior is restored and the NPC remains at home.
7. Repeat for LOOK_AT_PLAYERS.

### Wander / Native
1. Test a WANDER NPC with a non-zero wander radius and a NATIVE NPC with a non-zero home radius.
2. Pull each outside its allowed home/leash range in combat.
3. Verify no periodic snap occurs while fighting.
4. End combat and verify each returns through pathfinding when outside its configured range.

### Patrol
1. Create a multi-point patrol and let the NPC start moving toward a later waypoint.
2. Interrupt the route with combat and lure the NPC away.
3. End combat.
4. Verify the NPC resumes the logical patrol route/waypoint through pathfinding rather than teleporting home or restarting at point 1.
5. Repeat for Loop, Ping-pong and Random patrol modes if time permits.

### Schedule
1. Create a schedule with at least one WALK entry and one TELEPORT entry.
2. Confirm an ordinary time-slot transition into a TELEPORT entry still performs the configured explicit teleport.
3. During combat, move the NPC away from the currently active schedule destination.
4. End combat while that same schedule slot is active.
5. Verify combat recovery **walks/pathfinds** to the active schedule destination, even when that entry's configured movement mode is TELEPORT.
6. Verify the NPC resumes the schedule activity on arrival.

### Refresh/edit ownership
1. Move an NPC away from its placement through legitimate combat/path movement.
2. While it is displaced, change template-only settings such as health, movement speed or equipment and save.
3. Verify the runtime updates without teleporting the NPC to the placement.
4. Trigger any admin refresh/sync path available in the UI and verify position is not reset.
5. Use **Bring** and verify it still performs an immediate explicit move to the administrator.
6. Edit the placement XYZ/yaw/pitch explicitly and verify that still performs an immediate move.
7. Use **Respawn** and verify the NPC is hard-positioned at its configured respawn anchor.

### Static gravity anchor
1. Place a no-AI, gravity-affected NPC slightly above valid ground and let it settle naturally at its home X/Z.
2. Verify only the saved Y may settle to the grounded position.
3. Knock/lure the NPC horizontally away through combat and verify its current X/Z is never written back as the new placement/home anchor.

## 3. Player NPC visual loadout
Use both a **Wide/Steve** and **Slim/Alex** player-model NPC.

1. Equip a helmet, chestplate, leggings and boots. Verify every armor slot is visible and follows the player model.
2. Equip a main-hand sword and an off-hand item. Verify both hands render the configured stacks.
3. Swap the main/off-hand assignments and verify physical left/right hand placement remains correct.
4. Equip enchanted armor and enchanted held items. Verify normal equipment/item glint remains visible.
5. Enter combat with a sword and verify normal held-item/attack swing presentation.
6. Enter combat with a bow and verify the bow-and-arrow arm pose.
7. Enter combat with a crossbow and verify the crossbow hold pose.
8. Enter combat with a shield and verify the blocking pose is visible while the NPC is in combat.
9. Repeat the held-item tests on Slim/Alex and check that items align with the 3-pixel arms rather than floating at Wide/Steve hand offsets.
10. Test both default skins and at least one custom 64x64 skin.
11. Change equipment live from the editor while the NPC is displaced. Verify the visuals update without teleporting the NPC home.

## 4. Regression checks
- NPC overhead role/name/faction/quest labels remain single-rendered.
- NPC combat damage/armor continues to come from the gameplay-active equipment already introduced before this hotfix.
- Dashboard remains the canonical four-column layout from dev3.40.2.
- Reduced SSU GUI scaling stays centered and input hitboxes match the rendered controls.
- Network protocol remains `118`.
- NPC definition schema remains `19`; NPC placement schema remains `4`; Ability Library schema remains `1`.
