# SSU 1.9.0-dev2.5 test checklist

1. Build with Java 25 using `gradlew.bat clean build`; use the exact same protocol-79 jar on client and server.
2. Hold the Minigame Setup Tool and verify large colored flag previews appear for lobby, spectator, each team/player spawn and every Domination linked respawn.
3. Create new CTF and Domination arenas and confirm only one Red and one Blue base spawn are generated.
4. Add two optional spawns for a team and confirm repeated starts/deaths use the configured alternatives.
5. For every Domination node, use **Set Domination node spawn** and confirm the orange labeled preview moves to the selected block.
6. Capture one node for Blue, die near that node as Blue and confirm the linked node spawn is used.
7. Die near a neutral or Red-owned node as Blue and confirm that node spawn is ignored.
8. Move a node and confirm its linked respawn moves by the same offset until explicitly reset.
9. Save a verified arena snapshot and confirm reset does not include the client-side setup preview flags.
