# Runtime test checklist — SSU 1.9.0-dev3.40.2

## Dashboard fixed four-column layout

1. Open Dashboard on a wide display and confirm Home remains four columns.
2. Repeat on a user/display combination that previously produced three columns. Home must still show four columns.
3. Test SSU GUI Scale at 100%, 90%, 80%, 70% and 60%; the module column count must remain four at every setting.
4. With 12 Home modules visible, confirm layout is 4 columns × 3 rows and every tile/label remains inside the panel.
5. Enable optional Home modules so 13–16 tiles are visible; confirm 4 columns × up to 4 rows remains inside the standard panel.
6. Confirm tile hover/click hitboxes still match visuals after SSU scaling.
7. Confirm Admin dashboard still uses four columns.
8. Confirm non-Dashboard module grids such as Economics retain their existing responsive layout.

## Fixed logical canvas / automatic fit

9. On a narrow logical viewport, confirm the portrait sidebar is still present and Home does not switch layout.
10. Leave configured SSU scale at 100% on a viewport smaller than 680×390 logical pixels; the Dashboard should automatically fit rather than clip/reflow.
11. Confirm automatic fit uses the same transformed mouse coordinates: every tile, Profile, Settings and Close button must click exactly where rendered.
12. Open Wallet/Profile and confirm their compact 544×312 canonical canvas also auto-fits without reflow.
