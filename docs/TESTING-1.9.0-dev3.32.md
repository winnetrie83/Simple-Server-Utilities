# SSU 1.9.0-dev3.32 test checklist

## Upgrade / migration
1. Open a world from dev3.30.1 or dev3.31 and confirm it loads without deleting NPC data.
2. A normal Entity NPC must remain Entity.
3. A Player-skin NPC must remain Player and keep its Wide/Slim setting.
4. If a dev3.31 Custom model draft/definition exists, confirm it safely appears as Entity using its fallback entity type.

## Player model
1. Create a Player NPC with Default texture; verify normal mannequin/player rendering.
2. Set a local 64x64 PNG; verify the skin appears.
3. Toggle Wide / Steve and Slim / Alex; verify arm geometry changes while the same skin remains.
4. Try a non-64x64 local PNG in Player mode; Save must reject it without crashing.

## Vanilla entity textures
1. Create a zombie Entity NPC with no override; verify vanilla appearance and animations.
2. Apply a local PNG following the zombie UV layout; verify the zombie model uses it.
3. Repeat with villager, skeleton and one quadruped such as cow/pig.
4. Put two NPCs of the same entity type next to each other with different custom PNGs; both must keep their own texture without flickering or swapping.
5. Set Texture source back to Default; vanilla texture must return and the dynamic texture must be released.

## Runtime systems
1. Verify custom-textured NPCs still patrol, schedule, collide and fight normally.
2. Verify held items/armor still render on humanoid mobs where vanilla supports them.
3. Spawn a custom-textured template through a Natural Spawn Profile and a Spawner Profile; dynamic instances should inherit the texture.
4. Walk out of sync/despawn range and return; texture should resync correctly.
5. Disconnect/reconnect and confirm textures return without duplicate/missing texture warnings.

## URL texture safety
1. Valid HTTPS PNG should load and sync.
2. HTTP/non-PNG/oversized/invalid URL must fail safely with an editor/log error, never a client/server crash.

## Regression
1. Re-test the dev3.30.1 mob-without-ATTACK_DAMAGE combat case; no server tick crash.
2. Test bossbar, abilities, patrol and schedule on one textured NPC.
