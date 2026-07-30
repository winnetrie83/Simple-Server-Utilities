# SSU Mail System 1.5.0-dev2

This release refines the first durable mailbox implementation from 1.5.0-dev1.1. Existing mailbox IDs, attachment escrow, queue state and delivery receipts remain compatible.

## Sent Mail management

Players can delete one selected outgoing mail or use a double-confirmed **Clear sent** action. Deleting Sent Mail affects only the sender-side history; it never removes the recipient's copy or reverses a delivered item/money attachment.

The new integer permission `ssu.mail.sent_limit` controls how many newest outgoing records are retained. Its default is `20`, range is `0..100000`, and `0` disables Sent Mail retention while mail sending remains available.

Outgoing anti-spam accounting is stored separately in `outgoingSendHistory`. Clearing or capping Sent Mail therefore does not reset `ssu.mail.daily_send_limit` or `ssu.mail.send_cooldown`.

## Bulk Inbox clearing

**Clear inbox** removes only mail that is:

- currently visible;
- already read;
- free of unclaimed item or money attachments.

Unread mail, durable overflow-queue mail and mail with unclaimed attachments are preserved. After the clear operation, queued mail is promoted normally up to the player's visible inbox soft cap.

## Delivery status

A sender-side record now stores:

- when the recipient first opened the mail;
- when all item stacks were claimed;
- when the money attachment was claimed.

The Sent Mail list and detail panel expose these states. Status tracking begins when dev2 processes an action; old dev1 records have no historic open/claim timestamp to reconstruct.

## Recipient lookup and validation

The compose recipient field requests a bounded server-authoritative list of known players. Sources include the permission player index, durable mailbox names and online players. Exact matches and prefix matches are ranked first, followed by case-insensitive alphabetical ordering. Up to eight results are returned; six are visible in the side panel at one time.

The server still validates the typed name during Send. An unknown recipient is rejected before item serialization or money escrow. The composer stays open, every text field and attachment remains intact, and the error is shown in red.

## Compose input and locked slots

The composer uses a 300 x 248 container layout. It shows:

- nine mail attachment slots;
- all 27 normal inventory slots;
- the nine hotbar slots;
- a right-side player suggestion panel.

Unavailable attachment slots draw a temporary lock glyph. This drawing can later be replaced with a resource-pack texture without changing slot permissions.

The configured inventory key is intercepted by the composer. When a text field is focused, the event is delivered directly to that field so letters such as `E` and `Shift+E` are entered normally instead of closing the container. With no text field focused, the key is consumed and the composer remains open.

## Attachment-mail auto-delete preferences

Three personal preferences default to `false`:

- private/player attachment mail;
- server/system/recovery attachment mail;
- Auction House attachment mail.

A matching mail is removed only after all of its item and money attachments are claimed. Text-only mail is never removed by this option. These preferences are stored in player UI preference schema 2 and can be changed in **Settings > Mail** or by `/ssu settings mail`.

## Categorised personal settings

The dashboard Settings page now groups every currently persistent SSU player preference into:

- General;
- Minimap;
- World map;
- Borders, including clearing all personally selected region borders;
- Mail.

Categories for future modules such as Auction House, Treecapitator or Vein Miner should be added when those modules and their player settings exist. Module-specific claim/region property settings remain in their own property editors rather than being duplicated as global preferences.

## Storage migration

Mailbox schema is now 3:

- existing inbox and sent records are retained;
- a missing dev1 sent limit migrates to the default `20`;
- an explicit schema-3 sent limit of `0` remains valid;
- old sent timestamps seed the independent rolling send-history list when possible.

Player UI preferences migrate to schema 2. Existing minimap choices remain unchanged, world-map overlays preserve their former enabled behaviour, and all three new mail auto-delete settings remain off.

## Network compatibility

Network protocol is `21`. Client and server must use the same 1.5.0-dev2 build.
