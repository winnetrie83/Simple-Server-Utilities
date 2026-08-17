package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcSpawnProfileEditorOpenPayload(boolean create, String originalId, String profileJson,
        List<String> templateIds, String notice, boolean error, long requestId) implements CustomPacketPayload {
    public static final Type<NpcSpawnProfileEditorOpenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_spawn_profile_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSpawnProfileEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(NpcSpawnProfileEditorOpenPayload::encode, NpcSpawnProfileEditorOpenPayload::decode);

    public NpcSpawnProfileEditorOpenPayload {
        originalId = PayloadBounds.string(originalId, 64);
        profileJson = PayloadBounds.string(profileJson, 16_384);
        if (templateIds == null) templateIds = List.of();
        List<String> bounded = new ArrayList<>();
        for (String value : templateIds) {
            bounded.add(PayloadBounds.string(value, 64));
            if (bounded.size() >= 512) break;
        }
        templateIds = List.copyOf(bounded);
        notice = PayloadBounds.string(notice, 256);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcSpawnProfileEditorOpenPayload payload) {
        buffer.writeBoolean(payload.create);
        buffer.writeUtf(payload.originalId, 64);
        buffer.writeUtf(payload.profileJson, 16_384);
        buffer.writeVarInt(payload.templateIds.size());
        for (String id : payload.templateIds) buffer.writeUtf(id, 64);
        buffer.writeUtf(payload.notice, 256);
        buffer.writeBoolean(payload.error);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcSpawnProfileEditorOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean create = buffer.readBoolean();
        String original = buffer.readUtf(64);
        String json = buffer.readUtf(16_384);
        int count = buffer.readVarInt();
        if (count < 0 || count > 512) throw new IllegalArgumentException("Invalid NPC template count");
        List<String> templates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) templates.add(buffer.readUtf(64));
        return new NpcSpawnProfileEditorOpenPayload(create, original, json, templates,
                buffer.readUtf(256), buffer.readBoolean(), buffer.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
