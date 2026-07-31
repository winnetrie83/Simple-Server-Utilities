# SSU 1.6.0-dev11.4 test checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev11.4 build on client and dedicated server.
- Confirm connection succeeds with protocol 34.

## Recommended tool versus harvestability

- Look at dirt or grass block with an empty hand.
  - A wooden shovel icon is shown as the minimum recommended category.
  - The vertical bar is green because the block can still be harvested and drops normally.
- Hold a non-shovel item while looking at dirt.
  - The shovel icon remains visible.
  - The bar remains green because the recommendation is advisory.
- Hold any shovel tier.
  - The shovel icon remains visible.
  - The bar remains green.

## Required tools

- Look at obsidian with an empty hand or an insufficient pickaxe.
  - A diamond pickaxe icon is shown.
  - The bar is red because the correct minimum tool is not held and drops cannot be harvested correctly.
- Hold a diamond or netherite pickaxe.
  - The same diamond pickaxe minimum icon remains visible.
  - The bar turns green.
- Repeat with stone- and iron-tier requirement examples.

## Unbreakable and no-hint blocks

- Look at an unbreakable block such as bedrock: the bar is red.
- Look at a breakable block with no required or recommended tool: no icon is shown and the bar is green.

## Debug mode

- With `ssu.block_information.debug` granted and enabled, verify optional tools show `Recommended: ...`.
- Verify hard requirements show `Required: ...`.
- Confirm the bar semantics remain harvestability-only in debug mode.

## Regression

- Verify container previews from dev11.2 still render normally.
- Verify item/entity names, debug IDs, hardness and state lines remain unchanged.
- Verify the inventory default and permissions still limit previews correctly.
