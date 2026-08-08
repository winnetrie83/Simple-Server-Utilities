# SSU 1.9.0-dev3.20.1 runtime checklist

Use the same `1.9.0-dev3.20.1` build on client and dedicated server. Network protocol is `102`.

## World Edit input + compact overlay
- Obtain the **SSU World Edit Tool** from Admin Tools.
- Left-click a block: only Point 1 changes.
- Right-click a block: only Point 2 changes; the full editor must not open.
- Right-click in empty air: the full World Edit editor opens.
- Hold the World Edit Tool and press the configured **World Edit: compact tools** key (default `W`): a small transparent palette appears bottom-right with no screen blur/dim wash.
- Rebind the key in vanilla Controls and confirm the new binding works.
- Test ←/→/↑/↓/+Y/-Y one block at a time and confirm the selection border follows each move.
- Test rotate left/right/180, mirror X/Z and vertical flip from the compact palette; verify Undo/Redo from the full editor still restores state.
- Confirm Clipboard, Fill, Replace and Snapshots remain usable from the full GUI.

## Snapshot ghost preview
- Save a snapshot containing several visibly different blocks (stairs/slabs/logs/glass/foliage are useful), a chest/sign and at least one item frame/painting for placement verification.
- Select the snapshot and press Preview. The world behind the controls must remain sharp and undimmed.
- Confirm every non-air snapshot block is represented by its real block model/texture rather than a solid debug colour cube.
- Confirm glass/cutout/tinted blocks remain recognizable and the entire preview is visibly translucent/ghost-like.
- Move and rotate the preview with its compact controls, use Free mode to walk around it, then return with left-click.
- Cancel once and verify no world blocks changed.
- Preview again, Place, then confirm block entities/inventories and supported structural entities are restored by the normal snapshot placement path.
- Test a snapshot larger than 4096 block entries to confirm all chunks stream and the progress label reaches completion.

## World Edit GUI layout
- Open World Edit > Snapshots at GUI scales 2, 3 and 4 where practical.
- Verify the two explanatory text groups, Preview button, Load at Point 1 button and Selected label never overlap.

## Entity Insight
- Place an Armor Stand inside Entity Insight range: no Entity Insight name or `20/20 HP` may appear.
- Observe friendly, neutral and hostile mobs to ensure green/yellow/red behavior is unchanged.
- Hurt a normally non-hostile mob that flees; while it is moving away and not targeting a player, its Entity Insight label should become cyan.
- When it stops fleeing, verify the label automatically returns to its normal attitude color.
- If a mob targets a player while moving away, hostile/red must take priority over cyan.
