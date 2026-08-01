# SSU 1.7.0-dev1.1 hotfix test checklist

Use the exact same `1.7.0-dev1.1` JAR on client and server. Network protocol remains 37.

1. Run `gradlew.bat clean build` with Java 25.
2. Confirm `AuctionCategory.java` compiles without a `BlockTags.SAPLINGS` error.
3. Open the Auction House and verify saplings appear under Plants.
4. Re-run the full `TESTING-1.7.0-dev1.md` Auction House checklist.
