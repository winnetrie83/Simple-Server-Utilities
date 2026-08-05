# SSU 1.9.0-dev2.9.3 manual test matrix

## Respawn delay

1. Configure a CTF arena with a 5-second respawn delay, start a match, and defeat a player.
2. Confirm the death is intercepted without a vanilla death screen, the player enters spectator mode, and the large countdown shows `5` through `1`.
3. Confirm the player cannot damage, heal, capture, collect boosts or be selected by role abilities while waiting.
4. Confirm the player returns in Survival at a valid team spawn with full role health and the existing role loadout intact.
5. Repeat in Domination after controlling a node and confirm the destination follows the controlled-node respawn selection.
6. Defeat a CTF flag carrier and confirm the flag drops before the delay begins.
7. End or leave the match during a pending respawn and confirm the title clears and the original player state is restored.

## Controlled healing

1. Take damage in active CTF or Domination while the food bar is full and wait at least 30 seconds; health must not regenerate naturally.
2. Confirm the food bar remains full and food cannot be used from the protected match loadout.
3. Confirm Healer single-target, AOE and self-heals still restore health.
4. Pick up a Regeneration boost and confirm it restores one health point every 50 ticks for its configured duration without duplicate vanilla regeneration healing.
5. Confirm non-minigame healing remains unchanged after leaving the match.
