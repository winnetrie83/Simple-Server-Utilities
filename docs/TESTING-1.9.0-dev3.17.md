# SSU 1.9.0-dev3.17 runtime checklist

Use the same `1.9.0-dev3.17` build on client and dedicated server.

## 1. King of the Hill PvP

1. Create/use a KOTH arena whose managed Region has normal PvP disabled.
2. Start a real two-team KOTH match.
3. Verify Red can damage Blue and Blue can damage Red while the match is running.
4. Set `Friendly fire: No`; verify teammates cannot damage one another while enemies still can.
5. Set `Friendly fire: Yes`; verify teammates can damage one another according to the KOTH rule.
6. End/leave the match and verify the Region's ordinary PvP protection applies again.
7. Open General, Hill rules and Arena/setup editor tabs at common GUI scales; verify no title/helper/field-label overlap and that all numeric/time fields are readable.

## 2. Region Tool separation

1. Obtain `Region Tool` from Admin Tools.
2. Verify two sequential left-clicks set Region Point 1/Point 2 and right-click opens Region setup.
3. Verify Region setup still supports Region creation/bounds, Protection, Access & rent, Auto reset and Browse.
4. Verify the old generic build/fill/portable-selection editing page is no longer exposed from the Region setup tabs.

## 3. World Edit Tool selection and basic edits

1. Obtain `World Edit Tool` from Admin Tools; verify it is unavailable when the Regions/admin-edit module is disabled or the player lacks the required selection/edit permissions.
2. First left-click sets Point 1; second left-click sets Point 2; the next left-click starts a new Point 1. Right-click should open World Edit only after both points exist.
3. Fill a small selection with one block, then with several weighted blocks.
4. Add more than 6 fill entries and verify the palette remains usable/paged. Test a total below 100% and confirm the remainder becomes air.
5. Test Fill water, Fill lava and Clear to air.
6. Confirm large-but-valid operations run as scheduled jobs rather than freezing the server tick.

## 4. Clipboard and history

1. Copy a selection and paste it at Point 1 elsewhere.
2. Cut a selection; verify source is cleared and clipboard can be pasted.
3. Use Undo and verify the previous full area is restored; use Redo and verify the edit returns.
4. Perform several edits and verify multiple undo/redo steps work up to the bounded history depth.
5. Repeat with chests or other supported block entities containing items; verify full-snapshot operations used by transforms/history preserve their stored data.
6. Logout/restart as appropriate and verify the in-memory undo history is intentionally session-only.

## 5. Replace

1. In Replace, switch inventory authoring between SOURCE and TARGET.
2. Add multiple source block types and multiple weighted replacement targets.
3. Verify targets must total exactly 100%.
4. Run Replace and verify only source types are changed; unrelated blocks remain untouched.
5. Undo and Redo the replacement.

## 6. Transform / move

1. Test Rotate left, Rotate right and Rotate 180.
2. Test Mirror east/west and north/south.
3. Test vertical flip.
4. Move a selection with positive and negative X/Y/Z offsets.
5. Verify selection bounds follow the resulting structure and the previous footprint is cleared when relocation is intended.
6. Verify Undo restores both world contents and the useful selection bounds.

## 7. Full snapshot integration

1. Save the current World Edit selection as a portable full snapshot.
2. Refresh/list it in the Snapshots tab.
3. Preview it with the existing ghost preview system; cancel without world changes.
4. Preview again and confirm/place it; verify the placed result is undoable.
5. Load a stored snapshot directly at Point 1 and verify supported block entities/entities are retained.

## 8. Mine Setup Tool controls

1. Obtain the Mine Setup Tool.
2. First left-click sets Point 1; second left-click sets Point 2.
3. Verify right-click does not modify Point 2 and only opens Mine Administration.
4. After a complete selection, left-click again and verify a fresh Point 1 starts.

## 9. Block Party editor

1. Open Block Party > Rounds and verify no manual comma-separated block-ID field exists.
2. Add blocks from inventory/hotbar; verify they appear as real icons, inventory is not consumed and vanilla tooltips work.
3. Select a palette slot and replace it; right-click a slot to remove only that entry.
4. Verify duplicate block IDs are rejected and a maximum of 16 blocks is enforced.
5. Verify saving with fewer than 2 blocks is rejected.
6. Verify labels clearly identify Initial round, Minimum round, Speedup/round, Drop duration, Tile size and Elimination depth, with seconds/blocks where applicable.
7. Save and run Block Party to verify the authored palette is the one used by the floor logic.

## 10. Regression

- CTF/Domination/Spleef combat and Region PvP behavior remain unchanged.
- Existing Regions, Region reset schedules, portable snapshots and Minigame definitions load normally.
- Existing KOTH STATIC/ROTATING behavior, HUD/dome and arena rotation still work.
- No admin inventory item is consumed by World Edit or Block Party ghost authoring.
