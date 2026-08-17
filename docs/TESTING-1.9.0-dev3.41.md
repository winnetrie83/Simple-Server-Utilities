# SSU 1.9.0-dev3.41 test checklist

## Baseline / startup
- Start a copied world from working dev3.40.6.1.
- Confirm normal client/server startup and existing NPC/ability behavior.
- Open Dashboard -> Module Settings; verify ON/OFF/BLOCKED and dependency tooltips.

## Permissions inversion
- Claims ON + Permissions OFF: Claims stays active using built-in ownership/default rules; operators retain admin access.
- Claims OFF + Permissions ON: Permissions remains active and global/non-claim administration works.
- Re-enable both and confirm ranks, overrides and claim data persist.

## Hard-dependency cascades
- Economy OFF with Mail/Auction configured ON -> Mail and Auction become BLOCKED, configured preferences remain ON; Economy ON restores them in order.
- NPC Core OFF with NPC Shops configured ON -> NPC Shops BLOCKED; restoring NPC Core restores Shops.
- Regions OFF -> Mines/Minigames/Dungeons/Jails become BLOCKED as applicable; Teleport remains active.
- Moderation OFF -> Jails BLOCKED; restoring Moderation restores Jails.

## Optional degradation
- Regions ON + Economy OFF: ordinary region/protection/snapshot operations continue; rent/economy operations pause.
- Teleport ON + Claims/Regions/Permissions OFF: standalone teleport continues.
- Spawn ON + Teleport OFF: respawn fallback continues; `/spawn` travel reports that the Teleport engine is unavailable.
- Visualization ON while Claims/Regions independently toggle: stale border layers clear and only active sources render.
- Mines ON + Holograms OFF: mine logic continues; Holograms ON recreates/refreshed managed mine holograms.
- Statistics ON + Holograms OFF: statistic administration works without hologram calls.
- Warps ON + Mail OFF: rental lifecycle completes without optional expiry mail.
- Quests ON + NPCs OFF with quest access `npc`: effective access falls back to menu; `menu`/`npc`/`both` work when NPCs return.
- NPC Core ON with Quests/Minigames/Dungeons/Warps/Mail/Auction/NPC Shops/Teleport selectively OFF: NPC editor/runtime stays usable and does not touch unloaded optional managers.
- Identity ON + Permissions/Minigames OFF: titles remain usable; rank/minigame unlock integrations safely disappear.

## Runtime data safety
For Economy, Permissions, Claims, Regions, NPCs and Mines: test ON -> OFF -> ON in one session, restart while OFF, then re-enable. Confirm final saves happen, no duplicate runtime objects appear, and persistent data survives.
- Claims ON + Homes OFF: delete/resize a claim and confirm disabled Home storage is not loaded or deleted; re-enable Homes and verify its persisted data is still present.
- Regions ON + Economy OFF: verify no legacy rental amount is migrated through unloaded/default Economy settings; paid rent actions remain paused until Economy returns.

## Rewards / reload
- Minigame rewards with Mail ON use mail path; with Mail OFF use Content Core fallback.
- Mail OFF + full inventory, or Economy OFF + money reward: fail closed instead of silently losing rewards.
- Use SSU reload after changing module settings; confirm module graph refreshes first, newly enabled modules load once, and blocked modules are not reloaded through legacy paths.

## Regression sweep
Claims/protection, Homes/Warps/Spawn/Teleport, Mail/Auction, NPCs/NPC Shops, Quests, Minigames, Dungeons, Mines, Moderation/Jails/Onboarding/Kits, Holograms/Statistics/Achievements/Community Statistics, Map Markers/maps, Block Information and Utility Mining.
