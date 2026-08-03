# SSU 1.8.0-dev18.0 testing

## Compatibility and startup

1. Back up the world and install the same dev18.0 build on client and server.
2. Start a dedicated server with Java 25 and confirm protocol 65 connects correctly.
3. Confirm existing claims, homes, warps, ranks, regions, shops, quests, minigames, dungeons and dimensions load unchanged.
4. Confirm legacy commands still execute and produce the same server-side results as their GUI equivalents.

## Claims and homes

1. Open Claims & Homes and verify Homes is shown there, not in Travel.
2. Create a home, overwrite it at a new position, teleport and delete it with the two-step confirmation.
3. Start a delayed home teleport and cancel it from the GUI.
4. Verify an ordinary player cannot use `/claims tp` and sees no player claim-teleport button.
5. Verify an administrator with claim bypass can search all player claims, teleport and delete with confirmation.

## Travel

1. Teleport to spawn and a warp.
2. As an administrator, create/move/delete a warp and set/clear spawn.
3. Cancel delayed spawn and warp teleports.

## Ranks and permissions

1. Create, rename and set a default rank.
2. Assign/reset a known player and confirm personal overrides remain intact.
3. Delete a non-protected rank and confirm affected players fall back safely.
4. Use Permission Editor → Check for an online player and compare the effective value with the command result.

## Regions

1. Set selection points from current position and exact coordinates.
2. Fill a small selection and verify the job result notification.
3. Save a snapshot, modify blocks and reset.
4. Test clear, redefine and delete confirmations on disposable regions.
5. Test rental add-time, pause/resume and global renting pause.
6. Confirm region locks, size limits and active jobs prevent unsafe operations.

## Utility Mining and maintenance

1. Change Treecapitator/Veinminer defaults and every custom/disabled list.
2. Confirm malformed values and identifiers are rejected clearly.
3. Change/reset border colors and refresh borders.
4. Refresh holograms and NPCs; move a hologram to the administrator position.
5. Change NPC-shop buy-back retention.
6. Verify reload is blocked while a long-running job is active.
7. Reload after enabling/disabling modules and confirm the current state is respected.

## Minigames and dungeons

1. Add and set an online player's minigame score through the lobby.
2. Verify offline/invalid players and invalid numbers produce clear feedback.
3. Advance an administrator's active dungeon run from the lobby.
