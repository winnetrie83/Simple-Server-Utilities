package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative U-menu request while a player may be in a minigame. */
public record MinigameMatchOverviewRequestPayload(String action, long requestId)
        implements CustomPacketPayload {
    public static final Type<MinigameMatchOverviewRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_match_overview_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameMatchOverviewRequestPayload> STREAM_CODEC =
            StreamCodec.of(MinigameMatchOverviewRequestPayload::encode, MinigameMatchOverviewRequestPayload::decode);

    public MinigameMatchOverviewRequestPayload {
        action = PayloadBounds.trimmedString(action, 16).toLowerCase(java.util.Locale.ROOT);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameMatchOverviewRequestPayload payload) {
        buffer.writeUtf(payload.action, 16);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameMatchOverviewRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameMatchOverviewRequestPayload(buffer.readUtf(16), buffer.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
