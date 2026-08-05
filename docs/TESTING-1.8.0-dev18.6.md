# SSU 1.8.0-dev18.6 test checklist

Back up the test world before enabling Player Claim tax. Client and dedicated server must use the exact same dev18.6 build and network protocol 69.

## Build, migration and startup

1. Build with Java 25: `gradlew.bat clean build`.
2. Start a dedicated NeoForge 26.2 server and a separate client.
3. Confirm protocol 69 connects without a mismatch.
4. Confirm existing dev18.5 claims, Homes, warps and economy balances load unchanged.
5. Inspect a migrated claim JSON and verify schema 2 contains an initialized tax cycle, due time, rate/multiplier snapshots and a peak that is at least the live chunk count.
6. Confirm existing claims receive a new full cycle on first dev18.6 load rather than being charged immediately.
7. Confirm `player_claims/tax_settings.json` migrates to schema 2 and remains disabled unless it was deliberately enabled.
8. Confirm `player_claims/tax_settlements.json` is schema 2 and starts empty when there are no settlements.

## Peak invariant and per-claim cycles

1. Create a claim with 10 chunks and note its due time.
2. Expand it to 50 chunks; verify the taxable peak becomes 50.
3. Remove 10 chunks; verify current size becomes 40 while peak stays 50.
4. Expand to 70 chunks; verify both current size and peak become 70.
5. Remove chunks again; verify the peak remains 70 until the cycle is successfully paid or settled.
6. Restart after each step and verify the same peak survives.
7. Manually lower `taxPeakChunks` below the live chunk count in a disposable copied world, then start the server and verify it is repaired upward and persisted.
8. Create a second claim later and verify it has its own creation-based due time.
9. Successfully pay a cycle and verify its new peak resets to the claim's current size, not its previous historical maximum.

## Reminder is an estimate

1. Configure a short test interval and reminder lead under **Admin Center → Economics → Player Claim Tax**.
2. Verify the reminder identifies one claim, its current estimated amount, current cycle peak and scheduled time.
3. Verify the mail explicitly says the amount/penalty may rise when the claim expands and does not fall when chunks are removed.
4. Expand after the reminder and verify the final amount and peak increase without sending a misleading “fixed invoice” message.
5. Shrink after the reminder and verify the final amount does not decrease below the recorded peak.
6. Disable Mail before the reminder and verify destructive collection does not start. Re-enable Mail and verify a full warning window is provided before collection.
7. Stop the server through the reminder window and verify a missed reminder postpones the due time instead of causing immediate confiscation at startup.

## Successful automatic payment

1. Give a player sufficient balance and make two claims due at the same time.
2. Verify the due claims are grouped into one settlement and one Economy debit with transaction type `CLAIM_TAX`.
3. Verify each due claim starts a new cycle after payment.
4. Verify non-due claims keep their own existing cycle.
5. Reconnect/restart immediately after the debit and verify the idempotency key prevents a second charge.
6. Change the admin rate, interval and dimension multiplier during an active cycle; verify that active claim keeps its snapshots and the new values apply only to its next cycle.
7. Verify dimension multipliers affect the money amount but never the peak chunk count.

## Voluntary claim deletion

1. Attempt **Delete** on a taxable claim in the Claim Map.
2. Verify the choice screen shows current chunks, taxable peak, estimated full-cycle tax, due time and existing confiscation.
3. Click **Pay tax & delete** once and verify nothing happens except the second-confirmation state.
4. Click it again with sufficient balance; verify exactly one debit, claim deletion and linked-Home deletion.
5. Repeat with insufficient balance; verify the action is cancelled and neither claim nor capacity is lost.
6. Test **Forfeit capacity & delete** and verify the first click only arms confirmation.
7. Confirm it and verify no money is charged, the selected claim/Homes are deleted and exactly that claim's peak is permanently confiscated.
8. Attempt deletion through `/claims delete`, final-chunk unclaim and Claim Map batch removal; verify player routes cannot bypass the choice screen.
9. Verify an administrator can deliberately delete a claim without player tax settlement when no active settlement exists.
10. Verify even an administrator cannot mutate/delete the owner's claims while a journaled settlement is active.

