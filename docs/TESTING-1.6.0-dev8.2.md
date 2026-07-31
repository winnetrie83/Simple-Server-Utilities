# SSU 1.6.0-dev8.2 — Claim management test checklist

Use the exact same `1.6.0-dev8.2` build on the dedicated server and client. Network protocol is `30`.
Back up the world before testing a development build.

## 1. Claim spawn removal

1. Open the dashboard and the settings page of an owned claim.
2. Confirm that there is no claim-specific **Set spawn**, **Clear spawn** or stored spawn row.
3. Run `/claims help` and confirm that no claim `setspawn` command is listed.
4. Try `/claims setspawn <claim>` and `/claims admin setspawn ...`; both should be unknown commands.
5. Run `/claims tp <claim>`.
6. Confirm that SSU teleports to the centre/surface of an automatically selected claimed chunk.
7. Test a claim in another dimension as well.
8. Confirm that global `/spawn` and region spawn settings still work normally.

## 2. Trust and Untrust player selection

Prepare an owned claim, at least one second player and preferably one previously known offline player.

1. Open the owned claim's settings.
2. On the Trust row, open the player dropdown.
3. Confirm that the owner is not listed.
4. Confirm that online and previously known players can be listed.
5. Select a player and click **Apply**.
6. Confirm that the player appears under Trusted players and disappears from the Trust dropdown.
7. Open the Untrust dropdown and confirm that it lists only players currently trusted in this claim.
8. Select the trusted player and click **Apply**.
9. Confirm that the player disappears from Trusted players and returns to the Trust dropdown.
10. With more than eight available players, scroll the dropdown and verify that all rows remain selectable.
11. Confirm that a player without the claim-trust permission cannot apply either action.

## 3. Claim welcome message

Set a recognizable welcome message on a claim containing multiple adjacent chunks.

1. Walk from unclaimed land into the claim: the message should appear once.
2. Walk between chunks belonging to that same claim: the message must not repeat.
3. Walk out of the claim and back in: the message should appear once again.
4. Walk directly from claim A into claim B: claim B's message should appear once.
5. Stand inside a claim and remain still for at least 30 seconds: no repeated messages should occur.
6. Log out and reconnect while positioned inside the claim: the message should appear once after reconnecting.
7. Clear the welcome message and re-enter: nothing should be displayed.

## 4. Compatibility and regression

1. Start a copy of an existing dev8/dev8.1 world and confirm claims, chunks, owners, trusted players and flags still load.
2. Save after changing a claim and restart the server; confirm the claim remains correct.
3. Confirm regions, region spawns, homes, warps, server spawn, economy, mail, holograms and border settings still load.
4. Verify that a protocol-29 client cannot join a protocol-30 server and that matching dev8.2 client/server builds connect normally.
