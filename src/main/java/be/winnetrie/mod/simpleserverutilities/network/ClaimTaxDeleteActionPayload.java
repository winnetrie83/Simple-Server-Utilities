package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Final, double-confirmed player choice for deleting a taxed claim. */
public record ClaimTaxDeleteActionPayload(
        String claimName,
        Mode mode,
        int centerChunkX,
        int centerChunkZ,
        int radius
) implements CustomPacketPayload {

    public enum Mode { PAY_AND_DELETE, FORFEIT_AND_DELETE }

    public static final Type<ClaimTaxDeleteActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "claim_tax_delete_action")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimTaxDeleteActionPayload> STREAM_CODEC =
            StreamCodec.of(ClaimTaxDeleteActionPayload::encode, ClaimTaxDeleteActionPayload::decode);

    public ClaimTaxDeleteActionPayload {
        claimName = claimName == null ? "" : claimName.trim();
        mode = mode == null ? Mode.PAY_AND_DELETE : mode;
        radius = Math.max(2, Math.min(12, radius));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClaimTaxDeleteActionPayload payload) {
        buffer.writeUtf(payload.claimName, 64);
        buffer.writeEnum(payload.mode);
        buffer.writeVarInt(payload.centerChunkX);
        buffer.writeVarInt(payload.centerChunkZ);
        buffer.writeVarInt(payload.radius);
    }

    private static ClaimTaxDeleteActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClaimTaxDeleteActionPayload(
                buffer.readUtf(64),
                buffer.readEnum(Mode.class),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
