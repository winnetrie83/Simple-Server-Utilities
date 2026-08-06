# SSU 1.9.0-dev3.0 — Minigame Experience test plan

## Compatibility

Use the exact same dev3.0 build on client and server. Back up the world and `simpleserverutilities/minigames` storage before testing.

## Core lifecycle

1. Queue enough players for CTF, Domination and Spleef.
2. During preparation, verify late joins remain possible and ready-state updates are visible.
3. Verify no player may join after RUNNING begins.
4. Disconnect and reconnect inside/outside the configured grace period.
5. Trigger AFK warning and removal without affecting active players.
6. Force tied objective scores and verify overtime, then verify a normal finish.
7. Vote rematch, next arena and leave with multiple vote combinations.
8. Confirm forced/test starts do not award XP, ratings, statistics or summary mail.

## Results, rewards and transactional progression

- Generate kills, assists, damage, healing, captures, defenses and objective time.
- Verify the kill feed, compact/expanded/hidden HUD modes and post-match result ordering.
- Confirm the result screen shows the projected XP/level immediately, but progression storage does not change until reward delivery succeeds.
- Force a participation or winner reward failure and verify cleanup pauses with no XP, rating, weekly, history or summary-mail settlement.
- Repair the reward dependency and verify one reward package, one progression settlement and one history entry are produced.
- Interrupt progression/history storage after reward delivery and verify cleanup retries without repeating rewards or applying XP twice.
- Force one summary-mail delivery to fail after durable settlement; verify the retry uses the same correlation key and does not duplicate earlier summaries.
- Verify XP, levels, per-game ratings, badges, weekly progress, title selection and victory-effect selection persist after restart.
- Confirm progression never changes combat attributes or equipment.

## Spectator controls

- Enter spectator state through elimination/respawn wait where applicable.
- Use comma and period to cycle living participants.
- Verify disconnected, eliminated and pending-respawn players are skipped.

## Arena tools

- Validate a correct arena and arenas missing snapshots, spawns, flags/nodes, floors and boost points.
- Use teleport-to-issue for every issue containing a location.
- Clone an idle arena and confirm IDs/regions remain unique.
- Export and import an arena template; reject malformed, oversized or conflicting data.

## Diagnostics

- Open Minigame System Health, run integrity check and review counts.
- Create harmless stale runtime references in a test world and verify conservative cleanup does not delete valid player recovery or definitions.

## Ability foundation

- Test Tank enemy AOE slow/knockback and all Healer abilities.
- Confirm target filters, cooldowns, particles, sounds and miss-consumes-cooldown behavior remain unchanged.

## Integration

- Verify new minigame event types can advance an existing event-driven quest.
- Verify custom statistic counters update and can be selected by a statistics hologram leaderboard.
- Verify existing participation/winner rewards are delivered exactly once.

## Regression

Retest delayed respawn, disabled natural regeneration, role loadout locks, DPS bow, boosts, objective cast interruption, runtime borders, arena build mode and crash/logout recovery.
