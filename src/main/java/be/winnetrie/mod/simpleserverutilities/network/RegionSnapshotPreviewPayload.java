package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Strictly bounded, chunked real-block snapshot preview state for one administrator. */
public record RegionSnapshotPreviewPayload(
        boolean active,
        boolean reset,
        String snapshotName,
        String dimension,
        long origin,
        int sizeX,
        int sizeY,
        int sizeZ,
        int totalBlocks,
        int chunkIndex,
        int chunkCount,
        int paletteOffset,
        int totalPaletteEntries,
        List<String> palette,
        List<PreviewBlock> blocks
) implements CustomPacketPayload {
    public static final int MAX_BLOCKS_PER_CHUNK = 4096;
    public static final int MAX_PALETTE = 65536;
    public static final int MAX_PALETTE_PER_CHUNK = 48;
    private static final int MAX_STATE_LENGTH = 768;

    public static final Type<RegionSnapshotPreviewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_snapshot_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionSnapshotPreviewPayload> STREAM_CODEC =
            StreamCodec.of(RegionSnapshotPreviewPayload::encode, RegionSnapshotPreviewPayload::decode);

    public RegionSnapshotPreviewPayload {
        snapshotName = PayloadBounds.string(snapshotName, 64);
        dimension = PayloadBounds.string(dimension, 128);
        sizeX = Math.max(0, Math.min(4096, sizeX));
        sizeY = Math.max(0, Math.min(4096, sizeY));
        sizeZ = Math.max(0, Math.min(4096, sizeZ));
        totalBlocks = Math.max(0, totalBlocks);
        chunkCount = Math.max(1, Math.min(4096, chunkCount));
        chunkIndex = Math.max(0, Math.min(chunkCount - 1, chunkIndex));
        totalPaletteEntries = Math.max(0, Math.min(MAX_PALETTE, totalPaletteEntries));
        paletteOffset = Math.max(0, Math.min(totalPaletteEntries, paletteOffset));
        palette = palette == null ? List.of() : palette.stream()
                .map(value -> PayloadBounds.string(value, MAX_STATE_LENGTH))
                .limit(MAX_PALETTE_PER_CHUNK)
                .toList();
        if (paletteOffset + palette.size() > totalPaletteEntries) {
            palette = palette.subList(0, Math.max(0, totalPaletteEntries - paletteOffset));
        }
        blocks = blocks == null ? List.of() : List.copyOf(blocks.subList(0,
                Math.min(MAX_BLOCKS_PER_CHUNK, blocks.size())));
    }

    public static RegionSnapshotPreviewPayload clear() {
        return new RegionSnapshotPreviewPayload(false, true, "", "", 0L,
                0, 0, 0, 0, 0, 1, 0, 0, List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RegionSnapshotPreviewPayload payload) {
        buffer.writeBoolean(payload.active);
        buffer.writeBoolean(payload.reset);
        buffer.writeUtf(payload.snapshotName, 64);
        buffer.writeUtf(payload.dimension, 128);
        buffer.writeLong(payload.origin);
        buffer.writeVarInt(payload.sizeX);
        buffer.writeVarInt(payload.sizeY);
        buffer.writeVarInt(payload.sizeZ);
        buffer.writeVarInt(payload.totalBlocks);
        buffer.writeVarInt(payload.chunkIndex);
        buffer.writeVarInt(payload.chunkCount);
        buffer.writeVarInt(payload.paletteOffset);
        buffer.writeVarInt(payload.totalPaletteEntries);
        buffer.writeVarInt(payload.palette.size());
        for (String state : payload.palette) buffer.writeUtf(state, MAX_STATE_LENGTH);
        buffer.writeVarInt(payload.blocks.size());
        for (PreviewBlock block : payload.blocks) {
            buffer.writeVarInt(block.relativeIndex());
            buffer.writeVarInt(block.paletteIndex());
        }
    }

    private static RegionSnapshotPreviewPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        boolean reset = buffer.readBoolean();
        String name = buffer.readUtf(64);
        String dimension = buffer.readUtf(128);
        long origin = buffer.readLong();
        int sizeX = buffer.readVarInt();
        int sizeY = buffer.readVarInt();
        int sizeZ = buffer.readVarInt();
        int totalBlocks = buffer.readVarInt();
        int chunkIndex = buffer.readVarInt();
        int chunkCount = buffer.readVarInt();
        if (chunkCount < 1 || chunkCount > 4096 || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("Invalid snapshot preview chunk " + chunkIndex + "/" + chunkCount);
        }
        int paletteOffset = buffer.readVarInt();
        int totalPaletteEntries = buffer.readVarInt();
        if (totalPaletteEntries < 0 || totalPaletteEntries > MAX_PALETTE
                || paletteOffset < 0 || paletteOffset > totalPaletteEntries) {
            throw new IllegalArgumentException("Invalid snapshot preview palette bounds.");
        }
        int paletteSize = buffer.readVarInt();
        if (paletteSize < 0 || paletteSize > MAX_PALETTE_PER_CHUNK
                || paletteOffset + paletteSize > totalPaletteEntries) {
            throw new IllegalArgumentException("Invalid snapshot preview palette segment size: " + paletteSize);
        }
        List<String> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) palette.add(buffer.readUtf(MAX_STATE_LENGTH));
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_BLOCKS_PER_CHUNK) {
            throw new IllegalArgumentException("Invalid snapshot preview block count: " + count);
        }
        List<PreviewBlock> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) blocks.add(new PreviewBlock(buffer.readVarInt(), buffer.readVarInt()));
        return new RegionSnapshotPreviewPayload(active, reset, name, dimension, origin, sizeX, sizeY, sizeZ,
                totalBlocks, chunkIndex, chunkCount, paletteOffset, totalPaletteEntries, palette, blocks);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record PreviewBlock(int relativeIndex, int paletteIndex) {
        public PreviewBlock {
            relativeIndex = Math.max(0, relativeIndex);
            paletteIndex = Math.max(0, paletteIndex);
        }
    }
}
