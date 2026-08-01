# SSU 1.7.0-dev1.4 Auction House administration test checklist

Use the exact same `1.7.0-dev1.4` JAR on client and server. Network protocol is 38.

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm a player without `ssu.auction_house.admin` sees neither **Admin** nor **Blacklist**.
3. Confirm an administrator sees both tabs and that Admin Overview contains every active listing.
4. Search, category-filter and sort Admin Overview; verify the seller, quantity, price, time and hover tooltip remain correct.
5. Select a listing and press **Admin cancel**. Confirm the listing disappears and every unsold item is returned to the seller by mail.
6. Confirm the seller's return mail clearly says the auction was cancelled by an administrator.
7. Create another listing, select it in Admin Overview and press **Seize**.
8. Confirm the listing leaves the active market as soon as seizure is durably recorded, and is deleted only after the full unsold quantity is delivered to the chosen administrator mailbox, split by real stack size and no more than nine stacks per mail.
9. Confirm the seller receives a system-mail notice naming the administrator who seized the listing.
10. Interrupt/restart during a multi-mail seizure and confirm it resumes for the originally recorded administrator without duplicate items or seller returns.
11. Hold an item in the main hand and use Blacklist → **Add held item**. Repeat with an empty main hand and an item in the offhand.
12. Confirm both items appear in the searchable Blacklist page with item icons, names and registry identifiers.
13. Try to create a new auction for a blacklisted item. Confirm creation is rejected and the extracted inventory items are restored exactly.
14. Confirm component variants of the same base item are also rejected because the blacklist uses the base registry identifier.
15. From Admin Overview, press **Blacklist** on a selected listing. Confirm the item is added but the existing listing remains active.
16. Explicitly cancel or seize that existing listing from Admin Overview.
17. Select a blacklist entry and press **Remove from blacklist**. Confirm the item can be listed again.
18. Restart the dedicated server and confirm blacklist entries persist in `auction_house/settings.json` with schema version 2.
19. Confirm existing schema-1 listings migrate to schema 2 and existing schema-1 purchase journals still load and transact normally.
20. Confirm sale tax editing, normal buying, My Auctions cancellation, expiry returns and purchase/seller mails still work.
21. Check the server log for administrator cancellation, seizure, blacklist-add and blacklist-remove records.
