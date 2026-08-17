package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests the editor for a synchronized hologram selected in the world. */
public record HologramEditorRequestPayload(String id) implements CustomPacketPayload {
    public static final Type<HologramEditorRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "hologram_editor_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HologramEditorRequestPayload> STREAM_CODEC =
            StreamCodec.of(HologramEditorRequestPayload::encode, HologramEditorRequestPayload::decode);

    public HologramEditorRequestPayload {
        String safe = id == null ? "" : id.trim();
        id = safe.length() <= 64 ? safe : safe.substring(0, 64);
    }

    private static void encode(RegistryFriendlyByteBuf b, HologramEditorRequestPayload p) {
        b.writeUtf(p.id, 64);
    }

    private static HologramEditorRequestPayload decode(RegistryFriendlyByteBuf b) {
        return new HologramEditorRequestPayload(b.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
