# SSU 1.9.0-dev3.26.2.1 — Compile hotfix test plan

1. Run the normal Java/Gradle build with Java 25.
2. Confirm the six previous `NpcManager.syncAll()` missing-symbol errors are gone.
3. Launch a test world and open an NPC linked to a quest.
4. Confirm quest-link edits refresh the NPC marker/dialogue immediately.
5. Confirm NPC admin/editor delete/save flows still refresh runtime NPCs normally.

No network or persistence migration is expected.
