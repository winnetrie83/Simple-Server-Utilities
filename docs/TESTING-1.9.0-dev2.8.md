# SSU 1.9.0-dev2.8 role-system test checklist

## Regression first

1. Run one CTF and one Domination match with **Tactical roles disabled**.
2. Confirm the normal configured weapon still appears, team-colored leather is cosmetic, boosts/objectives still work and the original player state returns after leaving.

## Queue and composition

1. Enable roles and set distinct per-team minimums, for example DPS 1, Tank 1, Healer 0.
2. Queue players with different preferred roles.
3. Confirm the lobby states that the preference is not guaranteed.
4. At match start, confirm every team satisfies all minimums and maxima and each player sees the assigned role.
5. Repeat with more players preferring one role than its maximum.
6. Let a player leave during countdown and confirm remaining roles rebalance or the countdown safely cancels and requeues players with their preferences.

## Shared role presentation

- Confirm every active CTF/Domination player wears a full leather set matching the team RGB color.
- Confirm the leather items do not add armor by themselves and do not lose durability.
- Compare `/attribute` or the armor/health HUD against the configured role health, armor and toughness.

## DPS

- Confirm Diamond Sword, Bow and one named role arrow are supplied.
- Fire repeatedly and confirm exactly one arrow is replenished.
- Hit an enemy and confirm the configured effect, level and duration.
- Hit a teammate with friendly fire disabled and confirm no role effect is applied.

## Tank

- Confirm Stone Sword and the team-colored patterned Shield.
- Raise the Shield while looking into the air, at a block and at an entity; protected arena interactions must not occur.
- Use Defensive Field with enemies inside and outside two blocks.
- Confirm only active enemies inside the true radius receive Slowness I.
- Confirm the item cooldown overlay and configured duration/cooldown.

## Healer

- Aim the single-heal item directly at an injured teammate within eight blocks and confirm the beam and configured heal.
- Aim away, beyond eight blocks and through an obstruction; no heal or cooldown should be consumed.
- Use AOE heal around injured allies and confirm it also may heal the caster, remains weaker than single heal and ignores enemies.
- Use self-heal while injured and confirm an instant 25% maximum-health heal.
- Confirm independent cooldown overlays for all three ability items.

## Restore and recovery

- Leave normally after each role and confirm inventory, equipment, effects, gamemode, health and original base maximum-health/armor/toughness values return.
- Test logout during a match and server restart recovery before public release.
