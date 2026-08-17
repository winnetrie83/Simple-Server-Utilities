# Runtime test checklist — SSU 1.9.0-dev3.40.4.1

## 1. Client startup recovery
1. Start the 26.2 NeoForge client with SSU dev3.40.4.1.
2. Verify the title screen renders normally; no black frame may remain while buttons are clickable.
3. Load a world and verify normal world rendering.

## 2. NPC Manager duplicate-render regression
1. Hold the NPC Tool and right-click empty air.
2. Verify exactly one NPC Manager widget set is visible.
3. Close/reopen several times and verify no stale controls accumulate.

## 3. NPC movement ownership
1. Pull a combat-capable NPC away from home during combat.
2. Verify periodic reconcile does not snap it back.
3. End combat and verify it returns through pathfinding/home or resumes patrol/schedule as configured.

## 4. Known temporary limitation
Player-model NPC armor and held-item visuals are intentionally rolled back in this rescue build. Do not use that as a pass/fail criterion for dev3.40.4.1.
