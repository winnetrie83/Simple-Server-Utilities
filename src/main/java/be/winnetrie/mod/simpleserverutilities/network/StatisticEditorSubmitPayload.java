package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.statistics.StatisticEventType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StatisticEditorSubmitPayload(
        String originalId,
        String id,
        String displayName,
        StatisticEventType eventType,
        String target,
        String unit,
        boolean enabled,
        long requestId
) implements CustomPacketPayload {
    public static final Type<StatisticEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "statistic_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatisticEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(StatisticEditorSubmitPayload::encode, StatisticEditorSubmitPayload::decode);

    public StatisticEditorSubmitPayload {
        originalId = PayloadBounds.trimmedString(originalId, 64);
        id = PayloadBounds.trimmedString(id, 64);
        displayName = PayloadBounds.trimmedString(displayName, 64);
        eventType = eventType == null ? StatisticEventType.BLOCK_BROKEN : eventType;
        target = PayloadBounds.trimmedString(target, 128);
        unit = PayloadBounds.trimmedString(unit, 24);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, StatisticEditorSubmitPayload p) {
        b.writeUtf(p.originalId(), 64);
        b.writeUtf(p.id(), 64);
        b.writeUtf(p.displayName(), 64);
        b.writeEnum(p.eventType());
        b.writeUtf(p.target(), 128);
        b.writeUtf(p.unit(), 24);
        b.writeBoolean(p.enabled());
        b.writeVarLong(p.requestId());
    }

    private static StatisticEditorSubmitPayload decode(RegistryFriendlyByteBuf b) {
        return new StatisticEditorSubmitPayload(b.readUtf(64), b.readUtf(64), b.readUtf(64),
                b.readEnum(StatisticEventType.class), b.readUtf(128), b.readUtf(24), b.readBoolean(), b.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
