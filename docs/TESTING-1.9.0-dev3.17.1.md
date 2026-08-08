# SSU 1.9.0-dev3.17.1 runtime checklist

Use the same `1.9.0-dev3.17.1` build on client and dedicated server.

## Compile hotfix
- Run `gradlew clean compileJava`.
- Open Admin Tools -> World Edit Tool.
- Verify validation/status feedback appears for invalid fill/replace/snapshot actions.
- Verify Copy/Cut/Paste/Undo/Redo and tabs still open normally.

No protocol/schema changes from dev3.17.
