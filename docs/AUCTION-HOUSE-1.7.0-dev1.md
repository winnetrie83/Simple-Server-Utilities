# Auction House — SSU 1.7.0-dev1

## Scope

The Auction House is a separate Core 2.0 module built on SSU Economy, Mail, Permissions, Transactions and Storage. It is a fixed-price, partial-quantity marketplace rather than a timed bidding system.

## Entry and permissions

- `ssu.auction_house.access` — use the Auction House from any trusted server entry.
- `ssu.auction_house.dashboard` — show/open the dashboard tile.
- `ssu.auction_house.max_active` — maximum simultaneous active listings; built-in fallback is 5.
- `ssu.auction_house.admin` — edit the global sale tax, manage the item blacklist and use the administrator listing overview.
- `ssu.auction_house.*` — wildcard.

The dashboard path checks both `access` and `dashboard`. `AuctionHouseManager.openTrusted(ServerPlayer)` checks only `access`, making it the intended future NPC/server-side entry without granting custom clients a way to open their own session.

## Browsing

The left category column contains:

1. Weapons
2. Armor
3. Tools
4. Building Blocks
5. Plants
6. Seeds
7. Food
8. Enchants
9. Potions
10. Ores
11. Metals
12. Logs
13. Machines
14. Miscellaneous

Listings are classified from the actual item, block tags and conservative item/block-name heuristics. Search compares a lower-cased substring against the item's effective hover/display name and never searches registry IDs. Results can be sorted ascending/descending by name, quantity, unit price or expiry time.

Each row contains a real one-count representation of the listed stack, full normal item tooltip on hover, display name, formatted unit price, available quantity, seller and remaining duration. Eight rows are transferred per page; the result panel supports previous/next controls and mouse-wheel page navigation.

## Buying

1. Select another player's listing.
2. Press **Buy**.
3. Enter a quantity from 1 through the remaining listing quantity.
4. Review the exact formatted total.
5. Press **Buy now**.

The server re-resolves the listing, expiry, seller, quantity, item, current sale tax and buyer balance. It reserves the listing before payment. A successful committed purchase plays the note-block pling on the buyer client. The final purchase button locks while processing so a rapid double-click cannot submit the same intended purchase twice.

Purchased items are reconstructed from the stored item components, split using the item's real maximum stack size, grouped at no more than nine stacks per mail, and continued across as many system mails as necessary.

## Selling

The selling screen is a real container menu with one offer slot and the complete player inventory. The offer stack identifies the exact item and components to list. The available count includes every matching stack in the player's inventory.

The player enters:

- price per individual item;
- quantity to remove from all matching stacks;
- duration: 12, 24 or 48 hours.

On creation, the server checks the live active-listing limit and re-parses the configured SSU currency amount. Inventory extraction is snapshotted. If listing persistence fails, every touched slot is restored. The creation button also locks while the server is processing the request.

## Own listings, expiry and cancellation

**My Auctions** shows the player's active listings. Cancellation returns only the unsold quantity by Auction House system mail. Expired listings are processed by minute maintenance and returned in the same safe nine-stack mail batches.

## Seller proceeds and tax

The default sale tax is 5.0% and can be changed from 0% through 100% in 0.1% increments by `ssu.auction_house.admin`.

At sale time SSU records the exact historical tax rate and amount. The seller receives a pre-funded money mail containing:

- item and sold quantity;
- buyer;
- transaction date/time;
- gross value;
- tax rate and tax amount;
- net amount claimable from the mail.

Tax remains in the isolated Auction House clearing account.

## Administrator overview and blacklist (dev1.5)

Players with `ssu.auction_house.admin` receive two additional tabs:

- **Admin** — every active listing, with the normal category, search and sort controls. A selected listing can be blacklisted, cancelled by an administrator, or seized.
- **Blacklist** — all blocked base item identifiers, shown with the real item icon/name when the item is currently registered. Administrators can select any non-empty slot from the rendered player inventory, or enter a full item ID such as `minecraft:diamond`. Registered IDs receive a visual preview before submission and are validated again by the server. Existing entries can be selected and removed.

Administrator cancellation returns the complete unsold quantity to the seller through the normal idempotent, nine-stack mail batching. Both administrator cancellation and seizure require a written reason. The seller receives the administrator name and reason by system mail, and the same information is written to the server log. Seizure sends the item batches to the administrator's mailbox. The intended administrator and reason are stored durably on the listing before any item mail is sent. Pending seizures are hidden from buying and automatically resume after mail recovery or a server restart. The listing is removed only after all seizure deliveries and the seller notice succeed.

The blacklist is based on the base item registry identifier, so all component, enchantment, durability and custom-data variants of that item are blocked from new listings. Adding an item does not silently alter existing listings; those remain visible in Admin Overview for an explicit cancel or seizure decision.

## Transaction safety

Every purchase has a persistent schema-1 journal. The stages are:

1. prepared;
2. listing reserved;
3. buyer funds captured;
4. seller-mail escrow funded;
5. seller mail delivered;
6. buyer mail(s) delivered;
7. committed.

Every economy mutation and mail delivery uses a deterministic correlation/idempotency key. Recovery checks the actual economy journal before retrying a transfer, and interrupted rollbacks are themselves journaled. Partial multi-mail deliveries safely resume because already-created correlations are not duplicated.

Completed and rolled-back purchase journals are retained for thirty days. The module is unavailable while Mail is disabled or Economy is disabled; persistent listings are not deleted in that state.

## Storage

Below the world SSU data root:

```text
auction_house/
  settings.json
  listings/<listing-uuid>.json
  purchases/<purchase-uuid>.json
```

Listings migrate to schema 3 for durable seizure ownership and reason recovery. Purchase journals remain schema 1. `settings.json` remains schema 2 for the persistent blacklist.
