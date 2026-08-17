package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Detailed bounded snapshot opened by the SSU menu key while participating in a match. */
public record MinigameMatchOverviewPayload(
        boolean active,
        boolean openDashboardFallback,
        String matchId,
        String minigameId,
        String displayName,
        String gameType,
        String description,
        String phase,
        long remainingSeconds,
        int yourTeam,
        String yourTeamName,
        String yourRole,
        long yourScore,
        boolean spectator,
        boolean overtime,
        List<TeamRow> teams,
        List<PlayerRow> players,
        List<String> objectiveLines,
        List<String> statusLines,
        List<String> ruleLines,
        String notice,
        boolean error,
        long requestId
) implements CustomPacketPayload {
    public static final int MAX_TEAMS = 16;
    public static final int MAX_PLAYERS = 128;
    public static final int MAX_LINES = 32;
    public static final Type<MinigameMatchOverviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_match_overview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameMatchOverviewPayload> STREAM_CODEC =
            StreamCodec.of(MinigameMatchOverviewPayload::encode, MinigameMatchOverviewPayload::decode);

    public MinigameMatchOverviewPayload {
        matchId = PayloadBounds.string(matchId, 64);
        minigameId = PayloadBounds.string(minigameId, 64);
        displayName = PayloadBounds.string(displayName, 128);
        gameType = PayloadBounds.string(gameType, 32);
        description = PayloadBounds.string(description, 2_048);
        phase = PayloadBounds.string(phase, 32);
        remainingSeconds = Math.max(-1L, Math.min(86_400L, remainingSeconds));
        yourTeam = Math.max(0, Math.min(16, yourTeam));
        yourTeamName = PayloadBounds.string(yourTeamName, 64);
        yourRole = PayloadBounds.string(yourRole, 16);
        yourScore = Math.max(0L, yourScore);
        teams = teams == null ? List.of() : List.copyOf(teams.subList(0, Math.min(MAX_TEAMS, teams.size())));
        players = players == null ? List.of() : List.copyOf(players.subList(0, Math.min(MAX_PLAYERS, players.size())));
        objectiveLines = boundedLines(objectiveLines);
        statusLines = boundedLines(statusLines);
        ruleLines = boundedLines(ruleLines);
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
    }

    public static MinigameMatchOverviewPayload inactive(boolean openDashboardFallback, String notice,
                                                         boolean error, long requestId) {
        return new MinigameMatchOverviewPayload(false, openDashboardFallback, "", "", "", "", "", "",
                0L, 0, "", "", 0L, false, false, List.of(), List.of(), List.of(), List.of(), List.of(),
                notice, error, requestId);
    }

    private static List<String> boundedLines(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        ArrayList<String> lines = new ArrayList<>();
        for (String line : source) {
            lines.add(PayloadBounds.string(line, 256));
            if (lines.size() >= MAX_LINES) break;
        }
        return List.copyOf(lines);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameMatchOverviewPayload payload) {
        buffer.writeBoolean(payload.active);
        buffer.writeBoolean(payload.openDashboardFallback);
        buffer.writeUtf(payload.matchId, 64);
        buffer.writeUtf(payload.minigameId, 64);
        buffer.writeUtf(payload.displayName, 128);
        buffer.writeUtf(payload.gameType, 32);
        buffer.writeUtf(payload.description, 2_048);
        buffer.writeUtf(payload.phase, 32);
        buffer.writeLong(payload.remainingSeconds);
        buffer.writeVarInt(payload.yourTeam);
        buffer.writeUtf(payload.yourTeamName, 64);
        buffer.writeUtf(payload.yourRole, 16);
        buffer.writeVarLong(payload.yourScore);
        buffer.writeBoolean(payload.spectator);
        buffer.writeBoolean(payload.overtime);
        buffer.writeVarInt(payload.teams.size());
        for (TeamRow row : payload.teams) row.encode(buffer);
        buffer.writeVarInt(payload.players.size());
        for (PlayerRow row : payload.players) row.encode(buffer);
        writeLines(buffer, payload.objectiveLines);
        writeLines(buffer, payload.statusLines);
        writeLines(buffer, payload.ruleLines);
        buffer.writeUtf(payload.notice, 512);
        buffer.writeBoolean(payload.error);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameMatchOverviewPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        boolean fallback = buffer.readBoolean();
        String match = buffer.readUtf(64);
        String minigame = buffer.readUtf(64);
        String display = buffer.readUtf(128);
        String gameType = buffer.readUtf(32);
        String description = buffer.readUtf(2_048);
        String phase = buffer.readUtf(32);
        long remaining = buffer.readLong();
        int team = buffer.readVarInt();
        String teamName = buffer.readUtf(64);
        String role = buffer.readUtf(16);
        long score = buffer.readVarLong();
        boolean spectator = buffer.readBoolean();
        boolean overtime = buffer.readBoolean();
        int teamCount = boundedCount(buffer.readVarInt(), MAX_TEAMS, "team");
        ArrayList<TeamRow> teams = new ArrayList<>(teamCount);
        for (int index = 0; index < teamCount; index++) teams.add(TeamRow.decode(buffer));
        int playerCount = boundedCount(buffer.readVarInt(), MAX_PLAYERS, "player");
        ArrayList<PlayerRow> players = new ArrayList<>(playerCount);
        for (int index = 0; index < playerCount; index++) players.add(PlayerRow.decode(buffer));
        List<String> objectives = readLines(buffer);
        List<String> statuses = readLines(buffer);
        List<String> rules = readLines(buffer);
        return new MinigameMatchOverviewPayload(active, fallback, match, minigame, display, gameType,
                description, phase, remaining, team, teamName, role, score, spectator, overtime,
                teams, players, objectives, statuses, rules, buffer.readUtf(512), buffer.readBoolean(),
                buffer.readVarLong());
    }

    private static void writeLines(RegistryFriendlyByteBuf buffer, List<String> lines) {
        buffer.writeVarInt(lines.size());
        for (String line : lines) buffer.writeUtf(PayloadBounds.string(line, 256), 256);
    }

    private static List<String> readLines(RegistryFriendlyByteBuf buffer) {
        int count = boundedCount(buffer.readVarInt(), MAX_LINES, "line");
        ArrayList<String> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) lines.add(buffer.readUtf(256));
        return List.copyOf(lines);
    }

    private static int boundedCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid minigame overview " + label + " count.");
        return count;
    }

    public record TeamRow(int team, String name, long score, int players) {
        public TeamRow {
            team = Math.max(0, Math.min(16, team));
            name = PayloadBounds.string(name, 64);
            score = Math.max(0L, score);
            players = Math.max(0, Math.min(128, players));
        }
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(team);
            buffer.writeUtf(name, 64);
            buffer.writeVarLong(score);
            buffer.writeVarInt(players);
        }
        private static TeamRow decode(RegistryFriendlyByteBuf buffer) {
            return new TeamRow(buffer.readVarInt(), buffer.readUtf(64), buffer.readVarLong(), buffer.readVarInt());
        }
    }

    public record PlayerRow(String playerId, String name, int team, String teamName, String role,
                            long score, long kills, long deaths, long assists, long captures,
                            long defenses, boolean disconnected, boolean eliminated, boolean self) {
        public PlayerRow {
            playerId = PayloadBounds.string(playerId, 64);
            name = PayloadBounds.string(name, 64);
            team = Math.max(0, Math.min(16, team));
            teamName = PayloadBounds.string(teamName, 64);
            role = PayloadBounds.string(role, 16);
            score = Math.max(0L, score);
            kills = Math.max(0L, kills);
            deaths = Math.max(0L, deaths);
            assists = Math.max(0L, assists);
            captures = Math.max(0L, captures);
            defenses = Math.max(0L, defenses);
        }
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(playerId, 64);
            buffer.writeUtf(name, 64);
            buffer.writeVarInt(team);
            buffer.writeUtf(teamName, 64);
            buffer.writeUtf(role, 16);
            buffer.writeVarLong(score);
            buffer.writeVarLong(kills);
            buffer.writeVarLong(deaths);
            buffer.writeVarLong(assists);
            buffer.writeVarLong(captures);
            buffer.writeVarLong(defenses);
            buffer.writeBoolean(disconnected);
            buffer.writeBoolean(eliminated);
            buffer.writeBoolean(self);
        }
        private static PlayerRow decode(RegistryFriendlyByteBuf buffer) {
            return new PlayerRow(buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt(),
                    buffer.readUtf(64), buffer.readUtf(16), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
