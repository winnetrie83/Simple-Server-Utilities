# SSU 1.8.0-dev18.0 GUI-first migration

## Design rule

Normal player and administrator workflows must be discoverable and complete through SSU GUIs. Commands remain available for compatibility, console operation, shortcuts and recovery, but they are no longer the primary interface.

GUI actions call the same managers, policies, transaction services, storage and job scheduler used by commands. This avoids duplicated validation and preserves existing data formats.

## New destinations

### Claims & Homes

- Claims remain the land-management entry point.
- Homes are opened from Claims & Homes, not Travel.
- Players can save or update the current position as a home, teleport, delete with confirmation and cancel a pending home teleport.
- Direct claim teleport is restricted to administrators server-side. Players use their homes.

### Travel

- Server spawn and global warps.
- Administrators can set/clear spawn and create, move or delete warps.
- Pending spawn/warp teleports can be cancelled.

### Admin Center

- Player Claims: search all player claims, inspect, teleport and delete with confirmation.
- Ranks: create, rename, set default, delete and reset a player to the default rank.
- Region Maintenance: snapshot, reset, clear, redefine, delete, exact selection coordinates, fill jobs and rental administration.
- Utility Mining: Treecapitator/Veinminer defaults plus custom/disabled block lists.
- Maintenance: reload, runtime status, border colors, border refresh, hologram/NPC refresh and NPC-shop buy-back retention.

### Existing specialized GUIs

- Hologram Manager: Move to current position.
- Permission Editor: live effective permission check for an online player.
- Minigame Lobby: administrator add/set score controls.
- Dungeon Lobby: administrator advance-stage control for the administrator's active run.

## Compatibility

- Existing commands are retained.
- `/ssu reload` and the Maintenance GUI share `SsuReloadService`.
- Managed dimensions remain restart-bound because registries/datapacks cannot be safely replaced during a running server session.
- Network protocol: 65.
- Storage and schema versions: unchanged.
