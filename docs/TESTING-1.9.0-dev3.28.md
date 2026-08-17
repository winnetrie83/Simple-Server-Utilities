# SSU 1.9.0-dev3.28 — NPC Combat & Reactions test plan

## Build / migration
1. Build against Minecraft 26.2 / NeoForge 26.2.0.7-beta.
2. Start a world containing dev3.27 NPC definitions and verify they load as schema 11 without manual edits.
3. Open an existing NPC and confirm the Combat page defaults to Fight back / Assist / Attack / Melee, 16 assist, 12 flee, 20 ticks.

## Self-defense
- Ignore: attack the NPC; it must not acquire or flee from the attacker.
- Flee: attack the NPC; it must move away using collision-aware navigation and resume its prior schedule/patrol after the reaction expires.
- Fight back: a neutral player/NPC attacks first; the victim may retaliate even though the attacker was not preconfigured HOSTILE.
- Fight + call allies: same as Fight back, with friendly defenders at the wider rally distance joining when configured to assist.

## Friendly defense
1. Create two same-faction NPCs and verify the faction resolves FRIENDLY between them.
2. Set defender Friendly attacked = Assist.
3. Attack the other NPC with a normally neutral player/entity.
4. Defender should temporarily target the attacker, not the friendly victim.
5. Set Friendly attacked = Ignore and verify it no longer joins.

## Hostile sight
- HOSTILE + Ignore: NPC does nothing on sight.
- HOSTILE + Avoid: NPC moves away and should not attack.
- HOSTILE + Attack: Mob-backed NPC chases and melee attacks.

## Profiles
- Passive: never performs SSU attacks even if Attack/Fight is selected.
- Melee: baseline chase and 20-tick base cadence.
- Defender: slightly slower chase / more deliberate cadence.
- Aggressive: visibly faster chase / shorter cadence.

## Navigation interaction
- A scheduled/patrolling NPC enters combat: route must pause.
- End/remove the threat: route must resume naturally rather than resetting the placement home.
- Stationary Mob NPC fights/flees, then returns to No-AI stationary state.
- Custom-skin mannequin Flee/Avoid must not phase through blocks. It is not expected to perform Mob melee attacks yet.

## Regression
- NPC interaction/dialogue/shop/quest marker still works after combat.
- Invulnerable NPC can still react to an attempted hit while taking no damage.
- SSU NPC outgoing damage to friendly/neutral entities remains blocked unless that exact entity is an authorized retaliation/assist target.
- Native-AI NPC targets are not blindly cleared when SSU has no active combat target.
