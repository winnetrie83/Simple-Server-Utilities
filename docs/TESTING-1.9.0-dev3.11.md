# SSU 1.9.0-dev3.11 runtime test checklist

Build basis: `1.9.0-dev3.10.1`  
Target: Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25  
Network protocol: `96`  
Changed persistence: Server Operations schema `3`; Mine definition schema `1`

Run a clean local compile first:

```bat
gradlew.bat clean compileJava
```

Test on a disposable dedicated server/world copy before using reset, profile import or other destructive administration actions.

## 1. Mail feedback wrapping

- Open Mail at a resolution/GUI scale where the previous green notice overflowed.
- Trigger a long success notice (for example clearing several read messages).
- Confirm the notice wraps inside the bottom area and never leaves the panel/screen.
- Confirm paging and bottom buttons remain clickable and are not covered by the notice.

## 2. Shared rich-text colour swatches

Check Mail Compose, Support ticket/reply editor, Floating Hologram text and rank-display rich text:

- Confirm there is no old `Color` dropdown.
- Confirm all 16 Minecraft colours are immediately visible as compact swatches.
- Hover every swatch and confirm the tooltip is the colour name rendered in that same colour.
- Select text and apply colour, bold, italic, underline and strikethrough.
- Confirm formatting survives save/reopen and server round-trip where applicable.

## 3. Floating Hologram editor

- Confirm the editor is visibly around 20% smaller than dev3.10.1 and remains usable at common GUI scales.
- Confirm X/Y/Z fields are compact and show two decimal places.
- Confirm Scale, Range, Image W/H, Score Rows and Refresh Sec fields are substantially shorter.
- Confirm Background uses the compact palette and the redundant free-form hex box is gone.
- Create/edit TEXT and SCOREBOARD holograms and confirm all compact fields still save the correct values.

## 4. Kits

As admin:
- Open Kit Administration; confirm the compact layout and clear field labels.
- Select a kit and verify its contents appear as actual item icons with vanilla hover tooltips.
- Open Edit contents and confirm all nine ghost slots, 27 inventory slots and nine hotbar slots have visible grid backgrounds.
- Left-click an inventory stack into a ghost slot: the whole stack should be copied without consuming the admin inventory.
- Right-click an inventory stack into the same compatible ghost slot: quantity should increase by one up to the normal stack limit.
- With an empty cursor, right-click a ghost stack to remove one and left-click to clear it.
- Confirm status feedback is readable and never renders behind inventory/items.
- Press Back and confirm it returns to the same Kit Administration workflow rather than closing everything.

As player:
- Confirm Player Kits is around 25% smaller.
- Confirm item contents render as real icons with vanilla tooltips.
- Confirm no hidden/overlapping button exists behind `Claim kit` and click targets do not leak through.

## 5. Support & Reports

As player:
- Confirm the main Support screen is around 25% smaller and only contains ticket overview/conversation controls, not the old Category/Edit-message draft controls.
- Press Create ticket and confirm a separate screen opens with category selection and rich-text description editing.
- Create a Player report and verify the known-player picker is required.
- Open an existing ticket, use Reply, and confirm a new rich-text thread message is appended.
- Close a ticket: a reason of at least three characters must be required.
- Confirm the close reason appears in the persisted conversation/history.

As admin/staff:
- Open Server Operations -> Reports and reply with rich text.
- Test Assign, Resolve, Reopen and Close; Close must require a reason.
- Reopen a previously closed ticket and confirm it is no longer eligible for the old close timestamp while its historical close system message remains visible.
- Change `Closed keep h`, refresh/restart and confirm the setting persists.
- With a disposable test state, use a short retention value and confirm CLOSED tickets are purged once expired even when unread.

Migration/restart:
- Start from a dev3.10.x schema-2 threaded ticket state and confirm it normalizes to schema 3 without losing messages/status/assignee/unread state.

## 6. Wallet & Transactions

- Confirm the wallet/transactions panel is around 20% smaller.
- Confirm Player and Amount have visible labels.
- Confirm Search is roughly one-third of the old width and still filters transactions.
- Press the player-picker button beside Player; confirm known permission/economy/online identities are fetched server-side with working search/paging, selection fills the player field, and payment/filtering uses that selection.
- Open transaction Details for rows at the top/middle/bottom and confirm the details pane is centered in the free area and never runs through the row buttons.
- Trigger a page load and confirm `Loading page...` sits below the top-right utility buttons.

## 7. Profile

- Confirm Profile is around 20% smaller.
- Confirm Choose title is to the right of `Selected title: ...` with enough spacing for long title names.
- Confirm the redundant Minimap line and grey title explanation are gone.
- Change title and confirm the compact layout refreshes correctly.

## 8. Dedicated Mines — phase 1

Administration/setup:
- Confirm `Mine administration` appears in Admin Center and `Mines` appears for players with access.
- Give the Mine Setup Tool. Left-click block A for corner 1 and right-click block B for corner 2; switch dimensions mid-selection and confirm stale opposite-dimension points are cleared.
- Create a mine, apply the tool selection, set Spawn here and Exit here, then save/restart and confirm all data persists under the Mine definition schema 1 storage.
- Verify an over-4,000,000-block selection is rejected safely.
- Configure a weighted palette and manually Reset now. Confirm the reset runs as a bounded SSU job instead of replacing the entire volume in one tick.
- Confirm player mining increments mined/remaining progress.
- Confirm a timed reset occurs at the configured interval and a percentage reset occurs once the mined threshold is reached.
- Confirm countdown warnings occur at the expected milestones.
- With `Empty only`, confirm automatic/manual reset is deferred while a player remains inside.
- With player movement enabled, confirm occupants are moved to Exit (or configured spawn/fallback) before reset.

Permissions/player use:
- Test global `ssu.mines.use` and per-mine `ssu.mines.<id>.use` on a non-op player.
- Confirm unauthorized breaking is cancelled and non-admin block placement in a mine is blocked.
- Confirm an authorized player can teleport to a mine with a configured spawn and sees real block icons/tooltips plus remaining/reset information.

Known phase-1 limits to verify are *not* accidentally advertised/assumed as implemented:
- no dedicated custom/no-drop + XP/Fortune/Silk rule editor yet;
- palette authoring is currently compact block-ID + weight input with visual item/block previews, not a full inventory-slot picker yet;
- integrated mine hologram creation and richer mine-specific statistics remain follow-up work.

## 9. Configuration profiles / lifecycle

- Export a configuration profile and confirm Mine definitions and Server Operations closed-ticket retention are included.
- Import on a disposable copy and confirm both restore correctly after the safety profile is created.
- Stop/restart the dedicated server and confirm Mine definitions, ticket retention and all polished GUIs still load normally.
- Confirm client and server reject a mismatched pre-dev3.11 protocol and work when both use protocol 96.
