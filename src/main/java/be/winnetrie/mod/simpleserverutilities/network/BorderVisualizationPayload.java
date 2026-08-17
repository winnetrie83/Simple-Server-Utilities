package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderLayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BorderVisualizationPayload(
        BorderLayer layer,
        boolean visible,
        String dimension,
        int claimVerticalRange,
        int renderDistance,
        List<Entry> entries
) implements CustomPacketPayload {

    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_BOXES_PER_ENTRY = 4096;
    private static final int MAX_EDGES_PER_ENTRY = 8192;

    public static final Type<BorderVisualizationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "border_visualization")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BorderVisualizationPayload> STREAM_CODEC =
            StreamCodec.of(BorderVisualizationPayload::encode, BorderVisualizationPayload::decode);

    public BorderVisualizationPayload {
        dimension = dimension == null ? "" : dimension;
        claimVerticalRange = Math.max(8, Math.min(256, claimVerticalRange));
        renderDistance = Math.max(8, Math.min(512, renderDistance));
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many border visualization entries: " + entries.size());
        }
    }

    public static BorderVisualizationPayload clear(BorderLayer layer) {
        return new BorderVisualizationPayload(layer, false, "", 64, 64, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BorderVisualizationPayload payload) {
        buffer.writeEnum(payload.layer);
        buffer.writeBoolean(payload.visible);
        buffer.writeUtf(payload.dimension);
        buffer.writeVarInt(payload.claimVerticalRange);
        buffer.writeVarInt(payload.renderDistance);
        buffer.writeVarInt(payload.entries.size());

        for (Entry entry : payload.entries) {
            buffer.writeEnum(entry.category);
            buffer.writeUtf(entry.label);
            buffer.writeInt(entry.strokeColor);
            buffer.writeInt(entry.fillColor);
            buffer.writeFloat(entry.strokeWidth);
            buffer.writeBoolean(entry.strokeBoxes);

            buffer.writeVarInt(entry.boxes.size());
            for (Box box : entry.boxes) {
                buffer.writeVarInt(box.minX);
                buffer.writeVarInt(box.minY);
                buffer.writeVarInt(box.minZ);
                buffer.writeVarInt(box.maxX);
                buffer.writeVarInt(box.maxY);
                buffer.writeVarInt(box.maxZ);
            }

            buffer.writeVarInt(entry.edges.size());
            for (Edge edge : entry.edges) {
                buffer.writeVarInt(edge.x1);
                buffer.writeVarInt(edge.z1);
                buffer.writeVarInt(edge.x2);
                buffer.writeVarInt(edge.z2);
            }
        }
    }

    private static BorderVisualizationPayload decode(RegistryFriendlyByteBuf buffer) {
        BorderLayer layer = buffer.readEnum(BorderLayer.class);
        boolean visible = buffer.readBoolean();
        String dimension = buffer.readUtf();
        int claimVerticalRange = buffer.readVarInt();
        int renderDistance = buffer.readVarInt();
        int entryCount = readBoundedSize(buffer, MAX_ENTRIES, "entries");
        List<Entry> entries = new ArrayList<>(entryCount);

        for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
            BorderCategory category = buffer.readEnum(BorderCategory.class);
            String label = buffer.readUtf();
            int strokeColor = buffer.readInt();
            int fillColor = buffer.readInt();
            float strokeWidth = buffer.readFloat();
            boolean strokeBoxes = buffer.readBoolean();

            int boxCount = readBoundedSize(buffer, MAX_BOXES_PER_ENTRY, "boxes");
            List<Box> boxes = new ArrayList<>(boxCount);
            for (int i = 0; i < boxCount; i++) {
                boxes.add(new Box(
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt()
                ));
            }

            int edgeCount = readBoundedSize(buffer, MAX_EDGES_PER_ENTRY, "edges");
            List<Edge> edges = new ArrayList<>(edgeCount);
            for (int i = 0; i < edgeCount; i++) {
                edges.add(new Edge(
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt()
                ));
            }

            entries.add(new Entry(category, label, strokeColor, fillColor, strokeWidth, strokeBoxes, boxes, edges));
        }

        return new BorderVisualizationPayload(layer, visible, dimension, claimVerticalRange, renderDistance, entries);
    }

    private static int readBoundedSize(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid border visualization " + name + " count: " + size);
        }
        return size;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            BorderCategory category,
            String label,
            int strokeColor,
            int fillColor,
            float strokeWidth,
            boolean strokeBoxes,
            List<Box> boxes,
            List<Edge> edges
    ) {
        public Entry {
            label = label == null ? "" : label;
            boxes = boxes == null ? List.of() : List.copyOf(boxes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            if (boxes.size() > MAX_BOXES_PER_ENTRY) {
                throw new IllegalArgumentException("Too many border boxes in one entry: " + boxes.size());
            }
            if (edges.size() > MAX_EDGES_PER_ENTRY) {
                throw new IllegalArgumentException("Too many border edges in one entry: " + edges.size());
            }
        }
    }

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    public record Edge(int x1, int z1, int x2, int z2) {
    }
}
