# SSU Core 2.0 migration

The migration is incremental. Existing managers remain the authoritative compatibility facade until each subsystem can be decomposed further without changing commands or world data.

## Completed foundation

### Dev1

- Typed service registry.
- Dependency-aware module registry and lifecycle bridge.
- Protection and teleport safety corrections.

### Dev2

- Reusable server-authoritative/client-rendered border snapshot system.

### Dev3 and dev4

- Persistent border settings and per-player visibility preferences.
- Single-writer batched storage service with coalescing, retry, immutable worker tasks and shutdown flush accounting.
- Dirty JSON record tracking for claims, regions, homes, warps and permission data.
- Fair bounded server-thread job scheduler with resource locks, status and cancellation commands.
- Multi-tick region fill, clear, snapshot capture and snapshot reset.
- Durable destructive-reset checkpoints with explicit interrupted/failed recovery state.
- Region spatial chunk index and permission-result cache with targeted invalidation.
- Transaction journal, rollback and idempotency primitives.
- Economy Core with exact minor-unit accounts and rental payment/refund recovery.
- Shared dashboard, claim-map, minimap and world-map networking/rendering foundations.
- Persistent client aerial-map cache isolated by server, resource-pack fingerprint and dimension.

## 1.4.0-dev1 lifecycle ownership

The following systems are now registered as dependency-aware `SsuModule` implementations:

- `EconomyModule`
- `ClaimModule`
- `PermissionModule`
- `RegionModule`
- `TeleportModule`
- `VisualizationModule`
- `MenuModule`

The registry resolves dependencies before initialization and server startup. Shutdown runs in reverse dependency order. The main mod entrypoint no longer directly loads or saves claims, permissions, regions, snapshots, rent recovery, border settings or visualization state.

Current dependency chain:

- permissions depend on claims;
- regions depend on economy and permissions;
- teleport and visualization depend on claims, regions and permissions;
- menu depends on all migrated player-facing services.

Active jobs are cancelled before module shutdown so region/snapshot managers persist a consistent final state. Session-only teleport requests and cooldowns are cleared on shutdown.

## Compatibility facade

Static references such as `SimpleServerUtilities.REGIONS` and existing manager APIs remain available intentionally. Commands, event handlers and protection code can therefore migrate incrementally instead of requiring a dangerous all-at-once rewrite.

The following remain main-entrypoint or legacy-service owned in 1.4.0-dev1:

- homes;
- warps;
- player UI preferences;
- batched storage worker;
- job scheduler;
- performance monitor;
- transaction manager.

These are candidates for later modules after the new dashboard and lifecycle ordering are runtime-confirmed.

## Dashboard boundary

`MenuModule` owns the server-side dashboard service. The dashboard now uses:

- one compact shell snapshot;
- bounded page requests and page responses;
- closed typed mutation actions;
- server-side permission, target and input validation.

It no longer uses free-form command strings as its client-to-server mutation interface.

## Next planned core milestones

1. Runtime-confirm the page-driven dashboard and module lifecycle on a copied world.
2. Add GameTests for protection, rental recovery and snapshot restoration.
3. Move homes, warps and player UI preferences into modules.
4. Replace remaining static-manager access with constructor-injected services one subsystem at a time.
5. Economy treasury and policy expansion.
6. Mail.
7. Auction House and bag inventory.
8. NPCs and quests.
9. Minigame framework.

## Compatibility rule

A migration step may change internal Java structure, but it must not silently discard or reinterpret existing world data. Any format change requires an explicit schema version, backup and reversible migration path.
