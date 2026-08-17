# SSU 1.9.0-dev3.34 — Combat Patterns + Threat test checklist

Use a disposable test world/server first. dev3.34 changes the NPC editor network payload and NPC definition schema.

## 1. Regression: old combat remains old combat
1. Load an existing dev3.33 NPC with Threat targeting OFF and Attack pattern OFF.
2. Confirm retaliation/flee/assist/hostile-sight behaviour still matches dev3.33.
3. Confirm random eligible abilities still work when abilities are configured.
4. Test both an Entity NPC and a native Player NPC.

## 2. Damage threat
1. Create an Aggressive/Defender NPC and enable Threat targeting.
2. Use two Survival players and damage the NPC by different amounts.
3. Confirm the higher-threat player becomes the target.
4. Set Damage multiplier to 0 and verify damage no longer raises threat.

## 3. Switch hysteresis
1. Set Switch ratio to 1.25.
2. Give Player A established threat, then let Player B barely exceed A.
3. Confirm the NPC stays on A until B exceeds the configured ratio.

## 4. Decay, range and invalid targets
1. Set a visible decay rate and stop generating threat; confirm stale threat eventually expires.
2. Move a target beyond Threat range and confirm it is pruned.
3. Switch a target to Creative/Spectator and confirm it is not selected.

## 5. Healing threat
1. Put a threat-enabled NPC in combat with an entity that is healed by an SSU system with a known healer.
2. Confirm the healer gains `actual healing × Healing multiplier` threat.
3. Confirm the built-in NPC Self Heal ability runs normally and does not create friendly/self targeting.

## 6. Ordered attack pattern
1. Add abilities such as Power Strike, Shockwave and Leap.
2. Enable Attack pattern and build: `Melee -> Power Strike -> Melee -> Shockwave`.
3. Confirm successful melee advances to the next step and configured abilities execute in order.
4. Confirm a chance-failed/unavailable ability does not permanently stall the sequence.

## 7. Conditions
1. Give one pattern step a close range and another a longer range.
2. Confirm only a currently matching step is selected.
3. Restrict a step to e.g. 0-50% own HP and confirm it is skipped above 50%.
4. Restrict a step to a boss phase and confirm it only executes in that phase.

## 8. Boss transitions/reset
1. Create a boss with at least two phases and a phase-aware attack pattern.
2. Cross a phase threshold and confirm the pattern starts from its reset cursor for the new phase.
3. Leave/reset the encounter and re-engage.
4. Confirm old threat, casts/cooldowns and pattern position do not carry over.

## 9. Editor reference safety
1. Rename an ability referenced by a pattern step; confirm the pattern follows the new ID.
2. Delete that ability; confirm the pattern step shows no selected ability and cannot save as an Ability step until a valid one is chosen.
3. Repeat rename/delete with a referenced boss phase.

## 10. dev3.33 Player NPC regression
- Wide/Steve and Slim/Alex skin rendering.
- Equipment/held items.
- Patrol/schedule/pathfinding.
- Chase/flee/melee.
- Boss bar/abilities.
- Reconnect and server restart.
