# SSU 1.9.0-dev3.21 - focused test checklist

This build introduces shared Content Core gameplay events, durable content rewards, generic objectives,
achievements, statistics schema changes, temporary permissions and World Edit preview changes. Run a normal
`clean build` first, then use this list for a focused client + dedicated-server smoke test.

## 1. Build / startup

- Run `./gradlew clean build` with the project's Java 25 / Gradle 9.2.1 toolchain.
- Start a dedicated server with an existing dev3.20.1 world copy.
- Confirm old statistics, quests, auctions, minigames, claims and identity/title data still load.
- Confirm the log reports achievement definitions/player-index loading without storage exceptions.
- Confirm a client with protocol 103 connects and an older protocol 102 build is rejected cleanly.

## 2. Shared Content Core events

Create temporary statistics/achievements for the following events and verify exactly one increment per real action:

- break/place a block;
- kill a mob and die once;
- deal/take damage;
- craft, right-click/use and consume an item;
- walk/sprint/swim/elytra travel;
- change dimension and enter a different biome;
- create a claim group and add chunks;
- finish a quest;
- play/win a minigame, kill/assist/heal/capture in a supported game;
- finish/fail dungeon events already exposed by the dungeon module;
- interact with an NPC/dialogue/service;
- buy/sell an auction.

Disable **Quests** while leaving Achievements enabled and repeat several vanilla actions. Achievement/statistic
progress must continue, proving vanilla Content Core events no longer depend on the Quest module.

## 3. Generic objective matching

Test an achievement with multiple required objectives; it must complete only when every required objective is met.
Also test:

- `ANY` target;
- `EXACT` target;
- `LIST` with multiple block/entity/item IDs;
- `TAG` using a valid block/item/entity registry tag available in the active datapacks;
- metadata filters such as `dimension=minecraft:the_nether`, `main_hand=minecraft:diamond_sword` and `self=false`;
- `COUNT`, `SUM`, `MAX` and `UNIQUE` aggregators;
- optional objectives (must not block completion);
- invalid/missing registry tags (must simply not match and must not break event handling).

## 4. Achievement administration

From Admin Dashboard -> Achievements:

- create an achievement with ID, icon, category, rich-text title/info, objective(s), no reward;
- edit it and confirm its ID cannot be renamed in place;
- create a second achievement and verify duplicate IDs are rejected;
- disable/re-enable an achievement;
- create a hidden achievement;
- delete a test achievement and restart the server; it must remain deleted;
- target an offline previously-seen player by name and by UUID;
- use `Reset` and verify progress can be earned again without re-paying the old reward;
- use `Reset + reward` and verify a new reward generation can deliberately pay again.

## 5. Player achievement browser / privacy

- Player Dashboard -> Achievements opens correctly.
- `All`, `Earned` and `Unearned` filters work across multiple pages.
- Earned date and objective progress are shown.
- Compare another player's visible achievement with the viewer's own progress.
- A non-earned hidden achievement is absent for a player who does not know it.
- The player who earned a hidden achievement can see it.
- Another viewer who has also earned that hidden achievement can compare it.
- Achievement chat announcements are clickable and open the comparison view.
- For a hidden achievement, viewers who have not earned it only see a generic hidden-achievement announcement.

## 6. Rewards / restart safety

Use separate test achievements for each reward type:

- `give_money`;
- one or more `give_item` rewards;
- exact `stack_json` item reward with components/custom data;
- `grant_permission` / `set_permission` as appropriate;
- `grant_temporary_permission` with a short duration;
- `unlock_title`;
- `unlock_cosmetic` generic entitlement;
- `unlock_cosmetic` with `id=minigame:victory:spark` (or another existing minigame cosmetic entitlement);
- `add_claim_chunks`;
- no reward / bragging rights only.

For item rewards, fill the inventory first and verify the mail fallback when Mail is available.
For temporary permissions, verify base permissions are not overwritten and expiration removes only the temporary layer.

Hard-crash testing is best done on a disposable world copy. Interrupt once after completion/reward preparation and
restart. The reward ledger must fail closed: the same reward generation must never be blindly paid twice. Inspect
`content/reward_ledger.json` if recovery is intentionally required.

## 7. Offline durable events

- List an auction, log the seller out, then complete the purchase with another player.
- Verify seller achievement/statistic sale/revenue progress is persisted while the seller is offline.
- Log the seller back in and verify a newly completed achievement receives its pending reward/announcement once.
- Restart after the committed purchase and verify auction durable-event replay does not double-count it.

## 8. Statistics regression

- Existing schema-1 definitions migrate to schema 2 and still display correctly.
- Minigame wins/kills/healing/etc. increment once, not twice.
- Normal gameplay should only load active player statistic records; leaderboard/rank/total queries may load all records.
- Put a deliberately higher/future schema in a disposable copy and confirm dev3.21 refuses to overwrite it.

## 9. World Edit snapshot preview

- Small saved selection: preview appears exactly as before.
- Large/high-palette saved selection: all palette segments and block chunks reconstruct correctly.
- Move/rotate/mirror the preview and verify no missing/shifted blocks.
- Walk so most of a large preview is outside the camera frustum; FPS/server traffic should remain reasonable.
- Cancel/close/reopen preview and verify stale session chunks do not leak into the new preview.

## 10. Entity Insight

- Armor Stands remain excluded.
- Hit a normally non-hostile mob and verify FLEEING appears while it is recently moving away.
- Wait more than the flee TTL, then move relative to the old attacker; stale FLEEING must not reappear.
- Hostile targeting must still take precedence over FLEEING.
- With multiple nearby entities, verify unchanged snapshots are not continuously re-sent to the client.

## 11. Reload / shutdown

- Use the SSU reload workflow and confirm achievements, temporary permissions and the reward ledger reload safely.
- Stop the server normally and restart; earned progress, reset generations and pending/committed reward state persist.
- Re-run a few quest/statistic/minigame/NPC flows after reload to catch subscription duplication.
