package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A closed, server-validated dashboard action; it cannot carry an arbitrary command. */
public record SsuMenuActionPayload(
        String action,
        String target,
        String secondary,
        String value,
        long requestId
) implements CustomPacketPayload {
    public static final Type<SsuMenuActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "menu_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuMenuActionPayload> STREAM_CODEC =
            StreamCodec.of(SsuMenuActionPayload::encode, SsuMenuActionPayload::decode);

    public SsuMenuActionPayload {
        action = PayloadBounds.string(action,48).trim().toLowerCase(java.util.Locale.ROOT);
        target = PayloadBounds.string(target,128).trim(); secondary = PayloadBounds.string(secondary,128).trim(); value = PayloadBounds.string(value,256).trim();
        requestId = Math.max(0L, requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b,SsuMenuActionPayload p){
        b.writeUtf(p.action,48);b.writeUtf(p.target,128);b.writeUtf(p.secondary,128);b.writeUtf(p.value,256);b.writeVarLong(p.requestId);}
    private static SsuMenuActionPayload decode(RegistryFriendlyByteBuf b){return new SsuMenuActionPayload(
            b.readUtf(48),b.readUtf(128),b.readUtf(128),b.readUtf(256),b.readVarLong());}
@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
