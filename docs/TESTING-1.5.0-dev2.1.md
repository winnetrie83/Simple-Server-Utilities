# SSU 1.5.0-dev2.1 test plan

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the same JAR on client and server; protocol 22 requires exact matching builds.
3. Open Mail > Compose and verify the Players list is closed initially.
4. Click Players and confirm the opaque dropdown is drawn in front of Subject, Money and Message.
5. Scroll the player list and select a known offline or online player.
6. Type a partial name and verify the list filters alphabetically.
7. Click Message and verify the caret starts at the top-left.
8. Press Enter several times and verify multiple lines are retained in the sent mail.
9. Fill the whole message box and verify internal scrolling works.
10. Verify Inventory and Hotbar are lower, centered and separated, with the Hotbar label between them.
11. Send mail with items and money, then verify attachments and status tracking still work.
