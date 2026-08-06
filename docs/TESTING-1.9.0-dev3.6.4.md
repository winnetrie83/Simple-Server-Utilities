# SSU 1.9.0-dev3.6.4 — Objective Capture Sound Test Checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Use the exact same dev3.6.4 build on client and server.
- Confirm the handshake still reports network protocol 91.

## Capture the Flag

1. Start a two-team CTF match.
2. Take the enemy flag. Confirm the existing flag-theft sounds still play as before.
3. Return the enemy flag to your own base and score.
4. Confirm every online member of the scoring team hears the vanilla Ponder goat horn.
5. Confirm every online member of the opposing team hears `block.beacon.deactivate`, not a goat horn.
6. Confirm no unrelated players outside the match receive either sound.
7. Score the winning capture and confirm the capture sound plays once before/alongside normal match-finish presentation, without duplication.

## Domination

1. Capture a neutral base completely.
2. Confirm every member of the capturing team hears Ponder.
3. Confirm every opposing player hears the beacon-deactivation loss cue even though the base was neutral.
4. Capture a base owned by the opposing team and repeat the checks.
5. Confirm claim-start and defense/interruption sounds remain unchanged.
6. Confirm the completed-capture sound triggers only once when ownership actually changes, not while the capture delay is counting down.

## Regression

- Verify CTF score, scoreboard, flag return and score-limit finish still work.
- Verify Domination ownership, marker replacement, objective credit and score generation still work.
- Test with one player disconnected: only currently online match members should receive packets.
