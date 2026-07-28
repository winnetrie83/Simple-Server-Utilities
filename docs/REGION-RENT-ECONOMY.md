# SSU Paid Region Renting

## Scope of 1.2.0-dev2

Server-region renting now uses the built-in Economy Core. Rent, renewal, owner payout, cancellation and refund are server-authoritative and use exact minor-unit values.

The existing region model, members, timers, snapshots and reset jobs remain in place. This release connects those systems to durable economy transactions without changing claim or permission formats.

## Price representation and migration

New region records store an exact `priceMinor` value. With the default euro configuration:

```text
1 minor unit   = € 0,01
100 minor units = € 1,00
```

The old whole-unit `amount` field is preserved. A legacy region with `amount: 100` migrates to `€ 100,00` when first saved by dev2.

Prices can now include decimals:

```text
/regions setrent shop_a 250,50 7
/regions setrentprice shop_a 275,00
```

A period is `-1` for permanent rent or at least `1` day. A zero-day period is rejected.

## Rent transaction

A normal rent or renewal uses this order:

1. Validate region state, renter, permissions, job locks, price and balance.
2. Write a durable `PREPARED` region-rent operation record.
3. Debit the renter through the Economy Core.
4. Credit the configured owner share, if applicable.
5. Mark payment as committed in the region-rent journal.
6. Update renter, members, timer, rental sequence and refundable value.
7. Save and synchronously flush region storage.
8. Mark the operation completed.

If region persistence cannot be confirmed, SSU restores the previous in-memory region state and tries to flush that restoration before refunding. If neither state can be confirmed, it leaves the operation pending instead of guessing. Startup recovery then checks the stored rental sequence and economy idempotency keys.

## Renewal

```text
/regions extend <name>
```

This command shows the price, period, current balance and a clickable confirmation. The actual charge occurs only through:

```text
/regions extendaccept <name>
```

Renewal adds a full configured period to the remaining rental time. When a rental is paused, the additional time is added to the paused remaining duration and the timer stays paused.

## Owner payout and server share

The global owner share is configured as a whole percentage:

```text
/regions rentconfig ownershare <0-100>
```

Example with rent price `€ 1.000,00` and owner share `80%`:

```text
Renter debit:        € 1.000,00
Region owner credit:   € 800,00
Server share:           € 200,00
```

When a region has several owners, the recipient is selected deterministically by UUID, excluding the renter. This avoids paying a renter to themselves and keeps restart recovery reproducible.

The server share is removed from circulation in this release. A dedicated server treasury account is planned for a later economy milestone.

## Cancellation refunds

Two global policies are available:

```text
/regions rentconfig playerrefund <0-100>
/regions rentconfig adminrefund <0-100>
```

Defaults:

```text
Player cancels own rental: 0%
Admin/server cancellation: 100%
```

The configured percentage is applied to the **remaining eligible rent value**, not blindly to the original payment.

Example:

- paid `€ 100,00` for ten days;
- five days remain;
- admin refund policy is `50%`.

The time-based remaining value is `€ 50,00`; the policy then refunds `€ 25,00`.

Renewal rolls remaining refundable value and the new payment into one new time window. Pausing freezes both the timer and refundable value. Natural expiry gives no refund.

## Reset-before-removal behavior

When `resetOnUnrent` or `resetOnExpire` is enabled and a snapshot exists, the existing bounded reset job runs first. Rental access is removed only after that reset completes. This prevents the renter from losing access while their reset is still pending. For a manual cancellation, the refund value is frozen when the confirmed cancellation begins, so a long reset job cannot reduce it.

An actively rented region cannot be deleted directly. It must first be cancelled so any configured refund and reset can be processed safely.

## Durable operation journal

Cross-module operations are stored below:

```text
simpleserverutilities/
└── regions/
    └── rent_transactions/
        └── <operation-uuid>.json
```

States include:

```text
PREPARED
PAYMENT_COMMITTED
REGION_COMMITTED
COMPLETED
ROLLED_BACK
FAILED
```

Pending operations are shown in the Wallet admin panel. Economy mutations also retain their own separate transaction journals and idempotency keys.

## Economy transaction types

Dev2 adds:

```text
REGION_RENT
REGION_RENEW
REGION_OWNER_PAYOUT
REGION_OWNER_PAYOUT_REVERSAL
REGION_REFUND
REGION_PAYMENT_ROLLBACK
```

These appear in player history and future transaction-detail screens.

## Dashboard

Players can see available rental regions and their own current rentals on the Regions page. Available regions have a Rent button. Their own rentals have Extend and Unrent actions.

Rental actions close the dashboard and show a chat confirmation so the player can review the exact price or refund before committing.

Economy administrators can edit:

- owner share percentage;
- player cancellation refund percentage;
- admin cancellation refund percentage.

The Wallet page also shows the number of unresolved region-rent operations.

## Current limitations

- Rent policy is global, not yet overridden per region.
- Only one region owner receives the configured owner share.
- Server share is currently removed from circulation instead of credited to a treasury account.
- Refunds that would exceed the configured maximum account balance remain pending for administrative resolution.
- Full transaction-detail and account-browser GUI pages are planned for the next economy milestone.
