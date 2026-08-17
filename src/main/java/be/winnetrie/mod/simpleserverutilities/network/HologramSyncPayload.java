package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HologramSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_LINES = 64;

    public static final Type<HologramSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "hologram_sync")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HologramSyncPayload> STREAM_CODEC =
            StreamCodec.of(HologramSyncPayload::encode, HologramSyncPayload::decode);

    public HologramSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many holograms: " + entries.size());
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, HologramSyncPayload payload) {
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeUtf(entry.id, 64);
            buffer.writeEnum(entry.type);
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeDouble(entry.x);
            buffer.writeDouble(entry.y);
            buffer.writeDouble(entry.z);
            buffer.writeInt(entry.color);
            buffer.writeInt(entry.backgroundColor);
            buffer.writeFloat(entry.scale);
            buffer.writeBoolean(entry.bold);
            buffer.writeBoolean(entry.italic);
            buffer.writeBoolean(entry.underlined);
            buffer.writeBoolean(entry.strikethrough);
            buffer.writeBoolean(entry.shadow);
            buffer.writeBoolean(entry.seeThrough);
            buffer.writeDouble(entry.viewDistance);
            buffer.writeUtf(entry.url, 2048);
            buffer.writeUtf(entry.imageSource, 2048);
            buffer.writeFloat(entry.imageWidth);
            buffer.writeFloat(entry.imageHeight);
            buffer.writeVarInt(entry.lines.size());
            for (String line : entry.lines) {
                buffer.writeUtf(line, HologramRichText.MAX_STORED_CHARACTERS);
            }
        }
    }

    private static HologramSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = bounded(buffer.readVarInt(), MAX_ENTRIES, "hologram entries");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(64);
            HologramType type = buffer.readEnum(HologramType.class);
            String dimension = buffer.readUtf(128);
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();
            int color = buffer.readInt();
            int backgroundColor = buffer.readInt();
            float scale = buffer.readFloat();
            boolean bold = buffer.readBoolean();
            boolean italic = buffer.readBoolean();
            boolean underlined = buffer.readBoolean();
            boolean strikethrough = buffer.readBoolean();
            boolean shadow = buffer.readBoolean();
            boolean seeThrough = buffer.readBoolean();
            double viewDistance = buffer.readDouble();
            String url = buffer.readUtf(2048);
            String imageSource = buffer.readUtf(2048);
            float imageWidth = buffer.readFloat();
            float imageHeight = buffer.readFloat();
            int lineCount = bounded(buffer.readVarInt(), MAX_LINES, "hologram lines");
            List<String> lines = new ArrayList<>(lineCount);
            for (int line = 0; line < lineCount; line++) {
                lines.add(buffer.readUtf(HologramRichText.MAX_STORED_CHARACTERS));
            }
            entries.add(new Entry(id, type, dimension, x, y, z, color, backgroundColor, scale, bold, italic,
                    underlined, strikethrough, shadow, seeThrough, viewDistance, url,
                    imageSource, imageWidth, imageHeight, lines));
        }
        return new HologramSyncPayload(entries);
    }

    private static int bounded(int value, int maximum, String label) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + value);
        }
        return value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            String id,
            HologramType type,
            String dimension,
            double x,
            double y,
            double z,
            int color,
            int backgroundColor,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean shadow,
            boolean seeThrough,
            double viewDistance,
            String url,
            String imageSource,
            float imageWidth,
            float imageHeight,
            List<String> lines
    ) {
        public Entry {
            id = id == null ? "" : id;
            type = type == null ? HologramType.TEXT : type;
            dimension = dimension == null ? "" : dimension;
            url = url == null ? "" : url;
            imageSource = imageSource == null ? "" : imageSource;
            lines = lines == null ? List.of() : List.copyOf(lines);
            if (lines.size() > MAX_LINES) {
                throw new IllegalArgumentException("Too many hologram lines: " + lines.size());
            }
        }
    }
}
