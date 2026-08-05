# SSU 1.9.0-dev2.8.1 hotfix test checklist

## Compile/API

- Build with Minecraft 26.2, NeoForge 26.2.0.7-beta and Java 25.
- Confirm `MinigameManager.java` has no diagnostics for `DYED_COLOR`, `WHITE_BANNER` or `AbstractArrow`.
- Start a dedicated server and connect with the exact same dev2.8.1 build.

## Objective casts

- With an empty main hand and empty offhand, right-click an enemy CTF flag and verify the castbar continues instead of instantly canceling.
- Repeat while holding a Tank shield in the offhand; the same click must start the cast and must not raise the shield or interrupt the cast.
- Right-click a Domination node and verify the castbar continues.
- During an established cast, perform a second right-click on a block: the cast must stop and the interaction must be consumed.
- During an established cast, test attack, block hit/break/place, item use, entity interaction, drop, movement and incoming damage; each must interrupt.

## Roles

- Verify team-colored cosmetic leather renders correctly while role armor/toughness attributes remain unchanged by the item equipment.
- Fire the DPS special arrow at an enemy and confirm the configured effect applies.
- Use an invalid/missing configured banner block and verify the Tank shield safely uses white as its dye fallback without crashing.
