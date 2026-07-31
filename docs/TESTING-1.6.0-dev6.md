# SSU 1.6.0-dev6 smoke-test checklist

## Build and connection

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the resulting dev6 JAR on both client and server.
3. Confirm protocol 28 accepts dev6/dev6 and rejects protocol-27 dev5.1.

## Floating text background

1. Create a five-line floating text with a visible ARGB background.
2. Confirm all lines share exactly one background rectangle.
3. Confirm no glyph extends outside the left or right edge, including bold text.
4. Walk from point-blank range to the configured view-distance edge and confirm text always remains in front of the background.
5. Turn and pitch the camera and confirm text and background remain one billboard.

## Rich text editor

1. Type more than 40 visible characters on one line and confirm overflow moves automatically to the next line.
2. Confirm Minecraft formatting codes do not count toward the 40-character limit.
3. Select a word and apply Bold, Italic, Underline and Strikethrough separately.
4. Keep the selection and apply one of the 16 Minecraft colours.
5. Confirm differently formatted ranges are visible directly in the editor and in-world after saving.
6. Select a formatted range, use Clear style and confirm surrounding formatting is retained.
7. Reopen the hologram and confirm all inline formatting remains editable.

## Coordinates and migration

1. Edit X, Y and Z locally and remotely, save, and confirm the hologram moves to the exact coordinates in the same dimension.
2. Confirm non-finite or out-of-world coordinate values are rejected.
3. Load a schema-3 hologram using whole-text style flags and confirm it migrates to schema 4 without losing its appearance.
4. Confirm remote Edit, Teleport and Delete still work from Admin Center.
