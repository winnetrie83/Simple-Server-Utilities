package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonLobbyRequestPayload(String action, String dungeonId, long requestId) implements CustomPacketPayload {
    public static final Type<DungeonLobbyRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "dungeon_lobby_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonLobbyRequestPayload> STREAM_CODEC = StreamCodec.of(DungeonLobbyRequestPayload::encode, DungeonLobbyRequestPayload::decode);
    public DungeonLobbyRequestPayload { action = PayloadBounds.string(action, 32); dungeonId = PayloadBounds.string(dungeonId, 128); requestId = Math.max(0L, requestId); }
    private static void encode(RegistryFriendlyByteBuf buffer, DungeonLobbyRequestPayload payload) { buffer.writeUtf(payload.action, 32); buffer.writeUtf(payload.dungeonId, 128); buffer.writeVarLong(payload.requestId); }
    private static DungeonLobbyRequestPayload decode(RegistryFriendlyByteBuf buffer) { return new DungeonLobbyRequestPayload(buffer.readUtf(32), buffer.readUtf(128), buffer.readVarLong()); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
