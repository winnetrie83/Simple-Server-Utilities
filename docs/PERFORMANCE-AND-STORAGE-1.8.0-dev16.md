# SSU 1.8.0-dev16 — Performance and storage redesign

## Purpose

Dev16 addresses the scale risks identified during the dev15.4 source audit. The release keeps the existing public feature set and save locations, but bounds long-lived data, reduces broad world scans and exposes real subsystem timings.

## Economy journal retention

The Economy Core now retains the latest completed transactions per participating account instead of keeping every completed transaction forever.

- Default: **50 transactions per participating account**.
- Administrator range: **1–1000**.
- Configure in the dashboard transaction administration page or with:
  - `/ssu admin economy history-limit`
  - `/ssu admin economy history-limit <1-1000>`
- A transaction involving two player accounts remains available while it belongs to the retained history of either account.
- Transactions without a participating account use the same bounded unscoped history.
- **PREPARED** transactions are never pruned; they remain available for crash recovery.
- Completed records outside every retained history are removed from memory and their individual JSON files are queued for deletion through the single-writer storage service.

A separate compact `economy/committed_keys.json` index preserves recent idempotency protection even after full transaction records are pruned. It stores at most 10,000 unique committed keys and is independent from the visible transaction history.

Economy accounts are intentionally still loaded globally because total supply, name resolution, transfers and administrator account browsing depend on a complete account index.

## Runtime performance measurements

`/ssu core performance` now reports bounded rolling timings for the main recurring SSU workloads:

- NPCs
- NPC dialogues
- holograms
- Block Information
- border visualization
- Content Progression
- custom statistics
- mail maintenance
- storage I/O

Each subsystem keeps only the latest 256 samples and reports rolling average, p95, all-time maximum, last runtime and total sample count. `/ssu core performance reset` clears both the old counters and these timing windows.

## NPC scaling

NPC tick work is divided into active sets:

- static No-AI gravity NPCs;
- placements with schedules;
- NPCs that can actively target players or hostile factions.

Faction targeting no longer compares every combat NPC against every other NPC. Every relation pass builds a temporary dimension/chunk index and only scans nearby buckets within the configured follow range. The relation pass remains on its existing ten-tick cadence.

Old basic interaction text is migrated to a one-node dialogue when necessary. Runtime interaction no longer maintains a second fallback conversation path.

## Holograms

Hologram definitions are indexed by dimension and chunk. Player synchronization only inspects nearby buckets before applying the exact configured distance check. Editing, replacing and deleting a hologram rebuilds the bounded index immediately.

The performance command reports index dimensions, cells, references and maximum bucket size.

## Block Information

Block Information separates target detection from full inventory scanning:

- target ray checks default to every 5 ticks;
- unchanged inventory content defaults to a full scan every 20 ticks;
- one modded inventory scan defaults to at most 1024 slots.

Server configuration keys:

- `blockInformationTargetRefreshTicks` — 1–40, default 5;
- `blockInformationContentScanTicks` — 5–200, default 20;
- `blockInformationMaxScannedSlots` — 64–4096, default 1024.

A target change still causes an immediate scan. Payloads are still sent only when the resulting content changed.

## Lazy player records

The following records are indexed at startup and loaded on demand:

- player homes — up to 256 clean owner records cached;
- player UI preferences — up to 512 clean records cached;
- Content Progression — clean offline records expire after ten minutes;
- mailboxes — up to 256 records cached, with eight offline mailboxes maintained per maintenance pass.

Dirty, queued, retrying and online records are not evicted. All writes continue through the existing storage service.

Permissions, custom statistics and economy accounts deliberately remain global because they provide complete lookup, leaderboard or aggregate views.

## Storage safety

The batched storage service now exposes whether a path is queued, actively writing or waiting for retry. Lazy caches use that state to avoid discarding records before their snapshot is durably handled. Storage operations are included in the performance timing report.

## Polling reductions

- Border synchronization has immediate login, respawn and dimension-change hooks, with a lower-frequency fallback check.
- Dialogue session expiry cleanup runs once per second instead of every server tick.

## Code deduplication

Dev16 introduces shared helpers for repeated low-risk structures:

- `WorldPositionValues` for normalized persistent dimension/position values;
- `PayloadBounds` for bounded network strings, lists and non-negative values;
- `SsuGuiGeometry` for common hit-testing and index wrapping.

Home, warp, spawn, minigame and dungeon locations now use the shared normalization rules. Sixty-three payload classes use the common bounds helper.

## Deliberately retained architecture

Two high-risk structural changes were not mixed into this runtime release:

1. `SsuDashboardScreen` and `SsuMenuService` remain large classes. Shared GUI primitives were introduced, but a complete page/controller split should be a dedicated UI-only release with full in-game regression testing.
2. Historical save migrations remain present. They cost little after startup and should only be removed after SSU declares a minimum supported upgrade version.
