# SSU 1.9.0-dev3.2.1 test checklist

Base: `1.9.0-dev3.2`

## 1. Local build

Run with Java 25:

```bat
gradlew.bat clean build
```

Confirm that the unresolved-method errors are gone from:

- `TeleportPolicy.java`
- `MinigameEvents.java`
- `MinigameManager.java`
- `MinigameCommands.java`

## 2. Score administration

During a running minigame:

- use the administrator score-add action/command;
- use the administrator score-set action/command;
- verify that the target player's score changes correctly;
- verify that score changes outside a running match fail safely.

## 3. Match membership and teleport policy

- Verify that a player participating in a match cannot use protected normal teleport routes.
- Verify that players outside a match retain their normal teleport permissions.
- Verify that opening the in-match overview with `U` still works.

## 4. Damage, death and respawn

- Verify that minigame damage handling still cancels invalid outside damage.
- Verify death/elimination in Spleef and other elimination modes.
- Verify eliminated players become spectators and are moved to the spectator spawn.
- Verify respawning does not return an eliminated player to normal play.

## 5. Disconnect and reconnect

- Disconnect during preparation and during a running match.
- Reconnect within the configured grace period and verify the participant is restored correctly.
- Reconnect after grace expiry and verify safe spectator/recovery behaviour.
- Verify a permanent disconnect can still eliminate or withdraw the player as required by the game type.

## 6. Ready-removal regression

- Confirm that no Ready/Unready button or ready counter has returned.
- Confirm that preparation time alone starts the match.
- Confirm that the final `10` through `1` countdown and `GO!` still work.
