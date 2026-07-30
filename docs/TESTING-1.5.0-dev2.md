# Testing SSU 1.5.0-dev2

Back up the test world before installing this development build. Build with Java 25:

```bat
gradlew.bat clean build
```

Install the generated JAR from `build\libs` on both client and server. Both sides must use protocol 21.

## 1. Migration smoke test

1. Start a world previously used with the confirmed-working 1.5.0-dev1.1 build.
2. Verify claims, regions, economy, homes, warps, spawn, permissions and existing mail still load.
3. Verify existing inbox mail, queued mail, item attachments, money attachments and sent records remain present.
4. Restart once and verify the migrated mailbox/player-setting JSON loads again without warnings.

## 2. Individual and bulk deletion

1. Send at least three mails.
2. Open Sent Mail, select one and use **Delete sent mail**.
3. Verify only the sender-side record disappears and the recipient still has the delivered mail.
4. Press **Clear sent** once and verify it changes to a confirmation action.
5. Confirm within five seconds and verify all saved outgoing records disappear.
6. Verify the daily send limit and cooldown were not reset by deleting Sent Mail.
7. In Inbox, prepare one unread text mail, one read text mail and one read mail with an unclaimed attachment.
8. Double-confirm **Clear inbox**.
9. Verify only the read attachment-safe mail is removed. The unread mail and unclaimed attachment mail must remain.
10. Fill the visible inbox soft cap, create queued overflow mail and clear eligible visible mail. Verify queued mail promotes normally and is not bulk-deleted unseen.

## 3. Sent Mail retention permission

1. Set `ssu.mail.sent_limit` to `2` for the test player.
2. Send three mails and reopen Sent Mail.
3. Verify only the newest two sender-side records remain.
4. Verify all three recipient deliveries still exist.
5. Set the limit to `0`, send another mail and verify sending succeeds while Sent Mail retains no record.
6. Restore the desired permission value.

## 4. Open and claim status

1. Player A sends Player B a mail with one item stack and money.
2. Before B opens it, A should see **Not opened**, **Items pending** and **Money pending**.
3. B opens the mail. Refresh A's Sent Mail and verify an opened timestamp appears.
4. B claims only the item. Verify A sees the item claim timestamp while money remains pending.
5. B claims the money. Verify A sees both claim states.
6. Restart the server and verify these states persist.
7. Delete A's sent record and verify this does not affect B's mail or attachments.

## 5. Recipient suggestions and validation

1. Open Compose and leave the recipient blank. Verify a bounded alphabetical list of known players appears.
2. Type the beginning and then a middle fragment of a known name. Verify exact/prefix matches rank above contains matches.
3. Click a suggestion and verify it fills the recipient field.
4. Test an online player, a known offline player and a player known through an existing mailbox.
5. Enter a definitely nonexistent player name and fill subject, body, item and money fields.
6. Press Send. Verify the composer stays open, the red error is visible, no field resets, no item leaves its mail slot and no money enters escrow.
7. Correct only the recipient and resend. Verify exactly one mail is delivered.

## 6. Inventory key and locked slots

1. Focus Recipient, Subject and Message in turn and type lower-case and upper-case `E` using the configured inventory key.
2. Verify the character is entered and the composer does not close.
3. Focus the money field and verify the inventory key also does not close the screen; invalid characters may still be rejected later by money parsing.
4. Remove text-field focus and press the inventory key. Verify the composer remains open.
5. With `ssu.mail.max_attachments = 1`, verify the remaining eight slots visibly show a lock placeholder and reject item placement/shift-clicking.
6. Raise the permission and verify the correct additional slots unlock, up to the hard cap of nine.

## 7. Auto-delete by source

All three settings must initially be OFF.

1. Enable only **Private attachment mail**. Claim every attachment from a player mail and verify that mail is removed automatically.
2. Verify server and Auction House source mail remain after claims while their settings are OFF.
3. Repeat with only **Server attachment mail** enabled; SYSTEM and RECOVERY mail should auto-delete after full claim.
4. Repeat with only **Auction attachment mail** enabled.
5. For a mail containing both items and money, claim only one type and verify it remains. It may auto-delete only after both are claimed.
6. Verify text-only mail never auto-deletes through these settings.
7. Restart and verify all three choices persist independently.

## 8. Categorised Settings

1. Open the dashboard Settings page.
2. Verify General, Minimap, World map, Borders and Mail categories are present.
3. Check every current persistent player setting is available in the appropriate category.
4. Change each minimap/world-map overlay toggle and verify the corresponding map updates.
5. Change border visibility and verify claims/regions render accordingly.
6. Change mail auto-delete settings and verify they affect claims as described above.
7. Restart client/server and verify persistent settings remain.

## 9. Regression checks

- `/mail` and the dashboard Mail button both open correctly.
- Normal closing/Back returns all unsent item attachments.
- Item and money attachment claims remain idempotent.
- Inbox soft-cap overflow remains durable across restart.
- Existing system/pre-escrowed Auction House delivery APIs remain idempotent.
- Homes, warps, claims, regions, world map, minimap and teleport permissions still work.
