# Dashboard and Core modules — SSU 1.4.0-dev1

## Scope

This build combines roadmap phases 4 and 5:

- a scalable, page-driven dashboard and Admin Center;
- the first broad lifecycle-managed Core 2.0 migration.

It intentionally does not add new save formats or change the confirmed dev4.3 map renderer.

## Compact dashboard shell

Opening `U` or `/ssu menu` now sends only the information required to draw the dashboard shell:

- player name and primary rank;
- feature and administration access, including separate permission-admin, core-admin and rent-policy-admin capabilities;
- UI/minimap and border preferences;
- wallet summary and economy policy summary;
- scheduler/storage counts;
- aggregate claim, chunk, region, rental, home and warp counts;
- core performance counters.

The old compatibility lists in the shell payload are empty. Large collections are requested only when their page is opened.

## Page-specific data

The client can request one bounded page at a time for:

- owned claims;
- homes and warps;
- visible/rentable regions;
- player or admin-visible transactions;
- economy accounts;
- active jobs;
- rent-recovery journal operations;
- ranks or a selected player's permission data.

Each request contains a page identifier, page index, bounded page size, bounded search query and monotonically increasing request ID. The client ignores stale responses. The server limits every returned list to at most 50 entries and bounds every encoded string.

Default dashboard pages use six rows, keeping network and rendering work small even on a large server.

## Typed actions

Dashboard controls send a closed action ID plus bounded fields. The server switch accepts only implemented actions. There is no arbitrary command field.

Implemented action families:

- wallet payments;
- persistent player UI/minimap settings;
- claim and region border visibility;
- claim-map opening and claim visualization;
- region visualization, rent, extend and two-click-confirmed cancellation;
- home and warp teleport requests;
- rent-economy policy percentages;
- economy account give, take and set;
- player rank assignment;
- personal permission set and unset;
- active-job cancellation;
- performance-counter reset.

Every action independently rechecks relevant permissions and resolves its target on the server. Admin tiles are disabled from explicit capability flags, but those flags are presentation only: a crafted packet still cannot obtain authority from a visible client button.

## Detail and administration views

The dashboard now exposes richer server data without sending it all at login:

- claim IDs, dimension, chunk count, trusted players, spawn and flags;
- region bounds, owners, members, flags, priority, volume, spawn, snapshot/job state and rent policy;
- complete transaction source, destination, actor, module, reason, failure and timestamps;
- economy account balance/revision/update information;
- active-job operations and progress;
- rental recovery status, amounts and errors;
- rank permissions and selected-player rank/personal overrides.

Search, current page and unfinished input are preserved across shell refreshes. Server action results can refresh only the affected page or the compact shell.

## Network compatibility

The protocol is `13`. All players must use the matching 1.4.0-dev1 client when the server runs this build. A dev4.3 client/server pair uses protocol 12 and is intentionally incompatible.

## Core 2.0 module migration

Claims, permissions, regions/snapshots/rent journal, teleports, visualization and menu services now own their registration and lifecycle through `SsuModule` implementations.

This is an ownership migration, not a save migration. Existing managers and commands remain authoritative. World data paths and JSON schemas are unchanged.

## Map compatibility

The dev4.3 map renderer, persistent aerial-tile format and renderer fingerprint remain unchanged. Installing 1.4.0-dev1 should not invalidate or rebuild the dev4.3 map cache.
