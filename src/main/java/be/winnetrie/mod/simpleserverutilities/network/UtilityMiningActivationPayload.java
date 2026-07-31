package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Keeps the server informed about the state of the two client-side mining keybinds. */
public record UtilityMiningActivationPayload(boolean treecapitatorHeld, boolean veinminerHeld)
        implements CustomPacketPayload {

    public static final Type<UtilityMiningActivationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "utility_mining_activation")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UtilityMiningActivationPayload> STREAM_CODEC =
            StreamCodec.of(UtilityMiningActivationPayload::encode, UtilityMiningActivationPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, UtilityMiningActivationPayload payload) {
        buffer.writeBoolean(payload.treecapitatorHeld);
        buffer.writeBoolean(payload.veinminerHeld);
    }

    private static UtilityMiningActivationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new UtilityMiningActivationPayload(buffer.readBoolean(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
