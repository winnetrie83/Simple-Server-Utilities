# SSU 1.9.0-dev3.24 - focused test checklist

1. Open the player dashboard and confirm Achievements uses the new trophy icon.
2. Confirm Minigames uses the new game-controller icon.
3. Confirm a Cosmetics tile is visible with the new mask icon. Open it and verify it only shows a Coming Soon placeholder and performs no server action.
4. If admin, open Admin Center and confirm Achievement/Minigame tiles use the same new dedicated icons.
5. Enable the minimap and confirm the player marker uses the supplied arrow texture.
6. Test North Up ON while facing north/east/south/west and confirm arrow orientation follows player yaw.
7. Test North Up OFF and confirm the rotating map keeps the player arrow in the expected fixed orientation.
8. Test Classic Circle, Classic Rectangle, Textured Circle and Textured Rectangle; confirm the arrow stays centered.
9. Confirm the textured rectangle retains the dev3.23.3 map/frame sizing.
10. Regression-test dashboard navigation, Achievements and Minigames opening normally.
