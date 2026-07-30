# SSU 1.4.0-dev3 Minecraft Test Plan

Use a backup/copy of a dev2.1 test world. Client and server must both use protocol 18.

## 1. Build and startup

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm `BUILD SUCCESSFUL` and use the JAR from `build/libs` on both client and server.
3. Start the copied world and inspect `latest.log` for module, payload, permission or spawn-storage errors.
4. Confirm existing claims, regions, rentals, homes, warps, economy data and maps still load.

## 2. Set and persist server spawn

1. Give the administrator `ssu.spawn.admin = true`, or use an operator account.
2. Stand at an obvious position and use `/spawn set` or Travel → **Set spawn here**.
3. Move elsewhere and run `/spawn`.
4. Verify position, dimension, yaw and pitch are restored safely.
5. Restart the server and verify `/spawn` still reaches the same destination.
6. Use `/spawn info`, then `/spawn clear`; verify `/spawn` reports that no spawn is set.
7. Set it again for the remaining tests.

## 3. Basic permission behaviour

1. Confirm a default player can use `/spawn` because `ssu.spawn.use` defaults to true.
2. Set a personal/rank `ssu.spawn.use = false`; verify command and dashboard teleport are denied.
3. Reset it to inherited/default and verify use returns.
4. Confirm a non-admin cannot use `/spawn set`, `/spawn clear`, `/setspawn` or `/delspawn`.

## 4. Block spawn inside `servertestarea`

1. Open the region's Settings screen.
2. Open **Permissions**, search for `ssu.spawn.use` and choose **Deny**.
3. Stand inside `servertestarea` and run `/spawn`; it must be rejected.
4. Use the Travel-page Server Spawn button; it must be rejected identically.
5. Leave the region and verify `/spawn` works again.
6. Return to the editor and choose **Default**; verify the inherited/default permission applies again.
7. Repeat through commands:

```text
/regions perm servertestarea set ssu.spawn.use false
/regions perm servertestarea list
/regions perm servertestarea unset ssu.spawn.use
```

## 5. Strict deny and bypass

1. Give a player personal `ssu.spawn.use = true`.
2. Deny `ssu.spawn.use` on `servertestarea`; verify the personal Allow does not escape the region.
3. Give that player `ssu.spawn.region_bypass = true`; verify `/spawn` now works from the region.
4. Also set personal `ssu.spawn.use = false`; verify bypass does not override this non-region deny.
5. Reset both personal values after testing.

## 6. Delayed guard

1. Set `ssu.spawn.teleport.delay = 5` and ensure movement cancellation is disabled for this test if necessary.
2. Start `/spawn` outside `servertestarea`.
3. Enter `servertestarea` before the countdown expires.
4. Verify the pending teleport is cancelled with the region-denial message.
5. Leave the region and immediately try again; verify no cooldown was consumed by the cancelled request.
6. Start `/spawn` inside the denied region; it must fail before a pending teleport is created.
7. Verify `/spawn cancel` removes a valid pending request.

## 7. Region priority

1. Create two overlapping regions with different priorities.
2. Deny spawn on the lower-priority region and leave the higher-priority region at Default/Allow; verify the effective higher-priority region wins.
3. Reverse the permissions/priorities and verify the new effective region wins.
4. With equal priority, verify the smaller containing region follows the existing tie-break behaviour.

## 8. Region permission GUI

1. Search by key and by description text.
2. Test Previous/Next/Refresh and Back at several GUI scales.
3. Set/reset a boolean, integer and custom text permission.
4. Enter an invalid integer and verify the server rejects it without changing stored data.
5. Verify a region manager without region edit/admin permission cannot open or mutate the permission editor.
6. Change an override through command while the GUI is open, press Refresh and verify the authoritative value appears.
7. Restart and verify all overrides persist.

## 9. Regression checks

- homes, warps, claim and region teleports;
- movement cancellation and cooldowns;
- region settings, managers/members and rentals;
- dashboard permission editor and Player Info;
- claim map, minimap and world map;
- two-client LAN/server connection;
- `/ssu reload` followed by `/spawn`;
- normal shutdown/restart and spawn-file deletion after clear.
