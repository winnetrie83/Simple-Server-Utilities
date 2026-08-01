# SSU 1.7.0-dev1.2 hotfix test checklist

Use the exact same `1.7.0-dev1.2` JAR on client and server. Network protocol remains 37.

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm `AuctionSort.java` compiles without `listing() is undefined for Object`.
3. Open the Auction House and verify every sort mode works in both directions:
   - Name
   - Quantity
   - Unit price
   - Remaining time
4. Confirm equal values remain deterministic through the listing-ID tie-breaker.
5. Re-run the full `TESTING-1.7.0-dev1.md` Auction House checklist.
