package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.utilitymining.UtilityMiningResolver;
import be.winnetrie.mod.simpleserverutilities.utilitymining.UtilityMiningType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact client preview. The server has already applied config, permissions and protection checks. */
public record UtilityMiningPreviewPayload(
        UtilityMiningType miningType,
        String dimension,
        int outlineColor,
        int brightness,
        boolean showInfo,
        String blockName,
        List<Long> blocks
) implements CustomPacketPayload {

    public static final Type<UtilityMiningPreviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "utility_mining_preview")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UtilityMiningPreviewPayload> STREAM_CODEC =
            StreamCodec.of(UtilityMiningPreviewPayload::encode, UtilityMiningPreviewPayload::decode);

    public UtilityMiningPreviewPayload {
        miningType = miningType == null ? UtilityMiningType.NONE : miningType;
        dimension = dimension == null ? "" : dimension;
        brightness = Math.max(10, Math.min(100, brightness));
        blockName = blockName == null ? "" : blockName.trim();
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        if (blocks.size() > UtilityMiningResolver.HARD_MAX_BLOCKS) {
            throw new IllegalArgumentException("Too many utility-mining preview blocks: " + blocks.size());
        }
    }

    public static UtilityMiningPreviewPayload clear() {
        return new UtilityMiningPreviewPayload(UtilityMiningType.NONE, "", 0, 10, false, "", List.of());
    }

    public static UtilityMiningPreviewPayload of(
            UtilityMiningType type,
            String dimension,
            int outlineColor,
            int brightness,
            boolean showInfo,
            String blockName,
            List<BlockPos> positions
    ) {
        List<Long> packed = positions.stream().map(BlockPos::asLong).toList();
        return new UtilityMiningPreviewPayload(type, dimension, outlineColor, brightness,
                showInfo, blockName, packed);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, UtilityMiningPreviewPayload payload) {
        buffer.writeEnum(payload.miningType());
        buffer.writeUtf(payload.dimension(), 256);
        buffer.writeInt(payload.outlineColor());
        buffer.writeVarInt(payload.brightness());
        buffer.writeBoolean(payload.showInfo());
        buffer.writeUtf(payload.blockName(), 128);
        buffer.writeVarInt(payload.blocks().size());
        for (long block : payload.blocks()) {
            buffer.writeLong(block);
        }
    }

    private static UtilityMiningPreviewPayload decode(RegistryFriendlyByteBuf buffer) {
        UtilityMiningType type = buffer.readEnum(UtilityMiningType.class);
        String dimension = buffer.readUtf(256);
        int color = buffer.readInt();
        int brightness = buffer.readVarInt();
        boolean showInfo = buffer.readBoolean();
        String blockName = buffer.readUtf(128);
        int count = buffer.readVarInt();
        if (count < 0 || count > UtilityMiningResolver.HARD_MAX_BLOCKS) {
            throw new IllegalArgumentException("Invalid utility-mining preview block count: " + count);
        }
        List<Long> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(buffer.readLong());
        }
        return new UtilityMiningPreviewPayload(type, dimension, color, brightness, showInfo, blockName, blocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
