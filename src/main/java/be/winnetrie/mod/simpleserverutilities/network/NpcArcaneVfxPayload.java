package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Lightweight one-shot visual sync for the custom Arcane Missiles client renderer. */
public record NpcArcaneVfxPayload(
        int mode,
        String dimension,
        int sourceEntityId,
        int targetEntityId,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        int durationTicks,
        int seed
) implements CustomPacketPayload {
    public static final int MODE_CHARGE = 0;
    public static final int MODE_VOLLEY = 1;

    public static final Type<NpcArcaneVfxPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_arcane_vfx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcArcaneVfxPayload> STREAM_CODEC =
            StreamCodec.of(NpcArcaneVfxPayload::encode, NpcArcaneVfxPayload::decode);

    public NpcArcaneVfxPayload {
        mode = mode == MODE_CHARGE ? MODE_CHARGE : MODE_VOLLEY;
        dimension = PayloadBounds.string(dimension, 128);
        durationTicks = Math.max(1, Math.min(100, durationTicks));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcArcaneVfxPayload payload) {
        buffer.writeByte(payload.mode);
        buffer.writeUtf(payload.dimension, 128);
        buffer.writeVarInt(payload.sourceEntityId);
        buffer.writeVarInt(payload.targetEntityId);
        buffer.writeDouble(payload.startX);
        buffer.writeDouble(payload.startY);
        buffer.writeDouble(payload.startZ);
        buffer.writeDouble(payload.endX);
        buffer.writeDouble(payload.endY);
        buffer.writeDouble(payload.endZ);
        buffer.writeVarInt(payload.durationTicks);
        buffer.writeInt(payload.seed);
    }

    private static NpcArcaneVfxPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcArcaneVfxPayload(
                buffer.readUnsignedByte(),
                buffer.readUtf(128),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
