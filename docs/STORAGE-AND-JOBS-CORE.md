# Storage and job core — SSU 1.1.0-dev3

Dev3 introduces the first production-facing pieces of Core 2.0: coalesced storage and bounded server-thread jobs. Existing world data formats remain unchanged.

## Batched storage service

`BatchedStorageService` owns one daemon storage worker. Callers serialize immutable snapshots before enqueueing them; the worker never reads Minecraft world state.

### Guarantees

- Operations for the same normalized file path are coalesced so the newest queued state wins.
- Files are written through the existing atomic replacement helper.
- Deletions use the same ordered queue.
- Failed operations are retried up to three total attempts.
- Paths that still fail are exposed through `retryRequired` statistics and can be queued again by a later save.
- `flush` waits for queued work within a supplied timeout.
- Server shutdown queues final module saves and flushes before stopping the worker.

### Dirty record tracking

`DirtyJsonRecordStore` fingerprints serialized JSON per record. An unchanged record is not queued again unless its previous disk operation ultimately failed.

Dev3 migrates these paths:

- per-claim records;
- per-player claim-limit records;
- the claim player index;
- per-region records;
- region settings;
- border settings and per-player border preferences.

Homes, warps and permissions deliberately keep their old save paths in this milestone. Their migration will follow after dev3 is tested.

## Multi-tick job scheduler

`SsuJobScheduler` executes large world mutations on the normal server thread, but limits how much work they may perform in one tick.

Default global limits:

- 5,000 logical operations per tick;
- approximately 4 ms scheduler time budget per tick;
- maximum slice of 500 operations before another queued job gets a turn.

This preserves Minecraft thread safety while avoiding a single synchronous million-block loop.

### Migrated operations

- weighted region-selection fill;
- region clear;
- region snapshot reset;
- automatic reset triggered by region rent expiry or unrent.

For rent-triggered resets, SSU keeps the rental state until the reset job completes. If the job is cancelled or the server stops, the expired/pending rental can be retried instead of silently marking a partially reset region as finished.

### Resource locks

Jobs declare stable resource keys. A second job is rejected while the same region resource is owned by another job. This prevents clear/reset/fill conflicts involving the same server region and prevents a region from being rented while its old contents are still being reset.

### Commands

```text
/ssu core status
/ssu core jobs list
/ssu core jobs cancel <uuid>
```

These commands require `ssu.core.admin` for players. Console may use them directly.

`/ssu reload` is refused while jobs are active because reloading managers underneath a running world operation would be unsafe.

## Important limitations

Jobs are not persisted across restart in dev3. If a server stops or an admin cancels an active fill/clear/reset, already processed blocks remain changed and unprocessed blocks remain untouched. Test on a copy and let important jobs finish.

Snapshot reset still restores block states only. It does not yet preserve:

- container contents;
- block-entity NBT;
- sign text;
- entities;
- scheduled block ticks.

Persistent/resumable jobs and complete block-entity snapshots are separate future milestones.
