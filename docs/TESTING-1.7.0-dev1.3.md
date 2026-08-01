# SSU 1.7.0-dev1.3 UI hotfix test checklist

Use the exact same `1.7.0-dev1.3` JAR on client and server. Network protocol remains 37.

1. Run `gradlew.bat clean build` with Java 25.
2. Open the dashboard at several Minecraft GUI Scale values.
3. Confirm the portrait/sidebar border ends at the dashboard panel and never continues below it.
4. Open Auction House → Sell/Create Auction.
5. Confirm the screen is wider and less cramped.
6. Confirm the offer slot has a clearly visible highlighted frame.
7. Confirm “Drop one stack here” is no longer shown.
8. Confirm all 27 main-inventory slots and all 9 hotbar slots have visible individual cell borders and correctly aligned item rendering.
9. Confirm normal drag, click and shift-click behaviour still works for the offer slot, inventory and hotbar.
10. Confirm price, quantity, duration, tax, Create auction and Back controls still work unchanged.
11. Re-run the full `TESTING-1.7.0-dev1.md` Auction House checklist.
