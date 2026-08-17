# SSU 1.9.0-dev3.40.8 test checklist

## Client startup
- Launch the dev client and confirm the title screen renders normally.
- Load an existing world and confirm no black-screen regression.

## Arcane Missiles custom VFX
- Assign the built-in Arcane Missiles ability to a hostile NPC.
- Fight at roughly 4-8 blocks, 12-18 blocks and 24-28 blocks.
- Confirm the cast charge uses custom rune/hand geometry rather than the old End Rod/Witch/Dust particle beam.
- Confirm each damage pulse spawns five visibly separate purple missile heads.
- Confirm missile heads visibly travel over time, fan outward, curve, weave and converge on the target.
- Confirm each missile has a continuous custom textured ribbon trail rather than particle dots.
- Confirm each missile produces a custom impact sprite near the target.
- Strafe while targeted and confirm each newly-fired volley aims at the target's current position.
- Confirm the effect disappears shortly after the volley and does not leave persistent geometry.
- Confirm nearby observers can see the spell and players farther than ~96 blocks do not receive its VFX payload.

## Regression
- Confirm Player-NPC mainhand and offhand rendering still works.
- Confirm improved combat/return pathfinding still works.
- Confirm right-click-air with the NPC Tool opens only one NPC Manager layer.
