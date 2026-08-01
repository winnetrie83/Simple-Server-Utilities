# SSU 1.6.0-dev12.11 test checklist

Use the exact same dev12.11 jar on the client and dedicated server. Network protocol remains 36.

## World Map marker context panel

1. Open the World Map and right-click empty terrain.
2. Confirm the dark framed panel fully fills the space between **Add marker** and **Close**.
3. Right-click an existing marker.
4. Confirm terrain is no longer visible:
   - between **Edit** and **Delete**;
   - between the top button row and **Close**.
5. Confirm Add/Edit/Delete/Close still react normally and their hover tooltips remain available.

## World Map legend

1. Open the World Map.
2. Confirm the yellow square in **LAYERS** is labelled **Player**.
3. Toggle personal markers with the left-side `M` control.
4. Confirm marker circles toggle while the yellow player square remains visible on the map and the Player legend swatch remains active.

## Regression

- Middle-mouse panning still works.
- Right-click marker create/edit/delete still works.
- Claims and server-region layers still toggle.
- Map cache, live terrain radius, relief and minimap remain unchanged.
