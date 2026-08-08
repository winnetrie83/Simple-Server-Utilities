# SSU 1.9.0-dev3.23.1 — focused test checklist

## Build

1. Run `gradlew.bat clean build` (or at minimum `gradlew.bat compileJava`) with Java 25.
2. Client and server must both use dev3.23 because the network protocol is 105.

## Achievement icon picker

1. Open Achievement Administration and edit/create an achievement.
2. General > `Choose item…` must open an inventory-style catalogue with real icons, not a text-button list.
3. Scroll through several rows; filter by translated name and by registry ID.
4. Click an item: it should highlight while the picker stays open.
5. Cancel: the old icon must remain unchanged.
6. Reopen, select an item, press `Select item`: the editor must show the chosen icon ID.
7. Save the achievement, close/reopen it, and confirm the icon persisted.
8. Repeat with at least one registered modded item if another content mod is installed.

## Achievement item reward picker

1. Put normal, enchanted/custom-component and stacked items in the editing admin's inventory.
2. Add an Item reward and press `Choose inventory…`.
3. Confirm only the admin's own 36 inventory/hotbar slots are shown with icons, counts and tooltips.
4. Select a stack and cancel: the existing reward must remain unchanged.
5. Select a stack and confirm: the Reward editor must immediately show that item and its stack count.
6. Change Count independently, save and reopen the achievement.
7. Verify the reward item/components and edited Count remain correct.
8. Confirm the source inventory stack was never consumed, moved or changed.

## Server Operations labels

Check Activity, Scheduler and Chat at normal GUI scale and a smaller GUI scale. No configuration value should appear as an unexplained standalone number/text field. Verify:
- Activity: retention days, rollback player/UUID, hours and radius blocks.
- Scheduler: task name, action, schedule syntax/units and optional payload/message.
- Chat: slow/duplicate/flood seconds, max messages, caps %, minimum chars, blocked phrases, mute player, duration in minutes and reason.
- Tooltips must supplement rather than replace the visible labels.

## Region Tool input

1. Hold the SSU Region Tool.
2. Left-click block A: only Point 1 changes.
3. Right-click block B: only Point 2 changes; the Region GUI must NOT open.
4. Right-click the air: the Region GUI must open.
5. Repeat while targeting interactive blocks to ensure the Point 2 action is consumed.
6. Confirm the selection border and permission checks still work.

## Minimap frames

1. Existing player data from schema 12 should migrate to schema 13 with `Frame: CLASSIC`.
2. With Classic selected, verify the minimap looks/behaves as in dev3.22.
3. Select Rectangle + Textured: the supplied square frame must render and scale with minimap size.
4. Select Circle + Textured: the supplied round frame must render and terrain must not appear in the transparent square corners outside the circle.
5. Toggle North-up, player-up, claims, regions, markers and calendar while using both frames.
6. Test minimap sizes 64, 96, 128 and 256 and all four screen positions.
7. Switch repeatedly between Classic/Textured and Circle/Rectangle and reconnect; the setting must persist.

## Regression

- Achievement save/reopen and reward execution.
- World Edit Tool left block=P1, right block=P2, right-air=GUI remains unchanged.
- Server Operations backup/world/health pages remain usable.
- World Map / Claim Map / minimap terrain cache and markers remain functional.
