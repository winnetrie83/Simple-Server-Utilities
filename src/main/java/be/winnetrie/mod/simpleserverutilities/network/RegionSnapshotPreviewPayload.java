package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded translucent selection-snapshot preview state for one administrator. */
public record RegionSnapshotPreviewPayload(
        boolean active,
        String snapshotName,
        String dimension,
        long origin,
        int sizeX,
        int sizeY,
        int sizeZ,
        int totalBlocks,
        boolean sampled,
        List<PreviewBlock> blocks
) implements CustomPacketPayload {
    public static final int MAX_BLOCKS = 4096;
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
        blocks = blocks == null ? List.of() : List.copyOf(blocks.subList(0, Math.min(MAX_BLOCKS, blocks.size())));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RegionSnapshotPreviewPayload payload) {
        buffer.writeBoolean(payload.active);
        buffer.writeUtf(payload.snapshotName, 64);
        buffer.writeUtf(payload.dimension, 128);
        buffer.writeLong(payload.origin);
        buffer.writeVarInt(payload.sizeX);
        buffer.writeVarInt(payload.sizeY);
        buffer.writeVarInt(payload.sizeZ);
        buffer.writeVarInt(payload.totalBlocks);
        buffer.writeBoolean(payload.sampled);
        buffer.writeVarInt(payload.blocks.size());
        for (PreviewBlock block : payload.blocks) {
            buffer.writeVarInt(block.relativeIndex());
            buffer.writeInt(block.color());
        }
    }

    private static RegionSnapshotPreviewPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        String name = buffer.readUtf(64);
        String dimension = buffer.readUtf(128);
        long origin = buffer.readLong();
        int sizeX = buffer.readVarInt();
        int sizeY = buffer.readVarInt();
        int sizeZ = buffer.readVarInt();
        int totalBlocks = buffer.readVarInt();
        boolean sampled = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_BLOCKS) throw new IllegalArgumentException("Invalid snapshot preview block count: " + count);
        List<PreviewBlock> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) blocks.add(new PreviewBlock(buffer.readVarInt(), buffer.readInt()));
        return new RegionSnapshotPreviewPayload(active, name, dimension, origin, sizeX, sizeY, sizeZ,
                totalBlocks, sampled, blocks);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record PreviewBlock(int relativeIndex, int color) { }
}
