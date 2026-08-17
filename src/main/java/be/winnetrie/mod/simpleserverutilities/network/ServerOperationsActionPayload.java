package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded Server Operations action envelope; rich support messages use the larger value allowance. */
public record ServerOperationsActionPayload(boolean admin, String action, String target, String value, String extra, long requestId)
        implements CustomPacketPayload {
    private static final int MAX_ACTION = 48;
    private static final int MAX_TARGET = 256;
    private static final int MAX_VALUE = 16_384;
    private static final int MAX_EXTRA = 4_096;

    public static final Type<ServerOperationsActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "server_operations_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerOperationsActionPayload> STREAM_CODEC =
            StreamCodec.of(ServerOperationsActionPayload::encode, ServerOperationsActionPayload::decode);

    public ServerOperationsActionPayload {
        action = PayloadBounds.string(action, MAX_ACTION);
        target = PayloadBounds.string(target, MAX_TARGET);
        value = PayloadBounds.string(value, MAX_VALUE);
        extra = PayloadBounds.string(extra, MAX_EXTRA);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ServerOperationsActionPayload payload) {
        buffer.writeBoolean(payload.admin);
        buffer.writeUtf(payload.action, MAX_ACTION);
        buffer.writeUtf(payload.target, MAX_TARGET);
        buffer.writeUtf(payload.value, MAX_VALUE);
        buffer.writeUtf(payload.extra, MAX_EXTRA);
        buffer.writeVarLong(payload.requestId);
    }

    private static ServerOperationsActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ServerOperationsActionPayload(buffer.readBoolean(), buffer.readUtf(MAX_ACTION), buffer.readUtf(MAX_TARGET),
                buffer.readUtf(MAX_VALUE), buffer.readUtf(MAX_EXTRA), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
