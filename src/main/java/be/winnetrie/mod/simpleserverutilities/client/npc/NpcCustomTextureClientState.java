package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
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
            if (current != null && current.hash.equals(entry.hash())) continue;
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(entry.png()));
                if (image.getWidth() != 64 || image.getHeight() != 64) {
                    image.close();
                    continue;
                }
                Identifier textureId = Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,
                        "npc_skin/" + entry.hash().toLowerCase(java.util.Locale.ROOT));
                DynamicTexture texture = new DynamicTexture(() -> "SSU NPC " + entry.definitionId(), image);
                minecraft.getTextureManager().register(textureId, texture);
                PlayerModelType model = "slim".equals(entry.model()) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
                PlayerSkin skin = PlayerSkin.insecure(new ClientAsset.ResourceTexture(textureId), null, null, model);
                if (current != null && !current.textureId.equals(textureId)) minecraft.getTextureManager().release(current.textureId);
                BY_DEFINITION.put(entry.definitionId(), new SkinEntry(entry.hash(), textureId, skin));
            } catch (Exception ignored) {
                // A malformed texture never takes down the client; the vanilla mannequin skin remains active.
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

    private record SkinEntry(String hash, Identifier textureId, PlayerSkin skin) {}
}
