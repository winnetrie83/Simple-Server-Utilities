# SSU 1.1.0-dev4 test plan

Use a copy of a world. Client and server must both run dev4 because the network protocol changed from 3 to 4.

## Build

1. Install/select Java 25.
2. Run `gradlew.bat clean build` on Windows or `./gradlew clean build` on Linux.
3. Confirm `build/libs/simpleserverutilities-1.1.0-dev4.jar` exists.
4. Use the same JAR on server and client.

## Dashboard

1. Join the world and press `U`.
2. Confirm the dashboard opens and the key can be changed in Controls.
3. Close it and open it with `/ssu menu`.
4. Verify non-admin players do not receive the Regions/Core administration pages.
5. Verify an operator/admin receives those pages.
6. Navigate between Home, Claims, Travel and Settings repeatedly.
7. Confirm the game does not pause and no disconnect occurs.

## Player actions

1. Confirm owned claims are listed.
2. Use **Show** on a claim, then hide the focused claim.
3. Confirm homes and permitted warps appear.
4. Teleport to one home and one warp from the dashboard.
5. Toggle claim borders and nearby region borders independently.
6. Relog and confirm the personal toggles persist.
7. Deny `ssu.borders.claims.view` or `ssu.borders.regions.view`; confirm the related dashboard toggle is disabled.

## Claim ribbon visualization

1. Enable claim borders near a single-chunk claim.
2. Verify a low translucent ribbon follows the exact four outer edges.
3. Walk uphill/downhill and confirm the ribbon follows camera height.
4. Confirm the ribbon is not a 128-block-high wall and does not dominate the landscape.
5. Create adjacent and L-shaped claim chunks; confirm shared internal edges are absent.
6. Stand behind terrain/buildings and confirm the ribbon is depth-tested rather than always drawn through everything.
7. Compare own green claims and another player's blue claims.

## Multiple region borders

1. Run `/regions show region_a` and `/regions show region_b`.
2. Confirm both exact purple region boxes remain visible.
3. Run `/regions hide region_a`; confirm only `region_a` disappears.
4. Run `/regions hide`; confirm all individually selected region boxes disappear.
5. Repeat the same operations from the dashboard Regions page.
6. Relog and confirm individually selected regions remain selected.
7. Delete a selected region and confirm it is cleaned from the saved selection on the next sync.
8. Toggle nearby region overview off; confirm individually selected region boxes remain visible.

## Regression checks

- Existing claim/region protection still works.
- Region selection remains yellow and exact.
- Claim and region color commands still update visible geometry.
- Homes, warps, permissions, storage batching and long-running region jobs still work.
- `/ssu core status` and job commands still work.

## Logs to report

When a test fails, include:

- `logs/latest.log`;
- exact command/action;
- client or dedicated/integrated server;
- screenshots of incorrect borders or dashboard layout;
- whether it reproduces after reconnecting.
