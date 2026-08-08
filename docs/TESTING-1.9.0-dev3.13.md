# SSU 1.9.0-dev3.13 — Dedicated Jail runtime checklist

Test on a real Minecraft 26.2 client + dedicated NeoForge 26.2.0.7-beta server with the exact same dev3.13 JAR on both sides.

## 1. Compile / version / upgrade

- Build both client and server from dev3.13 and confirm network protocol 97 matches on both sides.
- Start with an existing dev3.12 world and confirm moderation records, inventories, Regions and Mines still load.
- Confirm legacy active Jail task records using the old `taskRegion` field still work and do not receive a surprise new task deadline.
- Restart after creating/editing a physical Jail and confirm its definition persists.

## 2. Jail Administration and Region nesting

- Create an SSU Region that will contain the Jail.
- Open Jail Administration and create a Jail with that Region as parent.
- Give/use the Jail Setup Tool: left-click corner 1, right-click corner 2, then apply the selection as Jail bounds.
- Confirm a selection partly or fully outside the parent Region is rejected.
- Configure a smaller task work area and confirm it must fit completely inside the Jail.
- Set Intake, Task spawn and Release exit.
- Add multiple solitude-cell spawnpoints and verify the configured cell radius persists.
- Confirm Intake/Task/cells must be inside the Jail; Release exit must remain inside the parent Region.
- Disable a Jail and confirm no new sentence can target it.
- With an active prisoner, confirm destructive structural edits such as changing bounds/parent, clearing cells or deleting the Jail are rejected.

## 3. Region -> Jail -> Mine nesting

- Create a normal Mine whose bounds are fully or partly inside the physical Jail.
- Confirm the Mine remains an independent Mine: palette, resets, stats and permissions still belong to Mines, not Jail.
- Confirm the Jail itself still validates only against its parent Region and does not require a Mine.
- During a Jail task, mine a required block that is also inside the nested Mine and confirm Jail progress and Mine mined/stat counters can both advance without normal player drops escaping the punishment flow.

## 4. Task-only punishment

- Jail an online player using `Task only` with a rich-text reason, task deadline, required blocks and issued tools.
- Confirm any active SSU teleport is cancelled and active minigame/dungeon participation is ended before the Jail state is captured.
- Confirm the player is immediately moved to the selected Jail task spawn and their normal state/inventory is preserved for later restoration.
- Confirm only configured Jail tools remain available.
- Press U and confirm the normal player dashboard is replaced by the Jail dashboard.
- Attempt normal SSU pages/actions, commands, item use, containers, combat, item pickup/drop and unrelated block breaking; confirm they are blocked.
- Mine a configured required block inside the work area and confirm progress advances with no normal block drop.
- Mine the same block outside the work area or a non-required block and confirm it is denied.
- Complete the final requirement and confirm the punishment automatically completes, community resources are distributed, the original player state is restored and the player leaves Jail via Release exit/fallback.

## 5. Buyout-or-task choice

- Jail a player with `Buyout or Task` and a positive buyout.
- Confirm the Jail dashboard opens immediately and cannot be closed during the 30-second decision window.
- Confirm the dashboard shows buyout amount, current balance, task summary and remaining choice time.
- With enough balance, buy out and verify debit + immediate restoration/release.
- With insufficient balance, attempt buyout and confirm Task is immediately selected and locked in.
- Make no choice and confirm Task is automatically selected at 30 seconds.
- After Task is selected, confirm buyout is no longer available even if the balance later increases.

## 6. Task deadline and permanent failure

- Use a short task deadline for testing.
- Leave the task unfinished while online and confirm expiry permanently bans with the exact reason `failed to complete punishment`.
- Repeat while the prisoner is offline and confirm the once-per-second server scan still persists the permanent ban.
- Confirm a completed task before the deadline never triggers the failure ban.
- Admin-unban a failed prisoner, then log them in and confirm their pre-Jail state is restored and they are moved out of Jail.

## 7. Time / solitude punishment

- Configure at least two cell spawnpoints.
- Jail multiple players with `Time / solitude` and confirm cells are assigned using the least-used configured cell.
- Confirm a time prisoner can walk inside the configured cell radius.
- Attempt to leave the cell radius or change dimension and confirm confinement returns the prisoner to the assigned cell.
- Confirm commands, block/item/entity interactions, combat, containers, pickups and unrelated SSU functions are blocked.
- Let the timer expire while online and confirm automatic restoration/release.
- Disconnect before expiry, reconnect after expiry and confirm the sentence is completed/restored immediately instead of placing the player back into solitude first.

## 8. Admin release and offline restoration

- Release an online prisoner and confirm the pre-Jail state is restored immediately.
- Jail a player, disconnect them, then release them while offline.
- Reconnect and confirm the preserved pre-Jail state is restored before normal play; the offline release must not lose the inventory/effects/gamemode backup.
- Confirm whitelist rules still apply normally after an offline release; only an actually active prisoner may bypass the whitelist in order to serve punishment.

## 9. Teleport and confinement security

- As a prisoner, try homes/warps/spawn/other SSU teleport routes and confirm they are denied.
- As an admin, teleport the prisoner outside the Jail with a vanilla/admin teleport and confirm confinement returns them immediately on the next server tick.
- From Player Management use `Teleport to prisoner` and confirm the admin is teleported to the prisoner instead.
- Confirm the prisoner cannot use admin status/operator bypass to access unrelated SSU functions while jailed.

## 10. Freeze interaction

- Freeze a player, then Jail them. Confirm Jail supersedes/clears the freeze so there is no teleport tug-of-war between the old freeze anchor and the Jail.
- Attempt to freeze an already jailed prisoner and confirm the action is rejected as redundant/conflicting.
- After normal release, confirm the player's normal admin/permission capabilities return if they had them before.

## 11. GUI clarity

- Verify Player Management Jail tab shows readable status, Jail/mode/path/reason and formatted dates/times rather than raw timestamps.
- Open Configure punishment and verify Jail, punishment mode, rich-text reason, task setup and all labels are understandable without JSON editing.
- Verify the inventory-backed Jail Task editor shows required block slots, amounts, issued tools, real inventory/hotbar and normal item tooltips.
- Verify Jail Administration clearly exposes parent Region, bounds, work area, intake/task/release points, cells and active prisoner count.
- Verify Jail dashboard text/buttons do not overlap at common GUI scales and each punishment path shows only relevant controls.

## 12. Restart / persistence

- Restart during the 30-second choice and confirm the absolute choice deadline persists; if already expired, Task becomes selected.
- Restart during a Task sentence and confirm progress, task deadline, tools and pre-Jail player backup persist.
- Restart during a Time sentence and confirm cell assignment and release time persist.
- Confirm an offline-release restore-pending state survives restart until the player next logs in.

## 13. Configuration profiles

- Export a configuration profile and confirm `jails/definitions` is included.
- Import it and confirm physical Jail definitions return correctly.
- Confirm active prisoner/sentence data, player inventories and punishment progress are NOT treated as configuration-profile data.

## 14. Regression

- Confirm Regions still work independently and their normal nesting/priority behavior is unchanged.
- Confirm Mines dev3.12 functionality still works outside and inside a Jail.
- Confirm Claims, Minigames, Dungeons, Teleports, Permissions, Mail, Economy, Kits and Support continue to work for non-jailed players.
- Confirm normal admins who are not jailed retain their expected operator/admin bypass behavior.
