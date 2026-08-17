package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.statistics.StatisticEventType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StatisticEditorOpenPayload(
        boolean editing,
        String originalId,
        String id,
        String displayName,
        StatisticEventType eventType,
        String target,
        String unit,
        boolean enabled
) implements CustomPacketPayload {
    public static final Type<StatisticEditorOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "statistic_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatisticEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(StatisticEditorOpenPayload::encode, StatisticEditorOpenPayload::decode);

    public StatisticEditorOpenPayload {
        originalId = PayloadBounds.string(originalId, 64);
        id = PayloadBounds.string(id, 64);
        displayName = PayloadBounds.string(displayName, 64);
        eventType = eventType == null ? StatisticEventType.BLOCK_BROKEN : eventType;
        target = PayloadBounds.string(target, 128);
        unit = PayloadBounds.string(unit, 24);
    }

    private static void encode(RegistryFriendlyByteBuf b, StatisticEditorOpenPayload p) {
        b.writeBoolean(p.editing());
        b.writeUtf(p.originalId(), 64);
        b.writeUtf(p.id(), 64);
        b.writeUtf(p.displayName(), 64);
        b.writeEnum(p.eventType());
        b.writeUtf(p.target(), 128);
        b.writeUtf(p.unit(), 24);
        b.writeBoolean(p.enabled());
    }

    private static StatisticEditorOpenPayload decode(RegistryFriendlyByteBuf b) {
        return new StatisticEditorOpenPayload(b.readBoolean(), b.readUtf(64), b.readUtf(64), b.readUtf(64),
                b.readEnum(StatisticEventType.class), b.readUtf(128), b.readUtf(24), b.readBoolean());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
