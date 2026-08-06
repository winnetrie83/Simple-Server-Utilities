# SSU 1.9.0-dev3.2 manual test checklist

## Weekly progression settings

1. Open each implemented minigame editor and enter **Progression & integration**.
2. Verify **Weekly challenges** can be turned ON/OFF and all six values can be edited:
   - matches required and matches XP;
   - wins required and wins XP;
   - impact required and impact XP.
3. Save, restart the dedicated server, reopen the definition and verify every value persisted.
4. Complete matches around each threshold and verify the configured XP is awarded exactly once per weekly category.
5. Turn weekly challenges OFF and verify neither weekly progress nor weekly XP changes for that minigame.
6. Verify the Minigame Profile shows the selected minigame's configured thresholds and rewards.

## Preparation flow

1. Start a match and verify there is no Ready/Unready button, no ready counter and no extra ready wait.
2. Verify players can join the preparing match until it becomes RUNNING or reaches maximum players.
3. Verify the match starts automatically when preparation time expires, provided minimum players and role composition remain valid.
4. Test preparation times of `0`, `5`, `10`, `11` and `60` seconds.
5. For the final ten seconds, verify one large number and one sound are produced per second, without duplicate numbers or sounds.
6. Verify `GO!` appears once when RUNNING begins.

## Important messages

1. End a CTF/Domination match by having the complete opposing team leave.
2. Verify the title/subtitle remains readable for approximately five seconds and the same reason is retained in chat.
3. Repeat for countdown cancellation, time-limit draw/overtime expiry, administrator finish and normal objective victory.
4. Verify cleanup does not immediately erase a cancellation reason.

## In-match U menu

1. Outside a match, press `U` and verify the normal SSU dashboard opens.
2. During COUNTDOWN and RUNNING, press `U` and verify the detailed match overview opens instead.
3. Verify phase/time, team scores, roster, roles, K/D/A, objectives, current status and rule summary are accurate for Spleef, CTF and Domination.
4. Test the overview as a living player, eliminated spectator and pending-respawn player.
5. Press **Leave match**, cancel the confirmation, then confirm it. Verify inventory, effects, gamemode, teleport/recovery and team state are restored safely.
6. During POST_GAME, verify the screen explains that automatic safe return is in progress rather than restoring the pre-match state over committed rewards.

## Profile and mining colours

1. Open Minigame Profile at multiple GUI scales and verify **Victory effect** is fully visible above the selector and selected-effect text does not overlap.
2. Cycle Treecapitator and Veinminer outline colours and verify this exact order:
   White, Light Gray, Gray, Black, Brown, Red, Orange, Yellow, Lime, Green, Cyan, Light Blue, Blue, Purple, Magenta, Pink.
3. Restart the client/server and verify both selections persist.
4. Load a dev3.1 preference file with an old outline RGB value and verify it migrates to the nearest fixed colour.
