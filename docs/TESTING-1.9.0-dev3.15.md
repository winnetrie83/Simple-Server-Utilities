# SSU 1.9.0-dev3.15 runtime checklist

Use the exact same dev3.15 build on client and server. This build changes the network protocol to 99.

## 1. Upgrade / persistence
- Start a copy of an existing dev3.14.1 world and confirm normal startup.
- Confirm existing schema-8 NPC definitions, placements, dialogue links, shops, loadouts, schedules and respawn settings still load.
- Confirm existing schema-19 minigames still load and validate.
- Restart once after saving an NPC and each new minigame type; verify persistence.

## 2. NPC editor regression
- Create a normal vanilla-model NPC and save/reopen it.
- Edit Identity, Behavior, Relations, Stats, Loadout, Schedule and Respawn independently; switching pages must not discard unsaved values.
- Verify dialogue editing, functions/services, factions, placement movement, delete and template reuse still work.
- Verify hidden custom name still hides only the overhead text and does not break entity-to-definition tracking.

## 3. NPC custom skins — local server file
- Put a valid 64x64 PNG below the server SSU storage path `simpleserverutilities/npcs/textures` (subfolders are allowed).
- In NPC Appearance choose `Local server PNG`, enter the relative path and save.
- Confirm the NPC uses a mannequin shell with the expected skin on every connected client.
- Test both Wide and Slim model shapes.
- Replace the PNG contents at the same relative path, reopen/save the NPC and confirm the new image is distributed without a server restart.
- Confirm `../`, absolute paths, missing files, non-PNG data, non-64x64 PNGs and files over 512 KiB fail safely/fall back without crashing.

## 4. NPC custom skins — HTTPS
- Configure a direct HTTPS URL to a valid 64x64 PNG <= 512 KiB.
- Confirm the texture appears on two separate clients without either client having a local copy.
- Change the URL, save and confirm the texture changes.
- Re-save after the remote file changes at the same URL and confirm the server refetches it.
- Confirm HTTP (non-HTTPS), malformed URLs, non-2xx responses, oversized downloads and invalid PNGs fail safely.
- Switch Texture source back to Vanilla and confirm the old dynamic custom skin is removed from clients.
- Disable/delete a custom-textured NPC and confirm no stale custom skin remains if the definition is later reused.

## 5. Merchant / Shop NPC workflow
- Set an NPC role to Merchant.
- Use `Create NPC shop`; confirm the embedded editor opens without requiring a technical shop ID.
- In the embedded shop inventory, left-click an admin inventory stack and confirm the full exact stack is copied as a ghost offer without consuming it.
- Right-click an admin inventory stack and confirm exactly one item is copied without consuming it.
- Save with `Save & back`; confirm the NPC editor returns and the linked shop remains attached after saving/restarting.
- Reopen with `Edit NPC shop` and verify offers, schedules/pricing and other shop data remain intact.
- Use `Shared shop...` to link an intentionally shared shop and verify Shop Manager still exposes its advanced/shared-shop controls.
- Unlink a shop and confirm the NPC no longer opens it.

## 6. King of the Hill creation/setup
- Create King of the Hill through both the Minigame selection flow and Minigame Setup Tool flow.
- Confirm a managed Region/snapshot is created where expected.
- Configure lobby, spectator location, both team spawns and Hill Center.
- Confirm setup visuals clearly identify the hill and both teams.
- Open the dedicated King of the Hill editor; save/reopen score target, radius, interval, points, weapon, friendly fire, names and colours.
- Validation must reject an invalid/missing hill center, unknown weapon or incomplete arena setup.

## 7. King of the Hill match runtime
- Start with at least one player per team.
- No players on hill -> neutral/no scoring.
- One team only on hill -> control and periodic team scoring.
- Both teams on hill -> contested/no scoring.
- Leave and re-enter the radius and verify control changes immediately/cleanly.
- Confirm only players physically on the controlling hill receive individual objective contribution/score.
- Reach the configured target and verify the correct team wins, winner title/colour/effects/results and rewards.
- Test death + configured respawn delay and team spawn return.
- Test friendly fire OFF and ON.
- Test leave/disconnect during a match and normal cleanup/restoration.
- Restart/recovery test with an active arena snapshot copy if practical.

## 8. Block Party creation/setup
- Create Block Party through both minigame creation routes.
- Configure lobby, spectator, playfloor and one player spawn per configured maximum player.
- Test max player counts above 16 up to 32 to verify player-slot/team handling remains valid.
- Verify duplicate/missing spawns, an invalid palette, fewer than two palette blocks and an oversized playfloor are rejected.
- Open the dedicated Block Party editor; save/reopen palette, initial/minimum seconds, speedup, drop time, tile size and fall depth.

## 9. Block Party match runtime
- Start with 2+ players and verify the full playfloor is repainted from the configured palette.
- Confirm the target safe block is announced with its translated block name.
- Stand on the correct block and wrong block with different players; wrong players must be eliminated at reveal.
- Confirm all unsafe floor blocks disappear while the safe blocks remain.
- Confirm the drop phase lasts the configured time and the next round repaints the floor.
- Verify countdown decreases each round but never below the configured minimum.
- Verify the target does not immediately repeat when another palette option exists.
- Verify falling below the configured elimination depth eliminates the player.
- Verify one survivor wins; if everyone fails the same reveal, the match ends as a draw.
- Verify the original arena snapshot restores when the match finishes/cancels.
- Test player disconnect/leave and ensure eliminated players cannot be re-added as winners.

## 10. Cross-module smoke test
- Verify Jail/Mine task mining from dev3.14.1 still removes blocks correctly and permissions still work.
- Verify Spleef, Capture the Flag and Domination can still create/start/finish after the minigame schema upgrade.
- Verify ordinary NPCs without custom textures render exactly as before.
- Verify server shutdown does not log uncaught NPC texture fetch/client payload errors.
