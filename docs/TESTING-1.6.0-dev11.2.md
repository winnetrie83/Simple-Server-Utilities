# SSU 1.6.0-dev11.2 test checklist

## Required setup

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev11.2 jar on the dedicated server and every client.
- Confirm the network protocol is 34.
- Enable the Block Information module and the player's personal **Block information** setting.

## Permissions and default limit

1. Keep `ssu.block_information.inventory` unset or false and look at a filled chest:
   - the normal block name/tool HUD remains;
   - no content icons or `Empty` row appears.
2. Grant `ssu.block_information.inventory=true` without assigning a numeric limit:
   - exactly the first non-empty item stack is shown;
   - its normal stack count is rendered;
   - an ellipsis appears when additional non-empty stacks exist.
3. Set `ssu.block_information.inventory.max_items=3`:
   - at most three non-empty stacks are shown in slot order.
4. Set the numeric permission to `0`:
   - content preview is disabled while ordinary Block Information remains available.
5. Set it above `54` or below `0` through the permission editor:
   - the editor must reject/normalize the value to the documented 0-54 range.
6. Grant `ssu.block_information.inventory.full=true`:
   - the numeric limit is overridden up to the hard cap of 54 stacks.
7. Revoke the inventory permission while looking at a container:
   - the existing preview disappears within about one second without reconnecting.
8. Repeat with an operator account and no explicit inventory grant:
   - operator status alone must not bypass the strict content permission.

## Vanilla blocks and containers

1. Test an empty and filled single chest.
2. Test both halves of a filled double chest:
   - the same combined inventory preview should appear from either half.
3. Test a chest blocked by a solid block/cat:
   - no content preview should be exposed.
4. Test a locked container with and without the correct key:
   - contents appear only when `canOpen` succeeds.
5. Test barrel, furnace/blast furnace/smoker, hopper, dispenser, dropper, brewing stand, shulker box, crafter and chiseled bookshelf.
6. Test a flower pot:
   - a filled pot shows the planted item icon;
   - an empty pot shows `Empty`.
7. Test a lectern with and without a book.
8. Test a campfire with one or more cooking items.
9. Test a jukebox/decorated pot when their current Minecraft implementation exposes a normal container/capability.
10. Test an ender chest with two players:
    - each player sees only their own ender chest inventory.

## Entities and displays

1. Test an item frame with and without an item.
2. Test an armor stand with armor and hand items:
   - equipment is shown in head/chest/legs/feet/main-hand/off-hand order, bounded by the player's maximum.
3. Test a chest/hopper minecart or another `Container` entity.
4. Look at another player:
   - their player inventory must never be previewed.

## Protection and privacy

1. In a claim/region where the player may interact, confirm the preview appears.
2. Deny interaction with that same target:
   - the preview must disappear even though the block/entity name still renders locally.
3. Test both halves of a protected double chest and ensure access to both positions is required.
4. Test a newly generated loot-table chest before legitimate opening:
   - no preview appears;
   - looking at it must not generate or consume its loot table.
5. Place a shulker box, written book, filled map or another component-heavy item inside a chest:
   - only its item icon and outer stack count are shown;
   - nested contents, text, map data and other per-stack metadata are not transferred/displayed.

## Modded compatibility and performance

1. Test a modded block exposing NeoForge `Capabilities.Item.BLOCK`.
2. Test a modded inventory entity exposing `Capabilities.Item.ENTITY` or `ENTITY_AUTOMATION`.
3. Confirm unsupported machines still show their ordinary translated name without errors or a fake empty preview.
4. Move the crosshair rapidly between containers and ordinary blocks:
   - stale icons must not attach to the wrong target.
5. Change a viewed container's contents:
   - the preview should update within about 0.25 seconds;
   - unchanged targets should remain visually stable.
6. Test a very large modded handler:
   - scanning and preview remain bounded;
   - at most 54 stacks are sent/shown.

## Regression

- Confirm compact names, required-tool icons and the red/green held-tool indicator still work.
- Confirm `ssu.block_information.debug` and its personal toggle still work independently.
- Confirm claims, regions, mail, statistics, holograms, minimap and utility mining retain their data/settings.
