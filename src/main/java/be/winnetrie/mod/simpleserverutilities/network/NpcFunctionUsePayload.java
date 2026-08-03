package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests one function from an NPC currently configured in service-menu mode. */
public record NpcFunctionUsePayload(String instanceId, String functionId) implements CustomPacketPayload {
    public static final Type<NpcFunctionUsePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_function_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcFunctionUsePayload> STREAM_CODEC =
            StreamCodec.of(NpcFunctionUsePayload::encode, NpcFunctionUsePayload::decode);

    public NpcFunctionUsePayload {
        instanceId = PayloadBounds.string(instanceId, 36);
        functionId = PayloadBounds.string(functionId, 64);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcFunctionUsePayload payload) {
        buffer.writeUtf(payload.instanceId, 36);
        buffer.writeUtf(payload.functionId, 64);
    }

    private static NpcFunctionUsePayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcFunctionUsePayload(buffer.readUtf(36), buffer.readUtf(64));
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
