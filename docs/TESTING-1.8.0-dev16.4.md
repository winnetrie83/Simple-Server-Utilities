# SSU 1.8.0-dev16.4 trusted-player manager test

1. Build client and server from the same dev16.4 source with Java 25.
2. Open Dashboard → Claims → Settings for a claim you own.
3. Confirm Claim Settings shows one `Trusted players` row with a `Manage` button and no separate Trust/Untrust rows.
4. Open Manage and verify the Trusted players tab lists every existing trusted player.
5. Filter the trusted list by partial name and UUID text.
6. Remove a trusted player and confirm the player disappears, loses claim build access and reappears as an Add candidate.
7. Open Add player, search an online player and a previously known offline player, and add each.
8. Confirm the list persists after server restart.
9. Test as a player without `claims.trust`: the manager must be unavailable/read-only and forged add/remove payloads must be rejected.
10. Test a broad search with more than 100 known accounts and confirm the GUI reports the total and requests a narrower search.
