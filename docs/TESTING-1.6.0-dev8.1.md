# SSU 1.6.0-dev8.1 gameplay test checklist

Use the same dev8.1 build on the client and server. Back up the world before testing.

## Main dashboard

1. Open the normal player dashboard and confirm the player tiles are Claims, Travel, Wallet, Mail and Profile; Regions must no longer appear here.
2. Confirm the top-right X closes the dashboard and that no large green Close arrow is shown at the bottom-left of the main page.
3. Open every player tile, Settings and Admin Center. Confirm each subpage has one compact arrow-only Back control and that it returns to the previous page without triggering another action.
4. Confirm the left profile panel shows the portrait, player name, primary rank and balance only. Claims, Homes and Rentals counters must be absent.
5. Hover each dashboard tile with dashboard hints enabled. Confirm its description appears as a tooltip instead of grey footer text.
6. Check the dashboard at a normal and a narrow GUI scale. Page titles should be larger, centered in the available header area and must not overlap the sidebar, X, Settings or Admin buttons.
7. Confirm the former grey page subtitle, home/admin heading and home/admin subheading are absent.

## Settings

1. Open Settings and confirm the short instruction reads: `Choose a category, then click a setting to change it.`
2. Switch through every category and confirm the category buttons and settings do not overlap the instruction.
3. Open Mail settings and confirm the labels use DELETE/KEEP wording for claimed player, server and auction mail.
4. Hover each of the three mail settings and confirm the tooltip explains the mail source and that DELETE happens only after all item and money attachments are claimed.
5. Toggle each mail setting, close Settings, reopen it and confirm the selected DELETE/KEEP state persists.

## Regression checks

- Admin Regions tools and existing region data remain available.
- Mail claiming, normal mail deletion and expiry behavior remain unchanged apart from the clearer preference labels.
- Dashboard Settings/Admin/X buttons still route correctly.
- Claims, Travel, Wallet, Mail and Profile pages still load their server-authoritative data.
- Existing module, border, hologram and player-preference data remains compatible.
