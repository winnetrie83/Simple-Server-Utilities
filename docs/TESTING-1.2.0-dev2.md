# SSU 1.2.0-dev2 manual test plan

Use a backup test world. Client and server must both use `1.2.0-dev2` because the network protocol is version `8`.

## 1. Upgrade and data compatibility

1. Start from a dev1 test world containing claims, regions, homes, warps, permissions and economy balances.
2. Install dev2 on both client and server.
3. Confirm all existing systems and balances load unchanged.
4. Inspect a legacy rentable region and confirm its old whole-unit price displays as the same euro amount.
5. Stop and restart once; confirm region JSON now contains the optional dev2 rental metadata.

## 2. Configure a rental region

```text
/regions setrent <name> 100,00 7
/regions setrentprice <name> 125,50
/regions setrentperiod <name> 7
```

Verify:

- `€ 125,50` is displayed;
- period `0` is rejected;
- period `-1` is accepted as permanent;
- actively rented regions cannot be deleted.

## 3. Basic paid rent

1. Give a player exactly `€ 200,00`.
2. Open the SSU Regions page and select Rent.
3. Confirm the GUI closes and chat shows price, period and current balance.
4. Click the confirmation.
5. Verify:
   - `€ 125,50` was deducted exactly;
   - the player became renter and member;
   - the timer started;
   - one `REGION_RENT` transaction exists;
   - the rent operation journal is `COMPLETED`;
   - a second player cannot rent the same region.

## 4. Insufficient funds and repeated confirmation

1. Attempt rent with insufficient balance; verify no region or balance change.
2. Click the same confirmation more than once; verify only one successful rent/payment exists.
3. Restart and verify the payment is not repeated.

## 5. Renewal

1. Open the renter's Regions page and select Extend.
2. Verify chat shows the price, period and current balance.
3. Confirm once.
4. Verify one full period is added and one `REGION_RENEW` transaction exists.
5. Repeat while the timer is paused; verify the timer remains paused and paused remaining time increases.

## 6. Owner payout

```text
/regions rentconfig ownershare 80
```

For a rent of `€ 100,00`, verify:

- renter loses `€ 100,00`;
- deterministic region owner gains `€ 80,00`;
- `€ 20,00` is the server share removed from circulation;
- `REGION_OWNER_PAYOUT` appears in history;
- renter is never selected as their own owner recipient.

Also test `0%` and `100%`.

## 7. Player cancellation refund

```text
/regions rentconfig playerrefund 50
```

1. Rent for a finite period.
2. Let roughly half the period remain, or use a short controlled test period/data copy.
3. Select Unrent.
4. Verify chat shows the estimated refund before confirmation.
5. Confirm and verify the refund equals 50% of the remaining eligible value, not 50% of the original price.
6. Verify region membership and timer are cleared.

## 8. Admin cancellation refund

```text
/regions rentconfig adminrefund 100
```

1. Have an admin cancel another player's active rental.
2. Verify the remaining eligible value is refunded pro rata at 100%.
3. Set policy to `0%` and repeat; verify no refund.

## 9. Pause/refund freeze regression

1. Rent a finite region and note its estimated 100% refund.
2. Pause the timer.
3. Wait several minutes or manipulate test time in a development environment.
4. Verify the eligible refund does not decrease while paused.
5. Resume and verify both timer and refund decay continue from the frozen values.

## 10. Reset jobs

1. Save a snapshot and enable `resetOnUnrent`.
2. Modify blocks inside the rented region.
3. Cancel rent.
4. Verify rental removal/refund occurs only after the bounded snapshot reset job completes.
5. Repeat with expiry and verify natural expiry provides no refund.

## 11. Restart recovery

Test each interruption using process termination only on a disposable world:

- after rent journal preparation but before debit;
- after renter debit but before region save;
- after owner payout but before region save;
- after region save but before operation completion;
- after cancellation region save but before refund.

After restart, verify one of the following consistent outcomes:

- rental exists and payment remains charged; or
- rental does not exist and payment/payout is compensated.

Never accept a duplicate charge, duplicate refund or rented region with an automatically refunded confirmed payment.

## 12. Dashboard and protocol

1. Open Wallet as a normal player; verify recent region transactions appear.
2. Open Wallet as economy admin; verify policy fields and pending operation count.
3. Apply all three policy fields and refresh.
4. Confirm a dev1 client cannot join a dev2 server and vice versa due protocol mismatch.
