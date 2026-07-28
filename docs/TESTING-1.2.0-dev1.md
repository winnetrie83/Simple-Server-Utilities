# SSU 1.2.0-dev1 manual test plan

Use a backup test world. Client and server must both use dev1 because the network protocol is version 7.

## Build and startup

1. Run `gradlew.bat clean build` with Java 25.
2. Install the resulting JAR on client and server.
3. Start a copied world.
4. Confirm no economy load exception appears in the log.
5. Confirm these paths are created after economy access/save:
   - `simpleserverutilities/economy/settings.json`
   - `simpleserverutilities/economy/accounts/`
   - `simpleserverutilities/economy/transactions/`

## Balance and wallet

1. Run `/balance`.
2. Open the SSU menu with `U` and open Wallet.
3. Confirm command and GUI show the same balance.
4. Close/reopen the world and confirm the balance remains unchanged.

## Player payment

Use two players whose economy accounts already exist.

1. Give player A money with `/ssu economy give <A> 100,00`.
2. Run `/pay <B> 12,50` as A.
3. Confirm A loses exactly `€ 12,50`.
4. Confirm B gains exactly `€ 12,50`.
5. Confirm both histories contain one committed transfer.
6. Repeat through the Wallet payment fields.

## Input validation

Confirm these fail without changing balances:

- `/pay <B> -1`
- `/pay <B> 0`
- `/pay <B> 1,234` when only two decimal places are configured
- payment to self
- payment larger than balance
- payment beyond configured maximum
- payment to an unknown economy account

## Admin mutations

Test:

```text
/ssu economy give <player> 10,00
/ssu economy take <player> 2,50
/ssu economy set <player> 50,00
/ssu economy balance <player>
/ssu economy history <player> 20
/ssu economy status
```

Confirm exact resulting balances and committed transaction records.

## Permissions

1. Deny `ssu.economy.pay` and confirm `/pay` fails.
2. Deny `ssu.economy.balance` and confirm balance viewing fails.
3. Confirm a non-admin cannot use `/ssu economy give`.
4. Grant `ssu.economy.admin` and retest.

## Restart recovery

1. Complete several transactions.
2. Stop the server normally and restart.
3. Confirm balances and histories are unchanged.
4. Inspect account revisions and transaction status in JSON.
5. For advanced testing only: on a copied world, stop between repeated economy actions and verify no committed transaction is applied twice after restart.

## Regression

Confirm existing systems still work:

- claim map and claim modifications
- claim/region borders
- homes and warps
- region lookup/protection
- permissions and admin menu
- jobs and performance page
