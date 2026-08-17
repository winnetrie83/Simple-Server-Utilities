package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded post-game result table and cosmetic progression summary. */
public record MinigameResultsPayload(
        boolean visible,
        String matchId,
        String minigameId,
        String title,
        String reason,
        int voteSecondsRemaining,
        int experienceGained,
        int level,
        long experienceIntoLevel,
        long experienceForNextLevel,
        List<String> badges,
        List<PlayerRow> rows,
        long requestId
) implements CustomPacketPayload {
    public static final int MAX_ROWS = 128;
    public static final int MAX_BADGES = 8;
    public static final Type<MinigameResultsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_results"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameResultsPayload> STREAM_CODEC =
            StreamCodec.of(MinigameResultsPayload::encode, MinigameResultsPayload::decode);

    public MinigameResultsPayload {
        matchId = PayloadBounds.string(matchId, 64);
        minigameId = PayloadBounds.string(minigameId, 64);
        title = PayloadBounds.string(title, 128);
        reason = PayloadBounds.string(reason, 512);
        voteSecondsRemaining = Math.max(0, Math.min(600, voteSecondsRemaining));
        experienceGained = Math.max(0, Math.min(1_000_000, experienceGained));
        level = Math.max(1, Math.min(100, level));
        experienceIntoLevel = Math.max(0L, experienceIntoLevel);
        experienceForNextLevel = Math.max(0L, experienceForNextLevel);
        badges = badges == null ? List.of() : List.copyOf(badges.subList(0, Math.min(MAX_BADGES, badges.size())));
        rows = rows == null ? List.of() : List.copyOf(rows.subList(0, Math.min(MAX_ROWS, rows.size())));
        requestId = Math.max(0L, requestId);
    }

    public static MinigameResultsPayload clear() {
        return new MinigameResultsPayload(false, "", "", "", "", 0, 0, 1, 0L, 100L,
                List.of(), List.of(), 0L);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameResultsPayload payload) {
        buffer.writeBoolean(payload.visible);
        buffer.writeUtf(payload.matchId, 64);
        buffer.writeUtf(payload.minigameId, 64);
        buffer.writeUtf(payload.title, 128);
        buffer.writeUtf(payload.reason, 512);
        buffer.writeVarInt(payload.voteSecondsRemaining);
        buffer.writeVarInt(payload.experienceGained);
        buffer.writeVarInt(payload.level);
        buffer.writeVarLong(payload.experienceIntoLevel);
        buffer.writeVarLong(payload.experienceForNextLevel);
        buffer.writeVarInt(payload.badges.size());
        for (String badge : payload.badges) buffer.writeUtf(PayloadBounds.string(badge, 48), 48);
        buffer.writeVarInt(payload.rows.size());
        for (PlayerRow row : payload.rows) row.encode(buffer);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameResultsPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        String match = buffer.readUtf(64);
        String minigame = buffer.readUtf(64);
        String title = buffer.readUtf(128);
        String reason = buffer.readUtf(512);
        int vote = buffer.readVarInt();
        int gained = buffer.readVarInt();
        int level = buffer.readVarInt();
        long into = buffer.readVarLong();
        long needed = buffer.readVarLong();
        int badgeCount = buffer.readVarInt();
        if (badgeCount < 0 || badgeCount > MAX_BADGES) throw new IllegalArgumentException("Invalid minigame badge count.");
        ArrayList<String> badges = new ArrayList<>(badgeCount);
        for (int index = 0; index < badgeCount; index++) badges.add(buffer.readUtf(48));
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ROWS) throw new IllegalArgumentException("Invalid minigame result row count.");
        ArrayList<PlayerRow> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) rows.add(PlayerRow.decode(buffer));
        return new MinigameResultsPayload(visible, match, minigame, title, reason, vote, gained,
                level, into, needed, badges, rows, buffer.readVarLong());
    }

    public record PlayerRow(String playerId, String name, int team, String role, boolean winner,
                            long score, long kills, long deaths, long assists,
                            long damage, long healing, long captures, long defenses,
                            long objectiveSeconds, long contribution) {
        public PlayerRow {
            playerId = PayloadBounds.string(playerId, 64);
            name = PayloadBounds.string(name, 64);
            team = Math.max(0, Math.min(16, team));
            role = PayloadBounds.string(role, 16);
            score = Math.max(0L, score);
            kills = Math.max(0L, kills);
            deaths = Math.max(0L, deaths);
            assists = Math.max(0L, assists);
            damage = Math.max(0L, damage);
            healing = Math.max(0L, healing);
            captures = Math.max(0L, captures);
            defenses = Math.max(0L, defenses);
            objectiveSeconds = Math.max(0L, objectiveSeconds);
            contribution = Math.max(0L, contribution);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(playerId, 64);
            buffer.writeUtf(name, 64);
            buffer.writeVarInt(team);
            buffer.writeUtf(role, 16);
            buffer.writeBoolean(winner);
            buffer.writeVarLong(score);
            buffer.writeVarLong(kills);
            buffer.writeVarLong(deaths);
            buffer.writeVarLong(assists);
            buffer.writeVarLong(damage);
            buffer.writeVarLong(healing);
            buffer.writeVarLong(captures);
            buffer.writeVarLong(defenses);
            buffer.writeVarLong(objectiveSeconds);
            buffer.writeVarLong(contribution);
        }

        private static PlayerRow decode(RegistryFriendlyByteBuf buffer) {
            return new PlayerRow(buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt(),
                    buffer.readUtf(16), buffer.readBoolean(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
