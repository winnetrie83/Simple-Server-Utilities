# SSU 1.9.0-dev3.24.1 — focused test checklist

## NPC overhead labels
1. Spawn or approach an SSU NPC and target it with the crosshair.
2. Confirm no second vanilla/custom-name label appears while targeted.
3. Confirm SSU Role + Name (and optional Faction) remain visible according to Name visible.
4. Confirm labels are approximately twice the previous dev3.24 size.
5. Test NPC scale 0.5, 1.0, 2.0 and another authored value; confirm text size and vertical spacing scale with the NPC.

## Remote custom skin
1. Configure texture source URL with a direct HTTPS 64x64 PNG, including the reported minecraftskins.com test URL.
2. Save the NPC and allow several seconds for the async download/sync.
3. Confirm the mannequin receives the custom skin.
4. Temporarily use an invalid/unreachable URL; confirm gameplay remains stable and server logs a bounded warning.
5. Restore a valid URL or wait 30+ seconds after a transient failure; confirm the loader can retry instead of remaining permanently cached as failed.

## Regression
- Local PNG NPC skins still load.
- NPC interaction/dialogue/shop behavior still uses the authored SSU display name.
- Entity Insight still excludes SSU NPCs.
