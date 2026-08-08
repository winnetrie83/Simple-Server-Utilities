# SSU 1.9.0-dev3.22 focused runtime test checklist

Target: Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25  
Network protocol: `104`  
Persistence schemas: unchanged from dev3.21.1

Run first:

```bat
gradlew.bat compileJava > compile-report.txt 2>&1
```

## 1. Achievement browser size and rewards
- Open Player Dashboard -> Achievements and confirm the browser is about 25% smaller than dev3.21.1 without clipped controls.
- Earn an item-reward achievement such as 10 diamonds. Confirm Reward shows the diamond icon plus `10 × Diamond`, not internal `give_item` syntax.
- Confirm money, permission, temporary permission, cosmetic, title and claim-chunk rewards are human-readable.
- Confirm the vanilla challenge-complete advancement sound plays exactly once when the achievement completes.
- Restart after completion and confirm the sound/reward is not replayed simply because the player rejoins.

## 2. Admin Achievement UX
- Open Admin Dashboard -> Achievements and confirm the browser/editor are about 25% smaller.
- Use `Player: <name>` and search/select an online player and a previously known offline player; confirm no UUID must be typed and the selected progress loads immediately.
- Create an achievement using General / Objectives / Rewards. Collapse and reopen each section.
- Choose icon/reward items from the item picker; choose block/item/entity targets and registry tags from their searchable pickers.
- Confirm common objective types use friendly labels such as `Break blocks` and progress choices such as `Count each event`, rather than raw event constants.
- Confirm Advanced filters still permit metadata/custom-event use when explicitly opened.
- Edit an older exact-ItemStack reward and save the achievement without changing that reward; confirm its exact custom stack data remains intact.

## 3. Hidden/comparison/regression
- Confirm hidden achievements remain invisible to viewers who have not earned them and visible to the earning player.
- Confirm All / Earned / Unearned filters still work.
- Confirm Reset and Reset + reward retain their distinct behavior.
- Confirm clickable achievement chat announcements still open the correct comparison.

## 4. Hologram Editor
- Confirm the old stray text behind/next to the color swatches is gone.
- Confirm coordinates are shown as `X: [field]  Y: [field]  Z: [field]` with narrower fields and no standalone `Coordinates` label.
- Confirm Source, Scoreboard Objective, rich-text buttons and color palette do not overlap.
- Save TEXT, IMAGE and SCOREBOARD holograms and confirm behavior is unchanged.

## 5. Mail Composer
- Confirm the window is roughly 19-20% shorter.
- Confirm inventory is higher, hotbar is close to it, and Back/Send Mail are higher without overlapping inventory/hotbar slots.
- Send mail with text, money and item attachments and confirm normal behavior remains intact.

## 6. Server Operations labels
- Backups: confirm visible labels identify Backup name, Automatic backups, interval in minutes and Keep backups; hover relevant controls for explanatory text.
- Worlds: select each dimension and confirm labels identify Center X, Center Z, Border size, Pregeneration radius, Chunks/tick and Auto-pause MSPT.
- Confirm `Save pregen settings` replaces the vague `Save throttle` label.

## 7. Simple Health verdict
- Open Server Operations -> Health. Confirm the first prominent information is one colored verdict: GREAT / GOOD / NEUTRAL / BAD / VERY BAD plus a short plain-language explanation.
- Confirm technical TPS/MSPT/heap/cache/module details are hidden by default and appear only after `Technical details: SHOW`.
- Apply temporary server load and refresh; confirm the verdict can degrade as TPS/MSPT/p95/heap thresholds are crossed and returns when load subsides.
