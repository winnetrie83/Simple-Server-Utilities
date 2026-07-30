# Testing SSU 1.4.0-dev1

Use a copied dev4.3 world. Build with Java 25 and install the same 1.4.0-dev1 JAR on client and server. Network protocol is 13.

## 1. Build and migration safety

```bat
gradlew.bat clean test build
```

Confirm:

- all unit tests pass;
- the JAR reports `1.4.0-dev1`;
- existing claims, regions, region snapshots, rentals, balances, transaction history, permissions, homes, warps and UI preferences remain present;
- no duplicate-service or missing-module error appears during startup;
- the server log shows no failure while loading claims, permissions, economy, regions, snapshots, rent recovery or border settings.

## 2. Protocol matching

- Join with a matching 1.4.0-dev1 client: connection must succeed.
- Try a dev4.3 client against the dev1 server only on a disposable test instance: it must be rejected because protocol 12 and 13 may not be mixed.

## 3. Compact shell and navigation

Open the dashboard with `U` and `/ssu menu`.

Confirm:

- the home page opens promptly;
- profile/rank, balance and aggregate counts are correct;
- the texture-backed tiles and player portrait still render at normal GUI scale;
- narrow GUI scales fall back to a usable compact layout;
- opening Claims, Travel, Regions or Transactions loads only that page;
- Next/Previous and search return correct rows;
- changing pages quickly never lets an older response overwrite the current page;
- a shell refresh preserves the active page, page number, search and unfinished text fields.

## 4. Player actions

With a non-admin test player, verify only permitted actions are available and work:

- pay another known economy account;
- teleport to a home and a warp;
- open the claim map for an owned claim;
- show/hide an owned claim border;
- show/hide a visible or rentable region;
- rent and extend a test rental;
- verify Unrent changes to Confirm on the first click and only cancels on the second click;
- change dashboard hints, minimap options and border visibility.

Confirm balances, cooldowns, permissions and rental rules are still enforced by the server.

## 5. Crafted/unauthorized action safety

Use players with different ranks or temporarily remove permissions.

Confirm a player cannot gain access by reopening an already visible screen after permission removal:

- economy give/take/set is rejected without economy administration;
- job cancellation and counter reset are rejected without core administration;
- rank assignment and personal overrides are rejected without permission administration;
- hidden/non-rentable regions cannot be inspected or visualized by an unrelated player;
- claim actions cannot target another player's claim;
- payment is rejected when economy use or pay is disabled.

## 6. Economy administration

On the Accounts page:

- search by player name and UUID;
- inspect balance, revision and update time;
- give a small amount;
- take a smaller amount;
- set a test account balance;
- verify each operation appears in transaction history with actor, reason and module details;
- verify invalid amounts, unknown UUIDs and insufficient balances return an error without changing data.

## 7. Transactions and rent recovery

- Search transactions by source, destination, actor and reason.
- Select rows and inspect type, status, amount, module, failure and timestamps.
- Open rent operations and inspect completed and pending records.
- If a copied world contains an unresolved rental journal record, verify it is visible and startup recovery behaves as before.
- Update owner share and player/admin refund percentages, restart, and verify the values persist.

## 8. Permission administration

- With an empty search, inspect rank priorities and rank overrides.
- Search an online and an offline known player by name.
- Assign a valid rank.
- Set a personal permission override.
- Remove that override.
- Restart and verify rank/override persistence.
- Try an unknown player, rank or blank permission key; no data may be changed.

## 9. Jobs and shutdown ordering

Start a large region fill, clear, snapshot save or reset.

Confirm:

- the Jobs page shows description, operation count and progress;
- an authorized cancellation invokes the normal job cancellation path;
- an unauthorized player cannot cancel it;
- stopping the server during an active job cancels jobs before module-owned managers perform final saves;
- after restart, existing snapshot/reset safety markers behave exactly as in dev4.3.

## 10. Teleport lifecycle

Create a delayed home or warp teleport, then stop the server before completion. After restart, confirm no stale pending teleport or old cooldown is applied from the previous server lifecycle.

## 11. Map regression

The map renderer was not changed. Confirm:

- minimap, claim map and world map retain the accepted dev4.3 appearance;
- right-click dragging still works;
- connected claims still render only their outer perimeter;
- existing dev4.3 persistent tiles return without a forced rebuild;
- no new cache fingerprint or unsupported tile-version warning appears.

## 12. Final persistence check

Restart normally and recheck:

- claims and trusted players;
- region owners/members/rentals/snapshots;
- economy balances and transaction history;
- ranks and personal overrides;
- homes and warps;
- player UI/minimap preferences;
- border visibility and pinned regions.
