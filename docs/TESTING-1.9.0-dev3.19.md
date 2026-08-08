# SSU 1.9.0-dev3.19 runtime checklist

Use the same `1.9.0-dev3.19` build on client and dedicated server. Network protocol remains `101`.

## Rich text palettes
- Open Compose Mail and verify the 16 swatches are 2 rows of 8, with no overlap with the helper text.
- Open Create/Edit Floating Hologram and verify the inline text palette is 2 rows of 8 and each swatch is clearly readable at 14x14.
- Open any reusable Rich Text editor (for example support/template text) and verify 2 rows of 8.
- Open Rank Prefix Editor and verify 2 rows of 8.
- Hover Black in the shared palette and in the Hologram background palette. The word `Black` must be white/readable, while selecting Black must still apply black formatting/background color.

## World Edit item frames / hanging entities
- Build a small asymmetric wall selection with item frames on north/east/south/west faces; put visibly oriented items/maps in at least two frames.
- Capture it through the World Edit full-snapshot transform path and rotate right 90 degrees. Paste/confirm and verify every frame moved with the structure and is attached to the newly correct wall face.
- Repeat rotate left 90 and rotate 180.
- Repeat mirror X and mirror Z.
- Test a floor- or ceiling-mounted item frame with vertical flip if supported by the current vanilla entity placement.
- Include a painting to verify the generic HangingEntity Facing transform, not just item frames.
- Undo and Redo one transformed placement and verify frames survive with correct facing/content.

## NPC target name
- Spawn an SSU NPC with Role / Name / Faction label.
- Look away: only the SSU overhead label should be present.
- Put the crosshair directly on the NPC: Minecraft must not add a second vanilla copy of the NPC name.
- Verify normal non-SSU custom-named mobs retain normal vanilla nameplate behavior.
- Verify Entity Insight still excludes SSU NPCs and still works for ordinary living entities.
