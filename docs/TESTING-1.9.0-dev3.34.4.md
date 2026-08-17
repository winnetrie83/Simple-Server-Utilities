# SSU 1.9.0-dev3.34.4 test checklist

This build focuses on overhead-label rendering and model-aware NPC locomotion. Use a fresh copy of the test world if possible, but existing schema-15 NPCs require no migration.

## 1. Player NPC identity label

1. Create/use a Player visual NPC with a role such as `Citizen` and a name such as `Guard`.
2. Stand close and aim the crosshair directly at the NPC.
3. Verify that Minecraft does **not** add `Player NPC` (or another vanilla/type-name plate) through the SSU label.
4. Verify that only the configured SSU identity stack is present: quest marker if applicable, role, display name and faction if configured.

## 2. Smooth moving labels

1. Put a Player NPC on a 3+ waypoint Loop patrol with Pause 0.
2. Watch from the side while it walks at Route speed `1.0`.
3. Repeat at `0.5`.
4. Verify that role/name/faction/quest text remains visually attached to the head/model and does not hop one client tick behind it.

## 3. Player/ground patrol regression

- Player NPC: Loop 3+ points for several complete rounds.
- Villager (or another normal ground Mob shell): repeat the route.
- Confirm both use smooth continuous paths and still recover around ordinary terrain obstacles.

## 4. Hopping locomotion

1. Create Slime and Magma Cube visual NPCs in Patrol mode.
2. Route them over reasonable flat terrain.
3. Verify that they move by their native hopping style and do not glide/walk like a Player NPC.
4. Verify route arrival/advance still works.

## 5. Free-flight locomotion

1. Create a Vex NPC with patrol points at different X/Y/Z heights. Leave the generic `Can fly` override OFF first.
2. Verify that it remains airborne, can steer vertically and does not fall as a ground shell.
3. Repeat with a Ghast/Phantom/Bat-style shell where practical.
4. Let the NPC arrive/stop and verify it does not continue drifting toward an old wanted position.

## 6. Flying-path locomotion

- Test Allay/Bee/Parrot-style NPC shells on separated waypoints.
- Confirm their own flying navigation is used rather than the Player NPC movement implementation.

## 7. Water/amphibious locomotion

- Test a fish/Guardian-style NPC with waypoints in water.
- Test an amphibious shell (for example Axolotl/Frog/Turtle) in a sensible environment.
- Verify native water movement is retained and SSU does not force ground-like walking.

## 8. Explicit movement overrides

- Enable `Can fly` on an otherwise grounded shell and confirm SSU's manual flight override still works.
- Test `Can swim` on a compatible unusual shell if used by your server.

## 9. Combat/schedule regression

- For at least one ground and one flying NPC, confirm schedule travel, combat chase and flee movement still select and approach the intended destinations.
- Confirm boss/threat/attack-pattern data from dev3.34 is unchanged.

## 10. Reconnect/resync

- Disconnect/reconnect near moving NPCs.
- Confirm custom Player skin/model state, SSU labels and patrol movement all resume correctly.

## Report useful details

If a shell behaves incorrectly, report the selected entity/model type, Behavior mode, Route speed, Can fly/Can swim values, waypoint layout (including height difference) and whether it is moving on land, in water or in air.
