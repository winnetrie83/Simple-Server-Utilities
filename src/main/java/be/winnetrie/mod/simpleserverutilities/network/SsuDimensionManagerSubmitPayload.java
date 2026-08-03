package be.winnetrie.mod.simpleserverutilities.network;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuDimensionManagerSubmitPayload(
        String action,
        String originalId,
        String definitionJson,
        long requestId
) implements CustomPacketPayload {
    public static final int MAX_JSON = 32768;
    public static final Type<SsuDimensionManagerSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "dimension_manager_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuDimensionManagerSubmitPayload> STREAM_CODEC =
            StreamCodec.of(SsuDimensionManagerSubmitPayload::encode, SsuDimensionManagerSubmitPayload::decode);

    public SsuDimensionManagerSubmitPayload {
        action = PayloadBounds.string(action, 16).trim().toLowerCase(Locale.ROOT);
        originalId = PayloadBounds.string(originalId, 128).trim();
        definitionJson = PayloadBounds.string(definitionJson, MAX_JSON);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuDimensionManagerSubmitPayload payload) {
        buffer.writeUtf(payload.action, 16);
        buffer.writeUtf(payload.originalId, 128);
        buffer.writeUtf(payload.definitionJson, MAX_JSON);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuDimensionManagerSubmitPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuDimensionManagerSubmitPayload(
                buffer.readUtf(16), buffer.readUtf(128), buffer.readUtf(MAX_JSON), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
