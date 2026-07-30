# SSU 1.3.0-dev4.1 hotfix

This build is a focused stabilization build on top of `1.3.0-dev4`. Dashboard/Core phases 4 and 5 remain paused until the reset and map changes have been confirmed in Minecraft.

## Drop-free region reset

The reset clear pass no longer behaves like a sequence of player block breaks. Before a saved position is replaced with air, SSU clears inventory-bearing block entities, detaches the block entity and uses Minecraft's client-update, known-shape and suppress-drops flags. The saved snapshot data was already loaded before this destructive phase, so the original inventory is restored from the snapshot rather than dropped into the world.

The restore pass now has two bounded stages:

1. Restore every saved block and block entity without neighbour reactions.
2. Reconcile neighbours after the complete structure exists.

This prevents support-sensitive blocks such as crops, torches and wall-mounted blocks from breaking while their support position is still waiting later in the restore stream.

## Snapshot format version 3

Version 3 keeps the compressed palette/block format from dev4 and adds structural hanging entities:

- item frames;
- glow item frames;
- displayed item and item rotation;
- frame facing/position and entity properties;
- paintings and their selected variant.

Current hanging entities in the region are silently discarded before the reset and restored only after all blocks have been restored and reconciled.

Version 1 and 2 snapshots remain readable. They cannot contain item-frame or painting data because the old formats never saved it. For those snapshots, dev4.1 preserves the hanging entities that are present immediately before the reset. Frames already lost during an earlier dev4 reset cannot be reconstructed automatically; place them again and run `/regions save <name>` to create a version-3 snapshot.

## Minimap claim perimeter

The minimap payload now includes the immutable claim UUID for every claimed chunk. A chunk edge is drawn only when the neighbouring chunk belongs to a different claim or wilderness. Internal borders between chunks of the same connected claim are no longer rendered.

This payload change increases the SSU network protocol from 11 to 12. Client and server must both use dev4.1.

## Right-click map drag

The claim-map and world-map screens now capture right-button press, movement and release directly and pass them to their map widget. This avoids container focus/routing swallowing right-button drag events and allows release outside the widget to commit the pan.

## Height relief

All three maps use the same aerial atlas. Dev4.1 combines:

- normalized local hill shading;
- broader hill/valley shading;
- stronger north-west directional one-block height differences;
- a narrow light/shadow rim on block edges;
- a subtle elevation contour that remains uniform across a flat plateau.

The renderer fingerprint changed, so cached dev4 terrain is automatically rebuilt instead of being reused with the old relief.