## Confirmed insufficient funds

Use a disposable player and world backup.

1. Prepare one or more due claims and at least one additional current claim.
2. Record the summed unweighted peaks of the claims whose tax is due.
3. Lower the player's balance below the grouped amount.
4. At collection, verify all current claims owned by that player are unclaimed and all linked Homes are removed.
5. Verify world blocks, containers and terrain are not restored or reset.
6. Verify the player's currently used chunks become zero before effective capacity is evaluated.
7. Verify permanent confiscation equals exactly the sum of the due/taxed claim peaks; dimension multipliers do not change this count.
8. Example: permission limit 100, currently used 35, taxed peak 50 → after removal used 0, confiscated 50, effective/available 50.
9. Verify `tax peak < current chunks` can never occur; for current 70, the peak must be at least 70.
10. Verify the consequence mail reports the amount, all-claim/Home removal and exact confiscated chunks.
11. Verify other players' claims, Homes and capacity are untouched.

## Permanent capacity and permissions

1. Verify effective limit is always `effective permission limit - total confiscated chunks`, clamped only for display/use at zero.
2. Change the player's rank from a 100-chunk limit to 150 after a 50-chunk confiscation; verify effective capacity changes from 50 to 100, not back to 150.
3. Apply a penalty larger than the current permission limit in a disposable test; verify effective capacity is zero while the full penalty remains stored for future rank upgrades.
4. Verify every creation route (Claim Map, command compatibility route and direct GUI flow) uses the reduced effective limit.
5. Restart and verify confiscation entries remain keyed by settlement UUID and are not added twice.

## Dependency and failure safety

1. Disable Economy before a due payment. Verify the settlement remains pending, no claim/Home is deleted and no capacity is confiscated.
2. Re-enable Economy and verify the exact same settlement resumes.
3. Force a non-balance Economy error in a disposable development environment and verify it never takes the forfeiture path.
4. Make claim/Home/limit storage temporarily unwritable and verify the journal does not advance past a step that is not durable.
5. Verify claim mutation and Home creation/movement are blocked while the owner's settlement is active.
6. Verify other players can continue using their claims.

## Crash and idempotency recovery

Use backups and terminate the disposable server process at controlled points.

1. Stop after Economy commits but before the settlement journal advances; restart and verify no duplicate debit.
2. Stop after one of several claims is removed; restart and verify only remaining claims are processed.
3. Stop after claims/Homes are removed but before confiscation; restart and verify the penalty is applied once.
4. Stop after confiscation but before completion; restart and verify the same settlement entry is recognized and not applied twice.
5. Verify a completed penalty-bearing settlement remains in the ledger as its permanent audit/recovery source.
6. Remove the corresponding penalty entry from a copied limit file while retaining the completed ledger; start and verify reconciliation restores the exact missing penalty.
7. Change the penalty amount to a conflicting value in a disposable copy; verify Player Claim tax enters safety halt rather than choosing either value.

## Persistent safety halt

1. In a disposable copied world, corrupt `player_claims/tax_settlements.json` and remove/also corrupt its `.bak`.
2. Start the server and verify `player_claims/tax_safety_halt.json` is created.
3. Verify tax enforcement and claim mutation fail closed while tax remains enabled, including after another restart.
4. Verify simply restarting cannot clear the halt.
5. Restore or repair the correct settlement ledger first. Only then remove `tax_safety_halt.json` and reload/restart.
6. Verify normal operation resumes only after both the underlying data and marker are deliberately handled.

## Regression checks

1. Claim creation, expansion, shrinking, trust, flags, borders and map navigation still work when no settlement is active.
2. Removing a normal non-final chunk still deletes Homes located in that removed chunk.
3. Full non-tax administrator deletion still removes linked Homes.
4. Travel shows only surviving claim-bound Homes.
5. Player-warp rentals and Region Selection transforms from dev18.5 still work unchanged.
