# SSU 1.9.0-dev2.7 test checklist

## Boost presentation

1. Start a CTF match with all four boost types enabled.
2. Confirm the floating items are Golden Boots, Golden Apple, Diamond Chestplate and Rabbit Foot.
3. Pick up each boost and confirm:
   - Speed plays only the firework launch/rocket sound, without an explosion sound.
   - Regeneration plays the beacon power-select sound.
   - Armor plays the diamond armor-equip sound.
   - Jump plays the wind-charge throw sound.
4. Confirm the sounds originate around the collecting player rather than playing globally at full volume.
5. Repeat in Domination.

## Spleef infinite projectile

1. Wait for the configured standard-projectile unlock.
2. Throw the named Snowball and confirm it travels in a straight line without falling.
3. Confirm one Snowball remains visibly present in the inventory after the throw.
4. Confirm the vanilla radial cooldown overlay appears on that Snowball.
5. Click repeatedly during cooldown and confirm the item neither vanishes nor becomes unusable after cooldown.
6. Confirm another valid throw works as soon as the overlay finishes.
7. Confirm only one configured floor block breaks on impact.

## Spleef power projectile

1. Wait for a Power Egg award.
2. Confirm the Egg travels in a straight line without falling.
3. Confirm it is consumed normally and the configured stack maximum is respected.
4. Confirm impact removes the target floor block plus its four horizontal neighbours.

## Manual boost setup markers

1. Select a CTF or Domination arena in the Minigame Setup Tool.
2. Register several manual boost spawn indices and confirm an upright End Rod appears at every position.
3. Replace an existing index and confirm the old End Rod is removed.
4. Save/recapture the arena snapshot and confirm all temporary End Rods are absent from the snapshot.
5. While still editing, confirm setup markers are restored after the snapshot completes.
6. Start a match and confirm no setup End Rod remains in the playable arena.
7. Confirm actual CTF flags and Domination node banners remain untouched.
