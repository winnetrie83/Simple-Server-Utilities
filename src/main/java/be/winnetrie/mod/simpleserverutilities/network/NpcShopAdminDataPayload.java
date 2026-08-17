package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One bounded page of reusable shared shops for the visual administrator library. */
public record NpcShopAdminDataPayload(String query, int pageIndex, int pageCount, int totalShops,
        List<Entry> entries, String notice, boolean error, long requestId) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 10;
    public static final Type<NpcShopAdminDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_admin_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopAdminDataPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopAdminDataPayload::encode, NpcShopAdminDataPayload::decode);

    public NpcShopAdminDataPayload {
        query = PayloadBounds.string(query, 64);
        pageIndex = Math.max(0, pageIndex);
        pageCount = Math.max(1, pageCount);
        totalShops = Math.max(0, totalShops);
        entries = entries == null ? List.of() : List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
        notice = PayloadBounds.string(notice, 256);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcShopAdminDataPayload payload) {
        buffer.writeUtf(payload.query, 64);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarInt(payload.pageCount);
        buffer.writeVarInt(payload.totalShops);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) entry.encode(buffer);
        buffer.writeUtf(payload.notice, 256);
        buffer.writeBoolean(payload.error);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcShopAdminDataPayload decode(RegistryFriendlyByteBuf buffer) {
        String query = buffer.readUtf(64);
        int page = buffer.readVarInt(), pages = buffer.readVarInt(), total = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid NPC shop admin row count.");
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(Entry.decode(buffer));
        return new NpcShopAdminDataPayload(query, page, pages, total, entries,
                buffer.readUtf(256), buffer.readBoolean(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(String id, String displayName, boolean enabled, int offerCount,
            int npcDefinitionCount, int npcPlacementCount) {
        public Entry {
            id = PayloadBounds.string(id, 64);
            displayName = PayloadBounds.string(displayName, 64);
            offerCount = Math.max(0, offerCount);
            npcDefinitionCount = Math.max(0, npcDefinitionCount);
            npcPlacementCount = Math.max(0, npcPlacementCount);
        }
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64); buffer.writeUtf(displayName, 64);
            buffer.writeBoolean(enabled); buffer.writeVarInt(offerCount);
            buffer.writeVarInt(npcDefinitionCount); buffer.writeVarInt(npcPlacementCount);
        }
        private static Entry decode(RegistryFriendlyByteBuf buffer) {
            return new Entry(buffer.readUtf(64), buffer.readUtf(64), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }
}
