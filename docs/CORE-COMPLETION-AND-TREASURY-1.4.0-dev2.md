> **Historical dev2 document:** the permanent treasury and owner-payout design described below was retired by 1.4.0-dev2.1. It remains here only as development history.

# Core Completion & Server Treasury — SSU 1.4.0-dev2

## Core lifecycle completion

The following shared systems now have explicit `SsuModule` lifecycle ownership:

- asynchronous storage;
- bounded job scheduler;
- performance monitor;
- transaction coordinator;
- economy;
- server treasury;
- claims;
- permissions;
- homes;
- warps;
- player UI preferences;
- regions, snapshots and rent journal;
- teleports;
- visualizations;
- dashboard/menu.

The existing public managers remain the compatibility facade used by commands and protection events. This avoids a risky all-at-once rewrite while centralizing startup, shutdown and service registration.

Shutdown now has two stages. `beforeServerStopping` first cancels active jobs and invokes their completion callbacks. Normal module shutdown then runs in reverse dependency order, and the storage worker stops last so queued final saves can still flush.

## Removed test source set

`src/test`, JUnit dependencies and ModDevGradle's unit-test configuration were removed by request. Use:

```bat
gradlew.bat clean build
```

The build still compiles the main mod sources, processes resources and packages the JAR. Runtime behaviour remains covered by the manual Minecraft test plan.

## Server Treasury

The treasury is a normal journaled economy account with a deterministic UUID and the display name `Server Treasury`. It is stored inside the current world's existing economy folder, starts at zero and is hidden from player-account selectors.

### Permissions

- `ssu.economy.treasury.view`: view balance and transaction history.
- `ssu.economy.treasury.admin`: deposit, withdraw and pay players from treasury funds.

Both module defaults are `false`. A rank with a matching wildcard such as `ssu.* = true` still receives access through normal permission resolution.

### Region-rent flow

For a paid rent or renewal:

1. the renter is debited for the full price;
2. the configured owner share is credited to the deterministic eligible owner, if any;
3. the remainder is credited to the treasury;
4. the rental state is persisted;
5. any failed later step is compensated through idempotent reversal records.

No currency is created by the successful distribution: the renter debit equals owner payout plus treasury income.

### Cancellation refunds

Cancellation refunds now transfer money from the treasury to the renter instead of creating new currency. This keeps total supply stable. If the treasury does not contain enough funds, the region cancellation remains valid and the rent journal records a pending refund. Recovery retries the same idempotency key after restart, so replenishing the treasury allows the refund to complete safely without double payment.

This also means administrators should maintain enough treasury reserves when configuring large player/admin cancellation refund percentages, especially when a large owner share is paid immediately.

## Compatibility

- Mod version: `1.4.0-dev2`
- Network protocol: `16`
- Existing claim/region/snapshot/permission/home/warp/UI/map files: unchanged
- Economy accounts and transactions: existing records remain readable
- Rent journal: optional treasury amount/transaction fields default to zero/null for old records
