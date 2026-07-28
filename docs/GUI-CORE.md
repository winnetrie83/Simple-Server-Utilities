# GUI Core — SSU 1.1.0-dev4

## Goal

Dev4 introduces the first common screen and networking foundation for all future SSU modules. It intentionally uses real existing services rather than a disconnected visual mock-up.

The server remains authoritative. The client receives a bounded snapshot, renders and navigates locally, and sends existing commands for requested actions. The commands perform the normal permission and state validation.

## Opening the dashboard

- Default key: `U` (configurable in Minecraft Controls)
- Command fallback: `/ssu menu`

The dashboard does not pause the game.

## Current player pages

- **Home** — entry cards and counts.
- **Claims** — owned claim list and focused claim visualization.
- **Travel** — homes and available server warps with teleport actions.
- **Settings** — personal nearby claim- and region-border toggles.

## Current admin pages

Admin access is detected from operator/admin status or the existing region, permission, Core or visualization administration permissions.

- **Regions** — list all server regions and independently show or hide each border.
- **Core** — active long-running jobs and pending storage writes.

## Networking

`SsuMenuSnapshotPayload` is server-to-client only and includes bounded summaries rather than mutable manager objects:

- player/admin access state;
- personal border preferences;
- owned claims;
- admin region summaries;
- homes and permitted warps;
- job and storage counters.

Every list has a hard maximum of 4,096 records. Future large modules such as mail and Auction House should use paged request/snapshot/delta payloads rather than extending this opening snapshot indefinitely.

## Architecture rule for future modules

Each module should expose reusable server services. Commands and GUI requests call those same services; business rules must not be duplicated in screen classes.

Planned reusable additions include:

- navigation stack and breadcrumbs;
- searchable and sortable list components;
- tabs, toggles, numeric fields and dropdowns;
- item-slot grids and item previews;
- confirmation and error dialogs;
- permission-source and effective-value explanations;
- paged request/snapshot/delta networking;
- custom SSU visual theme and textures.

## Dev5 claim-map integration

The Claims page now opens the interactive claim-management map rather than only focusing a world border. The map remains a separate screen because it needs a large square canvas, panning, zooming and multi-chunk selection. Returning through **Back to SSU menu** requests a fresh server-authoritative dashboard snapshot.

The map uses the same design rule as the dashboard: client-side interaction and presentation, followed by a bounded server request that revalidates permissions and state before mutation.
