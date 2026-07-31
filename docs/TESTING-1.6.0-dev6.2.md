# SSU 1.6.0-dev6.2 focused test checklist

## Treecapitator wood-family regression

1. Grow or find a natural oak tree.
2. Strip one middle oak log with an axe.
3. Replace another middle segment with `minecraft:oak_wood` or `minecraft:stripped_oak_wood`.
4. Target the stripped or wood segment while Treecapitator is active.
5. Confirm the preview includes matching `oak_log`, `stripped_oak_log`, `oak_wood` and `stripped_oak_wood` blocks from the target height upward.
6. Break the target and confirm every selected oak-family trunk block is destroyed and charged durability once.
7. Place birch logs against the oak canopy and confirm they are not selected.
8. Build a standalone pile of logs without natural leaves and confirm Treecapitator does not activate.
9. Place leaves manually around a standalone pile and confirm tracked player-placed leaves do not make it qualify as a natural tree.
