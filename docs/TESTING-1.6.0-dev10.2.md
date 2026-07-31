# SSU 1.6.0-dev10.2 test checklist

## Required setup

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev10.2 jar on client and server.
- Test with one administrator and, ideally, a separate normal player.

## Correct Regions-page semantics

1. Start with the normal player's personal **Region borders** setting ON.
2. Open **Regions & Rentals** as administrator.
3. Confirm existing regions initially show a **Show** button after migration from older records.
4. Without pressing Show, stand near a region and confirm its border is not visible to the normal player.
5. Press **Show** for one region. Confirm the row changes to **Disable** and the normal player sees it only when their personal Region borders switch is ON.
6. Turn the player's personal switch OFF and confirm the border disappears even though the server region remains enabled.
7. Turn the personal switch ON again and confirm it returns.
8. Press **Disable** on the administrator page and confirm the border disappears for every online player within the next synchronization cycle.
9. Confirm it does not reappear as an automatic overview border.
10. Enable two regions, press **Disable all**, and confirm both disappear and both rows return to Show.

## Permission and module gates

- With a region server-enabled and the player switch ON, set `ssu.borders.regions.view=false`; confirm the region disappears, including for an operator account.
- Re-enable the permission and confirm only server-enabled regions return.
- Disable the Server Regions module and confirm all region and selection visualization clears.

## Persistence and geometry changes

- Enable one region, restart the server and confirm its row still says Disable and eligible players still see it.
- Disable another region, restart and confirm it remains hidden.
- Redefine the bounds of an enabled region and confirm its server-visible state is preserved while the geometry updates.

## Duplicate-layer regression

- Enable a region server-side and enable the player's personal region overlay. Confirm each edge is rendered once.
- Toggle Show/Disable repeatedly and confirm no stale `REGION_FOCUS` copy remains.
