package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Initial region settings submitted from the region admin tool GUI. */
public record RegionEditorSubmitPayload(
        String name,
        int priority,
        boolean allowBreak,
        boolean allowPlace,
        boolean allowInteract,
        boolean allowPvp,
        boolean allowExplosions,
        boolean allowPistons,
        boolean allowWater,
        boolean allowLava,
        boolean allowRedstone,
        boolean allowHoppers,
        boolean allowFireSpread,
        boolean rentable,
        String rentPrice,
        int rentPeriodDays,
        boolean resetOnExpire,
        boolean resetOnUnrent,
        long requestId
) implements CustomPacketPayload {
    public static final Type<RegionEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_editor_submit")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(RegionEditorSubmitPayload::encode, RegionEditorSubmitPayload::decode);

    public RegionEditorSubmitPayload {
        name = PayloadBounds.trimmedString(name, 64);
        rentPrice = PayloadBounds.trimmedString(rentPrice, 64);
        priority = Math.max(-1_000_000, Math.min(1_000_000, priority));
        rentPeriodDays = Math.max(-1, Math.min(365_000, rentPeriodDays));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, RegionEditorSubmitPayload p) {
        b.writeUtf(p.name(), 64);
        b.writeVarInt(p.priority());
        b.writeBoolean(p.allowBreak());
        b.writeBoolean(p.allowPlace());
        b.writeBoolean(p.allowInteract());
        b.writeBoolean(p.allowPvp());
        b.writeBoolean(p.allowExplosions());
        b.writeBoolean(p.allowPistons());
        b.writeBoolean(p.allowWater());
        b.writeBoolean(p.allowLava());
        b.writeBoolean(p.allowRedstone());
        b.writeBoolean(p.allowHoppers());
        b.writeBoolean(p.allowFireSpread());
        b.writeBoolean(p.rentable());
        b.writeUtf(p.rentPrice(), 64);
        b.writeVarInt(p.rentPeriodDays());
        b.writeBoolean(p.resetOnExpire());
        b.writeBoolean(p.resetOnUnrent());
        b.writeVarLong(p.requestId());
    }

    private static RegionEditorSubmitPayload decode(RegistryFriendlyByteBuf b) {
        return new RegionEditorSubmitPayload(
                b.readUtf(64), b.readVarInt(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readUtf(64), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarLong()
        );
    }



    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
