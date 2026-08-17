package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Result and refreshed template state for the region selection editor. */
public record RegionSelectionActionResultPayload(
        boolean successful,
        String message,
        long requestId,
        boolean clipboardAvailable,
        boolean selectionCleared,
        List<String> serverTemplates
) implements CustomPacketPayload {
    public static final Type<RegionSelectionActionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_selection_action_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionSelectionActionResultPayload> STREAM_CODEC =
            StreamCodec.of(RegionSelectionActionResultPayload::encode, RegionSelectionActionResultPayload::decode);

    public RegionSelectionActionResultPayload {
        message = PayloadBounds.string(message, 256);
        requestId = Math.max(0L, requestId);
        serverTemplates = serverTemplates == null ? List.of() : serverTemplates.stream()
                .filter(java.util.Objects::nonNull).map(value -> PayloadBounds.trimmedString(value, 64))
                .filter(value -> !value.isBlank()).distinct().limit(256).toList();
    }

    private static void encode(RegistryFriendlyByteBuf b, RegionSelectionActionResultPayload p) {
        b.writeBoolean(p.successful());
        b.writeUtf(p.message(), 256);
        b.writeVarLong(p.requestId());
        b.writeBoolean(p.clipboardAvailable());
        b.writeBoolean(p.selectionCleared());
        b.writeVarInt(p.serverTemplates().size());
        p.serverTemplates().forEach(value -> b.writeUtf(value, 64));
    }

    private static RegionSelectionActionResultPayload decode(RegistryFriendlyByteBuf b) {
        boolean successful = b.readBoolean();
        String message = b.readUtf(256);
        long requestId = b.readVarLong();
        boolean clipboard = b.readBoolean();
        boolean selectionCleared = b.readBoolean();
        int size = b.readVarInt();
        if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid server-template count: " + size);
        List<String> templates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) templates.add(b.readUtf(64));
        return new RegionSelectionActionResultPayload(successful, message, requestId, clipboard, selectionCleared, templates);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
