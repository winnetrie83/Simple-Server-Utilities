package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded setup-tool state and the administrator-visible minigame/arena catalogue. */
public record MinigameSetupToolOpenPayload(
        String notice,
        boolean error,
        long requestId,
        String selectedMinigameId,
        String selectedArenaId,
        String action,
        int team,
        int index,
        boolean hasFirstPoint,
        long firstPoint,
        boolean hasSelection,
        String selectionDimension,
        long selectionPoint1,
        long selectionPoint2,
        long selectionVolume,
        List<GameEntry> games
) implements CustomPacketPayload {
    public static final int MAX_GAMES = 128, MAX_ARENAS = 32;
    public static final Type<MinigameSetupToolOpenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_setup_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameSetupToolOpenPayload> STREAM_CODEC =
            StreamCodec.of(MinigameSetupToolOpenPayload::encode, MinigameSetupToolOpenPayload::decode);

    public MinigameSetupToolOpenPayload {
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
        selectedMinigameId = PayloadBounds.string(selectedMinigameId, 64);
        selectedArenaId = PayloadBounds.string(selectedArenaId, 64);
        action = PayloadBounds.string(action, 48);
        team = Math.max(1, Math.min(16, team));
        index = Math.max(0, Math.min(63, index));
        selectionDimension = PayloadBounds.string(selectionDimension, 128);
        selectionVolume = Math.max(0L, selectionVolume);
        games = games == null ? List.of() : List.copyOf(games.subList(0, Math.min(MAX_GAMES, games.size())));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameSetupToolOpenPayload payload) {
        buffer.writeUtf(payload.notice, 512);
        buffer.writeBoolean(payload.error);
        buffer.writeVarLong(payload.requestId);
        buffer.writeUtf(payload.selectedMinigameId, 64);
        buffer.writeUtf(payload.selectedArenaId, 64);
        buffer.writeUtf(payload.action, 48);
        buffer.writeVarInt(payload.team);
        buffer.writeVarInt(payload.index);
        buffer.writeBoolean(payload.hasFirstPoint);
        buffer.writeLong(payload.firstPoint);
        buffer.writeBoolean(payload.hasSelection);
        buffer.writeUtf(payload.selectionDimension, 128);
        buffer.writeLong(payload.selectionPoint1);
        buffer.writeLong(payload.selectionPoint2);
        buffer.writeVarLong(payload.selectionVolume);
        buffer.writeVarInt(payload.games.size());
        for (GameEntry game : payload.games) game.encode(buffer);
    }

    private static MinigameSetupToolOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        String notice = buffer.readUtf(512);
        boolean error = buffer.readBoolean();
        long requestId = buffer.readVarLong();
        String game = buffer.readUtf(64);
        String arena = buffer.readUtf(64);
        String action = buffer.readUtf(48);
        int team = buffer.readVarInt();
        int index = buffer.readVarInt();
        boolean hasFirst = buffer.readBoolean();
        long first = buffer.readLong();
        boolean hasSelection = buffer.readBoolean();
        String dimension = buffer.readUtf(128);
        long p1 = buffer.readLong();
        long p2 = buffer.readLong();
        long volume = buffer.readVarLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_GAMES) throw new IllegalArgumentException("Invalid setup game count: " + count);
        ArrayList<GameEntry> games = new ArrayList<>(count);
        for (int i = 0; i < count; i++) games.add(GameEntry.decode(buffer));
        return new MinigameSetupToolOpenPayload(notice, error, requestId, game, arena, action, team, index,
                hasFirst, first, hasSelection, dimension, p1, p2, volume, games);
    }

    public record GameEntry(String id, String displayName, String gameType,
                            String team1Name, String team2Name, int team1Color, int team2Color,
                            List<ArenaEntry> arenas) {
        public GameEntry {
            id = PayloadBounds.string(id, 64);
            displayName = PayloadBounds.string(displayName, 128);
            gameType = PayloadBounds.string(gameType, 32);
            team1Name = PayloadBounds.string(team1Name, 32);
            team2Name = PayloadBounds.string(team2Name, 32);
            team1Color &= 0x00FFFFFF;
            team2Color &= 0x00FFFFFF;
            arenas = arenas == null ? List.of() : List.copyOf(arenas.subList(0, Math.min(MAX_ARENAS, arenas.size())));
        }
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64); buffer.writeUtf(displayName, 128); buffer.writeUtf(gameType, 32);
            buffer.writeUtf(team1Name, 32); buffer.writeUtf(team2Name, 32);
            buffer.writeInt(team1Color); buffer.writeInt(team2Color);
            buffer.writeVarInt(arenas.size());
            for (ArenaEntry arena : arenas) arena.encode(buffer);
        }
        private static GameEntry decode(RegistryFriendlyByteBuf buffer) {
            String id = buffer.readUtf(64), name = buffer.readUtf(128), type = buffer.readUtf(32);
            String team1 = buffer.readUtf(32), team2 = buffer.readUtf(32);
            int color1 = buffer.readInt(), color2 = buffer.readInt();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ARENAS) throw new IllegalArgumentException("Invalid setup arena count: " + count);
            ArrayList<ArenaEntry> arenas = new ArrayList<>(count);
            for (int i = 0; i < count; i++) arenas.add(ArenaEntry.decode(buffer));
            return new GameEntry(id, name, type, team1, team2, color1, color2, arenas);
        }
    }

    public record ArenaEntry(String id, String displayName, String regionId, boolean enabled,
                             String bounds, String playFloor, String spectatorBounds, int spawns, int specialPoints) {
        public ArenaEntry {
            id = PayloadBounds.string(id, 64); displayName = PayloadBounds.string(displayName, 128);
            regionId = PayloadBounds.string(regionId, 128); bounds = PayloadBounds.string(bounds, 128);
            playFloor = PayloadBounds.string(playFloor, 128); spectatorBounds = PayloadBounds.string(spectatorBounds, 128);
            spawns = Math.max(0, spawns); specialPoints = Math.max(0, specialPoints);
        }
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64); buffer.writeUtf(displayName, 128); buffer.writeUtf(regionId, 128);
            buffer.writeBoolean(enabled); buffer.writeUtf(bounds, 128); buffer.writeUtf(playFloor, 128);
            buffer.writeUtf(spectatorBounds, 128); buffer.writeVarInt(spawns); buffer.writeVarInt(specialPoints);
        }
        private static ArenaEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ArenaEntry(buffer.readUtf(64), buffer.readUtf(128), buffer.readUtf(128), buffer.readBoolean(),
                    buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(128), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
