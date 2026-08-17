# Runtime test checklist — SSU 1.9.0-dev3.40.3

## NPC Manager tool opening
1. Give yourself the SSU NPC Tool.
2. Stand in the world with SSU GUI Scale at 60%, then right-click the air once.
3. Verify exactly one NPC Manager is visible. There must be no old/unscaled buttons, rows, or second manager behind it.
4. Close the manager and reopen it several times. Verify no screen layers accumulate.
5. Repeat at 80% and 100%.
6. Verify Placements/Templates/Spawning, Search, paging and row actions still work.
7. Verify opening the manager does not pause an integrated singleplayer world.
8. Right-click blocks/entities with the NPC Tool and verify the interaction does not accidentally open a second manager.

## Regression
- Dashboard remains four columns from dev3.40.2.
- Reduced SSU GUI scaling remains centered and input hitboxes match.
- Network protocol remains 118; NPC schema 19; Ability Library schema 1.
