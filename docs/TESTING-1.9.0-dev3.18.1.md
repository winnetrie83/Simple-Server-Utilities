# SSU 1.9.0-dev3.18.1 runtime checklist

Use the same `1.9.0-dev3.18.1` build on client and dedicated server.

## Player Dashboard icons
- Open the player dashboard at normal GUI scale.
- Confirm **Support** uses `ticket.png`.
- Confirm **Kits** uses `kits.png`.
- Confirm **Mines** uses `mines.png`.
- Confirm all three icons remain centered, crisp and fully inside the existing 54x54 dashboard tile artwork.
- Repeat at at least one different GUI scale.

## Entity Insight GUI-first behavior
- Open the player settings GUI and confirm Entity Insight can still be enabled/disabled there.
- Confirm **Show health**, **Insight range (0-32)** and **Max entities (1-50)** remain editable from the GUI.
- Confirm `/ssu settings entity_insight` is no longer registered as a mutation command branch.
- Confirm the default-granted `ssu.entity_insight.use` permission still gates rendering/use.

## Regression
- Verify the existing `/ssu settings` commands for older settings still behave as before.
- Verify player Dashboard Support/Kits/Mines tiles still open their original pages.
