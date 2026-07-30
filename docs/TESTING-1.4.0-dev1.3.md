# SSU 1.4.0-dev1.3 test plan

Use Java 25 and test on a copy of a confirmed dev1.2 world. Client and server must both use dev1.3 because the protocol is 15.

## 1. Build and migration

Run:

```bat
gradlew.bat clean test build
```

Start the copied world and confirm claims, regions/rentals, economy, ranks, personal overrides, homes, warps, snapshots and map cache still load without migration errors.

## 2. Compact permission editor

1. Open Admin Center → Players & Permissions.
2. Select Players and choose a known player.
3. Confirm the yellow selected-player/rank summary is fully below the permission-search box and no text overlaps.
4. Confirm rows no longer show `module default | effective` beneath every key.
5. Confirm a normal-size dashboard shows up to ten permission rows and pagination remains usable on smaller GUI scales.
6. Hover boolean, integer and custom keys. Verify the tooltip contains description, type/range, `Default: ...`, effective source and direct override where applicable.
7. Toggle a boolean, set an integer, reset each override and confirm the effective values update correctly.
8. Repeat in Rank mode, including a wildcard permission such as `ssu.claims.*`.

## 3. Player dropdown and search

1. Open Admin Center → Player Info.
2. Confirm the dropdown is alphabetically ordered.
3. Confirm online players are marked online and previously known offline players remain listed.
4. Type a partial name, press Search and confirm the dropdown is filtered.
5. Type an exact known name and confirm the profile loads.
6. Clear the search and confirm the complete bounded list returns.

## 4. Profile data

For an online player, verify UUID, ranks, access, balance, claims/chunks, homes, rentals, region names, dimension, position, health/food and override count.

Repeat for an offline player. Live-only fields must clearly report offline/unavailable rather than showing data from another player.

Compare claim/home/rental/balance totals with existing commands or dashboard pages.

## 5. Effective permission list

1. Inspect a player using only the default rank.
2. Inspect a player with an assigned rank, wildcard permission and personal override.
3. Page through the full permission list.
4. Confirm values and sources match the permission editor.
5. Confirm custom keys already stored on the player or assigned rank are included.

## 6. Edit permissions handoff

With permission-admin access, choose a player and press **Edit permissions**. Verify:

- the permissions page opens in Player mode;
- the same UUID/name is already selected;
- permissions load immediately;
- Back returns to Player Info with the inspected player retained.

An administrator without `ssu.permissions.admin` may inspect profiles but must not receive an active edit button.

## 7. Security and protocol

- A non-admin must not be able to request profile data by constructing a packet.
- Invalid page sizes and oversized lists must be rejected/bounded.
- Rapidly switch searches/players and confirm an older response never replaces a newer selection.
- A protocol-14 client must be rejected instead of being mixed with protocol 15.
