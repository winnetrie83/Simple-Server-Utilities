package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative preview of the contents of the block/entity currently inspected by a player. */
public record BlockInformationContentPayload(
        int targetType,
        String dimension,
        long targetId,
        List<ItemStack> items,
        int usedSlots,
        int totalSlots,
        boolean truncated
) implements CustomPacketPayload {
    public static final int TARGET_NONE = 0;
    public static final int TARGET_BLOCK = 1;
    public static final int TARGET_ENTITY = 2;
    public static final int MAX_ITEMS = 54;

    public static final Type<BlockInformationContentPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "block_information_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockInformationContentPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeByte(payload.targetType());
                buffer.writeUtf(payload.dimension(), 128);
                buffer.writeLong(payload.targetId());
                buffer.writeVarInt(payload.usedSlots());
                buffer.writeVarInt(payload.totalSlots());
                buffer.writeBoolean(payload.truncated());
                buffer.writeVarInt(payload.items().size());
                for (ItemStack item : payload.items()) ItemStack.STREAM_CODEC.encode(buffer, item);
            }, buffer -> {
                int targetType = buffer.readByte();
                String dimension = buffer.readUtf(128);
                long targetId = buffer.readLong();
                int usedSlots = Math.max(0, buffer.readVarInt());
                int totalSlots = Math.max(0, buffer.readVarInt());
                boolean truncated = buffer.readBoolean();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_ITEMS) {
                    throw new IllegalArgumentException("Invalid Block Information item count: " + count);
                }
                List<ItemStack> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) items.add(ItemStack.STREAM_CODEC.decode(buffer));
                return new BlockInformationContentPayload(
                        targetType, dimension, targetId, items, usedSlots, totalSlots, truncated);
            });

    public BlockInformationContentPayload {
        targetType = targetType < TARGET_NONE || targetType > TARGET_ENTITY ? TARGET_NONE : targetType;
        dimension = dimension == null ? "" : dimension;
        usedSlots = Math.max(0, usedSlots);
        totalSlots = Math.max(0, totalSlots);
        List<ItemStack> safeItems = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item == null || item.isEmpty()) continue;
                // Preview packets carry only item type and count. Per-stack components are deliberately
                // stripped so nested container data, books, maps and other large/private metadata are not leaked.
                safeItems.add(new ItemStack(item.getItem(), Math.max(1, item.getCount())));
                if (safeItems.size() >= MAX_ITEMS) break;
            }
        }
        items = List.copyOf(safeItems);
        truncated = truncated || usedSlots > items.size();
    }

    public static BlockInformationContentPayload clear() {
        return new BlockInformationContentPayload(TARGET_NONE, "", 0L, List.of(), 0, 0, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
