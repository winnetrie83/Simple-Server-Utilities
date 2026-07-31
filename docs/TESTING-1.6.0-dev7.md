# SSU 1.6.0-dev7 test checklist

Use exactly dev7 on both client and server. Back up the test world first.

## Remote PNG/JPG/GIF

- Ensure `allowRemoteHologramImages=true` in the server common config.
- Create an IMAGE hologram from a direct PNG URL; verify loading text is replaced by the image.
- Repeat with a JPG/JPEG URL.
- Repeat with an animated GIF and verify that all frames loop at sensible timing.
- Test an extensionless direct image endpoint.
- Test an ordinary HTML webpage URL and verify a concise in-world error appears.
- Test an unreachable host and verify the client remains responsive and shows an error.

## Internal resources

- Add PNG, JPG and GIF files to a resource pack under `assets/<namespace>/textures/holograms/`.
- Create image holograms with complete resource IDs.
- Replace an image, press F3+T and verify the changed file is loaded again.

## Billboard behaviour

- Walk around and above/below an image; it must continue facing the main camera.
- Verify Image W, Image H and Scale change the visible world dimensions.
- Verify See through ON draws over terrain and OFF respects normal depth.
- Place two image holograms near each other and verify right-click selects the billboard being aimed at.
- Edit coordinates locally and remotely; verify the full image moves.
- Test Admin Center edit, teleport and delete.

## Limits and failure handling

- Verify a file above 8 MiB is rejected without freezing the client.
- Verify a GIF above 180 frames is rejected.
- Verify an extremely noisy/complex animated GIF is rejected instead of exhausting client memory.
- Verify an unsupported file renamed to `.png` is rejected by decoded content.
- Verify private/local URLs such as localhost are rejected.
- Observe performance with several detailed 64 × 64 images and animated GIFs in view.

## Regression

- Confirm rich floating text, per-selection formatting and unified backgrounds still render correctly.
- Confirm link and scoreboard holograms remain unchanged.
- Confirm Treecapitator, Veinminer and Crops Harvesting still function.
