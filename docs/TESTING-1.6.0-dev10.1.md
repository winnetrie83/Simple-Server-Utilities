# SSU 1.6.0-dev10.1 test checklist

## Required setup

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev10.1 jar on client and server.
- Test with an operator/admin account as well as a normal player when possible.

## Region border hard gate

1. Give the test player personal **Region borders: ON**.
2. Set `ssu.borders.regions.view` to **false** through the effective server/rank/player permission configuration.
3. Confirm that no automatic region overview is visible.
4. Confirm that previously selected/pinned region borders disappear within about half a second.
5. Confirm that this also holds for an operator account.
6. Press **Show** beside a region and confirm the action is denied with “Region borders are not allowed by the server.”
7. Re-enable `ssu.borders.regions.view`; keep the personal toggle ON and confirm borders return.
8. Turn the personal toggle OFF and confirm borders disappear while the server permission remains enabled.

## Stale state and layers

- With a region pinned and visible, disable the server permission and verify both normal and focused borders disappear.
- Start a region selection, then disable the server permission and verify the selection outline is cleared.
- Disable the Server Regions module and verify every region visualization layer clears.

## Claim consistency

- Repeat the server deny/personal toggle test for `ssu.borders.claims.view`.
- Confirm an operator does not bypass an explicit deny for this border capability.
