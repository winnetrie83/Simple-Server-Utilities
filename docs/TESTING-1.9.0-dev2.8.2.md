# SSU 1.9.0-dev2.8.2 test checklist

## Build and connection

- Build with Minecraft 26.2, NeoForge 26.2.0.7-beta and Java 25.
- Connect client and dedicated server with the exact same dev2.8.2 build; protocol 82 must reject older clients safely.

## Runtime objective labels

- Start a Domination match and inspect neutral, owned and actively claimed nodes from multiple camera angles.
- Verify every live node label and claim timer has a fitted semi-transparent black background.
- Verify text and background rotate together without visible separation or z-fighting.

## Runtime minigame borders

- Open Dashboard → Settings → Borders and confirm **Minigame border** and **Spectator border** are available.
- With both enabled, join Spleef, CTF or Domination and confirm the cyan game-region outline appears during countdown and the match.
- Configure spectator bounds and confirm the purple spectator outline appears independently.
- Toggle each option off and on during a match; the corresponding layer must clear or return within one second.
- Confirm claim and region borders can remain enabled simultaneously without replacing either minigame layer.
- Leave/finish the match and verify both minigame border layers disappear immediately.
- Test an existing schema-3 border preference file and verify both new options migrate to enabled.
