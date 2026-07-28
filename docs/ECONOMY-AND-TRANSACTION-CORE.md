# SSU Economy and Transaction Core

## Scope through 1.2.0-dev2

The reusable transaction and economy foundation introduced in dev1 now powers real server-region rent payments, renewals, owner payouts, cancellation refunds and compensation after storage failures.

It still intentionally does not add Auction House listings, mail attachments, NPC shops, quest rewards, custom bags, bank accounts or multiple currencies.

## Currency representation

Balances use signed Java `long` values in exact minor units.

With the default configuration:

- `1` internal unit = `€ 0,01`
- `100` internal units = `€ 1,00`
- no `float` or `double` is used for official balances

The default display locale is Belgian Dutch and the default currency is euro. Economy settings can change the currency name, symbol, decimal count and limits.

## Storage

```text
simpleserverutilities/
├── economy/
│   ├── settings.json
│   ├── accounts/<player-uuid>.json
│   └── transactions/<transaction-uuid>.json
└── regions/
    ├── _settings.json
    ├── entries/<region>.json
    └── rent_transactions/<operation-uuid>.json
```

Account/settings records use batched dirty-record storage. Economy transaction preparation and region-rent operation journals use immediate atomic writes because recovery data must exist before value or ownership changes.

## Economy transaction lifecycle

1. Validate permissions, identities, amount and limits.
2. Calculate exact before/after balances and revisions.
3. Atomically write a `PREPARED` economy journal record.
4. Apply in-memory mutation through `SsuTransactionManager`.
5. Reverse already-applied in-memory steps if a later step fails.
6. Queue account persistence.
7. Mark the economy journal `COMMITTED`.

At startup, PREPARED and COMMITTED records reconcile against account revisions so an after-state is applied at most once.

## Cross-module rental lifecycle

Region renting adds a second operation journal that links one or more economy transactions to a specific region rental sequence. Recovery can determine whether the region commit reached disk and then either retain the payment or compensate it exactly once.

See `REGION-RENT-ECONOMY.md` for the complete flow.

## Module-facing economy API

Future modules should depend on `EconomyService` or the typed internal mutation methods, not command or GUI classes.

Supported primitives include:

- read and format a balance;
- transfer between accounts;
- credit/debit with module and reason metadata;
- explicit transaction types;
- deterministic idempotency keys;
- lookup of committed idempotency keys for recovery.

## Commands

Player commands:

```text
/balance
/economy
/economy balance
/economy history [limit]
/pay <player> <amount>
```

Admin commands:

```text
/ssu economy status
/ssu economy balance <player>
/ssu economy give <player> <amount>
/ssu economy take <player> <amount>
/ssu economy set <player> <amount>
/ssu economy history <player> [limit]
```

Region economy policy:

```text
/regions rentconfig ownershare <0-100>
/regions rentconfig playerrefund <0-100>
/regions rentconfig adminrefund <0-100>
```

Amounts accept Belgian formatting such as `10`, `10,50`, `1.234,56` and `€10,50`.

## Dashboard

The Wallet page shows balance, recent transactions and player-payment inputs. Economy administrators additionally see account count, total supply, rent policy controls and pending rental reconciliation operations.

The Regions page exposes available rentals and the player's current rentals. All values and permissions remain server-authoritative.

## Current limitations

- No bank/wallet split or multiple currencies.
- No dedicated server treasury account yet.
- No full GUI account browser or transaction-detail screen.
- Unknown offline names cannot receive normal player payments until an account exists.
- Journal pruning/archiving is intentionally deferred while recovery behavior is being tested.
