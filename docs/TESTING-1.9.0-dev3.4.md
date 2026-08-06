# SSU 1.9.0-dev3.4 — Manual Test Checklist

Use the same dev3.4 build on the dedicated server and every client. Network protocol is 89.

## 1. Build and startup

1. Build with Java 25 using `gradlew.bat clean build`.
2. Start a dedicated NeoForge 26.2 server with an existing dev3.3.3 world.
3. Confirm existing regions load without JSON errors and are rewritten safely with region schema 5 after a save.
4. Confirm old region protection, rental, members/managers, spawn, messages and permission overrides remain unchanged.

## 2. Damage indicators

1. Open **Settings > Combat** and cycle through all six styles:
   - Floating
   - Hearts
   - Compact
   - Pop
   - Burst
   - Drop
2. Cause both damage and healing for each style.
3. Confirm Pop has no exclamation mark and Burst has no square brackets.
4. Confirm Drop originates tightly from the affected entity, briefly pops upward, then falls downward.
5. Confirm damage remains red, healing remains green and the normal enable/disable and permission controls still work.

## 3. Region Tool — current-region editing

1. Equip a bound SSU Region Tool and stand inside an existing editable region.
2. Right-click a block and right-click into the air in separate tests.
3. Confirm both actions open **Region Setup Tool** directly in edit mode for the region at the player's position.
4. Verify all tabs:
   - General
   - Protection
   - Rent & access
   - Scheduled reset
5. Change and save priority, border visibility, welcome/leave messages and region spawn.
6. Toggle every protection flag and verify the corresponding protection behavior.
7. Open **Context permission overrides** and confirm it targets the current region.
8. Add/remove an online manager and member.
9. Confirm redefine and delete require their second confirmation click and respect rental, minigame and job locks.

## 4. Region Tool — creation workflow

1. Stand outside every region and right-click the Region Tool.
2. Confirm the setup screen explains that no region is active and offers point controls.
3. Set point 1 and point 2 by left-clicking blocks with the tool, or use the current-position buttons.
4. Right-click again and create a named region from the selection.
5. Configure protection, rental and scheduled-reset settings before saving.
6. Submit deliberately invalid rent text, invalid percentages and a duplicate region name.
7. Confirm failed validation does not leave a partially created region behind.
8. Confirm the legacy **Create server region** action also routes into the full Region Setup Tool.

## 5. Snapshot scheduled resets

1. Edit a region, open **Scheduled reset**, choose `SNAPSHOT` and capture a snapshot.
2. Modify several blocks after the capture.
3. Enable scheduled reset with a short test interval such as `10s`.
4. With **Wait until empty: ON**, remain inside past the deadline and confirm no reset starts.
5. Leave the region and confirm the reset starts immediately afterward.
6. Confirm the original snapshot is restored and Next/Last reset status updates.
7. Test **Reset now** and confirm it uses the last saved source/settings.
8. Restart the server before the next deadline and confirm the schedule persists.

## 6. Weighted block-preset resets

1. Put several block items in the normal 36-slot player inventory. Also test water and lava buckets.
2. Choose `PRESET` and click inventory slots to add up to six entries.
3. Assign percentages whose total is below 100%; confirm the displayed remainder becomes Air.
4. Save, alter the region and use **Reset now**.
5. Confirm the whole region is filled according to the weighted preset and no source inventory items are consumed.
6. Confirm duplicate slots, empty slots, non-block items, zero/over-100 percentages and totals above 100% are rejected.
7. Test containers in the region and confirm the preset reset does not create unsafe container-drop duplication.
8. Confirm regions above the one-million-block reset safety limit are rejected cleanly.

## 7. Safety and conflicts

1. Attempt reset while another snapshot/world-edit job locks the region; confirm it is rejected.
2. Attempt reset while a rental recovery journal entry is pending; confirm it is rejected.
3. Attempt scheduled/manual reset on a minigame-owned arena region; confirm the minigame system retains ownership.
4. Confirm only one reset can run for a region at a time.
5. Stop the server during a reset and verify normal SSU job/snapshot recovery behavior after restart.
