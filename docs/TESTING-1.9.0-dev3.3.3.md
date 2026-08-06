# SSU 1.9.0-dev3.3.3 — Runtime Test Checklist

Build and run the exact same dev3.3.3 jar on client and dedicated server.

## 1. Damage and healing indicators

1. Open `Settings > Combat` and confirm the style cycles through:
   - Floating
   - Hearts
   - Compact
   - Pop
   - Burst
2. Damage several living entities with whole-number and decimal damage values.
3. Heal a player/entity through SSU healing and confirm healing values are green.
4. Confirm damage remains red in every style.
5. Confirm all values are approximately twice the former size and stay strongly opaque for most of their lifetime.
6. Confirm Pop visibly enlarges and settles.
7. Confirm Burst moves outward as it rises.
8. Test at multiple GUI scales, camera distances and with several simultaneous indicators.
9. Disable Damage indicators and confirm no new indicators appear.
10. Deny `ssu.damage_indicators.use` and confirm the feature cannot be enabled.

## 2. Player titles

1. Select a title in Player Profile.
2. Confirm your own title is not visible in first-person.
3. Confirm your own title is also not visible in third-person.
4. Join with a second client and confirm each client sees only the other player's title.
5. Walk, sprint, jump, fall, swim and fly with the remote player.
6. Confirm the title follows smoothly without one-tick trailing or visible stutter.
7. Toggle `Visible title` OFF and ON and confirm remote visibility updates correctly.
8. Confirm rank/nameplate and chat formatting remain unchanged.

## 3. Universal inventory lock — Capture the Flag

Test once with roles enabled and once with roles disabled.

1. Keep `Inventory lock: ON` in the CTF editor.
2. Start a match and attempt all of the following:
   - drag a weapon to another slot;
   - shift-click a weapon or ability;
   - move armor into the inventory;
   - swap armor pieces;
   - move the offhand shield;
   - use number-key slot swapping;
   - leave an item attached to the inventory cursor;
   - press Q/drop.
3. Confirm the configured match layout is restored immediately without duplication or item loss.
4. Pick up the enemy flag and confirm the configured banner remains equipped on the head.
5. Attempt to remove/move the carried flag banner and confirm CTF restores it.
6. Drop, return and score the flag and confirm the team helmet is restored correctly.
7. Die and respawn and repeat the movement tests.
8. Disconnect/rejoin during the grace period and repeat the movement tests.

## 4. Universal inventory lock — regression

1. Repeat representative move/armor/offhand/cursor tests in Domination.
2. Repeat tool movement tests in Spleef.
3. Confirm the lock is active during preparation and during RUNNING.
4. Confirm spectator and post-game cleanup still restores the player's original inventory.
5. Confirm no match item leaks into the world or survives after recovery.

## 5. Explicit game exception

1. Set `Inventory lock: OFF` for a test definition.
2. Confirm inventory slots and equipment may be rearranged during that match.
3. Confirm SSU's separate anti-drop/leak safety still prevents temporary match items from being discarded into the world where applicable.
4. Turn the lock back ON and confirm immutable behavior returns.
5. Restart the server and verify the toggle persists.

## 6. Compatibility and recovery

1. Load existing schema-18 definitions and confirm they migrate to schema 19 with Inventory lock enabled.
2. Stop the server during preparation, during CTF flag carrying and during a respawn delay.
3. Restart and confirm recovery restores original inventory, equipment, effects, position and gamemode.
4. Confirm no duplicate cursor item, flag banner or role item remains.
