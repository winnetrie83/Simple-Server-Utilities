# SSU 1.6.0-dev12.8 test checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Use the exact same dev12.8 JAR on client and dedicated server.
- Confirm network protocol remains 35.

## In-world marker label

1. Create a marker and enable in-world marker icons.
2. Stand close to it and aim directly at the marker disc.
3. Confirm the marker name and distance are approximately twice as large as in dev12.7.
4. Confirm the translucent black background fits tightly around the text, with no large empty band above it.
5. Test at roughly 16, 64, 128 and 256 blocks. The label should retain a readable screen size through distance compensation.
6. Confirm long marker names remain fully inside the background.
7. Confirm characters with descenders and the distance line are not clipped below.
8. Look away and confirm the label disappears normally.

## Regression

- Confirm the filled marker disc is unchanged.
- Confirm beams are unchanged.
- Confirm World Map and minimap marker icons are unchanged.
- Confirm marker editing, deleting and visibility settings still work.
