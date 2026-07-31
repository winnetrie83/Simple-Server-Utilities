# Image holograms — SSU 1.6.0-dev7

SSU dev7 renders PNG, JPG/JPEG and animated GIF files as camera-facing image billboards.
The image is loaded and decoded by each connected client; the dedicated server stores and
synchronizes only the source, position and display settings.

## Remote image

1. Hold the Hologram Tool and right-click.
2. Change **Type** to **IMAGE**.
3. Enter a direct HTTP or HTTPS source in **Image source**.
4. Set **Image W** and **Image H** in world-block units.
5. Create the hologram.

A direct source must return PNG, JPG/JPEG or GIF bytes. The URL may end in the matching
extension, but an extensionless image endpoint also works when it returns supported image data.
A normal website page that contains an image is not a direct image source.

New configs use:

```toml
allowRemoteHologramImages = true
```

An existing common config retains its old value. Change it manually to `true` when remote
sources are still rejected by the server.

## Internal resource image

Put the file in a resource pack or the mod resources and enter the complete resource ID, for
example:

```text
simpleserverutilities:textures/holograms/example.png
```

Internal resources require a `.png`, `.gif`, `.jpg` or `.jpeg` suffix. Pressing F3+T clears the
client image cache and reloads changed resource-pack images.

## Display settings

- **Image W / Image H**: billboard width and height in world-block units.
- **Scale**: multiplies both image dimensions.
- **View distance**: maximum synchronization/render distance.
- **See through**: renders the image on top of world geometry.

The complete billboard is clickable with the Hologram Tool for local editing. Remote edit,
teleport and delete remain available from Admin Center → Holograms.

## Safety and performance limits

- Maximum download: 8 MiB.
- Maximum decoded dimensions: 4096 × 4096 and 16,777,216 pixels.
- Maximum animated GIF frames: 180.
- Maximum client render sample: 64 × 64 pixels per frame.
- Maximum client cache: 64 image sources; least-recently-used completed entries are evicted.
- Highly complex animated images are rejected after 262,144 merged render rectangles across all frames.
- Maximum three HTTP redirects.
- Loopback, private, link-local, multicast and carrier-grade NAT targets are rejected.
- Loading and decoding run asynchronously on two low-priority client threads.

The 64 × 64 sample is expanded to the configured world size. Flat areas with equal colours are
merged into larger rectangles, but highly detailed photographs or noisy GIFs are more expensive
than simple logos, icons and pixel art.
