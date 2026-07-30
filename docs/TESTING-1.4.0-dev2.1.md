# SSU 1.4.0-dev2.1 Minecraft Test Plan

Use a copy of a dev1.4.0-dev2 or older test world. Client and server must both use protocol 17.

## 1. Build and startup

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm `BUILD SUCCESSFUL` and use the JAR from `build/libs` on both sides.
3. Open the copied world and check `latest.log` for module, economy, region or payload errors.
4. Restart once and verify claims, regions, permissions, homes, warps and maps still load.

## 2. Treasury retirement

For a world that has already run experimental dev2:

1. Record that a `Server Treasury` account existed before migration, if applicable.
2. Start dev2.1.
3. Confirm there is no Treasury tile, treasury permission or treasury account in Player Info/Economy Accounts.
4. Confirm the log reports retirement of the experimental account once.
5. Restart and verify it is not recreated.

## 3. Region managers migration

1. Use a region that previously had an owner assigned.
2. Start dev2.1 and open region details/settings.
3. Confirm the old owner appears as a manager and still has ordinary region-management access.
4. Save/restart and inspect the region JSON: new output should use `managers`, not `owners`.
5. Test `/regions addmanager` and `/regions removemanager`.

## 4. Rent as a money sink

1. Create a temporary rentable region priced at an easy amount such as `€10,00`.
2. Record the renter balance and total economy supply.
3. Rent the region.
4. Confirm the renter loses exactly `€10,00` and no manager/admin account receives money.
5. Confirm total supply decreases by exactly `€10,00`.
6. Renew once and verify the same behaviour.
7. Restart and verify the rental remains active and is not charged again.

## 5. Refunds without treasury

1. Set a non-zero player cancellation refund.
2. Rent a timed region, wait briefly and record the previewed refund.
3. Cancel the rental.
4. Confirm the frozen pro-rata amount is credited to the former renter without requiring any server balance.
5. Confirm the refund transaction appears exactly once after a restart.
6. Repeat with reset-on-cancellation enabled and verify the amount is frozen before the snapshot reset begins.

## 6. Claim deletion from the claim map

1. Open the claim map and select a disposable claim.
2. Click `Delete claim` once; verify the button changes to `Confirm delete` and nothing is deleted yet.
3. Click again and verify the complete claim group disappears, its border hides and the map returns to creation mode.
4. Test with a player lacking `ssu.claims.delete`; deletion must be rejected server-side.
5. Confirm unrelated claims remain unchanged.

## 7. Claim settings

Open Settings from both the claim dashboard row and claim map.

Check every page and tooltip, then test:

- toggle each protection flag;
- set and clear the welcome message;
- trust an online player;
- trust a previously known offline player;
- remove a trusted player;
- set spawn while standing inside the claim;
- attempt to set spawn outside the claim;
- clear spawn;
- restart and verify persistence.

Use a second client to confirm changed protection behaviour actually applies in-world.

## 8. Region settings

Open a test region's Settings screen and test:

- all protection toggles;
- priority;
- welcome and leave messages;
- add/remove manager;
- add/remove member;
- rentable state, price and period;
- reset-on-expiry and reset-on-cancellation;
- set and clear spawn;
- multiple pages and Back/Refresh behaviour;
- restart persistence.

Verify a manager can change ordinary settings but cannot grant managers/members or change rental administration unless their SSU permissions allow it.

## 9. Tooltips and GUI scales

1. Hover every input type: boolean, integer, text, readonly and action.
2. Confirm explanations, defaults/current values and integer ranges are legible.
3. Test at your normal GUI scale and at least one smaller/larger scale.
4. Return from claim settings to the claim map and verify terrain rendering resumes correctly rather than showing a closed/blank texture.

## 10. Regression checks

- dashboard Player Info and permission editor;
- region save/reset including containers and item frames;
- minimap, claim map and world map;
- right-click dragging;
- homes and warps;
- two-client LAN connection;
- normal server shutdown and restart.
