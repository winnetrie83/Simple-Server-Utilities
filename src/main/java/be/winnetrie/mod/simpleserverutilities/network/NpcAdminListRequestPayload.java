package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAdminListRequestPayload(String mode, String query, int page, int pageSize, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcAdminListRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_admin_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcAdminListRequestPayload> STREAM_CODEC =
            StreamCodec.of((b,p)->{b.writeUtf(p.mode,16);b.writeUtf(p.query,64);b.writeVarInt(p.page);b.writeVarInt(p.pageSize);b.writeVarLong(p.requestId);},
                    b->new NpcAdminListRequestPayload(b.readUtf(16),b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readVarLong()));
    public NpcAdminListRequestPayload {
        mode = "templates".equalsIgnoreCase(mode) ? "templates" : "spawns".equalsIgnoreCase(mode) ? "spawns" : "placements";
        query = query == null ? "" : query; page = Math.max(0,page); pageSize = Math.max(1,Math.min(12,pageSize)); requestId=Math.max(0,requestId);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
