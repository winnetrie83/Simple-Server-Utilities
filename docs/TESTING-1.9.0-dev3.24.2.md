# SSU 1.9.0-dev3.24.2 — NPC custom skin focused test checklist

## Build
- Compile with the project-required Java 25 / NeoForge 26.2 environment.
- Client and dedicated/server side must use the same dev3.24.2 build.

## Local server PNG
1. Put a valid 64x64 PNG at `<world>/simpleserverutilities/npcs/textures/MrRuso.png`.
2. Edit an NPC: Appearance -> Texture source `Local server PNG`.
3. Enter `MrRuso.png`, choose Wide or Slim as appropriate, and Save.
4. Allow up to one normal NPC reconciliation cycle (~2 seconds).
5. Confirm the mannequin renders the actual skin rather than magenta/black missing texture.
6. Reopen the editor and confirm the stored source/path are unchanged.

## HTTPS PNG
1. Select the HTTPS texture source and enter a direct 64x64 PNG URL.
2. Save and allow the async download plus the next NPC reconciliation cycle.
3. Confirm the actual skin renders.
4. If it fails, inspect both server and client logs for `Custom NPC texture` / `Could not install custom NPC skin`; failures are no longer silent.

## Shared PNG regression
1. Create two NPC definitions using the same PNG.
2. Confirm both render the skin.
3. Change/remove the custom skin from one definition.
4. Confirm the other definition keeps rendering its skin.

## Model regression
1. Use one skin as Wide, then switch only the model shape to Slim.
2. Confirm the texture remains loaded and the arm model changes without a missing texture.

## Existing NPC visual regression
- Confirm SSU name/role labels remain scaled as in dev3.24.1.
- Confirm targeting an NPC does not restore the large vanilla duplicate nameplate.
