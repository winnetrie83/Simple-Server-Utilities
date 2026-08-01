# SSU 1.7.0-dev1.5.1 — focused Auction House hotfix test plan

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the exact same dev1.5.1 JAR on the dedicated server and client.
3. Open Auction House → Blacklist.
4. Enter a valid registry ID such as `minecraft:diamond`; verify that its icon and name preview appear.
5. Add it and verify that the visual blacklist entry is created.
6. Enter an unknown ID and verify that it is rejected.
7. Verify inventory-based blacklist selection still works.
8. Verify admin cancel and seize still require a non-blank reason.

Protocol: 39. Listing schema: 3. Settings schema: 2. Purchase journal schema: 1.
