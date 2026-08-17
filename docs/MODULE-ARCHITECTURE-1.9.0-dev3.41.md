# SSU module architecture — 1.9.0-dev3.41

## Model
SSU now separates **configured state** from **effective state**. A feature may stay configured ON while a missing required dependency safely suspends it (`BLOCKED`). When the dependency returns, the feature restarts automatically without rewriting its configured preference.

Dependency links have three meanings:
- **Required**: hard lifecycle dependency; participates in startup/shutdown ordering and can block activation.
- **Optional**: adds capability but never disables the consumer; calls must degrade safely.
- **Integration**: cross-feature bridge metadata; never controls activation.

## Core infrastructure
Storage, Transactions, Jobs, Performance, Content Core, UI Preferences, and the Menu/Dashboard shell are core infrastructure. They are not ordinary gameplay switches.

## Feature graph
| Feature | Required | Optional | Integration |
|---|---|---|---|
| Economy | Storage, Transactions | — | — |
| Permissions | Storage | Claims, Regions, Minigames, Moderation | — |
| Claims | Storage | Permissions, Economy, Homes, Mail, Regions, Visualization | — |
| Regions | Storage, Jobs | Economy, Permissions, Server Operations, Claims, Minigames | Teleport, Visualization |
| Teleport | — | Claims, Regions, Permissions, Moderation | Minigames |
| Spawn | Storage | Permissions, Teleport, Minigames, Dungeons | — |
| Homes | Storage | Claims, Permissions, Teleport | — |
| Warps | Storage | Economy, Permissions, Teleport, Mail | — |
| Dimensions | Storage | Permissions, Server Operations | — |
| Visualization | Storage | Claims, Regions, Permissions | — |
| Map Markers | Storage, UI Preferences | — | — |
| Mail | Storage, Transactions, Economy | Permissions | — |
| Auction House | Storage, Transactions, Economy, Mail | Permissions | — |
| NPC Core | Content Core, Storage | Permissions | Quests, Minigames, Dungeons, Warps, Mail, Auction House, NPC Shops, Teleport |
| NPC Shops | Storage, Transactions, Economy, NPC Core | Permissions | — |
| Quests | Content Core, Storage | Permissions | NPCs, Economy, Mail |
| Minigames | Content Core, Storage, Regions, Jobs | Permissions, Moderation, Onboarding, Visualization, Server Operations, Mail, Economy | NPCs, Quests, Economy, Mail, Visualization |
| Dungeons | Content Core, Storage, Regions | Permissions | NPCs, Economy, Mail |
| Mines | Storage, Regions, Jobs | Permissions, Holograms, Server Operations, Moderation | — |
| Moderation | Storage | Permissions, Spawn, Economy, Mail, Regions, Jails, Mines, Server Operations, Teleport, Minigames, Dungeons, Onboarding | — |
| Jails | Storage, Regions, Moderation | Permissions, Economy, Server Operations | — |
| Kits | Storage | Permissions, Economy, Server Operations | — |
| Onboarding | Storage, Spawn | Permissions, Moderation, Server Operations | — |
| Server Operations | Storage | Permissions, Economy, Moderation, Minigames, Dungeons | — |
| Holograms | Storage | Permissions, Statistics, Mines, Regions | — |
| Statistics | Storage | Holograms | — |
| Community Statistics | Storage, Content Core | — | — |
| Achievements | Storage, Content Core | Permissions | Economy, Mail |
| Identity / Titles | Storage, UI Preferences | Permissions, Minigames | — |
| Utility Mining | Storage, UI Preferences | Claims, Regions, Permissions | — |
| Block Information | UI Preferences | Permissions | — |

Crops Harvesting and Remote Hologram Images remain lightweight configuration features rather than independent lifecycle modules.

## Key corrections
- **Permissions no longer depends on Claims.** It is a standalone policy service; Claims can optionally consult it.
- **Regions no longer depends on Economy.** Ordinary regions/protection/snapshots continue; rentals pause when Economy is unavailable.
- **Teleport no longer depends on Claims/Regions/Permissions.** Those modules contribute optional checks.
- **Dashboard is a core shell**, not a hard dependant of gameplay modules.
- True implementation dependencies remain hard: Auction -> Economy + Mail; NPC Shops -> NPC Core + Economy; Mines/Minigames -> Regions + Jobs; Jails -> Regions + Moderation; Dungeons -> Regions.
- Spawn now requires only Storage: respawn fallback remains available without the Teleport engine, while `/spawn` travel reports Teleport as unavailable.
- Identity requires UI Preferences for its player-facing state, while Permissions and Minigames are optional integrations.

## Runtime transitions
On a configuration/dependency change the registry computes the target graph, stops blocked modules in reverse required-dependency order, flushes queued storage, starts newly available modules in dependency order, then calls `onDependencyStateChanged` on the stable active graph for optional bridge refreshes.

## Economy provider boundary
`EconomyService` remains the portable contract. New `EconomyProvider` metadata and `EconomyServices` active-provider lookup prepare alternative providers. dev3.41 still uses only `ssu_digital` / **SSU Digital Wallet**; balance/currency persistence is unchanged.

## Compatibility
- Baseline: supplied working `1.9.0-dev3.40.6.1` source.
- New build: `1.9.0-dev3.41`.
- Network protocol: `119` (Dashboard module-state metadata changed the snapshot payload).
- No intentional persistence schema changes; disabling/blocking a module does not delete its data.
