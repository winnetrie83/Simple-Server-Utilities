# NPC combat architecture — SSU 1.9.0-dev3.39

## 1. Equipment is the combat baseline

NPC equipment is no longer cosmetic-only and the Stats page no longer owns manual Armor, Armor Toughness or Attack Damage values.

- Main/offhand and armor slots retain their full encoded ItemStack data.
- Item attribute modifiers remain active.
- Gameplay enchantments remain on the equipped stacks.
- Managed equipment is never dropped as normal entity equipment.
- Durability is restored after attacks/damage and by a low-frequency safety pass. If a configured item would break during the vanilla damage sequence, SSU reapplies the authoritative configured stack.

`Armor multiplier` scales the armor and armor-toughness values contributed by equipment. `Melee damage x`, `Ranged damage x` and `Magic damage x` are channel multipliers applied after the equipment/ability baseline.

## 2. Movement stats

- **Walking speed x**: patrol, wander and schedule routes.
- **Running speed x**: combat chase/navigation.
- Running speed is constrained to be at least walking speed.
- Species-aware locomotion from dev3.35 remains responsible for how the physical entity moves (ground, hopping, flight, aquatic, etc.).

## 3. Attack channels

Each NPC can independently enable:

- **Melee** — ordinary equipped melee attacks and melee abilities.
- **Ranged** — ordinary equipped ranged attacks and ranged abilities.
- **Magic** — magic abilities.

Any one, two or all three can be enabled. A sword in main hand plus a bow in offhand can therefore use melee close up and ranged combat farther away when both channels are enabled.

## 4. Ability Workshop

The NPC Editor -> Abilities page opens a dedicated Ability Workshop. Up to 24 abilities remain stored on the NPC template and can still be selected by Attack Patterns and Boss Phase actions.

Every ability can configure:

- ID and display name
- executor/preset behavior
- Melee / Ranged / Magic channel
- Physical / Fire / Arcane / Ice / Nature / Shadow damage school
- Single target / Around self / Around target / Cone
- direct damage or equipment-damage multiplier
- self healing
- min/max range, chance and cooldown
- radius, cone angle and knockback
- stun and slow
- arbitrary effect/debuff registry ID, duration and amplifier
- bleed, DoT and HoT amount/duration/interval
- hit/projectile count and pulse interval
- wind-up and recovery
- channeling + interrupt on damage/movement
- charge speed

Presets are copied into ordinary editable ability data. Applying a preset keeps the existing ability ID so Attack Pattern references remain stable.

## 5. Important runtime semantics

- Physical equipment-backed melee uses the normal mob melee path where possible so equipped gameplay effects participate.
- The generic SSU ranged weapon executor derives base power from the equipped ranged weapon. Bow Power affects its hit damage; Flame/Punch are also represented. This is still an SSU ranged executor rather than a full vanilla draw/reload/projectile simulation and should receive runtime polish after testing.
- Magical damage schools respect the defender NPC's Magic Resistance. The attack channel multiplier is independent from the damage school.
- `Charge` refreshes target stun while charging and uses native/pathing-aware navigation rather than teleporting through blocks.
- `Thunderclap` uses an AoE around the caster with configurable knockback/slow and a lightning-thunder sound.
- `Slash` defaults to three 50%-equipment hits at short intervals.
- `Arcane Missiles` defaults to three Arcane pulses and is a movement/damage-interruptible channel.
- Periodic bleed/DoT/HoT effects tick independently from the slower target-selection cadence.

## 6. Migration

NPC definition schema is `18`.

Schema <=17 templates migrate automatically:

- old manual Movement Speed / Attack Damage / Armor / Armor Toughness are retired;
- old behavior speed becomes Walking speed;
- Running speed is initialized conservatively from the previous combat/profile chase behavior;
- Melee remains available for non-passive legacy combat profiles;
- Ranged ordinary weapon attacks remain opt-in;
- existing magic abilities remain enabled through the Magic channel;
- old abilities are mapped into the new attack-channel/shape model.
