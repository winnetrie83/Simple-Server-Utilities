# SSU custom NPC model foundation (dev3.31)

SSU dev3.31 separates **what an NPC is** from **how it is rendered**.

## Visual modes

- **Entity** — the normal vanilla/modded living entity renderer. This is the legacy/default behaviour.
- **Player skin** — the existing 64x64 local/HTTPS player-skin workflow using Minecraft's mannequin shell.
- **Custom model** — stores provider-neutral model, texture and animation resources while keeping a normal living entity as a safe physical fallback shell.

A Custom model definition therefore remains loadable even if an optional animation/rendering provider is missing. Movement, collision, combat, schedules, spawning, boss logic and persistence keep using the fallback living entity shell.

## Resource IDs

SSU stores resource IDs rather than absolute file paths.

Example configuration:

- Model: `mypack:entity/boss/stone_golem`
- Texture: `mypack:entity/boss/stone_golem.png`
- Animations: `mypack:entity/boss/stone_golem`

For GeckoLib 5 these correspond to the conventional resource-pack locations:

- `assets/mypack/geckolib/models/entity/boss/stone_golem.geo.json`
- `assets/mypack/textures/entity/boss/stone_golem.png`
- `assets/mypack/geckolib/animations/entity/boss/stone_golem.animation.json`

The `.geo.json` and `.animation.json` suffixes are intentionally omitted in the SSU editor. The texture keeps its `.png` suffix.

## Animation mapping

Every custom-model NPC template stores six semantic animation names:

- Idle
- Walk
- Attack
- Cast / ability
- Hurt
- Death

Defaults are `idle`, `walk`, `attack`, `cast`, `hurt`, and `death`. These names must match animation names in the exported animation JSON once an animated renderer provider consumes the definition.

SSU also has a provider-neutral `NpcAnimationBridge`. Combat triggers ATTACK, abilities trigger CAST, while HURT/DEATH/WALK/IDLE can be derived from the runtime entity. This keeps combat and boss code independent from any particular rendering library.

## Optional provider boundary

Core SSU code does not import GeckoLib classes. `NpcCustomModelSupport` owns the provider boundary so a missing or incompatible optional rendering library cannot make SSU or an existing world fail to start.

In dev3.31 the persisted data format, GUI, validation, migration and semantic animation bridge are complete. Until a renderer provider is installed/registered, Custom model mode deliberately renders the configured fallback living entity shell. This is a safety feature rather than a silent failure.
