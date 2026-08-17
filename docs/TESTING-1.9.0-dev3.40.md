# Runtime test checklist — SSU 1.9.0-dev3.40

## Shared Ability Library
- Open Dashboard -> Admin Tools -> Ability Library without holding/using the NPC Tool.
- Create `test_charge`, apply Charge preset, save and return to the library.
- Create `test_arcane`, apply Arcane Missiles preset, save.
- Create `test_clap`, apply Thunderclap preset, verify Requires stationary=ON and Min targets=1.
- Open two different NPC templates and assign the same `test_charge` from NPC -> Abilities -> Open Ability Library -> Assign.
- Edit `test_charge` once in the standalone library and verify both NPCs use the changed settings.
- Unassign from one NPC and verify the shared library entry remains and the other NPC keeps it.
- Verify deleting a still-assigned shared ability is blocked.

## Smart casting
- Arcane Missiles: engage from running chase range. NPC should stop its existing path, face the target and channel instead of cancelling itself because of its own old path.
- During Arcane Missiles, hit/knock the caster: Interrupt on damage/move should still cancel according to the configured toggles.
- Thunderclap: place target outside AoE but inside sight/follow range. NPC must NOT cast Thunderclap.
- Move one hostile inside Thunderclap radius. It should now become eligible.
- Set Min targets=2 and verify one nearby hostile is insufficient while two are sufficient.
- Cone ability: verify eligibility follows the current combat target direction rather than stale patrol yaw.

## Migration
- Start from a dev3.39 world with two NPCs that each own locally edited abilities of the same local ID. Verify both migrate and remain independently editable unless explicitly reassigned to one shared library definition.
- Verify migrated attack-pattern steps and boss Trigger Ability actions still resolve.

## Regression
- Ordinary equipment-driven melee/ranged combat remains functional.
- Patrol uses Walking speed; chase uses Running speed and resumes patrol/schedule after combat.
- Boss phases, threat, fixate and phase actions still function.
- NPC Editor save/reopen preserves assigned ability IDs and phase restrictions.
