# SSU Mail System — 1.5.0-dev1

## Scope

This development build adds the first complete player mailbox to Simple Server Utilities. It is designed as shared infrastructure for ordinary player mail and for future systems such as Auction House returns, sale proceeds, NPC rewards, quests and recovery deliveries.

## Player flow

Players can open Mail from the dashboard or with `/mail` when `ssu.mail.access` resolves to true.

The mailbox contains:

- Inbox: currently visible incoming mail.
- Sent: the player's outgoing-mail history.
- Compose: recipient, subject, message, optional money and up to nine item-stack slots.
- Attachment claims: items, money or all available attachments.
- Safe deletion: incoming mail cannot be deleted while it still has unclaimed attachments.

The compose container exposes all nine mail slots, the 27 main-inventory slots and the nine-slot hotbar. Only the number enabled by `ssu.mail.max_attachments` can accept items.

## Durable visible soft cap

Incoming delivery has no hard capacity limit. `ssu.mail.inbox_soft_cap` only determines how many messages are visible at once.

Example with a cap of 20:

1. The first 20 incoming messages are visible.
2. Further messages are saved as `QUEUED` in the player's mailbox JSON.
3. The player receives a mailbox-full alert.
4. When a visible message is deleted or expires, the oldest queued message is promoted.
5. Its retention timer starts only at promotion time.

Queued mail is therefore not an in-memory-only backlog and survives server restarts.

## Retention

`mailVisibleRetentionDays` in the common configuration defaults to 30 days.

- Only visible mail ages toward retention cleanup.
- Queued mail is retained indefinitely until promoted.
- Visible mail without unclaimed attachments is deleted after retention.
- Visible mail with unclaimed items or money is moved to the back of the durable queue rather than destroyed.
- Sent-mail history uses the same retention duration.

A bounded maintenance pass runs once per minute and processes at most 64 loaded mailbox records per pass.

## Permissions and defaults

| Key | Type | Default | Purpose |
|---|---:|---:|---|
| `ssu.mail.access` | boolean | true | Open and use the mailbox; may be denied until a quest unlocks it. |
| `ssu.mail.send` | boolean | true | Send player mail. |
| `ssu.mail.send.items` | boolean | true | Attach item stacks. |
| `ssu.mail.send.money` | boolean | true | Attach money. |
| `ssu.mail.max_attachments` | integer | 1 | Maximum item stacks per mail, clamped to 0–9. |
| `ssu.mail.inbox_soft_cap` | integer | 20 | Visible inbox capacity; extra mail remains queued. |
| `ssu.mail.daily_send_limit` | integer | 20 | Outgoing mail during the rolling previous 24 hours. Zero disables player sending. |
| `ssu.mail.send_cooldown` | integer | 5 | Seconds between outgoing player mails. |
| `ssu.mail.admin` | boolean | false | Reserved mail administration access. |

All keys can be managed through the existing permission commands and visual permission editor.

## Items and money

Item attachments are serialized with Minecraft's registry-aware `ItemStack.CODEC`, preserving stack components. The hard cap is nine stacks regardless of permission values.

Money uses exact economy minor units and a dedicated deterministic mail escrow account. Player money is transferred to escrow before delivery; claiming transfers it from escrow to the recipient with an idempotent mail-specific transaction key.

Item claims first simulate the 36 player storage slots. If all stacks do not fit, nothing is claimed. After a successful inventory plan, the mailbox is synchronously persisted; if that write fails, the inventory and claimed flags are restored.

## Auction House integration contract

`MailManager` exposes two server-side delivery paths:

- `deliverSystemMail(...)`: funds optional system money and delivers items/money.
- `deliverPreEscrowedMail(...)`: for modules that already journaled or escrowed the assets.

Both support `MailSource.AUCTION` and a caller-supplied correlation key. Delivery receipts remain in mailbox data even after the visible message is deleted, preventing the same auction event from being delivered twice.

Suggested future correlation keys:

- `auction:<auction-id>:expired-return`
- `auction:<auction-id>:seller-proceeds`
- `auction:<auction-id>:buyer-delivery`

## Storage

Mailbox files are stored per player below:

```text
<world>/simpleserverutilities/mail/mailboxes/<player-uuid>.json
```

Writes use the existing atomic JSON storage path with temporary files and backups. Mailbox schema 2 stores the player's last known inbox soft cap and durable delivery receipts.

## Known development-build limitation

The compose container returns unsent item stacks during a normal close. This first build does not persist an open, unsent draft. An abrupt process/server crash while items are sitting in the compose attachment slots therefore requires explicit runtime testing before the build should be treated as production-safe.
