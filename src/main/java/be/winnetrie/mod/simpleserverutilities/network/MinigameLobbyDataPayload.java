package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Complete bounded lobby snapshot for one player. */
public record MinigameLobbyDataPayload(
        String notice,
        boolean error,
        boolean canAdmin,
        boolean adminView,
        long requestId,
        String queuedMinigameId,
        String activeMatchId,
        List<GameEntry> games
) implements CustomPacketPayload {
    public static final int MAX_GAMES = 128;
    public static final Type<MinigameLobbyDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_lobby_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameLobbyDataPayload> STREAM_CODEC =
            StreamCodec.of(MinigameLobbyDataPayload::encode, MinigameLobbyDataPayload::decode);

    public MinigameLobbyDataPayload {
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
        queuedMinigameId = PayloadBounds.string(queuedMinigameId, 64);
        activeMatchId = PayloadBounds.string(activeMatchId, 64);
        games = games == null ? List.of() : List.copyOf(games.subList(0, Math.min(MAX_GAMES, games.size())));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameLobbyDataPayload payload) {
        buffer.writeUtf(payload.notice, 512);
        buffer.writeBoolean(payload.error);
        buffer.writeBoolean(payload.canAdmin);
        buffer.writeBoolean(payload.adminView);
        buffer.writeVarLong(payload.requestId);
        buffer.writeUtf(payload.queuedMinigameId, 64);
        buffer.writeUtf(payload.activeMatchId, 64);
        buffer.writeVarInt(payload.games.size());
        for (GameEntry game : payload.games) game.encode(buffer);
    }

    private static MinigameLobbyDataPayload decode(RegistryFriendlyByteBuf buffer) {
        String notice = buffer.readUtf(512);
        boolean error = buffer.readBoolean();
        boolean admin = buffer.readBoolean();
        boolean adminView = buffer.readBoolean();
        long request = buffer.readVarLong();
        String queued = buffer.readUtf(64);
        String match = buffer.readUtf(64);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_GAMES) throw new IllegalArgumentException("Invalid minigame game count: " + count);
        ArrayList<GameEntry> games = new ArrayList<>(count);
        for (int i = 0; i < count; i++) games.add(GameEntry.decode(buffer));
        return new MinigameLobbyDataPayload(notice, error, admin, adminView, request, queued, match, games);
    }

    public record GameEntry(
            String id,
            String displayName,
            String description,
            String iconItem,
            String gameType,
            boolean enabled,
            int minPlayers,
            int maxPlayers,
            int teamCount,
            int queuedPlayers,
            int runningMatches,
            int freeArenas,
            int blockedArenas,
            String victoryMode,
            boolean requirementsMet,
            String requirementReason,
            boolean queuedHere,
            boolean activeHere,
            boolean rolesEnabled,
            String preferredRole,
            String assignedRole,
            String matchState,
            int team,
            long score
    ) {
        public GameEntry {
            id = PayloadBounds.string(id, 64);
            displayName = PayloadBounds.string(displayName, 128);
            description = PayloadBounds.string(description, 8_192);
            iconItem = PayloadBounds.string(iconItem, 128);
            gameType = PayloadBounds.string(gameType, 32);
            minPlayers = Math.max(1, minPlayers);
            maxPlayers = Math.max(minPlayers, maxPlayers);
            teamCount = Math.max(1, teamCount);
            queuedPlayers = Math.max(0, queuedPlayers);
            runningMatches = Math.max(0, runningMatches);
            freeArenas = Math.max(0, freeArenas);
            blockedArenas = Math.max(0, blockedArenas);
            victoryMode = PayloadBounds.string(victoryMode, 32);
            requirementReason = PayloadBounds.string(requirementReason, 512);
            preferredRole = PayloadBounds.string(preferredRole, 16);
            assignedRole = PayloadBounds.string(assignedRole, 16);
            matchState = PayloadBounds.string(matchState, 32);
            team = Math.max(0, team);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64);
            buffer.writeUtf(displayName, 128);
            buffer.writeUtf(description, 8_192);
            buffer.writeUtf(iconItem, 128);
            buffer.writeUtf(gameType, 32);
            buffer.writeBoolean(enabled);
            buffer.writeVarInt(minPlayers);
            buffer.writeVarInt(maxPlayers);
            buffer.writeVarInt(teamCount);
            buffer.writeVarInt(queuedPlayers);
            buffer.writeVarInt(runningMatches);
            buffer.writeVarInt(freeArenas);
            buffer.writeVarInt(blockedArenas);
            buffer.writeUtf(victoryMode, 32);
            buffer.writeBoolean(requirementsMet);
            buffer.writeUtf(requirementReason, 512);
            buffer.writeBoolean(queuedHere);
            buffer.writeBoolean(activeHere);
            buffer.writeBoolean(rolesEnabled);
            buffer.writeUtf(preferredRole, 16);
            buffer.writeUtf(assignedRole, 16);
            buffer.writeUtf(matchState, 32);
            buffer.writeVarInt(team);
            buffer.writeVarLong(score);
        }

        private static GameEntry decode(RegistryFriendlyByteBuf buffer) {
            return new GameEntry(buffer.readUtf(64), buffer.readUtf(128), buffer.readUtf(8_192),
                    buffer.readUtf(128), buffer.readUtf(32), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readUtf(32), buffer.readBoolean(), buffer.readUtf(512),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(16),
                    buffer.readUtf(16), buffer.readUtf(32), buffer.readVarInt(), buffer.readVarLong());
        }
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
