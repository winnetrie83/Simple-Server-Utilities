# Recovering a world after an SSU dev17 managed dimension

The dev17.0-dev17.2 generator wrote a pre-1.21.11 dimension-type structure. Minecraft 26.2 rejects that generated datapack before SSU code can start, so installing the fixed jar alone cannot repair a world that is already blocked at the datapack loading screen.

## Recovery

1. Close Minecraft or stop the dedicated server completely.
2. Make a backup of the world folder.
3. Delete only this generated folder:

   `WORLD/datapacks/ssu_managed_dimensions`

4. Do **not** delete `WORLD/simpleserverutilities/dimensions/definitions`; those are the editable SSU dimension definitions.
5. Install/build SSU 1.8.0-dev17.3 on both client and server.
6. Start the world once. It loads without the generated dimension pack and SSU creates a corrected pack during startup.
7. Stop and start the world/server one more time so the corrected custom dimensions enter the world registries.

If the second startup still reports a datapack error, temporarily move the newest JSON file from `WORLD/simpleserverutilities/dimensions/definitions` out of the world folder. That indicates an invalid custom biome/block identifier rather than the dev17 dimension-type format bug.
