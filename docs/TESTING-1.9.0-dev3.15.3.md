# SSU 1.9.0-dev3.15.3 runtime checklist

## Jail task + Mine overlap

1. Create a Jail Task Area containing an enabled Mine whose palette has at least one required and one non-required block type.
2. Give the prisoner `ssu.mines.use` and the Mine-specific `ssu.mines.use.<mine-id>` permission.
3. Start a task punishment requiring only a subset of the Mine block types.
4. Mine a required block: it must stay AIR, create no physical drop, increment Mine mined statistics, and increment Jail task progress exactly once.
5. Mine a non-required block: it must stay AIR, create no physical drop, increment Mine mined statistics, and **not** change Jail task progress.
6. Finish the quota for a required block and mine more of that same block before completing the whole sentence: those blocks must still stay AIR, while the requirement counter remains capped.
7. Remove either the global or Mine-specific permission and verify Mine blocks are denied.
8. Verify non-required blocks outside an overlapping Mine remain protected by the requirement-only Jail Task Area behavior.
9. Allow the Mine to reset normally and confirm removed blocks return only through the Mine reset system.
