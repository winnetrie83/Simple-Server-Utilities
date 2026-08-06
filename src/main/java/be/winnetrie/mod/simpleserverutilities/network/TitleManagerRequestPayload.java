package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TitleManagerRequestPayload(boolean adminView, long requestId) implements CustomPacketPayload {
    public static final Type<TitleManagerRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "title_manager_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleManagerRequestPayload> STREAM_CODEC =
            StreamCodec.of((b, p) -> { b.writeBoolean(p.adminView); b.writeVarLong(Math.max(0L, p.requestId)); },
                    b -> new TitleManagerRequestPayload(b.readBoolean(), b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
