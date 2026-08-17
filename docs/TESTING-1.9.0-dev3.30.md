# SSU 1.9.0-dev3.30 test checklist

## Build / migration
- Build the project from a clean checkout/source extraction.
- Start a world that already contains dev3.29.1 NPC definitions, placements and Spawn Profiles.
- Confirm old NPCs load with no abilities and Boss encounter disabled.
- Confirm existing natural/spawner NPC profiles still spawn their linked templates.

## Normal NPC abilities
1. Open an NPC template and go to **Abilities**.
2. Add a Power Strike with a short cooldown and confirm the NPC uses it only while it has a valid combat target in range.
3. Add Ranged Blast with a larger max range and verify its End Rod beam/telegraph and damage.
4. Add Shockwave and place both hostile and friendly/neutral entities nearby. Verify only authorized/hostile targets are damaged.
5. Add Self Heal, damage the NPC, enter combat and verify it can heal itself while damaged.
6. Add Leap and verify normal chase navigation yields while the leap owns movement.
7. Verify wind-up telegraph, recovery delay and cooldown prevent constant ability spam.
8. Edit/save an ability while the NPC is fighting; verify an old in-flight cast does not execute using stale settings.

## Boss encounter
1. Enable **Boss encounter** on an NPC and keep **Boss bar** enabled.
2. Approach within Boss bar range and verify one bossbar appears with the correct name and live health percentage.
3. Leave range and verify the bar disappears for that player.
4. Create phases at 100%, 70% and 30% health and assign visibly different movement/cooldown/damage multipliers.
5. Bind at least one ability to the 70% phase and another to the 30% phase; leave one ability on **All phases**.
6. Damage the boss through both thresholds and verify phase transitions occur in the correct order and nearby players receive the transition overlay.
7. Verify the All phases ability can be selected in all phases while phase-bound abilities only run in their phase.

## Leash / reset
1. Start combat and pull the boss beyond **Reset distance**.
2. Verify SSU drops combat, cancels active ability state and returns the boss toward its placement/spawn anchor instead of continuing the chase.
3. With **Heal on reset** enabled, verify it returns to full health at the anchor and starts from the correct opening phase again.
4. Repeat with Heal on reset disabled and verify health is preserved.
5. Stop attacking while inside the arena, wait **Reset after idle seconds**, and verify a damaged boss resets/returns.
6. Give a healthy boss a schedule or patrol and verify the boss is not pinned to its anchor while no reset is required.

## Dynamic bosses
1. Use a dev3.29 Natural Spawn Profile linked to a boss-enabled template and verify the spawned instance gets the same abilities/phases/bossbar.
2. Repeat with a vanilla-Spawner-backed profile.
3. Verify reset home for a dynamic boss is its dynamic spawn position.
4. Verify despawning/removing the dynamic instance also removes its bossbar.

## Multiplayer / cleanup
- With two players in different distances, verify bossbar visibility is per-player.
- Kill, delete, respawn and disable a boss placement; verify no orphan bossbar remains.
- Rename/edit an NPC template during runtime and verify boss/ability state refreshes cleanly.
- Restart the server/world and verify ability/boss configuration persists.
