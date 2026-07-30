# Testing SSU 1.5.0-dev1

Use Java 25 and install the same dev1 JAR on client and server. Back up the world before testing.

## 1. Build and startup

```bat
gradlew.bat clean build
```

Confirm:

- compilation succeeds;
- the JAR is created in `build\libs`;
- client and dedicated/integrated server start without registration errors;
- an existing dev4 world loads with claims, regions, economy, permissions, homes, warps and spawn intact.

## 2. Basic mailbox access

1. Open the dashboard and select Mail.
2. Run `/mail`.
3. Set `ssu.mail.access=false` for a test player and verify both entry points show a locked/denied mailbox.
4. Restore access and verify the inbox opens.
5. Restart the server and verify access behaviour is unchanged.

## 3. Text and sent mail

1. Send a text-only mail to an online player.
2. Verify the recipient receives a live alert and the message appears unread.
3. Select it and verify it becomes read.
4. Verify the sender sees an independent Sent record.
5. Send to a known offline player, restart, then log that player in and verify delivery.
6. Verify self-mail and unknown-player mail are rejected.

## 4. Item attachments

1. With the default `ssu.mail.max_attachments=1`, confirm only one of the nine attachment slots is active.
2. Increase the permission to 9 and send nine different stacks.
3. Verify metadata/components, custom names, damage and stack counts survive delivery.
4. Close the composer without sending and confirm all items return to the inventory.
5. Fill the recipient's 36 storage slots and verify Claim Items refuses without changing the mailbox.
6. Free sufficient space, claim the items and verify they cannot be claimed twice.
7. Verify mail with unclaimed items cannot be deleted.

Also perform an explicit abrupt-close/crash test with disposable items because open compose drafts are not persisted in dev1.

## 5. Money attachments

1. Send €1,00 and verify the sender is debited exactly 100 minor units.
2. Verify the recipient does not receive the money before claiming it.
3. Claim once and verify the exact balance change.
4. Click Claim Money repeatedly and verify no duplicate credit.
5. Test insufficient funds, zero/negative/invalid input and `ssu.mail.send.money=false`.
6. Restart between delivery and claim, then repeat the claim test.

## 6. Outgoing limits

1. Set `ssu.mail.daily_send_limit=2` and send two mails.
2. Verify the third mail is refused during the rolling 24-hour window.
3. Set the limit to 0 and verify player mail sending is disabled.
4. Set `ssu.mail.send_cooldown=10`; verify immediate repeat sending is blocked and later succeeds.
5. Verify system/Auction-style delivery is not blocked by a recipient's outgoing limits.

## 7. Visible soft cap and queue

1. Set the recipient's `ssu.mail.inbox_soft_cap=2`.
2. Deliver five messages.
3. Verify exactly two are visible and three are reported as safely queued.
4. Restart the server and verify the same 2/3 state.
5. Delete one visible, attachment-free mail and verify the oldest queued message is promoted.
6. Verify the promoted message receives a fresh visible-retention start time.
7. Increase the cap and verify multiple queued messages are promoted in arrival order.
8. Lower the cap and verify excess visible mail returns safely to the queue.

## 8. Retention

For testing, temporarily set `mailVisibleRetentionDays=1` or adjust timestamps in a copied test world.

- attachment-free visible mail is cleaned up;
- queued mail does not expire;
- visible mail with unclaimed attachments returns to the back of the queue;
- sent-mail history is cleaned using the configured retention;
- maintenance continues after an integrated-server restart.

## 9. Auction delivery API preparation

From a temporary development hook, deliver twice using the same recipient, `MailSource.AUCTION` and correlation key. Verify only one message exists. Delete the message and invoke the same delivery again; verify the durable receipt still prevents redelivery.

## 10. Logs and files

Inspect:

```text
<world>/simpleserverutilities/mail/mailboxes/
```

Confirm JSON files remain readable after restart and no `.tmp` or broken-write files remain after normal operation. Capture `latest.log` for any exception, disconnect, desync or registration failure.
