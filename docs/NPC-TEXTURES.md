# SSU NPC textures — dev3.33

SSU NPC appearance is dependency-free and has two editor-facing visual modes: **Player** and **Entity**.

## Player visual mode

`Player` uses SSU's native `simpleserverutilities:player_npc` physical entity. It is a real pathfinding mob shell controlled by SSU's NPC AI/controllers, while the client renders it as a Minecraft-style 64x64 player model.

Choose **Wide / Steve** or **Slim / Alex** and optionally supply a custom 64x64 PNG.

Texture sources:

- **Default texture** — no SSU texture override; the renderer falls back to the matching vanilla Steve/Alex texture.
- **Local server PNG** — relative to the world's `simpleserverutilities/npcs/textures` folder.
- **HTTPS URL** — downloaded by the server, validated, cached and synced to clients.

Player skins must be exactly **64x64**. The SSU player renderer includes the normal skin overlay geometry: hat, jacket, sleeves and pants.

The physical Player runtime no longer uses Minecraft's mannequin entity or mannequin skin hook. Existing Player NPC definitions keep their fallback `entityType` only as persisted compatibility data; SSU does not overwrite it when upgrading the runtime shell.

## Entity visual mode

`Entity` uses the selected registered living entity type and therefore keeps its normal Minecraft geometry, movement animations, poses, equipment handling, hitbox and renderer behavior. An optional SSU PNG on the NPC template replaces the entity renderer's **base texture for each runtime NPC using that template**.

For best results, make the PNG with the same UV layout and dimensions/aspect as the vanilla texture used by the selected entity. Example: a custom zombie skin should follow the zombie texture layout; a villager skin should follow the villager layout.

The override is attached to each render state, not to the shared vanilla renderer. This allows two NPCs using the same entity model to have different textures in the same frame.

### Render-layer limitation

Only the base living-entity texture is overridden for Entity mode. Renderer layers which deliberately use another texture — for example armor, some eyes/emissive layers, saddles, wool overlays or other special effects — continue to use their normal Minecraft textures. This avoids rewriting entity-specific rendering logic.

## Custom models

Custom geometry remains intentionally unsupported. Legacy dev3.31 custom-model metadata remains readable only so development data can migrate safely; such definitions normalize back to Entity using their saved fallback living entity.

SSU has **no external NPC rendering dependency**.
