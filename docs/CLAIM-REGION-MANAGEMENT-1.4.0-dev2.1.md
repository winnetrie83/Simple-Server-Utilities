# Claim and Region Management — 1.4.0-dev2.1

## Claim map deletion

The interactive claim map now has a `Delete claim` button for the currently selected claim group. The first click arms the action and changes the label to `Confirm delete`; the second click sends the typed delete request.

The server rechecks:

- claim-map access;
- `ssu.claims.delete`;
- the selected claim group;
- claim ownership.

A successful deletion removes the entire connected claim group, clears its chunk-index records, hides its active border visualization and refreshes the map with no selected claim.

## Claim settings editor

The claim Settings button is available from both the dashboard claim page and the interactive claim map.

The editor includes:

- PvP;
- explosions;
- pistons;
- water, lava and other-fluid flow;
- redstone;
- hoppers;
- ownerless projectiles;
- fire spread;
- welcome message;
- trusted-player overview;
- trust and untrust actions for online or previously known players;
- set or clear claim spawn.

Individual controls respect their existing permission keys. For example, protection flags use claim flag permission while trust changes use claim trust permission. The server always verifies ownership again.

## Region settings editor

The region Settings button is available from the dashboard region page.

The editor includes:

- block break and place;
- interaction;
- PvP;
- explosions;
- pistons;
- water and lava flow;
- redstone;
- hoppers;
- fire spread;
- priority;
- welcome and leave messages;
- manager overview and add/remove actions;
- member overview and add/remove actions;
- rentable state;
- rent price;
- rent period;
- reset on expiry and cancellation;
- set or clear region spawn.

Region managers can administer ordinary region settings. Changing manager/member lists requires region-administration access. Rental fields require rent-administration access.

## Tooltips and input types

Every row has a tooltip containing:

- a short explanation;
- input type;
- allowed range for integer values;
- default value where applicable;
- current value.

Boolean values use ON/OFF buttons. Integer and text values use an input field and Set button. Access lists are read-only summaries with separate add/remove fields. Spawn operations use explicit action buttons.

## Network and authority

The settings interface uses three bounded payloads:

- request settings data;
- submit one typed setting action;
- receive the refreshed authoritative settings list.

The client never writes claim or region data directly. Every action is resolved against current server state and permissions before persistence.

## Deferred contextual permissions

Dimension-, region- and temporary-session permission scopes are intentionally deferred to the next major permission milestone. Dev2.1 keeps the existing region permission-override data compatible but does not yet expose the proposed scoped permission editor.
