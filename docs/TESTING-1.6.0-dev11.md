# SSU 1.6.0-dev11 test checklist

## Required setup

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev11 jar on the dedicated server and every client.
- Keep a backup of the test world before first launch.
- Test with one operator/administrator and, preferably, a separate normal player.
- Confirm the server log reports network protocol 32 and no statistic-storage load errors.

## 1. Block Information module

1. In **Admin Center → Module settings**, confirm **Block Information** is present and enabled.
2. In **Settings → General**, confirm **Block information: ON** is available.
3. Look at several blocks and verify a top-centre panel appears with:
   - translated block name;
   - registry ID;
   - readable namespace/mod name;
   - hardness or Unbreakable;
   - Correct tool, Wrong tool or Any tool;
   - bounded block-state properties when present.
4. Test stone with and without a pickaxe, a crop, stairs, a chest and an unbreakable block.
5. Open any screen and confirm the overlay is hidden while the screen is open.
6. Turn the personal setting OFF and confirm the overlay disappears immediately.
7. Turn it ON and confirm it returns.
8. Set `ssu.block_information.use=false` for the player and confirm the overlay disappears within about one second, even if the player is an operator when testing the strict gate.
9. Re-enable the permission, then disable the whole module; confirm the overlay is cleared.
10. Re-enable the module and confirm the player preference is retained.
11. Change dimension and reconnect; confirm the effective state is synchronized correctly.

## 2. Create and edit custom statistics

Open **Admin Center → Statistics**.

1. Create `diamond_ore_mined`:
   - display name: `Diamond ore mined`;
   - event: `Block broken`;
   - target: `minecraft:diamond_ore`;
   - unit: `blocks`;
   - tracking active.
2. Create `all_blocks_broken` with the same event and target `*`.
3. Create `zombies_killed` using entity killed and `minecraft:zombie`.
4. Create `deaths` using player death; confirm its target field is not used and saves as `*`.
5. Create `damage_dealt` and verify decimal output with 0.01 precision.
6. Create `play_time` and verify it increases once per online second.
7. Confirm invalid IDs, blank names and malformed registry IDs are rejected.
8. Search by ID, display name, event type and target.
9. Edit a definition and rename its ID; confirm existing player values follow the new ID.
10. Pause one statistic and confirm its value stops increasing while other definitions continue.
11. Resume it and confirm tracking restarts.
12. Reset it with the double-confirm action and confirm every player's value becomes zero.
13. Delete it with double confirmation and confirm the definition and values disappear.
14. Verify a non-authorized player cannot open or mutate the Statistics page. Administration requires `ssu.statistics.admin` or the normal operator bypass.

## 3. Event and filter accuracy

- Break ordinary stone: only wildcard block-broken definitions should increase.
- Break diamond ore: both the wildcard and exact diamond definition should increase once.
- Place a block and confirm only block-placed definitions increase.
- Kill a zombie directly and with a projectile; confirm the responsible player receives the kill.
- Kill a different entity and confirm an exact zombie filter does not increase.
- Die and confirm the victim's player-death value increases once.
- Deal and receive damage; confirm dealt/taken values use the post-event inflicted amount and do not increase for zero damage.
- Leave a player online for at least 10 seconds and confirm play time increases by approximately 10 seconds.
- Restart the server and confirm definitions and values persist.

## 4. Storage, lifecycle and performance

1. Inspect the world folder after activity:
   - `simpleserverutilities/statistics/definitions.json`;
   - `simpleserverutilities/statistics/players/<uuid>.json`.
2. Confirm statistics are not written as a new file on every individual gameplay event; values should be queued in batches.
3. Disable the Statistics module after producing values, re-enable it and confirm data reloads unchanged.
4. Run `/ssu reload` and confirm definitions/values remain available and tracking continues once.
5. Log out immediately after changing a value, restart the server and confirm the latest value was retained.
6. Create wildcard and exact definitions for the same event and confirm both update without duplicate increments.
7. Review the server log for archived-file messages only when intentionally testing malformed JSON.

## 5. Floating Text integration

### Personal rich-text values

1. Create a TEXT hologram containing `Mined: {{stat:diamond_ore_mined}}`.
2. Confirm every viewer sees their own value.
3. Add `Rank: {{rank:diamond_ore_mined}}` and confirm players without a positive value see `-`.
4. Apply mixed colours/styles around the tokens and confirm rich formatting remains intact after replacement.

### Statistic scoreboard

1. Create a SCOREBOARD hologram.
2. Set objective to `ssu:diamond_ore_mined`.
3. In SELF mode, confirm the viewer sees their own formatted value.
4. In TOP mode, confirm positive-value players are sorted descending and displayed with rank, name, value and unit.
5. Change **Score rows** and confirm title plus leaderboard rows respect the maximum.
6. Change **Refresh sec** and confirm this statistic hologram follows its own interval independently from other scoreboards.
7. Pause the statistic: existing values should remain visible but stop changing.
8. Delete the definition: confirm the hologram reports the missing statistic safely instead of crashing.
9. Disable the Statistics module: confirm statistic holograms/tokens show a safe disabled or unavailable state.

## 6. Compatibility regression

- Confirm claims, server-region Show/Disable state, homes, warps, economy, mail and existing holograms still load.
- Confirm region records remain schema 4 and server-disabled regions remain hidden.
- Confirm existing player settings migrate to schema 4 without losing minimap, map, mail or utility-mining preferences.
- Confirm ordinary vanilla scoreboard holograms still work and are not interpreted as custom statistics unless the objective begins with `ssu:`.
