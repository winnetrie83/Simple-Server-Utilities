# SSU 1.9.0-dev2.5.1 test checklist

1. Build with Java 25 using `gradlew.bat clean build`; use the same protocol-79 jar on client and server.
2. Open the Domination editor Nodes tab and verify the linked-respawn label renders without an `ACCENT` compile error.
3. In CTF, take the enemy flag, die, and confirm the configured physical banner appears at the death position while the carrier back-banner/glow disappear.
4. Right-click the dropped flag as a teammate of the defeated carrier and confirm it is picked up immediately without a cast.
5. Right-click that dropped flag as its original team and confirm it returns immediately to its base.
6. Take a flag, release sneak, then crouch and confirm the flag drops at the carrier position.
7. Pick a dropped flag up while already crouching and confirm it does not immediately drop again until sneak is released and pressed again.
8. Carry the enemy flag to base while your own flag is dropped and confirm no score is awarded; return your flag and confirm scoring becomes possible.
9. Finish/reload a match with a dropped flag and confirm no orphan banner remains in the arena.
