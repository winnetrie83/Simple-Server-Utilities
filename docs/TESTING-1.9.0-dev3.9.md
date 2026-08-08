# SSU 1.9.0-dev3.9 runtime test checklist

Build basis: `1.9.0-dev3.8`  
Target: Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25  
Network protocol: `94`  
New persistence: Server Operations schema `1`

Run a clean local compile first:

```bat
gradlew.bat clean compileJava
```

Then test on a disposable server/world copy before using backup restore, configuration import or pregeneration on production.

## 1. Lightweight activity log and rollback

- Open **Admin Center → Server operations → Activity**.
- Confirm Logging / Break / Place can be toggled independently and retained after refresh/restart.
- Break and place several ordinary blocks outside minigames/dungeons; confirm entries appear.
- Perform block changes during a minigame/dungeon run; confirm those temporary gameplay edits are not recorded.
- Roll back one test player using a small radius and short time window.
- Confirm only matching player + current dimension + radius + time entries are considered.
- Confirm rollback skips a position if another player already changed that block after the logged action.
- Confirm restoration is deliberately lightweight: block type/default state only; do **not** expect container NBT, inventories or custom block-state properties to be reconstructed.
- Confirm a large matching set is processed gradually rather than in one server tick.

## 2. Backups and restore

- Confirm automatic backups start **OFF** on a fresh Server Operations state.
- Create a manual backup; verify progress/status and that a ZIP appears under SSU server-operations backups.
- Confirm the backup archive does not recursively contain the backup directory.
- Enable automatic backups, change interval/retention, refresh and restart; verify the protected `Automatic backup` scheduler task follows those settings.
- Create at least two backups, delete one, then verify SSU refuses deletion of the final remaining backup.
- On a disposable world copy, select a backup and use the two-step restore action.
- Verify the server stops after staging, the chosen world restores on shutdown, and a `*-pre-restore-*` safety world remains beside the restored world.
- Restart the server manually/through the host after restore and verify SSU/player/world data is readable.

## 3. Scheduler / Task Manager

Create and test:
- interval task: `60` or `60m`;
- daily task: `daily@04:00`;
- one-time task: `once@yyyy-MM-ddTHH:mm`.

Test actions individually:
- `BACKUP`
- `BROADCAST`
- `MAINTENANCE_ON`
- `MAINTENANCE_OFF`
- `SAVE_SSU`
- `SSU_RELOAD`
- `STOP_SERVER`

Confirm enable/disable, Run now and Delete work. System automatic-backup task must not be deletable. A one-time task disables after running. An imported/expired one-time task must not suddenly execute.

`STOP_SERVER` intentionally only stops the JVM/server; an external host/watchdog must start it again if a true restart is desired.

## 4. Maintenance Mode

- Set a custom disconnect message.
- Enable maintenance without kicking online players, then attempt a fresh normal-player login.
- Confirm normal login is rejected.
- Grant `ssu.maintenance.bypass` to a test rank/player and confirm that player can join/remain online.
- Test **Kick online** and confirm non-bypass players disconnect while admins/bypass players remain.
- Disable maintenance and confirm normal logins work again.

## 5. Chat moderation

Automatic filtering defaults OFF on a fresh Server Operations state.

Enable it and test:
- slow mode;
- duplicate-message window;
- burst/flood limit;
- caps percentage/minimum length;
- links allowed/blocked;
- blocked words/phrases;
- temporary mute;
- permanent mute;
- unmute.

Grant `ssu.chat.moderation.bypass`; confirm automatic filters are bypassed but an explicit mute still blocks chat.

Grant `ssu.chat.staff`; send `#test`. Confirm only staff-chat recipients receive it and it does not enter normal formatted public chat.

Confirm Recent Chat is capped/in-memory and does not persist after a server restart.

## 6. Staff Audit Log

Perform representative high-value admin actions and verify entries appear:
- rank/permission change;
- economy admin mutation;
- moderation action;
- online/offline inventory or ender-chest edit;
- kit create/edit/delete;
- managed-dimension change;
- onboarding admin change;
- region save/reset;
- minigame manual arena restore/clone/export/import;
- backup/scheduler/maintenance/chat settings.

Restart and confirm audit rows persist. The log must remain bounded.

## 7. Server Health

- Open **Health** and confirm TPS/MSPT, heap, online players, active jobs, permission/cache/region metrics and module timings populate.
- Put temporary load on the server and confirm MSPT/TPS react without needing a separate profiler.
- Confirm normal gameplay does not show a new continuous entity/block-entity world scan.

## 8. Support / Reports

As a normal player:
- open **Support**;
- create a Help/Bug/Player report ticket;
- confirm own ticket history appears;
- confirm the fourth simultaneous open ticket is refused;
- close one own ticket.

As staff/admin:
- open **Server operations → Reports**;
- Assign me;
- add/update staff note;
- Resolve;
- Reopen;
- Close.

Restart and confirm tickets persist.

## 9. World borders and pregeneration

- Select each loaded dimension and verify current world-border center/size.
- Change center/size on a disposable test dimension and verify vanilla border changes.
- Configure pregeneration to 1 chunk/tick first.
- Start a small radius and verify progress.
- Increase server load above the configured MSPT threshold and verify pregeneration reports auto-paused.
- Remove load and verify it resumes.
- Stop a running job manually.
- Confirm SSU refuses a second simultaneous pregeneration job.

## 10. Effective Permission Inspector improvement

Use existing **Admin Center → Player Info & Profile**:
- select a player with a direct permission override;
- verify source shows `personal override` or `personal wildcard` plus matched key;
- test a permission inherited through one or more ranks;
- verify the source shows the rank inheritance path and wildcard/exact key that won.
- Specifically test a dynamic kit permission such as `ssu.kits.newkit`.
- Confirm no separate duplicate Permission Inspector was introduced.

## 11. Economy analytics

Open **Server operations → Economy**:
- confirm account count and total supply;
- loaded transaction count;
- loaded 24h volume;
- richest players;
- loaded transaction volume by type;
- large-transaction alerts.

Change the large-transaction threshold and verify alerts update. Economy mutations should still be performed in the existing Economy Admin GUI.

## 12. Configuration profiles

- Export a profile and confirm it appears in Profiles.
- Make a visible configuration change (for example a kit/rank/onboarding setting).
- Select the profile and use the two-step import confirmation.
- Confirm SSU automatically creates a persistent `pre-import-*` safety profile before applying the selected profile.
- Confirm configuration reloads and the exported settings return.
- Confirm player balances, mail, moderation player records/inventories and progression are not overwritten by a configuration profile.
- Delete a non-required profile using confirmation.
- Test a deliberately invalid/corrupt profile only on a disposable server and confirm the error points to the preserved pre-import safety profile.

## Performance sanity

During normal idle gameplay:
- no continuous whole-world/player/entity scans should appear;
- action/audit log disk writes should be batched;
- automatic backups should do nothing while disabled;
- chat filtering should do nothing while disabled;
- scheduler/health work should stay bounded;
- pregeneration should only run while explicitly active and should respect MSPT throttling.
