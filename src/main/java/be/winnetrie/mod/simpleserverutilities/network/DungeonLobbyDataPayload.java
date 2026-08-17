package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete bounded dungeon lobby snapshot for one player. */
public record DungeonLobbyDataPayload(
        String notice, boolean error, boolean canAdmin, long requestId,
        String queuedDungeonId, String activeRunId, List<DungeonEntry> dungeons
) implements CustomPacketPayload {
    public static final int MAX_DUNGEONS = 128;
    public static final Type<DungeonLobbyDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "dungeon_lobby_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonLobbyDataPayload> STREAM_CODEC = StreamCodec.of(DungeonLobbyDataPayload::encode, DungeonLobbyDataPayload::decode);

    public DungeonLobbyDataPayload {
        notice = PayloadBounds.string(notice, 512); requestId = Math.max(0L, requestId);
        queuedDungeonId = PayloadBounds.string(queuedDungeonId, 64); activeRunId = PayloadBounds.string(activeRunId, 64);
        dungeons = dungeons == null ? List.of() : List.copyOf(dungeons.subList(0, Math.min(MAX_DUNGEONS, dungeons.size())));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, DungeonLobbyDataPayload payload) {
        buffer.writeUtf(payload.notice, 512); buffer.writeBoolean(payload.error); buffer.writeBoolean(payload.canAdmin);
        buffer.writeVarLong(payload.requestId); buffer.writeUtf(payload.queuedDungeonId, 64); buffer.writeUtf(payload.activeRunId, 64);
        buffer.writeVarInt(payload.dungeons.size()); for (DungeonEntry dungeon : payload.dungeons) dungeon.encode(buffer);
    }

    private static DungeonLobbyDataPayload decode(RegistryFriendlyByteBuf buffer) {
        String notice = buffer.readUtf(512); boolean error = buffer.readBoolean(); boolean admin = buffer.readBoolean(); long request = buffer.readVarLong();
        String queued = buffer.readUtf(64); String run = buffer.readUtf(64); int count = Math.min(MAX_DUNGEONS, Math.max(0, buffer.readVarInt()));
        ArrayList<DungeonEntry> entries = new ArrayList<>(count); for (int i = 0; i < count; i++) entries.add(DungeonEntry.decode(buffer));
        return new DungeonLobbyDataPayload(notice, error, admin, request, queued, run, entries);
    }

    public record DungeonEntry(
            String id, String displayName, String description, String iconItem, boolean enabled,
            int minPlayers, int maxPlayers, int livesPerPlayer, int stageCount,
            int queuedPlayers, int runningRuns, int freeArenas, int blockedArenas,
            boolean requirementsMet, String requirementReason, boolean queuedHere, boolean activeHere,
            String runState, int currentStage, String stageName, long stageProgress, long stageRequired, int remainingLives
    ) {
        public DungeonEntry {
            id = PayloadBounds.string(id, 64); displayName = PayloadBounds.string(displayName, 128); description = PayloadBounds.string(description, 8_192); iconItem = PayloadBounds.string(iconItem, 128);
            minPlayers = Math.max(1, minPlayers); maxPlayers = Math.max(minPlayers, maxPlayers); livesPerPlayer = Math.max(0, livesPerPlayer);
            stageCount = Math.max(0, stageCount); queuedPlayers = Math.max(0, queuedPlayers); runningRuns = Math.max(0, runningRuns);
            freeArenas = Math.max(0, freeArenas); blockedArenas = Math.max(0, blockedArenas); requirementReason = PayloadBounds.string(requirementReason, 512);
            runState = PayloadBounds.string(runState, 32); currentStage = Math.max(0, currentStage); stageName = PayloadBounds.string(stageName, 128);
            stageProgress = Math.max(0L, stageProgress); stageRequired = Math.max(0L, stageRequired); remainingLives = Math.max(0, remainingLives);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id,64); buffer.writeUtf(displayName,128); buffer.writeUtf(description,8_192); buffer.writeUtf(iconItem,128); buffer.writeBoolean(enabled);
            buffer.writeVarInt(minPlayers); buffer.writeVarInt(maxPlayers); buffer.writeVarInt(livesPerPlayer); buffer.writeVarInt(stageCount);
            buffer.writeVarInt(queuedPlayers); buffer.writeVarInt(runningRuns); buffer.writeVarInt(freeArenas); buffer.writeVarInt(blockedArenas);
            buffer.writeBoolean(requirementsMet); buffer.writeUtf(requirementReason,512); buffer.writeBoolean(queuedHere); buffer.writeBoolean(activeHere);
            buffer.writeUtf(runState,32); buffer.writeVarInt(currentStage); buffer.writeUtf(stageName,128); buffer.writeVarLong(stageProgress); buffer.writeVarLong(stageRequired); buffer.writeVarInt(remainingLives);
        }

        private static DungeonEntry decode(RegistryFriendlyByteBuf buffer) {
            return new DungeonEntry(buffer.readUtf(64), buffer.readUtf(128), buffer.readUtf(8_192), buffer.readUtf(128), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(512), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readUtf(32), buffer.readVarInt(), buffer.readUtf(128), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt());
        }
    }


    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
