# SSU 1.6.0-dev8 gameplay test checklist

Use the same dev8 JAR on the client and server. Back up the world before testing.

## Module lifecycle

1. Open **Admin Center → Module settings** as an operator/admin.
2. Disable Claims, Homes, Warps, Regions, Mail and Floating Text / Media one at a time.
3. Confirm each disabled tile/tool is unavailable and its command root is hidden or denied.
4. For Floating Text / Media, keep the named Hologram Tool in hand and confirm that right/left clicks no longer perform SSU actions.
5. For Regions, keep the named Region Tool in hand and confirm it no longer selects or opens the region editor.
6. Re-enable every module and confirm the previously stored claims, homes, warps, regions, mail and holograms return unchanged.
7. Disable a module, run `/ssu reload`, and confirm the disabled module remains inactive and its stored data is not loaded into runtime.
8. Restart the server with several modules disabled and confirm they remain inactive until re-enabled from the Admin Center.
9. With Permissions disabled, reconnect a player and confirm no permission profile is recreated until Permissions is enabled again. With Regions disabled, leave the server running for more than one minute and confirm no rent-expiry processing occurs.

## Hologram distance and synchronization

1. Set hologram render/load distance to 32 blocks.
2. Place text, link, scoreboard, internal image and remote image holograms.
3. Walk beyond 32 blocks and confirm all types disappear; image loading should not start for sources that were never brought into range.
4. Walk back into range and confirm all types return. Verify links remain targetable up to the configured/effective distance.
5. Set one hologram's individual view distance below the global distance and confirm the shorter value wins.
6. Disable Remote Images and confirm already-existing HTTP(S) image holograms disappear while internal resource images remain.
7. Disable Floating Text / Media and confirm every hologram is removed immediately from connected clients.

## Claim and region border distance

1. Set claim and region border distance to 32 blocks.
2. Enable both border types and confirm nearby borders render.
3. Move farther than 32 blocks from their nearest edge and confirm they disappear. Test a very large claim/region while standing inside it: edges/faces farther than 32 blocks must still be clipped and invisible.
4. Pin/show a region, move away, and confirm the pinned border also obeys the distance.
5. Raise each distance independently and confirm claim and region ranges change separately.
6. Disable Claims or Regions and confirm the corresponding overview, focused/pinned border and region selection disappear immediately.

## Region jobs

1. Start a sufficiently large region save, reset or world-edit job.
2. Disable Regions while the job is active.
3. Confirm the job is cancelled cleanly, no further world changes occur, and Regions can be re-enabled afterward.

## Regression checks

- Treecapitator, Veinminer and Crop Harvesting retain their prior behavior when enabled.
- Turning Treecapitator or Veinminer off clears the current preview and held-key state.
- Admin dashboard navigation and back buttons still route correctly.
- Existing hologram rich text, multiline background, image animation and editor coordinates remain unchanged.
- Existing claim/region border colors and player visibility preferences migrate unchanged.
