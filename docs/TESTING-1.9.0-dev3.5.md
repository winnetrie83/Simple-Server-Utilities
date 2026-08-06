# SSU 1.9.0-dev3.5 — runtime test checklist

Use the exact same dev3.5 build on client and dedicated server. Network protocol is 90.

## 1. Rank priority and inheritance

1. Open Admin Center > Rank Management.
2. Select Manage for a non-default test rank.
3. Change its priority, refresh, restart the server and confirm persistence.
4. Add one existing parent rank and confirm its permissions are inherited.
5. Remove the parent and confirm inherited permissions disappear.
6. Try self-inheritance and a circular chain; both must be rejected without changing stored data.

## 2. Multiple ranks per player

1. Open Admin Center > Permissions > Players.
2. Select a player, select a rank and press Add.
3. Add a second rank and confirm the first remains assigned.
4. Remove one selected rank and confirm all other ranks remain.
5. Confirm the effective primary rank/prefix follows normal priority resolution.
6. Confirm Reset rank still returns the player to the server default without deleting personal permission overrides.

## 3. Claim access roles and owner-only settings

1. As a claim owner, open Claim Settings > Claim access > Manage.
2. Add a player. The initial role must be Member.
3. Toggle that player between Member and Co-owner and confirm persistence after restart.
4. Remove the player and confirm access is revoked.
5. Log in as the Member and Co-owner and verify neither can open Claim Settings, including through crafted/stale GUI routes.
6. Load a legacy schema-2 claim with trusted players; every legacy trusted player must migrate to Member.

## 4. Per-claim role permissions

From Claim access > Manage > Role permissions, test Co-owner, Member and Visitor separately. For each permission, test ON, OFF and Use default:

- break blocks;
- place blocks;
- damage/remove item frames and armor stands;
- open chests, barrels and other menu/container blocks;
- use doors, trapdoors and fence gates;
- use buttons, levers and pressure plates;
- pick up and drop item entities;
- use homes linked to the claim without editing/deleting them;
- damage sheep, villagers, monsters and other non-player living entities;
- interact with living entities;
- other block interactions.

Also verify:

- Owner always has full access regardless of role settings.
- A per-claim override affects only that claim.
- Use default returns to Admin Center > Permissions > Claim roles.
- Region protection still takes precedence where a server region overlaps a player claim.
- Visitors only see/use a permitted claim home while physically inside that claim.
- Assigned Members/Co-owners may use permitted claim homes through Travel but cannot edit them.

## 5. Global Claim roles permission editor

1. Open Admin Center > Permissions > Claim roles.
2. Confirm Owner, Co-owner, Member, Visitor and Outside/none targets exist.
3. Change global defaults for Co-owner/Member/Visitor.
4. Confirm claims without local overrides follow the new defaults.
5. Confirm claims with local overrides remain unchanged.
6. Owner controls must remain effectively immutable/full-access.

## 6. Region rental cancellation refunds

1. Open Economics > Rent Journal.
2. Set player cancellation and administrator cancellation percentages to distinct values.
3. Refresh and restart; confirm both persist.
4. Perform one player cancellation and one administrator cancellation and verify journal/accounting amounts use the configured percentages.
5. Validate 0 and 100; reject values outside 0–100 and non-numeric values.

## 7. Live minigame score correction

1. Start an active minigame.
2. Open Minigame Administration, enter an online participant and use Add score.
3. Use positive and negative values and confirm the live scoreboard updates.
4. Use Set score and confirm the exact value replaces the old score.
5. Try an offline player, a player outside a match and invalid numeric input; the operation must fail safely.
6. Finish/recover the match and confirm no score mutation leaks into later matches.

## 8. Regression checks

- Normal owned homes, warps and spawn still appear in Travel.
- Claim tax, delete and home cleanup continue to work with schema-3 claims.
- Item dropping never duplicates or destroys stacks when denied.
- Projectile/entity/PvP protection remains unchanged outside the new claim-role rules.
- Rank prefixes, chat formatting, titles, damage indicators, minigames and Region Setup Tool still work.
