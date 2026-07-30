> **Historical dev2 document:** the permanent treasury and owner-payout design described below was retired by 1.4.0-dev2.1. It remains here only as development history.

# Minecraft test plan — SSU 1.4.0-dev2

Always use a copied world and the same dev2 JAR on every client and the server/LAN host.

## 1. Build and startup

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm `BUILD SUCCESSFUL` and a new JAR in `build\libs`.
3. Open a copy of the confirmed dev1.3.1 world.
4. Confirm claims, regions, rentals, balances, permissions, homes, warps, snapshots and map cache still load.
5. Stop and restart once. Check `latest.log` for module dependency, duplicate service or storage-shutdown errors.

## 2. Lifecycle persistence

1. Add or change one home, warp, personal permission and minimap preference.
2. Start a non-destructive bounded region job and stop the server while it is active.
3. Restart and confirm the job was cancelled cleanly, the four changed records persisted and storage reports no stuck writes.

## 3. Treasury access

1. Give an admin rank `ssu.economy.treasury.view = true` and `ssu.economy.treasury.admin = true`.
2. Confirm the Admin Center shows Treasury, its balance and history.
3. Remove only the admin key: history should remain visible, mutation controls should disappear/be denied.
4. Remove the view key: the category and page should no longer be accessible.
5. Confirm `Server Treasury` does not appear in Economy Accounts, Player Info or permission-player dropdowns.

## 4. Manual treasury mutations

1. Add a small amount such as `€10,00`; confirm balance and an incoming transaction.
2. Remove `€2,00`; confirm the balance decreases and an outgoing transaction appears.
3. Pay `€1,00` to an existing player; confirm treasury decreases and the player balance increases by exactly the same minor-unit amount.
4. Try an invalid amount, unknown player and payment above treasury balance. Every action must fail without changing either account.

## 5. Region income distribution

Use a rentable test region with a known price and a region owner other than the renter.

1. Set an owner share, for example 25%.
2. Record renter, owner and treasury balances.
3. Rent the region.
4. Confirm renter decreases by 100% of price, owner increases by 25%, treasury increases by 75%.
5. Extend once and verify the same split and separate journal records.
6. Restart and verify all balances remain unchanged and the rent journal has no pending operation.

## 6. Treasury-funded refund

1. Configure a cancellation refund and ensure the treasury has enough funds.
2. Cancel a rental and verify the renter refund equals the frozen pro-rata value while treasury decreases by exactly that amount.
3. Repeat with a treasury balance below the refund amount.
4. Confirm the region is cancelled, no money is created, and Rent Journal reports a pending refund.
5. Replenish the treasury, restart the world and confirm recovery pays the refund exactly once.

## 7. Multiplayer and dashboard

1. Connect a second client with the exact same Minecraft, NeoForge and SSU versions.
2. Verify the second player's treasury/admin visibility follows only their SSU permissions.
3. Keep the treasury page open while another admin performs a mutation; refresh and verify pagination/search/current page remain usable.
4. Confirm ordinary wallet payments, Player Info, permission editor, region rental and maps still work.
