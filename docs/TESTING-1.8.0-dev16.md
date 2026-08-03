# SSU 1.8.0-dev16 testing guide

Use Java 25 and build both client and dedicated server from the exact same source:

```bat
gradlew.bat clean build
```

Always test on a backup of the world first.

## 1. Startup and migration

1. Start with an existing dev15.4 world containing economy accounts, transactions, homes, mailboxes, UI preferences, NPCs and holograms.
2. Confirm startup completes and the log reports indexed/lazy records and retained economy history.
3. Confirm Economy settings migrate to schema 2 with history retention 50.
4. Confirm NPC definitions migrate to schema 8.
5. Restart again and confirm no repeated migration or broken-file archives appear.

## 2. Economy retention

1. Create more than 50 completed transactions for one player, including payments and shop/mail/Auction House flows.
2. Flush storage or stop the server cleanly.
3. Confirm the player sees only the latest 50 retained transactions.
4. Confirm older completed transaction JSON files disappear after queued storage work completes.
5. Confirm PREPARED records are not deleted.
6. Change the value in the dashboard and with:

```text
/ssu admin economy history-limit 75
```

7. Verify the range rejects 0 and 1001.
8. Restart and confirm the configured value persists.
9. Re-submit a recent idempotency key and confirm it is rejected after the full transaction record is pruned, while still represented by the compact committed-key index.

## 3. NPC performance and behavior

1. Test normal NPC schedules, No-AI gravity, faction combat and hostile-player targeting.
2. Test with approximately 50, 100 and 200 active NPCs spread over several chunks.
3. Confirm NPCs do not target entities outside their follow range or another dimension.
4. Confirm deleting/editing a definition refreshes active tick sets.
5. Run `/ssu core performance` and inspect the NPC rolling average and p95.

## 4. Holograms

1. Test text, image, link and scoreboard holograms.
2. Place holograms across several dimensions and distant chunks.
3. Confirm only nearby holograms synchronize and all edit/move/delete changes appear immediately.
4. Check the hologram spatial-index statistics in `/ssu core performance`.

## 5. Block Information

1. Look at vanilla containers, item frames, armor stands and supported modded inventories.
2. Keep looking at one large modded storage and change its contents.
3. Confirm the overlay updates within the configured content-scan interval.
4. Move to another target and confirm the new target updates immediately.
5. Test configuration values at their minimum/default/maximum boundaries.

## 6. Lazy player records

1. Start a world with many offline home, mail, UI-preference and Content Progression files.
2. Confirm startup indexes these records without loading every record.
3. Access records for several offline players and verify data is correct.
4. Modify a record, immediately create cache pressure, restart and confirm no queued write was lost.
5. Check mailbox retention/queue promotion over several maintenance cycles.

## 7. Borders and dialogues

1. Confirm borders synchronize on login, respawn and dimension change.
2. Confirm fallback updates still catch claim/region changes.
3. Open and leave dialogue sessions idle; confirm normal expiry still works.

## 8. Storage and shutdown

1. Perform rapid changes to homes, mail, preferences and economy data.
2. Stop the server cleanly and restart.
3. Confirm there are no missing records, stale pending writes or duplicate transactions.
4. Use `/ssu core performance` before and after `/ssu core performance reset`.
