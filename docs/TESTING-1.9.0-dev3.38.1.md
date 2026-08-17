# SSU 1.9.0-dev3.38.1 test checklist

## GUI scale hotfix

1. Set SSU GUI Scale to 60%. Open Dashboard. The dark background overlay must cover the entire game viewport edge-to-edge; only the actual SSU panel/content should shrink.
2. Repeat at 70%, 80%, 90% and 100%. There must never be a smaller dark rectangle whose edges follow the scaled GUI.
3. At 100%, backdrop darkness and Dashboard layout should match dev3.38/pre-hotfix behavior.
4. At 60%, verify the dashboard avatar sits inside the portrait frame opening and is scaled proportionally with the frame. It must not remain at the old 100% screen position.
5. Change 60% -> 80% -> 100% while Dashboard is open. The avatar must rebuild/reposition immediately with the rest of the GUI.
6. Move the mouse around the portrait at reduced scale and confirm player yaw/pitch still reacts naturally.
7. Open representative non-Dashboard screens (NPC Editor, Auction House, Mail, map/claim screens, container-backed editor) and confirm their full-screen dim layer remains edge-to-edge while their panels are scaled.
8. Re-run dev3.38 input checks: buttons, text fields, tooltips, scrolling, map interaction and container slots remain aligned.

## Regression
- Minecraft global GUI Scale remains unchanged.
- Community statistics/Web API from dev3.37 remains functional.
- NPC/Boss systems from dev3.36+ remain unchanged.
- Network protocol 116; NPC schema 17; placement schema 4; Community Statistics schema 1.
