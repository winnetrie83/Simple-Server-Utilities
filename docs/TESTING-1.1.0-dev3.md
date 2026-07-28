# SSU 1.1.0-dev3 test plan

Use a copy of a test world. Client and server must both run dev3 because the network protocol changed from 2 to 3.

## Build and startup

1. Use Java 25.
2. Run `gradlew.bat clean build`.
3. Confirm `build/libs/simpleserverutilities-1.1.0-dev3.jar` exists.
4. Place the same JAR on client and server.
5. Start the server and confirm there is no payload-version or data-loading error.
6. Confirm existing claims, regions, homes, warps and ranks are still present.

## Personal visibility

Test with two different player UUIDs.

1. Player A runs `/ssu borders claims on`.
2. Player B leaves claim borders off.
3. Confirm only A sees nearby claims.
4. Player B runs `/ssu borders regions on`.
5. Confirm B sees server regions but not claims.
6. Disconnect and reconnect both players.
7. Confirm each player's choices persisted independently.
8. Test aliases `/claims borders on|off` and `/regions borders on|off`.
9. Deny `ssu.borders.claims.view`; confirm the local claim toggle cannot bypass it.
10. Deny `ssu.borders.regions.view`; confirm the local region toggle cannot bypass it.

## Default categories

1. Stand near one of your own claims; confirm it is green.
2. Stand near another player's claim; confirm it is blue.
3. Stand near an admin/server region; confirm it is purple.
4. Complete a temporary region selection; confirm it is yellow.
5. Confirm internal shared chunk edges are absent from connected claims.
6. Confirm a translucent fill is visible inside claim volumes.
7. Move at least 40 blocks vertically; confirm claim geometry follows your height.
8. Confirm the visible claim volume extends about 64 blocks below and 64 blocks above the camera.

`hostile_territory` is not automatically assigned in dev3 and therefore has no normal gameplay test yet.

## Admin color configuration

1. Grant `ssu.visualization.admin` to an admin account.
2. Run `/ssu borders color list`.
3. Run `/ssu borders color set own_claim #00FF00`.
4. Confirm visible own-claim snapshots refresh to the new color.
5. Change `server_region` and `selection` separately.
6. Confirm an ordinary player cannot use color commands.
7. Run `/ssu borders color reset own_claim`.
8. Run `/ssu borders color resetall`.
9. Restart the server and confirm saved custom colors persist when not reset.

## Focus layers and cleanup

1. Enable both overview layers.
2. Run `/claims show <name>` and `/regions show <name>`.
3. Confirm focused geometry can coexist with automatic overview geometry and a selection.
4. Run `/claims hide` and confirm only claim focus disappears.
5. Run `/regions hide` and confirm only region focus disappears.
6. Run `/regions selection clear` and confirm only selection disappears.
7. Change dimensions and confirm geometry from the old dimension is not rendered.
8. Disconnect and join another world; confirm no stale client visualization remains.

## Batched storage

1. Make several rapid changes to the same claim and region.
2. Run `/ssu core status`; inspect queued, completed, coalesced, pending and retry-required counts.
3. Allow the queue to drain and confirm pending returns to zero.
4. Restart cleanly and confirm all changes persisted.
5. Inspect existing JSON directories and confirm file names/schema remain compatible with the previous version.
6. Delete a claim or region and confirm its stale per-record JSON file is removed after the queue drains.

## Region fill job

1. Make a small completed selection.
2. Run the existing selection fill command.
3. Confirm a job UUID is returned immediately rather than the server freezing until completion.
4. Run `/ssu core jobs list` and confirm progress is visible.
5. Let the job complete and confirm a completion message and expected block count.
6. Test a weighted block list.
7. Start a larger fill and monitor server tick smoothness.
8. Cancel it with `/ssu core jobs cancel <uuid>` and confirm only the processed portion remains changed.

## Clear and snapshot reset jobs

1. Save a region snapshot.
2. Modify the region.
3. Run `/regions reset <name>`.
4. Confirm a job is scheduled and restores blocks over multiple ticks.
5. Run `/regions clear <name>` and confirm it also runs as a job.
6. Try to start a second conflicting operation on the same region; confirm it is rejected.
7. Try to rent a region while its reset is active; confirm renting is refused.
8. Confirm `/ssu reload` is refused while a job is active.

## Restart/cancellation limitation

1. Only on a disposable test copy, stop the server during a large job.
2. Confirm the world contains the already processed partial result after restart.
3. Confirm SSU starts normally and no stale in-memory job remains.

This behavior is expected in dev3 because jobs are not yet persisted or rolled back.

## Regression checks

- Claim creation, unclaim, trust and flags.
- Region create, redefine, priority, member/owner and flags.
- Homes and warps.
- Safe delayed teleports.
- Claim and region protection.
- Redstone and piston protection from dev1.
- Existing rent timers and commands.
