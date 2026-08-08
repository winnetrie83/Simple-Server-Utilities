# SSU 1.9.0-dev3.12 — Mines completion runtime checklist

Test on a real Minecraft 26.2 client + dedicated NeoForge 26.2.0.7-beta server with the exact same dev3.12 JAR on both sides.

## 1. Upgrade / persistence

- Start with at least one Mine created in dev3.11/dev3.11.1.
- Confirm it loads without data loss and is normalized from Mine schema 1 to schema 2.
- Confirm bounds, permission, spawn/exit, reset interval, mined threshold, palette and current mined count are preserved.
- Restart the server after editing rules/palette/hologram settings and confirm all new schema-2 fields persist.

## 2. Mine creation and Setup Tool

- Create a new Mine from Mine Administration.
- Give/use the Mine Setup Tool: left-click corner 1, right-click a block for corner 2, then apply the selection.
- Set Spawn here and Exit here and verify Teleport enters the correct dimension/position.
- Verify a mine over the configured 4,000,000-block safety limit is rejected.

## 3. Inventory-backed block palette

- Open Edit palette.
- Verify all 9 ghost slots, weight fields, player inventory and hotbar grids render.
- Click several vanilla and modded block items in inventory and verify they are copied to the selected ghost slot without consuming the real item.
- Verify selecting an occupied palette slot and clicking another block replaces that slot.
- Right-click a palette slot and verify it clears.
- Save different weights and reopen the editor to confirm persistence.
- Attempt to save no usable entries and confirm it is safely rejected.

## 4. Reset generation

- Run Reset now and verify the bounded SSU job replaces the full mine using the configured weighted palette.
- Confirm a large mine resets over multiple ticks rather than freezing the server in one tick.
- Verify current-cycle mined progress returns to 0 only after a successful completed reset job.

## 5. Timed reset warnings

- Configure a short interval and warning countdown.
- Test ACTIONBAR, CHAT and TITLE warning modes.
- Toggle Warning sound ON/OFF and verify the sound follows the setting.
- Confirm the configured warning milestones do not spam every tick.

## 6. Mined-threshold reset

- Configure a mined threshold, for example 20%, and a 10-second warning.
- Mine through the threshold.
- Verify reaching the threshold starts the configured warning countdown instead of immediately resetting.
- Confirm the threshold reset starts after the countdown and resets current-cycle progress.

## 7. Safe reset with players inside

- With Empty only ON, stand inside the mine when reset becomes due. Confirm reset is delayed and retries safely after about 30 seconds.
- With Empty only OFF and Move players ON, stand inside and confirm players are moved to Exit, or Spawn/fallback if no Exit is set, before reset starts.
- Verify players in another dimension are unaffected.

## 8. Normal drops / enchantment rules

- NORMAL + Fortune ON + Silk Touch ON: verify vanilla drops behave normally.
- Disable Fortune and mine with a Fortune tool: verify mine drops are calculated without Fortune while the player's actual held tool/enchantments remain unchanged.
- Disable Silk Touch and repeat with Silk Touch.
- Test both disabled together.

## 9. NONE drops and XP multiplier

- Set Drop mode to None and verify mine blocks create no item drops.
- Test XP multiplier 0, 0.5, 1 and >1 on blocks that normally award XP.
- Verify XP multiplier remains independent from the item-drop mode.

## 10. CUSTOM drops

- Set Drop mode to Custom.
- Copy real items into multiple custom drop ghost slots.
- Configure independent Min, Max and Chance % values.
- Verify 0% never drops and 100% always applies.
- Verify min/max random counts are respected and counts larger than an item's normal stack size are safely split into multiple ItemEntities.
- Reopen Mining rules and confirm entries persist.

## 11. Generated mine status hologram

- Enable Status hologram with no custom position and verify it chooses Spawn or mine-centre fallback.
- Use Hologram here and verify the custom position is used.
- Change hologram Range and confirm it persists.
- Verify the display updates live for remaining %, mined progress and next-reset status.
- Disable the Custom Statistics module and confirm Mine tokens still resolve instead of showing raw `{mine:...}` text.
- Disable Status hologram and confirm the generated mine hologram is removed.
- Delete the mine and confirm its generated hologram is also removed.

## 12. Mine statistics

- Mine blocks with at least two players and several block types.
- Verify Current cycle, Lifetime blocks, Mine teleports, Reset totals, Manual/Auto reset totals, Last mined and Last reset.
- Verify Top miners counts/names.
- Verify Most mined blocks use real block icons and vanilla hover tooltips.
- Restart server and confirm lifetime statistics persist.

## 13. Permissions

- Verify `ssu.mines.use` controls player access to the Mines module.
- Verify the per-mine key such as `ssu.mines.<id>.use` hides/blocks a mine for a player without access.
- Verify admins retain intended bypass/admin behaviour.
- Attempt to break blocks inside a denied mine and confirm the break is cancelled with actionbar feedback.
- Verify non-admin placement inside a Mine remains blocked.

## 14. Catalogue paging

- Create more than 10 mines and confirm Mine Administration can page through all of them.
- As a player with access to more than 8 mines, confirm player Mines paging reaches every visible mine.
- Select a mine on a later page, refresh/reopen and verify selection/page handling remains sane.

## 15. Audit / configuration profiles

- Perform admin create/save/palette/rules/reset/hologram/delete actions and inspect the Staff Audit Log for the expected Mines administration entries.
- Confirm automatic resets record their server-side audit entry.
- Export/import a configuration profile and confirm `mines/definitions` remains included and schema-2 Mine settings survive the round trip.

## 16. General regression

- Verify Regions continue to work independently; Mines must not turn into special Region flags or break Region reset behaviour.
- Verify Hologram Manager still resolves normal statistics tokens and normal holograms correctly.
- Verify minigames/dungeons/claims are unaffected by Mine breaking/drop hooks outside configured Mine bounds.
