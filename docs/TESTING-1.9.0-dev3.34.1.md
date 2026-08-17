# SSU 1.9.0-dev3.34.1 — Player NPC movement hotfix checklist

## Primary patrol test

1. Create/edit a **Player** visual NPC and choose **Behavior -> Patrol**.
2. Add a route with at least 3 waypoints over normal terrain, including a turn and a one-block height change if possible.
3. Leave waypoint pause at `0` for the first run.
4. Set **Route speed ×** to `1.0`. The NPC should walk at a normal vanilla-like pace and should not zig-zag, flick, spin or repeatedly stop while travelling between unchanged waypoints.
5. Set Route speed to `0.5` and save. The NPC must still move, at visibly slower speed.
6. Try `0.25`, `0.75`, `1.25` and `1.5`; all should remain functional and scale movement sensibly.

## Path stability

- Watch a long straight A -> B path. The NPC should keep one stable route instead of visibly steering left/right every few ticks.
- Put a simple obstacle between two waypoints. Vanilla pathfinding should route around it without SSU repeatedly rebuilding the path.
- Temporarily block an existing route. Stall recovery may repath, but normal unobstructed movement must remain calm.

## Waypoint flow

- With pause `0`, the NPC should continue directly toward the next route point without a forced 4-tick stop.
- With a non-zero pause, it should stop deliberately, face the waypoint yaw, wait, then continue.

## Regression checks

- Stats for a Player visual NPC must no longer show a second Movement Speed field.
- Stats for Entity visual NPCs may still expose the advanced native base movement attribute.
- Wander, schedules and combat chase still move correctly.
- Stationary/Look-at NPC gravity remains correct.
- Player NPC custom Steve/Alex skins and SSU overhead labels remain unchanged.
- Threat/Aggro and Attack Patterns from dev3.34 still work.

## Compatibility

- Network protocol: `114` (unchanged).
- NPC definition schema: `15` (unchanged).
- Existing Player NPC definitions that stored the old movement attribute automatically ignore that legacy override and use the native `0.25` base plus the Behavior route-speed multiplier.
