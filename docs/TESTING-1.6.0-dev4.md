# SSU 1.6.0-dev4 smoke-test checklist

## Build and connection

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the same dev4 JAR on client and server.
3. Confirm protocol 26 accepts dev4/dev4 and rejects mixed dev3/dev4 clients.
4. Back up the test world before first startup so the hologram schema migration can be compared safely.

## Floating-text scale migration and rendering

1. In dev3, create one text hologram at scale 8 and optionally several at lower scales, then stop the server cleanly.
2. Start the same world with dev4. Confirm the former scale-8 hologram now loads as scale 1 and keeps approximately the same useful visual size.
3. Restart once more and confirm the migrated value is not divided a second time.
4. Create new scale 1, 2, 4 and 8 holograms at the same distance. Confirm scale 1 is the new readable baseline and each higher value grows clearly.
5. Enter `NaN`, positive infinity or values outside 1–8 in the editor and confirm they are rejected safely.
6. Confirm multiline spacing remains readable at several scales.

## Precise hologram selection

1. Place two short holograms two or three blocks apart and another pair at different depths along a similar view direction.
2. Aim directly at each visible text line and right-click with the Hologram Tool. Confirm only the line under the crosshair opens.
3. Aim between the holograms and confirm neither is selected; normal create behaviour should open instead.
4. Test the visible left/right edge of a long line and each line of multiline text.
5. Confirm the nearest intersected hologram wins when two line planes overlap from the current camera angle.
6. Recheck local edit/delete plus remote Admin Center Edit, Teleport and Delete.

## Treecapitator target boundary and species

1. Activate Treecapitator and look at the middle log of a natural tree. Confirm the outline starts at that exact block and contains only connected logs at the same height or higher.
2. Break that middle log. Confirm logs below it remain and only the outlined upward section is mined.
3. Repeat from the bottom log and confirm the complete eligible trunk is selected; owned natural leaves may then be cleaned up.
4. Place or grow an oak and birch tree with touching trunks/canopies. Target oak and confirm no birch log is outlined or mined; repeat in reverse.
5. Confirm a touching different-species tree does not prevent the selected valid tree from activating. Shared leaves close to the neighbouring trunk may deliberately remain.
6. Test horizontal/branched trees and a permission block limit; confirm the limited selection remains connected outward from the targeted log.
7. Recheck player-placed log tracking, natural-tree validation, claims/regions and custom/disabled log lists.

## Treecapitator and Veinminer durability

1. Record the tool durability, then mine a known number of automatic logs/ores without Unbreaking. Count the manually broken origin plus every automatically broken non-leaf block and confirm one durability point is charged per block.
2. Repeat with Unbreaking I–III over enough blocks to observe that some normal damage attempts are prevented by the enchantment.
3. Confirm automatically removed leaves do not consume durability.
4. Use a nearly broken axe/pickaxe and confirm the normal break event occurs once and the chain stops when the required tool is gone.
5. Confirm creative-mode tools are not damaged.
6. Test Fortune/Silk Touch and a correctly tagged modded tool; verify ordinary drops and relevant block-break hooks still occur.

## Crops Harvesting

1. Open Admin Center → Admin Tools and confirm the **Crop harvesting: ON/OFF** button reflects and changes the global state.
2. With the feature enabled and `ssu.crops_harvesting.use=true`, right-click mature wheat, carrots, potatoes, beetroot, nether wart and cocoa. Confirm normal mature drops appear and the same crop returns to its first growth stage.
3. Right-click each crop before maturity and confirm nothing is harvested or reset.
4. Test torchflower/pitcher-style age-based crops, including clicking the upper half of a mature double-height crop. Confirm one loot roll and a clean planted-stage reset without an orphaned upper half.
5. Confirm sweet berry bushes retain their native harvest interaction and melon/pumpkin stems are not reset or harvested.
6. Test a modded `CropBlock`, a block tagged `c:crops`, and an age-based block added through `cropsHarvestingCustomBlocks`. Confirm mature-only harvest and reset.
7. Add a crop to `cropsHarvestingDisabledBlocks` and confirm SSU leaves it untouched.
8. Set `ssu.crops_harvesting.use=false` on the player/base rank and confirm right-click harvesting is unavailable; grant it through a later rank or personal override and confirm it becomes available.
9. Disable the global Admin Center toggle and confirm nobody can use the feature regardless of permission.
10. Test inside owned, trusted, denied and administrative regions/claims; both break and place protection must permit the harvest/reset operation.

## Regression

- Hologram creation, persistence, links, scoreboard placeholders and image placeholders still work.
- Hologram Admin Center state remains synchronized after the new crop-toggle field was added.
- Treecapitator/Veinminer activation modes, outlines, block limits and tool-tag requirements still work.
- Mail, claims, regions, maps, homes, warps, spawn, economy and permission editors still load normally.
