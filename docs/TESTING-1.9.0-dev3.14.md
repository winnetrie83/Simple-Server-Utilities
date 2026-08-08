# SSU 1.9.0-dev3.14 runtime checklist

Target: Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25.

## 1. Build / protocol

- Build both client and dedicated server from the same dev3.14 source.
- Confirm mod version is `1.9.0-dev3.14` and protocol is `98`.
- Confirm a mismatched older client is rejected normally.

## 2. Mine -> Region nesting

- Create a Region large enough to contain a Mine.
- From Admin Tools obtain **Mine Setup Tool**; there must be no redundant Setup Tool button in Mine Administration.
- Create a Mine named `prison`; confirm its default permission becomes `ssu.mines.use.prison` while typing/saving.
- Select Mine bounds fully inside the Region and apply **Set Mine Bounds**; confirm the read-only containing Region is shown.
- Try applying bounds partly/fully outside every Region; confirm the server rejects them with clear feedback.
- Resize/remove the containing Region so the Mine is no longer contained; confirm that invalid Mine does not become usable/resettable/teleportable until its bounds again fit a Region.
- Confirm a Mine can be geometrically nested inside a Jail that is itself inside the same Region.

## 3. Mine Administration GUI / hologram

- Confirm **New mine** does not overlap the title.
- Confirm status/feedback text stays inside the panel and wraps.
- Confirm palette item previews do not sit underneath buttons and still show vanilla item tooltips.
- Place/reposition a status hologram with **Hologram here**.
- Use **Remove holo**; confirm the generated hologram disappears immediately, remains gone after relog/restart, and GUI status changes to OFF.

## 4. Jail parent auto-detection

- From Admin Tools obtain **Jail Setup Tool**; there must be no redundant Setup Tool button in Jail Administration.
- Confirm there is no editable Parent selector.
- Create a Jail, select bounds fully inside a Region, apply **Set Jail Bounds** and confirm the containing Region is derived automatically.
- Attempt Jail bounds outside all Regions and confirm rejection.
- If two Regions contain the same Jail, confirm selection is deterministic (smallest containing Region, then name tie-break).

## 5. Jail/Task Area borders

- Select an existing bounded Jail in Jail Administration.
- Confirm a red/pink 3D Jail border appears for that admin only.
- Configure a Task Area and confirm its orange 3D border is distinguishable from Jail bounds.
- Resize the Jail/Task Area and confirm borders refresh to the new geometry.
- Close Jail Administration and confirm the Jail editor border layer disappears.
- Confirm normal players do not see these editor borders.

## 6. Solitude cell management

- Confirm **Cell radius** no longer appears anywhere in Jail Administration/Punishment.
- Add at least three cell spawnpoints.
- Cycle/select individual cells and confirm dimension + XYZ are shown.
- Move only Cell 2 and confirm Cell 1/3 remain unchanged.
- Delete only Cell 2 and confirm the remaining cells stay valid.
- Jail a TIME_ONLY prisoner; confirm the assigned cell cannot be moved/deleted while in use.
- Confirm the prisoner can move normally inside the physically built cell, cannot interact/use commands, and is returned if somehow moved outside the overall Jail bounds.

## 7. Punishment time labels

- Open **Configure punishment** and verify explicit units are shown: `Time sentence (hours)`, `Task deadline (hours)`, `Share period (days)`.
- Confirm the values still serialize to the same intended seconds/days internally (e.g. 168 task hours = seven days).

## 8. Jail task + nested Mine permissions

Prepare a task block inside a Mine that overlaps the Jail Task Area.

- Player has neither Mine permission: task mining must be denied.
- Grant only `ssu.mines.use`: still denied.
- Grant only `ssu.mines.use.prison`: still denied because global permission is missing.
- Grant both `ssu.mines.use` and `ssu.mines.use.prison`: required task block can now be mined and task progress increments.
- Confirm the jailed player still cannot use commands, teleport, open normal SSU pages, interact normally or exploit operator/admin status.
- Confirm blocks not required by the task and blocks outside Task Area remain blocked.

## 9. Admin prisoner overview

- Jail multiple players using Choice, Task and Time/Solitude modes where possible.
- Open **Jail Administration -> Prisoners**.
- Confirm each active prisoner shows name, online/offline, Jail, mode/path, start, remaining/deadline, reason and relevant task/cell/buyout data.
- Check Online and Path filters, paging, Refresh and selection behaviour.
- **Teleport to prisoner** works only for online jailed players.
- **Open punishment** opens that player's moderation details.
- **Release** removes the player from the active overview after refresh and restores normal state according to existing release rules.

## 10. Restart/migration

- Start dev3.14 on a copy of a dev3.13 world.
- Existing schema-1 Jail definitions must load without `cellRadius` problems and normalize to Jail schema 2.
- Existing Mine definitions must load as Mine schema 3 and retain any explicitly stored non-empty permission key; newly created Mines use the new permission convention.
- Restart again and verify containing Region IDs, cell keys/coordinates and Mine hologram removal persist.
