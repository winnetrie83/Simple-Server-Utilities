# SSU 1.6.0-dev5 smoke-test checklist

## Build and connection

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the same dev5 JAR on client and server.
3. Confirm protocol 27 accepts dev5/dev5 and rejects mixed dev4/dev5 connections.

## Treecapitator leaf cleanup from a higher starting block

1. Enable Treecapitator and natural-leaf cleanup.
2. Find a normal natural tree with at least four trunk logs.
3. Aim at the third trunk log from the ground and break it with a valid axe.
4. Confirm only that targeted log and connected same-species logs above it are selected and mined.
5. Confirm logs below the targeted block remain untouched.
6. Confirm the natural leaves assigned to that tree are removed after the selected upward trunk section completes.
7. Repeat from the second and fourth trunk logs.
8. Repeat with another tree species touching the first tree and confirm only the targeted log species is mined and shared/foreign canopy ownership remains conservative.
9. Lower the permitted Treecapitator block limit below the number of upward logs and confirm leaves are not removed after an incomplete limited chain.
10. Repeat with natural-leaf cleanup disabled and confirm only logs are removed.

## Floating-text background colour

1. Create a new text hologram and leave background at `00000000`.
2. Confirm no background rectangle is rendered.
3. Enter a six-digit background colour such as `AA0000` and save.
4. Confirm the editor applies the default semi-transparent alpha and renders a red background behind each visible line.
5. Enter an eight-digit ARGB value such as `80AA0000` and confirm exact opacity is retained after closing and reopening the editor.
6. Test multiline text, a link hologram and a scoreboard hologram; confirm every rendered line receives its own correctly sized background.
7. Toggle **Through** and confirm text and background follow the same visibility behaviour.
8. View the hologram from different directions and heights; confirm the background remains camera-facing and stays behind the text without obvious z-fighting.

## Minecraft colour presets

1. Open the text-colour preset button.
2. Confirm all 16 standard Minecraft colours appear in a four-by-four palette.
3. Select several presets and verify the text hex field updates to the expected six-digit RGB value.
4. Open the background preset button and confirm the same 16 colours appear.
5. Select a background preset from a transparent starting value and confirm a readable default alpha is applied.
6. Manually set a custom non-zero alpha, then select another background preset and confirm the existing alpha is preserved while RGB changes.
7. Select **No background (transparent)** and confirm the field becomes `00000000`.
8. Click outside an open palette and confirm it closes without changing the current colour.
9. Press Escape while a palette is open and confirm only the palette closes first.

## Persistence and remote editing

1. Create a hologram with custom text and background colours.
2. Restart the server and reconnect; confirm both colours persist.
3. Edit the hologram locally with the Hologram Tool and confirm both fields are preloaded.
4. Edit the same hologram from Admin Center → Holograms while far away and confirm the background setting is preserved.
5. Load an existing dev4 world and confirm old holograms remain the same size, retain their text colour and receive a transparent background by default.

## Regression

- In-world hologram targeting still selects the intended nearby line rather than another hologram several blocks away.
- Local and remote hologram edit, teleport and double-confirmed delete still work.
- Treecapitator still requires an axe, starts at the targeted block, uses only the exact log species and charges durability per automatic log.
- Veinminer still requires a pickaxe and charges durability per automatic ore.
- Crops Harvesting, claims, regions, permissions, mail, economy, maps, homes, warps and spawn continue to load and operate normally.
