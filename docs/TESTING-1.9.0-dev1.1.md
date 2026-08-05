# SSU 1.9.0-dev1.1 test checklist

Back up the test world first. Client and dedicated server must use the exact same build and network protocol 71.

## Build and migration

1. Run `gradlew.bat clean build` with Java 25 and Gradle 9.2.1.
2. Start a copy of the dev1 test world and confirm existing Spleef definitions load as Minigame definition schema 3.
3. Confirm unrelated claims, regions, mail, economy, permissions and NPC data remain unchanged.

## Dedicated Spleef editor

1. Open the existing Spleef definition and visit all five tabs.
2. Confirm every editable field has a visible name and explanatory text.
3. Confirm Spleef does not expose generic team count, late join or raw victory-mode fields.
4. Use **Use my position** for lobby, spectator and player spawns. Reopen the editor and confirm coordinates are stored and displayed with short decimal values.
5. Confirm a Selection Tool-managed region ID is visible but locked.
6. Confirm Player spawns are numbered automatically and the editor shows whether enough spawns exist for Maximum players.

## Reward packages and Mail

1. On Participation reward, select an empty reward slot and click an inventory stack. Confirm the exact stack, count and components appear without removing the administrator's real item.
2. Add several different reward stacks, replace one slot and right-click one slot to remove it.
3. Enter a formatted Economy amount such as `€ 12,50` and save/reopen.
4. Add a direct action such as `grant_permission` with `permission=ssu.example.reward; value=true`.
5. Configure a different Winner reward.
6. Complete a match as loser and winner. Confirm participation and winner mails explain their origin, identify the match/arena, and contain the expected item/money attachments.
7. Claim the attachments and confirm money enters the player account only when claimed.
8. Confirm the direct permission/key was applied immediately and is listed in the mail body.
9. Force a controlled Mail delivery failure on a test copy, then restore Mail and confirm cleanup retries without duplicating the direct action or mail.

## Spectator containment

1. Set the spectator point close to the arena and save successfully.
2. Try to save a spectator point far outside the region; confirm validation rejects it.
3. Become eliminated, fly more than 24 blocks beyond the arena footprint or change dimension, and confirm SSU returns the spectator to the configured point.
4. Confirm active players are still eliminated by death, leaving the footprint/dimension or falling below the configured elimination depth.

## Recovery and reset regression

1. Disconnect and reconnect during countdown, active play, spectator mode and post-game cleanup.
2. Restart the server during an active match and during arena reset.
3. Confirm original inventory/state restoration still occurs before reward processing.
4. Confirm the arena snapshot restores every broken floor block and no temporary Spleef tool or floor drop survives.
