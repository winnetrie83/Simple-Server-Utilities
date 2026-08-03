# SSU 1.8.0-dev16.3 native marker beam test

1. Build client and server from the exact same dev16.3 source.
2. Create several markers in different colours and enable their in-world beams.
3. Confirm each marker uses the animated vanilla beacon texture/core/glow instead of solid Gizmo bars.
4. View a beam across alternating walls, pillars and openings. Opaque blocks must hide only the covered beam fragments; visible gaps must still show the beam.
5. Enter a cave below the marker and confirm the underground part is visible only through open sight lines.
6. Confirm the beam spans the complete dimension build height and no longer stops at WORLD_SURFACE.
7. Test near the configured beam-distance boundary and after changing dimension.
8. Confirm marker discs, look-at labels, minimap/world-map icons and saved marker data are unchanged.
