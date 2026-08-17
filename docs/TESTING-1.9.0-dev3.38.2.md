# SSU 1.9.0-dev3.38.2 test checklist

## Reduced-scale background cleanup

1. Set SSU GUI Scale to 60% and open Dashboard -> Settings. The world should have one uniform fullscreen dim from edge to edge.
2. There must be no second dark/translucent centered rectangle surrounding the scaled SSU panel.
3. Repeat at 70%, 80% and 90%. Only the actual SSU panel/content should change size.
4. Switch back to 100% and confirm the pre-existing full-size appearance remains unchanged.
5. Verify the dashboard avatar still scales and stays inside the portrait frame at 60-90%.
6. Open representative screens (NPC Editor, Auction House, Mail, map/claim screens and a container-backed editor) and confirm there is exactly one uniform fullscreen backdrop.
7. Re-test buttons, text fields, scrolling, tooltips, maps and container slots at 60% to ensure input remains aligned.

## Regression
- Minecraft global GUI Scale remains unchanged.
- Community statistics/Web API from dev3.37 remains functional.
- NPC/Boss systems remain unchanged.
- Network protocol 116; NPC schema 17; placement schema 4; Community Statistics schema 1.
