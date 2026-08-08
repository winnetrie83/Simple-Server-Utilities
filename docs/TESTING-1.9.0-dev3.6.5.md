# SSU 1.9.0-dev3.6.5 testing checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev3.6.5 jar on client and server; network protocol is 92.

## Capture HUD spacing

### Capture the Flag

1. Start taking an enemy flag.
2. Confirm the capture label and progress bar remain at their previous bottom-center position.
3. Confirm `Do not move, attack, use items, or take damage.` is rendered as a separate centered line above the capture label.
4. Confirm the two text lines do not overlap at normal, large and small GUI scales.
5. Interrupt the cast by moving, attacking, using an item or taking damage and confirm the cast HUD clears.

### Domination

1. Start claiming a base.
2. Confirm the claim label and progress bar remain unchanged.
3. Confirm the instruction line appears above the claim label without overlap.
4. Repeat at multiple GUI scales and window resolutions.
5. Complete and interrupt claims and confirm the HUD clears in both cases.

## Regression

- Confirm ordinary action-bar messages still use the vanilla action bar.
- Confirm CTF/Domination result sounds from dev3.6.4 still work for the correct teams.
- Confirm no instruction line remains visible after leaving or finishing a match.
