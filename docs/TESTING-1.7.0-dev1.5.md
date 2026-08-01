# SSU 1.7.0-dev1.5 — focused Auction House test plan

## Build and compatibility

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the exact same dev1.5 jar on the dedicated server and client.
3. Confirm the connection succeeds with network protocol 39.
4. Confirm existing dev1.4 Auction House settings/listings load; listing records should normalize to schema 3 without losing items, seller, price, quantity or pending seizure ownership.

## Blacklist inventory picker

1. Grant `ssu.auction_house.admin` and open Auction House → Blacklist.
2. Confirm the left panel shows all 27 main-inventory slots and all 9 hotbar slots with normal item rendering and hover tooltips.
3. Select a non-empty slot. Confirm it receives a clear selection outline and the item name appears below the grid.
4. Press **Add selected item**. Confirm the server adds the base item ID and the result list shows the real icon, display name and registry ID.
5. Move or remove the chosen stack before pressing the button. Confirm the server uses the item currently in that slot or rejects an empty/changed slot; it must never trust a client-supplied item stack.
6. Confirm selecting an empty slot does not enable a valid submission.
7. Confirm no main-hand/offhand workflow or **Add held item** button remains.

## Manual item ID

1. Enter `minecraft:diamond`. Confirm a diamond icon, display name and canonical ID appear in the preview area.
2. Add it and confirm it appears visually in the blacklist result list.
3. Try a valid modded item ID and confirm the registered modded item is previewed and stored.
4. Try malformed text, an unknown namespace/path and `minecraft:air`. Confirm the server rejects each and the blacklist file remains unchanged.
5. Confirm a duplicate ID is rejected without creating a second entry.
6. Remove an entry and confirm it can be added again through either input method.

## Required administrative reasons

1. Select an active listing in Admin Overview and press **Admin cancel**.
2. Confirm a modal reason field appears and an empty/whitespace-only reason cannot be submitted.
3. Enter a reason and confirm the unsold items return to the seller by mail. The mail must include administrator name and exact normalized reason.
4. Confirm the server log includes listing ID, seller, administrator UUID/name and reason.
5. Repeat with **Seize**. Confirm the administrator receives the item attachments and the seller receives a notice containing administrator and reason.
6. Interrupt mail delivery after the seizure is persisted but before completion, restart the server, and confirm recovery keeps the same seizure recipient and reason without duplicating items or mail.
7. Confirm reasons longer than 200 characters are prevented/rejected.

## Regression

1. Create, browse, search, sort, buy, partially buy, cancel a personal listing and allow one listing to expire.
2. Confirm global tax editing and blacklist-from-selected-listing still work.
3. Confirm nine-stack mail batching, purchase pling, permissions and active-auction limits remain unchanged.
