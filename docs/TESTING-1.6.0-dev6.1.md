# SSU 1.6.0-dev6.1 focused test checklist

## Build and compatibility

1. Build with Java 25 using `gradlew.bat clean build`.
2. Put the resulting dev6.1 JAR on both client and server.
3. Confirm the connection succeeds with network protocol 28.

## Rich floating text

1. Create a text hologram containing `dit is vet groen`.
2. Select only `vet groen`.
3. Click **B**, then choose **Green** from the selection color palette.
4. Confirm the selected words immediately appear bold and green inside the editor.
5. Confirm no `§`, color-code letter or effect-code letter appears in the editable text.
6. Save and confirm only `vet groen` is bold and green in the world.
7. Reopen the hologram and confirm the same visible text, selection formatting and plain editor content are restored.
8. Apply underline and strikethrough to different selections and verify both editor and world output.
9. Type more than 40 visible characters on one line and confirm wrapping occurs without exposing hidden codes or losing styles.
10. Verify a multiline hologram can still be selected accurately across its rendered width.

## Treecapitator remnant and family handling

1. Find a natural tree and manually remove one middle trunk log.
2. Activate Treecapitator on a remaining log below the gap and verify the upper remnant is included.
3. Repeat while targeting a remaining log above the gap.
4. Repeat on a small tree where only one canopy-connected log remains.
5. Mix normal log, stripped log, wood and stripped wood of the same species in a natural tree remnant; verify they are treated as one family.
6. Place a different species directly against the tree and verify it is not included.
7. Verify a loose player-placed log without a natural canopy is still rejected.
8. Verify per-block permissions, protection checks, axe requirement and durability still apply.
