# SSU 1.9.0-dev3.35 test checklist

This build closes the current general NPC AI-polish pass and changes the cosmetic Role field. Test on a copy of an existing schema-15 world as well as with newly created NPCs.

## 1. Role / occupation migration

1. Open an NPC created before dev3.35 that used a fixed role such as Guard or Merchant.
2. Confirm **Role / occupation** is now a normal text field and contains the human-readable old role (`Guard`, `Merchant`, etc.).
3. Save it without changing the text and reopen the editor; the value must persist exactly.
4. Enter a custom title such as `Gate Captain`, `Blacksmith`, `Quest Giver` or any other short text and verify it persists.
5. Clear the field completely and verify the overhead role line disappears without affecting the NPC name/faction/quest marker.

## 2. 16-color role palette

1. On Identity, verify there are 16 small swatches beside the role field in **2 rows of 8**.
2. Select several colors (including Black, Gray, Red, Yellow and White) and save/reopen after each selection.
3. Verify the overhead role/title uses the selected color.
4. If the NPC uses the generated service menu, verify its role/title uses the same selected color there.

## 3. Shop independence from role

- Give an NPC any custom role (or leave it blank) and confirm Create/Edit NPC shop, Shared shop and Unlink shop remain available.
- Verify changing the cosmetic role does not create/remove/alter the linked shop.

## 4. Humanoid/ground AI family

- Player NPC and Villager: create a 3+ waypoint Loop with Pause 0 and Route speed 1.0. Let each complete multiple loops.
- Repeat Player NPC at Route speed 0.5.
- Verify stable body direction, no frantic left/right path resets and no stopping permanently at the first node.
- Trigger combat, then remove/defeat the target. Verify the NPC returns to its existing patrol route instead of resetting its route definition.

## 5. Hopping family

- Slime and Magma Cube: patrol multiple nodes on sensible terrain.
- Verify native hop movement is retained and route arrival advances reliably even when the body does not land on the exact waypoint center.
- Trigger chase/combat where appropriate and confirm it does not move like a Villager/Player shell.

## 6. Flying family

- Vex: use patrol points at different X/Y/Z heights and leave generic Can fly OFF.
- Verify free 3-D movement, smooth retargeting and continued waypoint progression.
- Test Allay/Bee/Parrot as flying-path examples where practical.
- Trigger combat and verify the NPC aims toward the target body rather than repeatedly diving at the feet.

## 7. Aquatic/amphibious family

- Use a fish/Guardian-style NPC in water. Wander should pick usable 3-D water destinations and patrol/schedule nodes should advance reliably.
- Test an Axolotl/Frog/Turtle/Drowned in a suitable environment and verify its native land/water controller remains authoritative.

## 8. Schedule arrival + combat return

1. Give an NPC at least two schedule locations.
2. Verify it reaches a location and starts its configured activity even when vanilla pathfinding stops slightly beside the exact stored coordinate.
3. Enter combat while travelling or performing the activity.
4. End combat and verify it resumes the current schedule slot cleanly.

## 9. Behavior diagnostics

- Open an existing NPC's Behavior page and verify **AI family** describes the runtime family.
- Verify **Runtime** reports an appropriate state such as Patrol x/y, Schedule x/y, Combat, Returning from combat, Wander, Native AI or Stationary.
- The snapshot is captured when the editor opens; close/reopen to sample a later live state.

## 10. Label regression

- Aim directly at a native Player NPC while it moves.
- Verify there is no extra vanilla/type label such as `Player NPC` through the SSU text.
- Verify role/name/faction/quest labels remain smoothly attached to the moving model.

## 11. Combat/tactics regression

- Confirm Threat/Aggro and ordered Attack Patterns from dev3.34 still save and run.
- Check at least one ground and one flying combat NPC.
- Verify boss reset still clears encounter state and the ambient movement route can resume afterwards.

## Report useful details

For a movement issue, report the exact Entity model, AI family shown in Behavior, Runtime line, Behavior mode, route speed, Can fly/Can swim state, waypoint layout and whether the NPC is on land/in water/in air.
