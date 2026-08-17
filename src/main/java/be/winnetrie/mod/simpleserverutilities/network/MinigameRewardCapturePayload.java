package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Copies an exact server-side administrator inventory stack into one visible reward slot. */
public record MinigameRewardCapturePayload(
        String originalMinigameId,
        String definitionJson,
        String rewardKind,
        int rewardSlot,
        int inventorySlot,
        boolean addOne,
        long requestId
) implements CustomPacketPayload {
    public static final Type<MinigameRewardCapturePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_reward_capture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameRewardCapturePayload> STREAM_CODEC =
            StreamCodec.of(MinigameRewardCapturePayload::encode, MinigameRewardCapturePayload::decode);

    public MinigameRewardCapturePayload {
        originalMinigameId = PayloadBounds.string(originalMinigameId, 64);
        definitionJson = PayloadBounds.string(definitionJson, 65_535);
        rewardKind = PayloadBounds.string(rewardKind, 24).trim().toLowerCase(java.util.Locale.ROOT);
        rewardSlot = Math.max(0, Math.min(8, rewardSlot));
        inventorySlot = Math.max(-1, Math.min(35, inventorySlot));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameRewardCapturePayload payload) {
        buffer.writeUtf(payload.originalMinigameId, 64);
        buffer.writeUtf(payload.definitionJson, 65_535);
        buffer.writeUtf(payload.rewardKind, 24);
        buffer.writeVarInt(payload.rewardSlot);
        buffer.writeVarInt(payload.inventorySlot);
        buffer.writeBoolean(payload.addOne);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameRewardCapturePayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameRewardCapturePayload(buffer.readUtf(64), buffer.readUtf(65_535),
                buffer.readUtf(24), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
