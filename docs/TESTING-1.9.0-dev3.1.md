# SSU 1.9.0-dev3.1 manual test checklist

## Arena Validator

1. Configure a lobby outside the playable region and verify that validation does not report an outside-region error.
2. Configure spectator spawn/bounds outside the playable region, in a valid dimension, and verify that this is accepted.
3. Register manual boost locations with their End Rod setup markers present. Validation must not report player-spawn warnings or occupied-space errors caused by the marker.
4. Remove the solid block below a boost location and verify that the validator reports one real blocking error.

## Borders

1. During a live match, toggle Minigame border and Spectator border independently and verify immediate show/hide behavior.
2. While holding the Setup Tool, repeat the toggles and verify that the setup overlays respond immediately.
3. Confirm claim and region layers remain unaffected.

## Boosts

1. Open the CTF and Domination Boosts tabs and verify that controls do not overlap at normal and larger GUI scales.
2. Configure Regeneration heal/second to 2.0, collect the boost, and verify two health points are restored each second for its duration.
3. Test decimal values such as 0.5 and upper bounds.

## Roles

1. Activate Tank Defensive Field beside an enemy and verify visible radial movement plus Slowness.
2. Verify teammates and spectators are not pushed.
3. Test knockback strength 0 and confirm only Slowness remains.

## Dashboard and settings

1. Confirm Questbook uses the supplied questbook icon and My Warps uses the supplied portal icon.
2. Confirm the scoreboard view cycles with J and L still opens Advancements.
3. Confirm Treecapitator and Veinminer show readable color names and still cycle through the same stored RGB palette.
4. Confirm all Match Flow and Progression & Integration fields have visible labels and descriptions.
