# SSU 1.6.0-dev12.6 test checklist

Build with Java 25:

```bat
gradlew.bat clean build
```

Use the exact same dev12.6 JAR on the client and dedicated server. Network protocol remains 35.

## Distance-compensated in-world marker

1. Enable in-world marker icons and create one marker in a clear line of sight.
2. Stand within roughly 5-16 blocks and verify the filled disc retains the compact dev12.5 size.
3. Walk to approximately 32, 64, 128, 256 and 512 blocks while keeping the marker visible.
4. Confirm the disc remains approximately the same readable screen size instead of shrinking into a tiny dot.
5. Aim at the disc at each distance and confirm the marker name and live distance remain legible.
6. Confirm only the closest marker to the crosshair receives a label when markers overlap visually.
7. Walk closer again and confirm the disc/label do not grow excessively at close range.
8. Turn off in-world marker icons and confirm both disc and label disappear; beams remain controlled separately.

## Regression

- Marker colour, filled-disc appearance and thin dark contrast rim remain unchanged.
- Marker beam range and rendering remain unchanged.
- World Map/minimap marker icons remain unchanged.
- Marker right-click creation/edit/delete and remote management remain functional.
- World Map biome/block cursor information and cache format 5 remain unchanged.
