package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StatisticEditorResultPayload(boolean successful, String message, long requestId) implements CustomPacketPayload {
    public static final Type<StatisticEditorResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "statistic_editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatisticEditorResultPayload> STREAM_CODEC =
            StreamCodec.of((b, p) -> {
                b.writeBoolean(p.successful());
                b.writeUtf(p.message(), 256);
                b.writeVarLong(p.requestId());
            }, b -> new StatisticEditorResultPayload(b.readBoolean(), b.readUtf(256), b.readVarLong()));
    public StatisticEditorResultPayload {
        message = message == null ? "" : message.length() <= 256 ? message : message.substring(0, 256);
        requestId = Math.max(0L, requestId);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
