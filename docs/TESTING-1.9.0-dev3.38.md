# SSU 1.9.0-dev3.38 test checklist

## Independent SSU GUI scale

1. Open SSU Dashboard with Minecraft GUI Scale unchanged. Confirm `Settings -> Interface` shows `SSU GUI scale: 100%`.
2. At 100%, compare against dev3.37: panel positions/layout/size should be unchanged.
3. Select Smaller and verify 90%, 80%, 70% and 60% each shrink the complete SSU screen around its centre without changing the internal layout/column arrangement.
4. Close/reopen Minecraft and confirm the chosen SSU percentage persists.
5. Open a vanilla inventory/options screen after closing SSU and confirm Minecraft's own GUI size has not changed.
6. At 60% and 80%, verify mouse alignment on:
   - normal Buttons
   - EditBox text fields / caret placement
   - dropdowns and custom row hitboxes
   - NPC editor tabs
   - Dashboard module tiles
   - item catalogue pickers
7. Verify scroll hit testing at reduced scale in Auction House, Mail compose recipient dropdown, Property Settings dropdown and registry/achievement item pickers.
8. Verify drag/click/release behavior on Claim Map and World Map at reduced scale.
9. Open container-backed SSU screens (mail compose, NPC loadout, kit editor, auction sell, player inventory admin) and verify visible inventory slots can still be clicked at their rendered positions.
10. Verify tooltips appear next to the scaled cursor target and are scaled with the SSU screen.
11. Check screens that use scissor/clipping/scroll regions for clipping aligned with the scaled content.
12. Switch scale while the Dashboard remains open, then immediately use buttons/text fields to confirm input mapping updates in the same frame.
13. Click the `SSU GUI scale: xx%` value and confirm it resets to 100%.
14. Confirm Smaller is disabled at 60% and Larger is disabled at 100%.

## Regression
- Community statistics + Web API endpoints from dev3.37 still function.
- NPC/Boss editors from dev3.36/3.37 open and save normally.
- Minecraft global GUI Scale in Video Settings is not modified by SSU.
