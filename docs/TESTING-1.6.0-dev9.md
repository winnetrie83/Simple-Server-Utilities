# SSU 1.6.0-dev9 test checklist

Build with Java 25:

```bat
gradlew.bat clean build
```

Use the generated dev9 JAR on both the dedicated server and the client. Protocol is 31.

## Mail rich text

1. Open Mail → Compose and type a multiline message.
2. Select separate text ranges and apply bold, italic, underline and strikethrough.
3. Apply at least three different Minecraft colors to different ranges.
4. Confirm no `§` formatting markers are visible in the editor.
5. Clear formatting from one selected range and verify only that range changes.
6. Send the mail and verify the Inbox renders all styles and colors correctly.
7. Open the sender's Sent page and verify the same formatted body appears there.
8. Restart the server and confirm the formatted Inbox and Sent copies still render correctly.
9. Verify an older plain-text mail still displays normally.
10. Verify a mail containing only formatting and whitespace is rejected unless it has an item or money attachment.

## Floating Text editor polish

1. Open a new and an existing hologram with the Hologram Tool.
2. Confirm the old default text-color hex field and default-color preset button are gone.
3. Confirm selection-based text colors, B/I/U/S and Clear style still work.
4. Edit an older hologram that used a legacy base color and verify its unformatted text keeps that color after saving.
5. Confirm background presets, scale/range, image size, score mode and Always visible still work.

## Scoreboard controls and timing

1. Create two scoreboard holograms using the same or different objectives.
2. Set one refresh interval to `0.5` seconds and the other to `5` seconds.
3. Change both scores repeatedly and verify the fast hologram updates independently without forcing the slow hologram to refresh every 0.5 seconds.
4. Confirm `Score rows` limits the number of top-score entries.
5. Confirm non-scoreboard holograms show the score controls disabled.
6. Restart the server and confirm the stored row count and interval are preserved.

## Regression

- Verify text, link, image and scoreboard holograms still render.
- Verify mail items and money attachments still send, claim and delete normally.
- Verify recipient suggestions and the player dropdown still work.
- Verify client/server mismatch is rejected when one side is still protocol 30.
