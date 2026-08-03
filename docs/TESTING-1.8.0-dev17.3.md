# SSU 1.8.0-dev17.3 testing

1. Follow `RECOVERY-1.8.0-dev17.3.md` for a world already blocked by dev17.0-dev17.2.
2. Start once and confirm SSU recreates `datapacks/ssu_managed_dimensions`.
3. Restart and confirm the world loads with the custom dimension registered.
4. Create one dimension for each preset: Overworld, Nether, End, Flat and Empty.
5. Restart and enter each dimension. Verify terrain, sky/fog, bed/anchor behavior, dimension height and the Empty 9x9 platform.
6. Edit dimension-type options, save, restart and verify the world still loads.
7. Confirm existing dimension-specific rank/player permissions remain intact.

No network or SSU storage migration is expected.
