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
import net.minecraft.resources.ResourceLocation;

/** Client cache of server-authoritative SSU NPC textures. */
public final class NpcCustomTextureClientState {
    private static final Map<String, TextureEntry> BY_DEFINITION = new HashMap<>();

    private NpcCustomTextureClientState() {}

    public static void apply(NpcTextureSyncPayload payload) {
        if (payload == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        for (NpcTextureSyncPayload.Entry entry : payload.entries()) {
            TextureEntry current = BY_DEFINITION.get(entry.definitionId());
            String normalizedModel = "slim".equalsIgnoreCase(entry.model()) ? "slim" : "wide";
            if (entry.png().length == 0 || entry.hash().isBlank()) {
                if (current != null && current.textureId != null) {
                    minecraft.getTextureManager().release(current.textureId);
                }
                if ("remove".equalsIgnoreCase(entry.model())) {
                    BY_DEFINITION.remove(entry.definitionId());
                } else {
                    // Player NPCs still need Wide/Slim state when they use the default Steve/Alex skin.
                    BY_DEFINITION.put(entry.definitionId(), new TextureEntry("", normalizedModel, null));
                }
                continue;
            }
            if (current != null && current.hash.equals(entry.hash()) && current.model.equals(normalizedModel)) continue;
            try {
                String definitionPath = NpcDefinition.sanitizeId(entry.definitionId());
                ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,
                        "npc_texture/" + definitionPath + "/"
                                + entry.hash().toLowerCase(java.util.Locale.ROOT));

                // A Wide/Slim-only change can reuse the already registered pixels.
                if (current != null && current.hash.equals(entry.hash()) && textureId.equals(current.textureId)) {
                    BY_DEFINITION.put(entry.definitionId(),
                            new TextureEntry(entry.hash(), normalizedModel, textureId));
                    continue;
                }

                NativeImage image = NativeImage.read(new ByteArrayInputStream(entry.png()));
                int width = image.getWidth();
                int height = image.getHeight();
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                    image.close();
                    SimpleServerUtilities.LOGGER.warn(
                            "Rejected custom NPC texture {} on the client: decoded size is {}x{}",
                            entry.definitionId(), width, height);
                    continue;
                }
                DynamicTexture texture = new DynamicTexture(image);
                minecraft.getTextureManager().register(textureId, texture);

                // Player-model textures are validated server-side as 64x64 PNGs. Entity-model
                // textures may use other dimensions and share this same DynamicTexture cache.
                if (current != null && current.textureId != null && !current.textureId.equals(textureId)) {
                    minecraft.getTextureManager().release(current.textureId);
                }
                BY_DEFINITION.put(entry.definitionId(),
                        new TextureEntry(entry.hash(), normalizedModel, textureId));
                SimpleServerUtilities.LOGGER.debug(
                        "Installed custom NPC texture {} as {} ({}x{})",
                        entry.definitionId(), textureId, width, height);
            } catch (Exception exception) {
                // A malformed/unsupported texture never takes down the client, but do not hide
                // the failure: the log must make texture-pipeline problems diagnosable.
                SimpleServerUtilities.LOGGER.warn(
                        "Could not install custom NPC texture {} (hash {}): {}",
                        entry.definitionId(), entry.hash(), exception.getMessage(), exception);
            }
        }
    }

    /** Returns whether a managed player-model NPC uses the Slim/Alex arm geometry. */
    public static boolean isSlimModelForEntity(int entityId) {
        String definitionId = NpcLabelClientState.definitionIdForEntity(entityId);
        if (definitionId == null || definitionId.isBlank()) return false;
        TextureEntry entry = BY_DEFINITION.get(definitionId);
        return entry != null && "slim".equals(entry.model);
    }

    /** Returns the dynamically registered texture for any managed NPC render family. */
    public static ResourceLocation textureForEntity(int entityId) {
        String definitionId = NpcLabelClientState.definitionIdForEntity(entityId);
        if (definitionId == null || definitionId.isBlank()) return null;
        TextureEntry entry = BY_DEFINITION.get(definitionId);
        return entry == null ? null : entry.textureId;
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (TextureEntry entry : BY_DEFINITION.values()) {
            if (entry.textureId != null) minecraft.getTextureManager().release(entry.textureId);
        }
        BY_DEFINITION.clear();
    }

    private record TextureEntry(String hash, String model, ResourceLocation textureId) {}
}
