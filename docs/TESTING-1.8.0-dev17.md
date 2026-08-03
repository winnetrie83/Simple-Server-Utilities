# SSU 1.8.0-dev17 testing

Use a backup of an existing world. Client and dedicated server must use the same dev17 build.

## Build and startup

1. Build with Java 25: `gradlew.bat clean build`.
2. Start a dedicated server with an existing dev16.5 world.
3. Confirm the permission manager loads without resetting ranks or players.
4. Confirm the log reports any legacy global dimension-scope migration only once.

## Dimension-scoped permissions

1. Open **Admin Center → Permissions**.
2. Select a rank, leave Dimension on **All dimensions**, and change a harmless boolean permission.
3. Select Overworld and set the opposite value. Confirm the row reports a rank dimension override.
4. Enter the Overworld and another dimension and confirm the effective permission changes with context.
5. Repeat for one specific player. Confirm the player-dimension override wins over the player's global value and rank values.
6. Reset the player-dimension value and confirm it falls back to the global player value, then rank-dimension/global rank values.
7. Test an inherited rank and confirm inherited dimension values are resolved before the child rank's own dimension values.
8. Create a new rank and confirm its global permissions automatically act as the same defaults in every dimension until explicit overrides are added.

## Legacy migration

1. On a copied world containing old `permissions/dimensions/*.json` records, start dev17.
2. Confirm each existing rank receives those values as dimension overrides without overwriting an already explicit rank-dimension value.
3. Restart and confirm migration is not repeated and the obsolete global dimension files are removed by the normal storage queue.

## Managed dimensions

For each preset (Overworld, Nether, End, Flat, Empty):

1. Open **Admin Center → Dimensions** and create a uniquely named definition.
2. Verify Create keeps the editor open and reports that restart is required.
3. Stop the server fully and restart it.
4. Confirm the dimension is listed as loaded and can be resolved by its `simpleserverutilities:<id>` key.
5. Confirm the custom display name remains visible in SSU.

### Empty preset

1. Create an Empty dimension with the default platform settings.
2. Restart.
3. Confirm a 9×9 platform exists at the configured Y and is created only once.
4. Modify the platform block/size/Y, save, restart and confirm the platform initialization is rerun for the changed settings.

### Flat/environment settings

1. Verify flat layer counts and block IDs generate the expected layers.
2. Test skylight, ceiling, fixed time, bed/anchor behavior, coordinate scale and height settings in separate disposable dimensions.
3. Confirm invalid numeric values are clamped safely by the server definition normalizer.

## Delete safety

1. Delete a managed dimension definition and confirm the editor requires a second confirmation.
2. Confirm the generated dimension and dimension-type JSON are removed.
3. Confirm rank/player overrides for that dimension are removed.
4. Restart and confirm the dimension is no longer loaded.
5. Confirm the existing dimension world folder remains on disk.

## Read-only dimensions

Confirm vanilla and externally/mod-provided dimensions appear in the list but cannot be edited or deleted by SSU.
