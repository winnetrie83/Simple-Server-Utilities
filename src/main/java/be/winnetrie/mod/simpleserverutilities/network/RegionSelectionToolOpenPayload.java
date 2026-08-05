package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Opens the region selector action menu for the current complete selection. */
public record RegionSelectionToolOpenPayload(
        String dimension,
        long point1,
        long point2,
        long volume,
        long maxEditableVolume,
        boolean canCreateRegion,
        boolean canCreateMinigame,
        boolean canEditBlocks,
        boolean clipboardAvailable,
        List<String> serverTemplates
) implements CustomPacketPayload {
    public static final int MAX_TEMPLATES = 256;
    public static final Type<RegionSelectionToolOpenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_selection_tool_open")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionSelectionToolOpenPayload> STREAM_CODEC =
            StreamCodec.of(RegionSelectionToolOpenPayload::encode, RegionSelectionToolOpenPayload::decode);

    public RegionSelectionToolOpenPayload {
        dimension = PayloadBounds.trimmedString(dimension, 256);
        volume = Math.max(0L, volume);
        maxEditableVolume = Math.max(1L, maxEditableVolume);
        serverTemplates = serverTemplates == null ? List.of() : serverTemplates.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> PayloadBounds.trimmedString(value, 64))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(MAX_TEMPLATES)
                .toList();
    }

    private static void encode(RegistryFriendlyByteBuf b, RegionSelectionToolOpenPayload p) {
        b.writeUtf(p.dimension(), 256);
        b.writeLong(p.point1());
        b.writeLong(p.point2());
        b.writeVarLong(p.volume());
        b.writeVarLong(p.maxEditableVolume());
        b.writeBoolean(p.canCreateRegion());
        b.writeBoolean(p.canCreateMinigame());
        b.writeBoolean(p.canEditBlocks());
        b.writeBoolean(p.clipboardAvailable());
        b.writeVarInt(p.serverTemplates().size());
        p.serverTemplates().forEach(value -> b.writeUtf(value, 64));
    }

    private static RegionSelectionToolOpenPayload decode(RegistryFriendlyByteBuf b) {
        String dimension = b.readUtf(256);
        long point1 = b.readLong();
        long point2 = b.readLong();
        long volume = b.readVarLong();
        long maxEditableVolume = b.readVarLong();
        boolean canCreate = b.readBoolean();
        boolean canCreateMinigame = b.readBoolean();
        boolean canEdit = b.readBoolean();
        boolean clipboard = b.readBoolean();
        int size = b.readVarInt();
        if (size < 0 || size > MAX_TEMPLATES) throw new IllegalArgumentException("Invalid server-template count: " + size);
        List<String> templates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) templates.add(b.readUtf(64));
        return new RegionSelectionToolOpenPayload(dimension, point1, point2, volume, maxEditableVolume,
                canCreate, canCreateMinigame, canEdit, clipboard, templates);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
