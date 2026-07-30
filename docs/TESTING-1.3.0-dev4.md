# SSU 1.3.0-dev4 test plan

Use the same dev4 JAR on client and server. Network protocol remains 11. Back up the world and the client `simpleserverutilities/map-cache-v2` folder before testing recovery scenarios.

## 1. Basic compatibility and loading

1. Start with a copy of a world last used by dev3.4.
2. Use Java 25 and matching dev4 client/server JARs.
3. Confirm claims, regions, ranks, permissions, homes, warps, balances, transactions and active rentals still load.
4. Open the dashboard, minimap, world map and claim map.
5. Confirm no player is disconnected for a protocol mismatch and no migration exception appears in the log.

## 2. Region mutation safety

Create an unrented test region with a saved snapshot and a second region that can be rented.

1. Start `/regions save <name>` and immediately try delete, redefine, clear and reset on the same region.
2. Confirm every conflicting operation is rejected while the capture job owns the region lock.
3. Start a fill, clear or reset job and repeat the conflicting commands.
4. Rent the second region and confirm delete, redefine, manual clear and manual snapshot save are rejected.
5. Create or retain a pending rent journal operation in a disposable test world and confirm mutation is rejected until reconciliation reaches a terminal state.
6. Redefine an unrented region so its old spawn lies outside the new bounds; confirm the invalid spawn is not retained.
7. Redefine a region while its spawn remains inside the new bounds; confirm the spawn is retained.

## 3. Snapshot invalidation on delete and redefine

1. Save a recognizable snapshot for an unrented region.
2. Delete the region.
3. Recreate a region with the same name and bounds.
4. Confirm `/regions reset <name>` reports that no active snapshot exists until a new snapshot is saved.
5. Repeat with redefine and confirm the previous snapshot was archived.
6. Inspect `simpleserverutilities/region_snapshots/archive` and confirm primary and backup generations are moved out of the active snapshot folder.

## 4. Bounded version 2 capture

1. Create a large but permitted region containing air, ordinary blocks, containers and signs.
2. Run `/regions save <name>`.
3. Confirm the command schedules a job instead of freezing until the complete region is scanned.
4. Watch `/ssu jobs` and confirm progress advances over multiple ticks.
5. Confirm the completion message reports the number of stored non-air blocks.
6. Confirm a gzip `.ssusnap` file is present and no `.tmp` file remains.
7. Save the region again and confirm a `.ssusnap.bak` generation is created.

## 5. Legacy snapshot migration

1. In a disposable dev3.4 world, create a version 1 `.json` region snapshot.
2. Upgrade to dev4 without saving a new snapshot.
3. Reset the region and confirm the legacy snapshot is restored correctly.
4. Save the same region in dev4.
5. Confirm the version 2 `.ssusnap` becomes authoritative and the old version 1 primary/backup files move to the archive folder.
6. Corrupt a copy of the version 2 primary while leaving a valid version 2 backup.
7. Reset and confirm the backup is validated, loaded and copied back to the primary path.
8. Corrupt both version 2 generations while leaving an older version 1 file; confirm SSU refuses the reset rather than silently restoring stale version 1 data.

## 6. Block entities

1. Place a chest with named items, a sign with text and other common block entities inside the test region.
2. Save the snapshot.
3. Change or remove their contents and states.
4. Reset the region.
5. Confirm block states restore.
6. Confirm chest contents and sign data restore where the Minecraft 26.2 block-entity API supports the captured SNBT.
7. Check the log for the one-time compatibility warning; if present, record which block-entity type did not restore.

## 7. Destructive reset checkpoints

Perform these tests only in a disposable copied world.

1. Start a reset on a sufficiently large region and cancel it after clearing has begun.
2. Confirm delete, redefine, save and clear are blocked with an unresolved-reset message.
3. Run reset again and let it complete.
4. Confirm the safety state is removed and normal region mutations work again.
5. Repeat by stopping the server during the clear or restore phase.
6. Restart and confirm the running checkpoint is marked interrupted and unsafe mutations remain blocked.
7. Complete a fresh reset and confirm old interrupted/failed/cancelled records are marked recovered.
8. Inspect `simpleserverutilities/region_snapshot_jobs` to verify status, phase and operation counters.

## 8. Dashboard in-place refresh

1. Open each dashboard page and navigate to a non-zero list page where possible.
2. Enter unfinished text in payment, rank, permission and economy policy fields.
3. Trigger a server-side dashboard refresh without closing the screen.
4. Confirm the same page remains open.
5. Confirm valid pagination is retained or clamped only when the list became shorter.
6. Confirm draft input remains intact, especially the three economy percentage fields while the Economy page is open.
7. Confirm newly received balances, claims, regions and permissions are visible after refresh.

## 9. Persistent aerial-map cache

1. Explore several chunks and view them on minimap, world map and claim map.
2. Exit normally and confirm `.ssuatlas` files appear below `simpleserverutilities/map-cache-v2`.
3. Rejoin without visiting all previously explored chunks first.
4. Confirm cached terrain appears without requesting or force-loading server chunks.
5. Test chunks at X/Z values around `-33`, `-32`, `-1`, `0`, `31` and `32` and confirm no cache collisions occur.
6. Confirm separate servers and singleplayer worlds do not share terrain.
7. Confirm dimensions use separate cache paths.

## 10. Resource-pack invalidation and write coalescing

1. Explore and refresh the same loaded chunk repeatedly.
2. Confirm the log diagnostics show bounded queued writes rather than an ever-growing queue.
3. Reload or change resource packs so the texture fingerprint changes.
4. Confirm newly captured tiles use the new fingerprint namespace and stale asynchronous reads do not reappear in memory.
5. Trigger a same-fingerprint renderer generation refresh and confirm incompatible files are invalidated before new writes are published.

## 11. Corruption and disk cap

1. Copy a tile and deliberately corrupt its contents.
2. Rejoin and request that tile.
3. Confirm the bad file is renamed with `.broken-<timestamp>` and the client continues without a crash.
4. Set `aerialMapCacheMiB` to a small allowed value such as 64 MiB in the client config.
5. Populate enough disposable cache data to exceed the cap.
6. Confirm oldest valid and archived-corrupt cache artifacts are pruned until usage returns under the configured limit.
7. Confirm normal shutdown drains pending writes on a best-effort basis and leaves no recurring `.tmp` files.

## 12. Performance and regression soak

1. Travel through new terrain for at least fifteen minutes while snapshots or other bounded jobs run on the server.
2. Open and close all maps and the dashboard repeatedly.
3. Check `/ssu core status`, `/ssu jobs` and debug logs for storage tasks, queued writes, tile hits/misses and capture time.
4. Confirm no unbounded memory growth, long synchronous snapshot freeze, repeated cache write storm or stuck region lock is observed.
5. Re-test claims, renting, refunds, homes, warps, permissions and ordinary region protection before promoting dev4 to the stable baseline.
