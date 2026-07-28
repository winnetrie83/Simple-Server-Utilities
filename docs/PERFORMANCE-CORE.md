# SSU Performance Core (1.1.0-dev6)

## Region spatial index

Admin regions are indexed in two-dimensional cells covering 8×8 Minecraft chunks. Position and area queries first retrieve a small candidate set and then run the existing exact 3D/2D tests, so overlap, priority and smallest-volume behaviour remain unchanged. Extremely large regions use an overflow list rather than allocating an excessive number of cell references.

## Permission cache

Raw resolved permission values are cached by player, permission key, dimension, claim role, region name and region-permission fingerprint. The cache is bounded to 50,000 entries and uses LRU eviction. Permission-data saves clear the cache immediately. Region permission changes naturally produce a new fingerprint.

## Storage migration

Homes, warps and all permission record categories now use the same coalescing storage worker and dirty-record detection already used by claims and regions. Existing JSON locations and schemas are unchanged.

## Administration

- `/ssu core performance` shows region-index, permission-cache, job and storage statistics.
- `/ssu core performance reset` resets runtime counters.
- The U-menu Core page shows the main cache/index values.

## Compatibility

Client and server must both use dev6 because the menu payload protocol changed to version 6. Existing world data remains compatible.
