package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuDimensionManagerDataPayload(
        long requestId,
        String notice,
        boolean error,
        boolean restartRequired,
        String selectedId,
        String selectedDefinitionJson,
        List<Entry> dimensions
) implements CustomPacketPayload {
    private static final int MAX_DIMENSIONS = 256;
    public static final Type<SsuDimensionManagerDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "dimension_manager_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuDimensionManagerDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuDimensionManagerDataPayload::encode, SsuDimensionManagerDataPayload::decode);

    public SsuDimensionManagerDataPayload {
        requestId = Math.max(0L, requestId);
        notice = PayloadBounds.string(notice, 512);
        selectedId = PayloadBounds.string(selectedId, 128);
        selectedDefinitionJson = PayloadBounds.string(selectedDefinitionJson, SsuDimensionManagerSubmitPayload.MAX_JSON);
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        if (dimensions.size() > MAX_DIMENSIONS) throw new IllegalArgumentException("Too many dimensions in manager payload.");
    }

    public static SsuDimensionManagerDataPayload denied(long requestId, String notice) {
        return new SsuDimensionManagerDataPayload(requestId, notice, true, false, "", "", List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuDimensionManagerDataPayload payload) {
        buffer.writeVarLong(payload.requestId);
        buffer.writeUtf(payload.notice, 512);
        buffer.writeBoolean(payload.error);
        buffer.writeBoolean(payload.restartRequired);
        buffer.writeUtf(payload.selectedId, 128);
        buffer.writeUtf(payload.selectedDefinitionJson, SsuDimensionManagerSubmitPayload.MAX_JSON);
        buffer.writeVarInt(payload.dimensions.size());
        for (Entry entry : payload.dimensions) {
            buffer.writeUtf(entry.id, 128);
            buffer.writeUtf(entry.displayName, 64);
            buffer.writeUtf(entry.preset, 32);
            buffer.writeBoolean(entry.loaded);
            buffer.writeBoolean(entry.vanilla);
            buffer.writeBoolean(entry.managed);
        }
    }

    private static SsuDimensionManagerDataPayload decode(RegistryFriendlyByteBuf buffer) {
        long requestId = buffer.readVarLong();
        String notice = buffer.readUtf(512);
        boolean error = buffer.readBoolean();
        boolean restartRequired = buffer.readBoolean();
        String selectedId = buffer.readUtf(128);
        String selectedJson = buffer.readUtf(SsuDimensionManagerSubmitPayload.MAX_JSON);
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_DIMENSIONS) throw new IllegalArgumentException("Invalid dimension list size: " + size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUtf(128), buffer.readUtf(64), buffer.readUtf(32),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        }
        return new SsuDimensionManagerDataPayload(requestId, notice, error, restartRequired, selectedId, selectedJson, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String id, String displayName, String preset, boolean loaded, boolean vanilla, boolean managed) {
        public Entry {
            id = PayloadBounds.string(id, 128);
            displayName = PayloadBounds.string(displayName, 64);
            preset = PayloadBounds.string(preset, 32);
        }
    }
}
