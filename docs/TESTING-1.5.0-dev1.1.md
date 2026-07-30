# Testing SSU 1.5.0-dev1.1

1. Build with Java 25 using `gradlew.bat clean build`.
2. Confirm `MailComposeScreen` compiles without final-field assignment errors.
3. Start client and server with the same JAR.
4. Open Mail -> Compose.
5. Confirm the complete 176 x 248 panel is centered.
6. Confirm all nine attachment slots, 27 main inventory slots and nine hotbar slots are visible and clickable.
7. Close Compose without sending and confirm attachments return to the player.
8. Send a text-only mail, an item mail and a money mail.
9. Recheck inbox, sent mail and attachment claiming.
