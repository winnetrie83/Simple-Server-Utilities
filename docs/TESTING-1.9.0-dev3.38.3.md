# SSU 1.9.0-dev3.38.3 test checklist

## Duplicate backdrop regression

1. Set SSU GUI Scale to 60% and open the Dashboard. There must be exactly one uniform fullscreen dim from edge to edge.
2. Confirm there is **no** second centered translucent/dark rectangle matching the scaled viewport.
3. Repeat with Dashboard -> Settings at 60%, then at 70%, 80% and 90%. Only the actual SSU panel/content should resize.
4. Return to 100% and confirm the original pre-dev3.38 appearance is unchanged.
5. Verify the Dashboard avatar still scales and remains centered inside its portrait frame.

## Screens that previously relied on vanilla background

6. Open Mail Compose at 60% and confirm it still has one edge-to-edge dim backdrop, with no scaled rectangle.
7. Open World Map and Claim Map at 60% and confirm their background treatment remains readable and edge-to-edge.
8. Open Claim Tax Delete at 60% and confirm its panel has one uniform fullscreen dim behind it.
9. Open Region Snapshot Preview and World Edit compact overlay and confirm they remain intentionally transparent/readable over the world.

## Input regression

10. At 60%, re-test dashboard buttons, Settings controls, text fields, scrolling, tooltips, map interaction and a container-backed SSU screen. Mouse hitboxes must still line up with the scaled visuals.

## Compatibility
- Minecraft global GUI Scale remains untouched.
- Network protocol 116; NPC schema 17; placement schema 4; Community Statistics schema 1.
