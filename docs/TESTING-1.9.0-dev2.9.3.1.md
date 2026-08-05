# SSU 1.9.0-dev2.9.3.1 validation and manual test matrix

## Compile hotfix

1. Confirm `MinigameManager#useTankSlow` calls `LivingEntity#knockback(double, double, double, DamageSource, float)`.
2. Confirm the source is `tank.damageSources().playerAttack(tank)` and the damage argument is `0.0F`.
3. Search the source tree for legacy three-argument `.knockback(...)` calls; none may remain.

## Runtime Tank Defensive Field

1. Start CTF or Domination with tactical roles enabled and assign one player as Tank.
2. Activate Defensive Field with an enemy inside the configured radius.
3. Confirm the enemy receives Slowness and is pushed radially away from the Tank without losing health from the ability itself.
4. Confirm allied players and the Tank are neither slowed nor pushed.
5. Set Tank knockback to `0.0` and confirm Slowness remains while push is disabled.
6. Test against a target with knockback resistance and confirm vanilla resistance still affects the push.
