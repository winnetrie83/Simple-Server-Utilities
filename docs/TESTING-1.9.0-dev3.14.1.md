# SSU 1.9.0-dev3.14.1 runtime checklist

## Jail task mining regression
1. Create/use a Jail Task Area overlapping a valid Mine.
2. Give the prisoner `ssu.mines.use` and the Mine-specific permission.
3. Configure a required block that exists in the Mine.
4. Mine one required block.
   - The block must disappear and remain air until the Mine's normal configured reset occurs.
   - No vanilla/custom item drop may be produced by the Jail task break.
   - Jail task progress must increase exactly once.
   - Mine current/lifetime mined statistics must increase exactly once.
5. Rapidly click the same block position while the break is being processed; it must not count multiple times.
6. Remove either Mine permission and verify the task block is denied and remains intact.
7. Verify normal non-jailed Mine mining/reset behavior remains unchanged.

## Compatibility
- Network protocol remains 98.
- Mine schema remains 3.
- Jail definition schema remains 2.
- Moderation/Jail sentence schema remains 2.
