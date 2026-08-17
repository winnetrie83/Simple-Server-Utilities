# SSU 1.9.0-dev3.34.3 — patrol arrival / route-state checklist

## Primary regression
1. Create/use a native Player NPC.
2. Behavior = Patrol. Patrol mode = Loop.
3. Configure at least 3 clearly separated waypoints (5-10 blocks apart is ideal for the first test).
4. Set Pause = 0 on every point.
5. Set Route speed x = 1.0.
6. Let the NPC run for at least three complete loops.

Expected: it must continuously travel 1 -> 2 -> 3 -> 1 without parking permanently on point 1.

## Arrival tolerance
- Put one waypoint on slightly uneven ground or one block higher/lower than the previous route segment.
- Confirm the NPC advances when vanilla navigation reaches a valid nearby final node.
- It should not need to stand on one exact floating-point XYZ coordinate.

## Smooth movement
- Repeat with Route speed x = 0.5.
- Confirm movement remains slower and smooth.
- Waypoint transitions should not visibly teleport or zero momentum.

## Other modes
- Ping-pong: confirm 1 -> 2 -> 3 -> 2 -> 1 repeatedly.
- Random: confirm it keeps choosing different next points and does not freeze after its first arrival.

## Pause regression
- Set point 2 Pause = 2 seconds.
- Confirm it stops intentionally at point 2, waits about 2 seconds, then continues.

## Unreachable waypoint
- Add a deliberately unreachable point.
- Confirm stall recovery eventually skips/replaces the route segment rather than freezing the complete patrol forever.

Protocol: 114. NPC definition schema: 15. Placement schema: 4.
