# SSU 1.9.0-dev3.27 — NPC AI foundation test plan

## Build
1. Build with the normal Java 25 / Gradle setup.
2. Confirm network protocol is still 108 and no persistence migration is requested.

## Collision and navigation
1. Create a Mob-backed NPC (for example villager) with Patrol and route it around walls, doors, stairs and uneven terrain.
2. Confirm it uses normal Minecraft pathing and does not walk through solid blocks.
3. Create a custom player-skin/mannequin NPC with Patrol or Schedule.
4. Put a solid wall directly between it and the destination. Confirm it no longer phases through the wall via incremental teleporting.
5. Make one patrol waypoint unreachable. Confirm the NPC eventually abandons that waypoint and continues the route instead of pushing forever.
6. Make a Wander target temporarily unreachable and confirm the NPC chooses another destination after bounded recovery.

## Combat versus movement
1. Give a Mob-backed scheduled NPC a hostile player/faction relation.
2. While it is walking to a schedule point, enter its hostile range.
3. Confirm combat navigation takes priority and schedule navigation does not fight the chase.
4. Leave/resolve combat and confirm the NPC resumes the current schedule destination.
5. Confirm an NPC with no hostile target keeps following patrol/schedule normally; the relation tick must no longer stop its path every 10 ticks.

## Patrol world editor
1. Open Movement > Edit route in world.
2. RMB several blocks and confirm waypoint particles plus connecting route particles appear.
3. Sneak+RMB near a waypoint to remove it.
4. Sneak+RMB air and confirm the last edit is restored.
5. RMB air and confirm the NPC editor reopens with the saved route.

## Schedule world editor
1. Open Schedule > Edit destinations in world.
2. RMB several blocks to add destinations and confirm they are visualized in chronological order.
3. Add two points without changing world time and confirm the second receives another free time rather than the exact same minute.
4. Test remove and undo with the same controls as patrol editing.
5. RMB air to finish and confirm the Schedule page reopens with the persisted destinations.
6. Fine-tune times and cycle On arrival through Idle, Look around, Work / use main hand, Guard area and legacy Chop tree.
7. Confirm Work/Chop tree swings the main hand and Look around/Guard visibly changes facing after arrival.

## Regression
1. Existing schema-4 placements with old `idle`, `look_around` and `chop_tree` schedule activities must load unchanged.
2. Teleport schedule movement must still intentionally use direct teleporting.
3. Linked NPC copies must retain independent patrol/schedule world coordinates.
4. NPC Quest/dialogue/shop flows from dev3.26.2.1 must remain functional.
