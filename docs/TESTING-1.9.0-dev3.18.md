# SSU 1.9.0-dev3.18 runtime checklist

Use the same `1.9.0-dev3.18` build on client and dedicated server.

## 1. Entity Insight basic rendering

1. Join with `ssu.entity_insight.use` unset/default and open Settings > Combat.
2. Verify Entity Insight defaults to ON, Show health ON, range 16 blocks and max entities 20.
3. Stand near a sheep and verify a green overhead label similar to `Sheep  10/10 HP`.
4. Disable Show health and verify only the entity name remains.
5. Disable Entity Insight and verify SSU no longer forces these mob labels.
6. Re-enable it and verify the labels return.

## 2. Range and nearest-entity cap

1. Drag Insight range through 0, 1, 16 and 32; verify entities outside the selected range are not included.
2. At range 0, verify no Entity Insight labels render even while the personal enable toggle remains ON.
3. Put more than 10 living entities in range and set Max entities to 1; verify only the nearest eligible entity receives a label.
4. Test several exact values up to 50 and verify the server never sends/renders more than the configured nearest count.
5. Verify players, invisible entities and SSU-managed NPCs are excluded.

## 3. Friendly / neutral / hostile status

1. Verify passive animals such as sheep/cows show green.
2. Verify a neutral mob such as a wolf, bee, enderman, piglin, polar bear, spider or iron golem shows yellow while not targeting a player.
3. Cause that neutral mob to target a player and verify its label changes to red within the periodic sync interval.
4. End/reset its hostility and verify it returns to yellow.
5. Verify a normal hostile monster (for example zombie/skeleton) remains red.
6. Tame a wolf and verify it is green while friendly.

## 4. Health updates

1. Damage a labeled mob and verify current HP changes while max HP stays correct.
2. Heal/regenerate the mob and verify current HP rises.
3. Verify whole values show without `.0` and fractional health uses one decimal place.
4. Verify custom-named mobs show their custom name with the same health formatting.

## 5. Permission hard gate

1. Explicitly set `ssu.entity_insight.use = false` for a non-operator test player.
2. Verify Entity Insight renders nothing even if that player's saved preference is ON.
3. Attempt to enable it through Settings or `/ssu settings entity_insight enabled true`; verify the server rejects activation.
4. Restore/unset the permission and verify the default-granted feature works again.

## 6. Persistence and commands

1. Set a non-default combination (for example OFF health, range 23, max 37), relog and restart the server.
2. Verify all four personal settings persist.
3. Verify `/ssu settings entity_insight health <bool>`, `range <0..32>` and `max_entities <1..50>` apply and persist.

## 7. Regression

- Player overhead title/rank rendering remains unchanged.
- SSU NPC role/name/faction labels do not duplicate with Entity Insight.
- Damage indicators continue to work independently.
- Settings > Combat remains readable at common GUI scales.
- KOTH/World Edit/Block Party/Mines behavior from dev3.17 remains unchanged.
