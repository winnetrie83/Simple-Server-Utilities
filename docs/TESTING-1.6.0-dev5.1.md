# SSU 1.6.0-dev5.1 smoke-test checklist

## Build and connection

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the same dev5.1 JAR on client and server.
3. Confirm protocol 27 accepts the pair and still rejects protocol-26 dev4.

## Treecapitator on damaged/remnant trees

1. Find a natural oak tree and remove its bottom log without Treecapitator.
2. Activate Treecapitator, target the remaining trunk and confirm the connected remainder above the targeted block is outlined and mined.
3. Repeat after removing one middle trunk log; target the upper remnant and confirm it can still be processed because it retains a natural canopy.
4. Confirm the disconnected lower remnant is not pulled through the air gap.
5. Confirm an isolated standalone log without a natural canopy is still rejected.

## Same-family wood variants

1. On one natural oak trunk, strip one log and replace another natural log variant with oak wood/stripped oak wood for the test.
2. Target any valid oak-family block and confirm connected `oak_log`, `stripped_oak_log`, `oak_wood` and `stripped_oak_wood` are selected together from the targeted height upward.
3. Place or find birch logs touching the oak structure and confirm they are not selected.
4. Repeat with crimson/warped stem and hyphae variants when available.
5. Confirm every automatically mined non-leaf block still costs one normal durability attempt and permissions/protection are checked per actual block variant.

## Floating-text billboard background

1. Create a floating text with a visible semi-transparent background.
2. Stand still and rotate the camera through a full circle; confirm text and background remain aligned as one rectangle.
3. Look sharply upward and downward; confirm the background follows the same pitch as the text and does not remain as an independent world-space plane.
4. Test first-person and third-person camera modes.
5. Walk around and through the hologram while rotating; confirm the small depth bias prevents z-fighting without a visible gap between text and background.
6. Repeat with multiline text, link suffix text and a scoreboard hologram.
7. Toggle see-through and confirm both layers change together.

## Regression

- Treecapitator still starts only at the targeted height, never mines a different wood family and removes eligible natural leaves only after a complete selected upward section.
- Veinminer, Crops Harvesting, local/remote hologram editing, teleport/delete, claims, regions, permissions, mail, economy and maps continue to operate normally.
- Existing schema-3 holograms retain their text/background settings; no storage or payload migration runs in dev5.1.
