# SSU 1.9.0-dev3.39 test checklist

## Build / migration

1. Build with the project's required Java 25 / NeoForge 26.2 toolchain.
2. Load an existing dev3.38.3 world and confirm NPC schema migrates to 18 without deleting placements, patrols, schedules, abilities, attack patterns or boss phases.
3. Open Stats and confirm the old manual Attack Damage / Armor / Armor Toughness fields are gone.

## Equipment-driven stats

1. Create the same NPC with no armor, iron armor and diamond/netherite armor; incoming physical damage should differ according to equipment.
2. Repeat with Protection-enchanted armor and confirm the enchantment remains present and affects normal gameplay damage.
3. Equip different melee weapons and compare ordinary melee damage.
4. Test Sharpness/other applicable melee enchantments and confirm the equipped enchanted stack remains intact.
5. Test a bow with Power, Flame and Punch using the Ranged channel.
6. Fight an NPC for several minutes and inspect every equipment slot: durability must remain full and no configured item may break/disappear.
7. Kill the NPC and confirm configured equipment itself does not drop unless independently represented in the SSU 9-slot loot table.
8. Change Armor multiplier and Melee/Ranged/Magic damage multipliers; verify they scale the expected channel without changing the stored equipment.

## Walking / running

1. Patrol with Walking speed 0.5, 1.0 and 1.5 and confirm ambient routes change speed smoothly.
2. Set Running speed above Walking speed, trigger combat and confirm the NPC accelerates during chase and returns to walking speed after combat.
3. Verify the editor rejects Running speed lower than Walking speed.
4. Repeat on Player/Villager, Slime and Vex/flying shells to make sure species locomotion remains intact.

## Attack-channel combinations

Test all useful combinations: Melee only, Ranged only, Magic only, Melee+Ranged, Melee+Magic, Ranged+Magic and all three.

For sword-mainhand + bow-offhand with Melee+Ranged enabled, verify melee is used close up and the offhand ranged weapon can be used at distance. A ranged mainhand should prefer ranged behavior.

## Ability presets

- **Charge**: target is stunned during the charge; NPC paths/collides rather than teleporting through walls; timeout exits cleanly if no path/contact is possible.
- **Thunderclap**: thunder sound, AoE target filtering, knockback and slow.
- **Slash**: three rapid equipment-backed hits; default 0.5 damage multiplier per hit; weapon enchantments/equipment durability remain correct.
- **Arcane Missiles**: three Arcane pulses; channel ends normally; taking damage interrupts when enabled; forced movement/knockback interrupts when enabled.
- **Arrow Volley / Fireball / Ice Ball / Leap / Mortal Strike / Bladestorm / Self Heal**: verify target shape, visuals/effects and cooldowns.

## Ability Workshop

1. Add each preset and edit it; presets must remain ordinary editable abilities.
2. Create a Custom ability and test Single, Around self, Around target and Cone shapes.
3. Test Physical, Fire, Arcane and Ice damage schools and Magic Resistance.
4. Test direct damage, equipment-damage multiplier, healing, knockback, stun, slow, bleed, DoT and HoT independently and in combinations.
5. Enter a valid effect ID such as `minecraft:weakness`, set duration/amplifier and confirm it applies. Invalid/missing effect IDs must be rejected on save.
6. Change hit count and pulse interval and verify multi-hit/channel timing.
7. Apply a preset over an ability already referenced by an Attack Pattern: the ability ID/reference must remain stable.
8. Use abilities from a Boss Phase action and from the ordered Attack Pattern system.

## Relations / safety

1. AoE and cone attacks must respect SSU hostile-target filtering and not hit protected/friendly entities unintentionally.
2. Threat, Fixate and Taunt behavior from dev3.34-dev3.36 must still work.
3. After combat, patrol/schedule state must resume normally.

## Regression

Re-test NPC labels, interaction/dialogue/shop/quest behavior, natural/spawner NPCs, Player skin rendering and GUI scale 60-100%.
