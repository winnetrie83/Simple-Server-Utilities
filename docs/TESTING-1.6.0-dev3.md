# SSU 1.6.0-dev3 smoke-test checklist

## Build and connection

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the same dev3 JAR on client and server.
3. Confirm protocol 25 accepts dev3/dev3 and rejects a mixed dev2/dev3 connection.

## Direct floating-text creation

1. Obtain the Hologram Tool from Admin Center → Admin Tools.
2. Right-click air while holding it in the main hand. Confirm the hologram editor opens immediately without a preceding left-click.
3. Enter a unique ID and text, then select **Create hologram**.
4. Confirm the text appears approximately one block along the direction in which you were looking when the editor opened.
5. Repeat while looking at a block and confirm block interaction is suppressed and the editor still opens once.
6. Confirm left-clicking with the tool does not damage blocks and does not set a creation anchor.

## Local edit and delete

1. Aim at the centre and then the visible edge of a short and a long text hologram.
2. Right-click with the Hologram Tool and confirm the existing editor opens with all settings preloaded.
3. Change text, colour, scale, formatting, range and ID; save and verify the same hologram is updated rather than duplicated.
4. Test LINK, SCOREBOARD and IMAGE-placeholder definitions in the same way.
5. Click **Delete hologram**, verify the warning, then click **Confirm delete** and confirm the hologram disappears for connected players.
6. Right-click without the Hologram Tool on a LINK hologram and confirm the normal website-link action still works.
7. Verify a player without `ssu.holograms.admin` cannot open, update or delete a hologram by tool or crafted request.

## Remote Admin Center management

1. Open `/ssu menu` → Admin Center → Holograms.
2. Confirm all stored definitions appear in a searchable, paged list with type and location.
3. Use **Edit** while far away and in another dimension; change a value and confirm the list refreshes after saving.
4. Use **Teleport** and confirm the administrator arrives at a safe standing position near the hologram.
5. Test a hologram over an unsafe/unsupported location and confirm teleport either finds a nearby vertical position or fails safely.
6. Click **Delete**, confirm the button changes to **Confirm**, and verify only the second click deletes the definition.
7. Confirm Admin Center → Admin Tools → Manage holograms opens the same page.

## Treecapitator tool requirement

1. Activate Treecapitator and break a valid natural tree with an empty hand, sword, shovel and pickaxe. Confirm only the origin block behaves normally and no chain starts.
2. Repeat with vanilla wooden through netherite axes and confirm Treecapitator works.
3. Test a correctly axe-tagged modded tool and confirm it works.
4. Switch away from the axe immediately after the origin breaks and confirm the remaining chain stops.
5. Use an almost-broken axe and confirm no further blocks are processed after the required tool is no longer held.

## Veinminer tool requirement

1. Activate Veinminer and break an allowed ore with an empty hand, axe, shovel and sword. Confirm no vein chain starts.
2. Repeat with vanilla pickaxes and a correctly pickaxe-tagged modded tool; confirm Veinminer works.
3. Switch away from the pickaxe during the chain and confirm processing stops safely.

## Regression

- Existing hologram commands, persistence and current-dimension synchronization still work.
- Region Tool creation and Region Editor result messages still work.
- Natural-tree detection, placed-log tracking, protection checks and configured block limits remain intact.
- Mail, claims, regions, maps, homes, warps, spawn, economy and permissions still load normally.
