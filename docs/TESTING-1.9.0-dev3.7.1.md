# SSU 1.9.0-dev3.7.1 — Compile Hotfix Test Checklist

Base: `1.9.0-dev3.7`

## First gate — local compile

Run with Java 25:

```bat
gradlew.bat clean compileJava
```

Expected: the 22 dev3.7 compiler errors in the newly-added dimension/moderation/onboarding/spawn code are gone.

Then run:

```bat
gradlew.bat clean build
```

## Focused runtime checks

### Dimensions
- Open Admin Center -> Dimensions.
- Select Overworld, Nether, End and a loaded custom dimension and use Teleport.
- Confirm each teleport lands at a safe location and does not cross to the wrong dimension.

### Server spawn fallback
- Set Server Spawn in a non-Overworld dimension.
- Die with no valid personal bed/respawn anchor: expect SSU Server Spawn.
- Set a valid bed/anchor and die: expect the personal spawn to remain first priority.
- Clear SSU Server Spawn and die without a personal spawn: expect vanilla Overworld spawn as final fallback.

### Onboarding
- Test a fresh/reset player with Lobby Spawn configured and with Lobby Spawn cleared.
- Confirm the vanilla fallback is safe when Lobby Spawn is absent.
- Confirm welcome firework presentation and sound occur without affecting onboarding state.

### Moderation warning
- Send a warning to one online player.
- Confirm the Call horn is heard by the warned player and is not broadcast to nearby unrelated players.
- Confirm title/subtitle duration and rich-text reason still work.

### Freeze / player management / offline inventory
- Freeze/unfreeze a player and verify the player remains anchored correctly.
- Open online and offline inventory/ender chest admin screens and save changes.
- Reset/complete onboarding for online players from admin controls.

## Compatibility
- Network protocol remains `93`.
- No storage/schema migration is introduced by this hotfix.
- Client and server should still use the exact same dev3.7.1 build.
