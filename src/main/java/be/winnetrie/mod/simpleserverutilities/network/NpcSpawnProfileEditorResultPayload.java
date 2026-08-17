package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcSpawnProfileEditorResultPayload(boolean success, String message, String savedId,
        long requestId) implements CustomPacketPayload {
    public static final Type<NpcSpawnProfileEditorResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_spawn_profile_editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSpawnProfileEditorResultPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeBoolean(payload.success);
                buffer.writeUtf(payload.message, 256);
                buffer.writeUtf(payload.savedId, 64);
                buffer.writeVarLong(payload.requestId);
            }, buffer -> new NpcSpawnProfileEditorResultPayload(buffer.readBoolean(), buffer.readUtf(256),
                    buffer.readUtf(64), buffer.readVarLong()));

    public NpcSpawnProfileEditorResultPayload {
        message = PayloadBounds.string(message, 256);
        savedId = PayloadBounds.string(savedId, 64);
        requestId = Math.max(0L, requestId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
