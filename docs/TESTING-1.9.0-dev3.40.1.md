# Runtime test checklist — SSU 1.9.0-dev3.40.1

1. Load the dev3.40 world/NPC that previously failed with `charge_1 references missing boss phase`.
2. Open NPC Editor -> Abilities and confirm Charge shows `Phase: All phases` when its old phase no longer exists.
3. Save the NPC; the save must succeed without a missing-boss-phase validation error.
4. Assign Charge to a normal non-boss NPC and save/reopen. It must remain assigned and usable.
5. Enable boss mode, restrict Charge to a real phase, save/reopen, and confirm the valid phase restriction is preserved.
6. Delete that phase (where allowed) or rename it and verify the assignment is updated/cleared safely rather than making the NPC unsaveable.
7. Repeat with an Attack Pattern step carrying a stale phase restriction.
8. Confirm shared Ability Library definitions themselves remain phase-agnostic; only NPC assignments carry the restriction.

Protocol remains 118; NPC schema remains 19; Ability Library schema remains 1.
