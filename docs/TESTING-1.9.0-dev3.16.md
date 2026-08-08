# SSU 1.9.0-dev3.16 runtime checklist

## Build / compatibility
- Client and dedicated server both use dev3.16 / network protocol 100.
- Existing dev3.15.x KOTH definition loads as STATIC without data loss.

## KOTH editor / setup
- Open Hill rules and confirm every numeric input has a visible label/unit.
- Toggle an arena between STATIC and ROTATING; save/reopen and verify persistence.
- With multiple existing arenas, cycle arenas and verify each keeps its own mode.
- STATIC: set hill centre with Setup Tool; verify white hill banner and translucent white dome.
- Team spawns: verify red/blue configured teams no longer use generic yellow banners.
- ROTATING: author at least 2 hill points using Hill point slots; verify markers/banners/dome preview and validation.

## STATIC gameplay
- Enter with one red and one blue player. Verify live score HUD is visible.
- Verify control bar proportions are red 40%, white 20%, blue 40%.
- Red majority moves yellow marker left with left arrow; blue majority moves right with right arrow; tie stops it.
- Score begins only after marker enters red/blue territory and that owning team still has at least one player physically inside the hill.
- Leaving the hill shows `Outside hill range`; entering shows `YOU ARE INSIDE THE HILL`.
- Dome is white in neutral territory and changes to the scoring team's configured colour.

## ROTATING gameplay
- Verify no tug-of-war control bar is shown.
- Majority presence scores; equal presence scores nothing.
- Verify warning is shown before rotation and point advances to the next authored point.
- Dome moves to the new active point and follows control colour.

## Match overview
- Open the detailed match menu and verify the ~20% smaller layout has no overlap/clipping at common GUI scales.
- Verify objectives show current KOTH state/presence.
