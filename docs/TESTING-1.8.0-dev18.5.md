# SSU 1.8.0-dev18.5 test checklist

Back up the test world first. Client and dedicated server must both use the exact same dev18.5 build.

## Build and startup

1. Build with Java 25: `gradlew.bat clean build`.
2. Start a dedicated NeoForge 26.2 server and a separate client.
3. Confirm protocol 68 connects without a mismatch.
4. Confirm existing claims, homes and traditional server warps load unchanged.
5. Inspect `warps.json`: legacy data should migrate to schema 2 and remain public server warps.
6. Confirm `player_claims/tax_settings.json` is created as schema 1 with tax disabled and rate 0.

## Region selection fill mix

1. Select a cuboid with the Region Tool and open **Edit selected blocks → Fill mix**.
2. Add a normal block from each inventory section used by the picker.
3. Add a water bucket and verify it places source-water blocks without consuming the bucket.
4. Add a lava bucket and verify it places source-lava blocks without consuming the bucket.
5. Use a 40% block + 20% water mix and verify the remaining 40% becomes air.
6. Use an empty list and verify the selection becomes 100% air.
7. Verify exactly 100% works without air.
8. Verify totals above 100% are blocked by the client and rejected by the server.
9. Verify empty slots, non-block items and duplicate inventory slots are rejected.
10. Put items in destination containers and verify fill/air replacement clears container contents without drops or duplication.

## Selection transforms

Test non-square selections so axis swaps are visible.

1. Rotate left 90°, right 90° and 180°.
2. Mirror east/west and north/south.
3. Flip vertically.
4. Verify the minimum selection corner stays anchored and the outline resizes after 90° rotations.
5. Verify the old footprint outside the new footprint becomes air, without clearing unrelated corner blocks in the union bounding box.
6. Test directional blocks: stairs, doors, trapdoors, furnaces, logs/pillars, rails, signs and wall/floor/ceiling-mounted blocks.
7. Verify container inventories and entities are not copied by transforms.
8. Verify large selections use jobs and conflicting region/cuboid locks prevent overlapping edits.
9. Clear or change the selection while capture is running and verify the transform cancels safely.

## Player Claim tax administration

1. Open **Admin Center → Economics → Player Claim Tax**.
2. Confirm tax starts disabled.
3. Set base rate, interval and reminder lead; verify reminder must be shorter than the interval and greater than zero before enabling.
4. Verify default multipliers: Overworld x1.0, Nether x1.2, End x1.5.
5. Change all vanilla multipliers and add/remove a custom dimension multiplier.
6. Verify every rate/schedule/multiplier change starts a new full cycle and clears the old reminder snapshot.
7. Verify multiple claim groups in the same dimension produce the same tax as one group with the same total chunks.
8. Verify unlisted dimensions use x1.0.
9. Verify the tax page is available only to authorized economy administrators.

## Claim-tax reminders and successful payment

1. Use a short test interval and reminder lead.
2. Give a player claims in multiple dimensions and sufficient balance.
3. Verify one reminder mail lists the maximum warned amount and permanent non-payment consequence.
4. Add claims after the reminder and verify they wait until the next cycle.
5. Remove claims after the reminder and verify the actual charge decreases.
6. Verify the charge appears once in the economy journal as `CLAIM_TAX`.
7. Restart between reminder and collection and verify no duplicate reminder/charge occurs.
8. Disable Mail or Economy and verify destructive collection pauses. Re-enable and verify the cycle resumes safely.

## Claim-tax insufficient funds

Use a disposable test player/world backup.

1. Ensure the player receives the reminder first.
2. Reduce balance below the full warned/current amount.
3. At collection, verify every claim owned by that player is deleted.
4. Verify every home inside those claims is deleted.
5. Verify no region snapshot restores claim ownership or claimed chunks.
6. Verify other players' claims/homes remain untouched.
7. Verify borders refresh and the player receives a consequence mail.
8. Restart immediately after enforcement and verify deletion is not repeated and no charge is duplicated.

## Player-warp permissions and defaults

1. Verify existing ranks default to `ssu.warps.rent=false` and `ssu.warps.rent.max=0`.
2. Confirm **My Warps** explains that new rentals are unavailable but still allows management of already-owned rentals.
3. Grant rent permission and a positive maximum; verify the UI updates.
4. Verify the default administrator price is 100 economy units and default period is 30 days.
5. Verify a player cannot exceed the effective maximum.
6. Verify the rental name accepts only 1–32 letters, numbers, underscores or dashes and is globally unique.

## Player-warp rental and management

1. Enter a new name and verify the first click shows the prepaid confirmation; the second click rents it.
2. Verify exactly one `WARP_RENT` debit and one private rented warp are created.
3. Retransmit/reclick and verify no double charge occurs.
4. Use **Move here** and verify location changes without payment or paid-until reset.
5. Disable Economy and verify new rental is blocked while existing warp Move/Public/Delete controls remain usable.
6. Toggle public/private and verify state persists across restart.
7. Verify private warps are visible only to owner and warp administrators.
8. Verify public warps appear in Travel only for players with effective warp-use/teleport permission.
9. Verify rented warps cannot be listed, teleported to, inspected, moved or deleted through legacy warp commands.
10. Delete a rented warp through My Warps and verify its name is immediately free.
11. Verify administrators can inspect/teleport/delete player rentals through the administrator GUIs without turning them into server warps.

## Automatic warp renewal

1. Configure a short rental period in **Economics → Player Warp Rentals**.
2. With sufficient balance, verify one `WARP_RENEW` debit and a new paid-until timestamp.
3. Restart around expiry and verify renewal is not charged twice.
4. With insufficient balance, verify the warp is deleted, the name is freed and the owner receives mail.
5. Verify a temporary non-balance economy error does not delete the warp and is retried later.
