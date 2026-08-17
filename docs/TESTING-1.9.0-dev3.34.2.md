# SSU 1.9.0-dev3.34.2 — patrol continuation hotfix checklist

1. Create/use a Player visual NPC with Behavior = Patrol.
2. Configure at least three patrol points several blocks apart and set Patrol = Loop.
3. Set all point pauses to 0 and Route speed to 1.0. The NPC must continuously travel 1 -> 2 -> 3 -> 1 without parking at a node.
4. Repeat with Route speed 0.5. The same loop must continue at a slower speed.
5. Set one waypoint pause to 2 seconds. Only that point should intentionally stop for about two seconds before continuing.
6. Repeat the route with Ping-pong and confirm 1 -> 2 -> 3 -> 2 -> 1.
7. Let the NPC loop for several minutes and check for no stop-after-first-point regression and no renewed left/right steering jitter.
8. Confirm an existing route with stored nonzero pause values retains those values.
