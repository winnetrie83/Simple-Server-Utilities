# Runtime test checklist — SSU 1.9.0-dev3.40.5

This build deliberately reintroduces Player-NPC equipment in small, testable stages after the dev3.40.4 startup regression. Stop at the first failure and keep the latest log.

## 1. Startup safety (critical)

1. Start the Minecraft 26.2 / NeoForge client with only dev3.40.5 replacing dev3.40.4.1.
2. Confirm the Mojang/loading screens and title screen render normally; there must be no black screen.
3. Open Options once and return to the title screen.
4. Load an existing test world.

**PASS:** normal title/world rendering.

## 2. Baseline Player NPC

1. Spawn/open a Player-model NPC with all six equipment slots empty.
2. Test Wide/Steve and Slim/Alex.
3. Walk around it, make it move and enter/leave combat.

**PASS:** skin/model/animations remain intact and there is no snap-back regression.

## 3. Held items

Test one change at a time:

1. Main hand only: sword or ordinary item.
2. Off hand only: shield or ordinary item.
3. Main + off hand together.
4. Bow in main hand; enter combat.
5. Crossbow in main hand; enter combat.
6. Sword/main + shield/off; enter combat.
7. Repeat at least main+off on Slim/Alex.

**PASS:** both hands render on the correct side and follow the arm/model without detached or duplicated items.

## 4. Armor

1. Helmet only.
2. Chestplate only.
3. Leggings only.
4. Boots only.
5. Full matching vanilla armor set.
6. Mixed armor materials.
7. At least one enchanted armor piece and one enchanted held item.
8. Repeat full armor on Wide/Steve and Slim/Alex.

**PASS:** correct body regions render, equipment follows animation, and normal enchanted glint/material appearance is retained.

## 5. Existing dev3.40.4.1 regressions

1. Force an NPC into combat away from home and let combat end. It must path back rather than teleport/snap.
2. Right-click air with the NPC Tool. NPC Manager must show one widget set only.
3. Search/page/close/reopen NPC Manager once to verify rebuilds remain single.

## If armor fails but the game still runs

Check `latest.log` for:

`SSU player-NPC armor renderer could not initialize; continuing without visual armor`

That message means the fail-soft guard worked: held-item rendering should remain testable even if the optional armor layer could not initialize.
