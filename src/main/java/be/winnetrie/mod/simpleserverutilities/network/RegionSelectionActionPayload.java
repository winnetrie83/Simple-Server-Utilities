package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative operation requested from the region selection editor. */
public record RegionSelectionActionPayload(
        String operation,
        String name,
        List<Integer> inventorySlots,
        List<Integer> percentages,
        long requestId
) implements CustomPacketPayload {
    public static final int MAX_MIX_ENTRIES = 9;
    public static final Type<RegionSelectionActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_selection_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionSelectionActionPayload> STREAM_CODEC =
            StreamCodec.of(RegionSelectionActionPayload::encode, RegionSelectionActionPayload::decode);

    public RegionSelectionActionPayload {
        operation = PayloadBounds.trimmedString(operation, 32);
        name = PayloadBounds.trimmedString(name, 64);
        inventorySlots = inventorySlots == null ? List.of() : inventorySlots.stream()
                .filter(java.util.Objects::nonNull).limit(MAX_MIX_ENTRIES).toList();
        percentages = percentages == null ? List.of() : percentages.stream()
                .filter(java.util.Objects::nonNull).limit(MAX_MIX_ENTRIES).toList();
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, RegionSelectionActionPayload p) {
        b.writeUtf(p.operation(), 32);
        b.writeUtf(p.name(), 64);
        b.writeVarInt(p.inventorySlots().size());
        for (int slot : p.inventorySlots()) b.writeVarInt(slot);
        b.writeVarInt(p.percentages().size());
        for (int percentage : p.percentages()) b.writeVarInt(percentage);
        b.writeVarLong(p.requestId());
    }

    private static RegionSelectionActionPayload decode(RegistryFriendlyByteBuf b) {
        String operation = b.readUtf(32);
        String name = b.readUtf(64);
        int slotCount = b.readVarInt();
        if (slotCount < 0 || slotCount > MAX_MIX_ENTRIES) throw new IllegalArgumentException("Invalid mix slot count");
        List<Integer> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) slots.add(b.readVarInt());
        int percentageCount = b.readVarInt();
        if (percentageCount < 0 || percentageCount > MAX_MIX_ENTRIES) throw new IllegalArgumentException("Invalid mix percentage count");
        List<Integer> percentages = new ArrayList<>(percentageCount);
        for (int i = 0; i < percentageCount; i++) percentages.add(b.readVarInt());
        return new RegionSelectionActionPayload(operation, name, slots, percentages, b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
