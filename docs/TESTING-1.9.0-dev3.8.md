# SSU 1.9.0-dev3.8 — Runtime test checklist

Build with Java 25 and use the exact same dev3.8 JAR on client and server. Network protocol remains 93.

## 1. GUI-first kit permissions

1. Create or edit a kit with permission key `ssu.kits.newkit`.
2. Open Admin Center → Permissions → Ranks → `user`.
3. Confirm `ssu.kits.newkit` appears without using a command or restarting the server.
4. Set it ON and save.
5. Log in as / test a player whose effective rank is `user` and confirm the kit becomes visible and claimable (subject to enabled/locked/price/cooldown/one-time settings).
6. Set the permission OFF and confirm access disappears.
7. Also verify per-player permission editing.
8. Delete/change the kit permission key and confirm any old explicitly assigned key remains identifiable as obsolete so it can be unset cleanly.

## 2. Minigame results alignment

Run CTF, Domination and Spleef matches and inspect the result screen at multiple GUI scales/resolutions.

- Header columns and row values must line up for Player, T, Role, K, D, A, Damage, Heal, Cap, Def, Obj and Impact.
- Winner star must not shift the player name column.
- Test short and long player names.
- Test DPS, Tank and Healer labels.
- Scroll a result with more than 12 rows if practical.

## 3. Onboarding — Decline & leave

1. Reset onboarding for a test player.
2. Join and open the mandatory onboarding screen.
3. Confirm `Decline & leave` is always available.
4. First click must ask for confirmation; second click must disconnect the player.
5. Rejoin and confirm rules are still unaccepted and onboarding starts again.
6. Complete onboarding normally afterward and confirm the ordinary dashboard returns.

## 4. Spleef temporary projectile inventory lock

Test with Inventory lock ON.

- Wait for the Infinite Spleef Projectile unlock. It must appear and remain usable instead of being erased immediately.
- Throw the infinite projectile repeatedly: it should remain available according to its intended cooldown behavior.
- Wait for Power Spleef Projectile awards. Awarded eggs must remain in inventory until used.
- Throw one Power projectile and confirm exactly one is consumed; the inventory lock must not restore the consumed egg.
- Stack multiple Power projectiles where configured and confirm each use decrements correctly.
- Attempt manual movement/removal of protected match items and confirm the general inventory lock still prevents unauthorized rearrangement.
- Finish/leave the match and confirm temporary Spleef items do not leak into the restored normal inventory.

## 5. Manual minigame arena snapshot restore

1. Save a valid arena snapshot using the normal minigame setup workflow.
2. Change blocks in the arena.
3. Open Minigame Setup Tool and click `Restore snapshot`.
4. First click must arm confirmation; `Confirm restore` must schedule the restore.
5. Confirm the arena returns to the saved snapshot through the bounded reset job.
6. Start a match in that arena and confirm manual restore is refused/disabled by the server while the arena is in use.
7. Confirm restore is also refused while the arena is already resetting or has no saved snapshot.
8. Confirm another idle arena can still be restored independently.

## 6. Region Setup Tool clarity/workflow

Review every tab at normal and smaller GUI scales.

### Navigation
- Tabs should read: Region, Protection, Access & rent, Auto reset, Selection, Browse.
- Header must clearly show EDIT / CREATE / SELECT mode, target region and local detected region.
- Remote region editing through Browse must remain functional.

### Region
- `Teleport to region`, `Select region bounds`, region spawn controls, redefine and delete should have clear non-overlapping labels.
- `Select region bounds` must copy the current region bounds into the active world selection; clearing must remove it.
- `Save settings` must persist editable fields.

### Selection
- Selection page must clearly explain that the two corners are chosen in the world with the Region Tool.
- Build/fill/create actions must remain functional: create region, clear to air, fill water/lava and weighted inventory block mix.
- Portable snapshot wording must be distinct from the Auto reset snapshot system.
- Snapshot save/preview/placement from earlier dev3.6.x must remain functional.

### Auto reset
- `Reset source` must clearly distinguish snapshot versus block preset.
- `Save reset snapshot` and `Restore region now` must act on the region reset snapshot, not portable selection snapshots.
- Scheduled reset settings and presets must still persist.

### Browse
- The region under the admin's feet should be marked HERE.
- Edit must open a remote region without requiring teleport.
- Teleport must still use region spawn when available or a safe fallback location.

## Regression smoke test

- Server/Lobby spawn and dimension teleport.
- Onboarding completion and normal dashboard access.
- Moderation Manage hub.
- Kits claim flow.
- Region snapshot ghost preview / Free mode.
- CTF, Domination and Spleef start/end/reset.
