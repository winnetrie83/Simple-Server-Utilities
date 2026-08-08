package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.network.NpcTextureSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Client cache of server-authoritative SSU custom mannequin skins. */
public final class NpcCustomTextureClientState {
    private static final Map<String, SkinEntry> BY_DEFINITION = new HashMap<>();

    private NpcCustomTextureClientState() {}

    public static void apply(NpcTextureSyncPayload payload) {
        if (payload == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        for (NpcTextureSyncPayload.Entry entry : payload.entries()) {
            SkinEntry current = BY_DEFINITION.get(entry.definitionId());
            if (entry.png().length == 0 || entry.hash().isBlank()) {
                if (current != null) {
                    minecraft.getTextureManager().release(current.textureId);
                    BY_DEFINITION.remove(entry.definitionId());
                }
                continue;
            }
            String normalizedModel = "slim".equalsIgnoreCase(entry.model()) ? "slim" : "wide";
            PlayerModelType model = "slim".equals(normalizedModel) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            if (current != null && current.hash.equals(entry.hash()) && current.model.equals(normalizedModel)) continue;
            try {
                String definitionPath = NpcDefinition.sanitizeId(entry.definitionId());
                Identifier textureId = Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,
                        "npc_skin/" + definitionPath + "/"
                                + entry.hash().toLowerCase(java.util.Locale.ROOT));

                // A model-only Wide/Slim change can reuse the already registered pixels.
                if (current != null && current.hash.equals(entry.hash()) && current.textureId.equals(textureId)) {
                    PlayerSkin skin = PlayerSkin.insecure(
                            new ClientAsset.ResourceTexture(textureId, textureId), null, null, model);
                    BY_DEFINITION.put(entry.definitionId(),
                            new SkinEntry(entry.hash(), normalizedModel, textureId, skin));
                    continue;
                }

                NativeImage image = NativeImage.read(new ByteArrayInputStream(entry.png()));
                int width = image.getWidth();
                int height = image.getHeight();
                if (width != 64 || height != 64) {
                    image.close();
                    SimpleServerUtilities.LOGGER.warn(
                            "Rejected custom NPC skin {} on the client: decoded size is {}x{}, expected 64x64",
                            entry.definitionId(), width, height);
                    continue;
                }
                DynamicTexture texture = new DynamicTexture(() -> "SSU NPC " + entry.definitionId(), image);
                minecraft.getTextureManager().register(textureId, texture);

                // ClientAsset's one-argument ResourceTexture constructor derives a resource-pack
                // path (textures/<id>.png). DynamicTexture is registered directly at textureId,
                // so explicitly use that same identifier as the render texture path.
                PlayerSkin skin = PlayerSkin.insecure(
                        new ClientAsset.ResourceTexture(textureId, textureId), null, null, model);
                if (current != null && !current.textureId.equals(textureId)) {
                    minecraft.getTextureManager().release(current.textureId);
                }
                BY_DEFINITION.put(entry.definitionId(),
                        new SkinEntry(entry.hash(), normalizedModel, textureId, skin));
                SimpleServerUtilities.LOGGER.debug(
                        "Installed custom NPC skin {} as {} ({})",
                        entry.definitionId(), textureId, normalizedModel);
            } catch (Exception exception) {
                // A malformed/unsupported texture never takes down the client, but do not hide
                // the failure: the log must make texture-pipeline problems diagnosable.
                SimpleServerUtilities.LOGGER.warn(
                        "Could not install custom NPC skin {} (hash {}): {}",
                        entry.definitionId(), entry.hash(), exception.getMessage(), exception);
            }
        }
    }

    public static PlayerSkin skinForEntity(int entityId) {
        String definitionId = NpcLabelClientState.definitionIdForEntity(entityId);
        if (definitionId == null || definitionId.isBlank()) return null;
        SkinEntry entry = BY_DEFINITION.get(definitionId);
        return entry == null ? null : entry.skin;
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (SkinEntry entry : BY_DEFINITION.values()) minecraft.getTextureManager().release(entry.textureId);
        BY_DEFINITION.clear();
    }

    private record SkinEntry(String hash, String model, Identifier textureId, PlayerSkin skin) {}
}
