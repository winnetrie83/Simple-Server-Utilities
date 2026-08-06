# SSU 1.9.0-dev3.6.1 — Compile Hotfix Test Checklist

## Build

- Use Java 25.
- Run `gradlew.bat clean build`.
- Confirm `SsuMenuService.java` no longer reports an effectively-final lambda error.
- Confirm `RegionSelectionSnapshotManager.java` no longer reports invalid `operationCount()` overrides.

## Claim role Permission Editor

- Open Admin Center → Permissions → Claim roles.
- Select Owner, Co-owner, Member, Visitor and Outside/none.
- Search targets and permissions.
- Change a claim-role permission and refresh the screen.
- Confirm the selected role remains stable and no server error occurs.

## Region selection snapshots

- Capture a selection snapshot containing blocks, a container inventory and at least one structural entity.
- Load it into ghost preview.
- Move, rotate and mirror the preview.
- Confirm placement and verify all blocks, inventory contents and structural entities restore correctly.
- Cancel another preview and verify the world remains unchanged.

## Compatibility

- Network protocol remains 91.
- Client and server must still use the exact same build.
- No storage or data migration is expected from dev3.6.
