# SSU Core 2.0 migration

The migration is incremental. Legacy managers remain authoritative until their module is migrated, preserving commands and world data.

## Completed foundation

### Dev1

- Typed service registry.
- Dependency-aware module registry and lifecycle bridge.
- Protection and teleport safety corrections.

### Dev2

- Reusable server-authoritative/client-rendered border snapshot system.

### Dev3

- Persistent border settings and per-player visibility preferences.
- Single-writer batched storage service.
- Dirty JSON record tracking for claims and regions.
- Fair bounded server-thread job scheduler.
- Multi-tick region fill, clear and snapshot reset.
- Job resource locking, status and cancellation commands.

## Next planned core milestones

1. Region spatial chunk index.
2. Permission-result cache with targeted invalidation.
3. Continue dirty-storage migration for homes, warps and permissions.
4. Persistent/resumable job records where recovery is required.
5. Transaction journal and rollback primitives.
6. Snapshot/delta networking and shared client GUI components.
7. Economy and mail.
8. Auction House and bag inventory.
9. NPCs and quests.
10. Minigame framework.

## Compatibility rule

A migration step may change internal Java structure, but it must not silently discard or reinterpret existing world data. Any format change requires an explicit schema version, backup and reversible migration path.
