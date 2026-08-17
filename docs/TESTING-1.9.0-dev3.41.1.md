# SSU 1.9.0-dev3.41.1 test checklist

## Startup lifecycle
- Launch the NeoForge client/server from a clean process.
- Confirm SSU reaches the title screen/server startup without `Cannot get config value before config is loaded`.
- Confirm COMMON config values are honored once the server starts.
- Open Dashboard -> Module Settings and verify configured/effective ON/OFF/BLOCKED states are populated normally.

## Dependency regression
- Economy OFF with Auction configured ON -> Auction BLOCKED; restore Economy -> Auction returns.
- Claims OFF + Permissions ON -> Permissions stays active.
- Permissions OFF + Claims ON -> Claims stays active.
- Regions ON + Economy OFF -> Regions stays active and rent/economy integration pauses.
- Teleport remains active with Claims/Regions/Permissions off.

## Runtime refresh
- Toggle at least Economy, Claims and Permissions ON -> OFF -> ON from Module Settings.
- Confirm configured state updates immediately after refresh and blocked dependants follow in dependency order.
- Restart after toggling and confirm the same configured/effective states are reconstructed after config load.
