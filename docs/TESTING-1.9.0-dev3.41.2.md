# SSU 1.9.0-dev3.41.2 test checklist

## Player Dashboard visibility
- Open the player Dashboard with all normal modules enabled and confirm the expected tiles are present.
- Disable Claims and confirm `Claims & Land` disappears instead of becoming grey.
- Disable Economy and confirm `Wallet` disappears; if Auction House remains configured ON it should be BLOCKED and its tile should also remain absent.
- Disable Mail, Kits, Mines, Warps, Quests, Achievements, Minigames and Dungeons one at a time and confirm their player-facing tiles disappear.
- Re-enable each module and confirm its tile returns after the module refresh.
- Confirm Travel, Support, Cosmetics and Profile remain available where normally shown.

## Admin visibility
- Open the Admin Dashboard / Module Settings with disabled modules and confirm they remain visible there as OFF or BLOCKED so they can still be managed.

## Module Settings layout
- Open Module Settings at the normal SSU GUI scale and confirm the two explanatory lines do not overlap the module range indicator, scroll arrows or first module row.
- Change SSU GUI scale through the supported range and confirm the intro text clips cleanly with an ellipsis when space is tight.
- Scroll the module list and confirm the range counter and arrows remain readable.

## Regression
- Confirm startup remains free of `Cannot get config value before config is loaded`.
- Economy OFF with Auction configured ON -> Auction BLOCKED; restore Economy -> Auction returns.
- Claims OFF + Permissions ON and Permissions OFF + Claims ON remain valid.
