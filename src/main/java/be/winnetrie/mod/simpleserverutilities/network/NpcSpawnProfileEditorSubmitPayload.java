package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcSpawnProfileEditorSubmitPayload(String originalId, String profileJson,
        boolean rebindSpawner, long requestId) implements CustomPacketPayload {
    public static final Type<NpcSpawnProfileEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_spawn_profile_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSpawnProfileEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeUtf(payload.originalId, 64);
                buffer.writeUtf(payload.profileJson, 16_384);
                buffer.writeBoolean(payload.rebindSpawner);
                buffer.writeVarLong(payload.requestId);
            }, buffer -> new NpcSpawnProfileEditorSubmitPayload(buffer.readUtf(64), buffer.readUtf(16_384),
                    buffer.readBoolean(), buffer.readVarLong()));

    public NpcSpawnProfileEditorSubmitPayload {
        originalId = PayloadBounds.string(originalId, 64);
        profileJson = PayloadBounds.string(profileJson, 16_384);
        requestId = Math.max(0L, requestId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
