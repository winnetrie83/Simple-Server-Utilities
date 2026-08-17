# SSU 1.9.0-dev3.41.3 test checklist

## Startup / baseline
- Compile with the normal Minecraft 26.2 / NeoForge / Java 25 toolchain.
- Start an existing copied world with the normal module configuration.
- Open Player Dashboard, Admin Dashboard and Module Settings; confirm normal layout and module status.

## Effective-state boundaries
- Disable Claims while Permissions stays ON. Permissions must remain active and claim event/protection work must stop.
- Disable Economy while Auction House is configured ON. Auction House must show BLOCKED administratively and disappear from the player Dashboard.
- Re-enable Economy; Auction House must become active again without changing its configured preference.
- Run Regions with Economy OFF. Ordinary region protection/editing must remain available while rentals/economy operations remain unavailable.
- Run Teleport with Claims and Regions OFF. Teleport must remain usable without those optional checks.

## Stale entry points
- Open a feature GUI, then disable/block that feature from another admin/session where practical. A stale serverbound action must be ignored/rejected rather than mutate unloaded feature state.
- Try a legacy command for an inactive feature. It must report that the feature is disabled/blocked instead of executing.
- For NPC shop commands, disable Economy or NPC Shops and confirm shop mutations are rejected.

## Runtime/data preservation
- Create representative data (claim/region/NPC or another persistent feature), switch the module ON -> OFF -> ON, and confirm the data returns unchanged.
- Restart the server while a dependent module is configured ON but BLOCKED; startup must remain clean and the configured preference must survive.

## Economy provider boundary
- With Economy active, grant a Content/Achievement/Quest money reward and confirm the digital wallet changes once.
- With Economy inactive, a money reward path must fail closed rather than modifying `EconomyManager` behind the module state.

## Regression / cleanup
- Confirm active Arcane Missiles still uses the working dev3.40.6.1 runtime effect. The removed dev3.40.8 custom-VFX experiment was never registered in this baseline.
- Confirm Utility Mining still works normally when enabled, and performs no world-hook work when the module is effectively inactive.
