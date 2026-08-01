# SSU 1.7.0-dev1 Auction House test checklist

Use the exact same `1.7.0-dev1` JAR on the Minecraft 26.2 client and dedicated NeoForge 26.2.0.7-beta server. Network protocol is 37.

## 1. Module and dashboard permissions

1. Confirm **Auction House** appears in Admin Center → Module Settings and is enabled.
2. As a normal player with no overrides, confirm the Auction House dashboard tile is visible and opens.
3. Set `ssu.auction_house.dashboard=false` for the player/rank and reconnect or refresh the dashboard.
4. Confirm the tile disappears.
5. Restore dashboard permission. Set `ssu.auction_house.access=false` and confirm the tile is unavailable and direct use is denied.
6. Set `ssu.auction_house.max_active=1` for the test player.
7. Confirm only one simultaneous listing can be created.

## 2. Browse layout, categories and search

1. Open the Auction House from the dashboard.
2. Confirm the left column contains All Items plus all fourteen requested categories.
3. Confirm the main result panel shows long horizontal rows.
4. Hover the displayed item icon and confirm its normal Minecraft tooltip/components appear.
5. Confirm every row shows item name, price/unit, quantity, seller and remaining duration.
6. Create listings in several categories and confirm the automatic category assignment is sensible.
7. Search using the full visible item name, a middle substring and different letter casing.
8. Confirm matching is based on the visible item name and partial names work.
9. Confirm a registry-ID fragment that is not in the visible name does not produce an ID-based match.
10. Toggle Name, Qty, Price and Time sorting in both directions.
11. Create more than eight results; confirm Previous/Next and mouse-wheel navigation above the result panel work.

## 3. Selling

1. Press **Sell** and confirm the complete player inventory is visible.
2. Drag one representative stack into the offer slot.
3. Confirm **Available** includes all inventory stacks with the exact same item/components.
4. Enter a price per unit, a quantity lower than/equal to the available count and select 12, 24 or 48 hours.
5. Press **Create auction** twice rapidly and confirm only one request can be submitted while the button shows its processing state.
6. Confirm exactly the requested quantity is removed across matching stacks and leftovers return from the offer slot.
7. Confirm My Auctions opens and shows the new listing.
8. Try zero/negative/invalid price text, zero quantity, quantity above available and creation beyond the permission limit; all must fail without losing items.

## 4. Buying and delivery

1. Use a second player account with sufficient balance.
2. Select a listing and confirm the **Buy** button appears only for another player's auction.
3. Press Buy, enter a partial quantity and confirm the displayed total equals quantity × unit price.
4. Press **Buy now** twice rapidly and confirm the processing lock prevents an accidental duplicate purchase request.
5. Confirm the pling plays once after the transaction commits.
6. Confirm the listing remaining quantity decreases by exactly the purchased amount.
7. Open the buyer mailbox and claim the purchased item attachments.
8. Sell/buy 70 white wool. Confirm one mail contains stacks of 64 and 6.
9. Sell/buy at least ten full stack-equivalents. Confirm delivery is split into a first mail of at most nine stacks and one or more continuation mails.
10. Attempt to buy with insufficient funds, an excessive quantity, an expired listing and the seller's own listing; all must be denied without item or money changes.

## 5. Seller proceeds and tax

1. Set the global tax to 5%, then sell an item to the second player.
2. Confirm the seller receives an Auction House money mail immediately.
3. Confirm its description includes item, sold quantity, buyer, date/time, gross, 5% tax, tax amount and net amount.
4. Claim the money and confirm the exact net value reaches the seller account.
5. Test 0%, a decimal value such as 2.5%, and 100%.
6. Change the tax after a purchase but before opening the seller mail; confirm the mail keeps the historical rate used for that sale.

## 6. Cancellation and expiry

1. Partially buy a listing, then cancel it as the seller.
2. Confirm only the unsold remainder returns by mail and the listing disappears.
3. Confirm a player cannot cancel another seller's listing.
4. Create a short test listing by temporarily adjusting its stored expiry or waiting for expiry in a test environment.
5. Confirm maintenance returns the remaining items once and removes the expired listing.

## 7. Recovery and regression

1. Restart client/server with active listings and confirm all listings, quantities, prices, sellers and expiry times persist.
2. Restart during controlled purchase stages in a disposable test world; confirm the economy/mail idempotency journal finishes or safely rolls back without duplicates.
3. Disable Mail: confirm the Auction House becomes unavailable while stored listing files remain.
4. Re-enable Mail and confirm listings return.
5. Disable Economy in its settings: confirm new AH use is blocked and stored data remains.
6. Verify Mail, Economy, dashboard, permissions, claims, regions, holograms, utility mining and maps still function.
