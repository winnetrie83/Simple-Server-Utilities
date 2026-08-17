# SSU 1.9.0-dev3.26.1 — NPC Dialogue condition editor hotfix

1. Open an NPC Dialogue Editor and open the Conditions page for a node.
2. Select a condition whose type is `any` and which does not contain exactly one child.
3. Press the `Type` button.
4. Confirm the editor skips the invalid direct `not` transition instead of showing a blocking error repeatedly.
5. Continue cycling until `quest_ready`, `quest_active`, `quest_available` and `quest_completed` can all be reached.
6. Confirm `Wrap NOT` still produces a valid `not` wrapper with exactly one child.
7. Validate and save the dialogue, then re-open it and confirm the condition is preserved.

Compatibility is unchanged from dev3.26: protocol 107; NPC dialogue schema 2; NPC definition schema 10; NPC placement schema 4.
