package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TitleManagerActionPayload(
        String action, String id, String displayName, int color, String unlockType,
        long requirement, String requirementValue, String targetPlayer, long requestId
) implements CustomPacketPayload {
    public static final Type<TitleManagerActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "title_manager_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleManagerActionPayload> STREAM_CODEC =
            StreamCodec.of(TitleManagerActionPayload::encode, TitleManagerActionPayload::decode);

    public TitleManagerActionPayload {
        action = PayloadBounds.string(action, 24);
        id = PayloadBounds.string(id, 64);
        displayName = PayloadBounds.string(displayName, 48);
        unlockType = PayloadBounds.string(unlockType, 32);
        requirement = Math.max(0L, Math.min(1_000_000_000L, requirement));
        requirementValue = PayloadBounds.string(requirementValue, 128);
        targetPlayer = PayloadBounds.string(targetPlayer, 64);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, TitleManagerActionPayload p) {
        b.writeUtf(p.action, 24); b.writeUtf(p.id, 64); b.writeUtf(p.displayName, 48);
        b.writeInt(p.color); b.writeUtf(p.unlockType, 32); b.writeVarLong(p.requirement);
        b.writeUtf(p.requirementValue, 128); b.writeUtf(p.targetPlayer, 64); b.writeVarLong(p.requestId);
    }
    private static TitleManagerActionPayload decode(RegistryFriendlyByteBuf b) {
        return new TitleManagerActionPayload(b.readUtf(24), b.readUtf(64), b.readUtf(48), b.readInt(),
                b.readUtf(32), b.readVarLong(), b.readUtf(128), b.readUtf(64), b.readVarLong());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
