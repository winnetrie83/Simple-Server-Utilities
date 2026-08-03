# SSU 1.8.0-dev16 — Architecture decisions

## Canonical state ownership

SSU's long-lived manager instances declared by `SimpleServerUtilities` remain the canonical owners of module state. Core 2.0 module classes provide dependency ordering, lifecycle participation and service discovery; they must not duplicate the manager's data.

This formalizes the existing architecture and prevents a second state tree from being introduced while the mod is still evolving quickly.

## Network payloads

Typed payload records remain separate. They are intentionally not replaced by a generic JSON envelope. Typed codecs provide strict size limits, compile-time structure and server-side validation.

Repeated primitive bounds now live in `PayloadBounds`, while each feature retains its own typed payload.

## Persistent locations

`WorldPositionValues` centralizes normalization for dimension IDs, coordinates, yaw and pitch. Feature-specific records such as Home, Warp, Server Spawn, Minigame Location and Dungeon Location retain their own semantic types and delegate only the common value normalization.

## Player-data loading policy

Data is lazy only where a record can be addressed independently and global aggregation is not required.

Lazy/bounded:

- Homes
- UI preferences
- Content Progression
- Mail

Global by design:

- Economy accounts: total supply, transfers and account/name administration
- Permissions: effective resolution and known-player/rank views
- Custom statistics: definitions, leaderboards and administrative resets

## Economy durability

The full transaction journal and the compact committed-key index serve different purposes:

- retained transaction records provide player/admin history and crash recovery;
- the bounded committed-key index protects recent idempotent operations after old history is pruned;
- PREPARED transactions always remain full records until recovered.

The single-writer storage service remains the owner of asynchronous writes and deletes.

## Migration policy

Save migration code remains until a future stable release defines the oldest directly supported upgrade version. Removing migration code is not treated as a performance optimization because it normally runs only during loading and protects existing worlds.

## Future structural work

A later UI-only refactor should split the dashboard and server menu service by page while preserving all payloads and visible behavior. That change is intentionally separate from dev16's runtime/storage release so performance regressions and GUI regressions can be isolated during testing.
