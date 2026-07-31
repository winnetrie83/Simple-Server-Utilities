# SSU 1.6.0-dev12.4 — Filled marker icon test plan

## Build and compatibility

1. Build with Java 25 using `gradlew.bat clean build`.
2. Install the resulting dev12.4 jar on both client and dedicated server.
3. Confirm protocol 35 accepts dev12.4 on both sides and existing dev12.x markers load without migration.

## Filled in-world marker icon

1. Enable in-world marker icons and create markers in several bright and dark Minecraft colours.
2. Confirm the former hollow ring is replaced by a fully filled coloured circle with only a thin dark contrast edge.
3. Compare against dev12.3 and confirm the new icon diameter is approximately half as large.
4. Walk around, above and below the marker and confirm the filled circle remains camera-facing.
5. Confirm the circle is centred at the stored marker coordinate and does not become a horizontal ground ring.
6. Test markers in front of foliage, terrain and sky and confirm the chosen colour remains readable.
7. Toggle in-world marker icons off and on and confirm only the circle changes.
8. Toggle beams independently and confirm the full-height beam remains unchanged.
9. Confirm World Map and minimap marker icons, colours, marker editing and distance settings are unchanged.
