# Player profile and permission UI — SSU 1.4.0-dev1.3

## Compact permission rows

The permission editor keeps the Player/Rank filter, target dropdown, rank assignment controls and separate target/permission searches from dev1.2. The selected target summary now occupies a dedicated row below the permission search instead of sharing its vertical space.

Permission rows only show the key and its type-appropriate editor. The repeated `module default | effective: ...` subtitle was removed so a normal dashboard can show up to ten keys per page. Hovering a row now provides:

- the permission description;
- boolean/integer/text input type and numeric bounds where applicable;
- the module default;
- the effective resolved value and source;
- the exact direct override when present.

Reset still removes only the exact direct player/rank override. Wildcard, inherited-rank and module-default resolution remain server-authoritative.

## Player Info & Profile

The Admin Center now contains a separate Player Info category. The page is available only to dashboard administrators and is backed by dedicated request/response payloads.

The first dropdown is alphabetically sorted and merges:

- online players;
- offline players remembered by the permission store;
- offline players that still have an economy account.

A separate text field filters the dropdown. Entering an exact known name loads that profile directly; partial input narrows the dropdown.

The selected profile displays:

- UUID and online/offline status;
- primary and assigned ranks;
- live administrator status when online;
- economy balance or missing-account state;
- claim-group and claimed-chunk counts;
- home and active-rental counts plus rented region names;
- live dimension, coordinates, health and food when online;
- the count of direct personal permission overrides.

Every effective permission is shown as paged text with its final value and resolution source. Built-in catalogue keys and existing custom keys from the player or assigned ranks are included.

Administrators with `ssu.permissions.admin` can use **Edit permissions**. This switches to the permission editor in Player mode and preselects the inspected player.

## Security and compatibility

All profile data is assembled on the server. Requests are bounded, require administrator access, and never accept arbitrary permission values or commands. Client-side request IDs reject stale profile responses.

Network protocol is 15. No world/player save schema changed.
