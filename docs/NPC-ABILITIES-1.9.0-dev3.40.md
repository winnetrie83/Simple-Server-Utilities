# SSU 1.9.0-dev3.40 — Shared NPC Ability Library

## Architecture

Abilities are server-wide reusable definitions stored below `simpleserverutilities/npcs/abilities/`. NPC definitions no longer persist private copies. Each NPC stores up to 24 `NpcAbilityAssignment` records containing a stable shared ability ID and an optional NPC-specific boss-phase ID.

The library supports up to 256 definitions. Editing a shared definition invalidates active ability/cooldown/pattern runtime state so assigned NPCs pick up the new definition without copying data back into every NPC template. Deletion is blocked while the ability has assignments.

## Admin workflow

1. Dashboard -> Admin Tools -> Ability Library.
2. Create a technical ID, open the Ability Workshop, apply a preset or edit the custom fields, then Save.
3. Open an NPC -> Abilities -> Open Ability Library.
4. Select the shared ability and click Assign.
5. Optionally cycle the NPC-specific Phase restriction.
6. Tactics / Boss phase actions can reference the assigned stable ability ID.

## AI requirements

`requiresStationary` gives the cast ownership of stationary movement: active navigation is stopped, MoveControl is put in WAIT, horizontal velocity is suppressed and the NPC faces its target. `interruptOnMove` then reacts to real displacement after cast start.

`minTargets` is evaluated against the configured target shape and hostile DamageFilter before the ability can be chosen. Around-self abilities therefore need actual enemies inside their radius. This is the key guard for Thunderclap/Bladestorm-style abilities.

Preset defaults are starting behavior and remain editable in the shared Ability Workshop.

## Migration

Schema <=18 embedded definitions are imported before NPC normalization. IDs are rewritten in attack patterns and boss Trigger Ability actions. Per-NPC copies are intentionally migrated to distinct shared records on collisions so old NPC-local customization cannot unexpectedly become shared.
