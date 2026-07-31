# SSU 1.6.0-dev11.3 test checklist

## Build and compatibility

- Build with Java 25 using `gradlew.bat clean build`.
- Install the exact same dev11.3 build on client and dedicated server.
- Confirm connection succeeds with protocol 34.

## Required tool icons

- Look at obsidian: a diamond pickaxe icon is shown.
- Hold no tool or an insufficient tool: the vertical bar is red.
- Hold a diamond/netherite pickaxe: the vertical bar is green.
- Verify existing minimum-tier examples still work for stone/iron/diamond tool tags.

## Recommended tool icons

- Look at dirt, grass block, gravel or sand: a shovel category icon is shown even though the block can be broken by hand.
- Hold no tool: the bar is red.
- Hold any shovel tier, including a modded shovel using normal mining behavior: the bar is green.
- Look at logs/planks: an axe icon is shown; an axe turns the bar green.
- Look at ordinary pickaxe-efficient blocks that do not require a correct tool: a pickaxe icon is shown.
- Look at hoe-efficient blocks: a hoe icon is shown.
- Check leaves/wool/cobweb: shears are preferred when vanilla shears-efficiency tags apply.
- Check sword-efficient blocks such as bamboo-like tagged blocks: a sword icon is shown when no shears recommendation takes priority.

## No-hint blocks

- Look at blocks with no required or standard recommended tool: no icon is shown and the bar remains green.

## Debug mode

- With `ssu.block_information.debug` explicitly granted and debug enabled, verify required blocks show `Required: <minimum tool>`.
- Verify dirt or another optional-tool block shows `Recommended: Shovel (any tier)`.
- Verify shears display `Recommended: Shears`.
- Revoke debug permission and verify technical lines disappear while the compact icon remains.

## Container regression

- Verify the dev11.2 item preview still appears below the title and tool icon.
- Confirm inventory permissions and the default one-item limit remain unchanged.
