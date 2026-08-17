# SSU 1.9.0-dev3.40.5.1 test checklist

This is deliberately a staged equipment test. Armor is not registered in this build.

1. Start Minecraft and verify the title screen is visible (no black window).
2. Load a world and verify existing SSU/NPC UI still opens normally.
3. Spawn/use a Player-model NPC with no loadout.
4. Give only a mainhand item and verify it renders.
5. Give only an offhand item and verify it renders.
6. Test sword, bow, crossbow and shield poses.
7. Test both Wide/Steve and Slim/Alex models.
8. Confirm NPC combat/pathfinding still behaves as in dev3.40.4.1.
9. Confirm NPC Tool right-click-in-air opens only one NPC Manager GUI.

Expected: armor remains gameplay-active server-side but is intentionally not rendered in this staging build. Once startup + held items are confirmed, armor can be reintroduced separately using the slot-based 26.x equipment model path.
