package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Cosmetic-only minigame progression profile, ratings and configured weekly challenge progress. */
public record MinigameProfilePayload(
        int level,
        long experienceIntoLevel,
        long experienceForNextLevel,
        long matchesPlayed,
        long matchesWon,
        String selectedTitle,
        String selectedVictoryEffect,
        int weeklyMatches,
        int weeklyWins,
        long weeklyContribution,
        String challengeMinigameId,
        String challengeDisplayName,
        boolean weeklyChallengesEnabled,
        int weeklyMatchesRequired,
        int weeklyMatchesExperience,
        int weeklyWinsRequired,
        int weeklyWinsExperience,
        long weeklyContributionRequired,
        int weeklyContributionExperience,
        List<String> badges,
        List<String> titles,
        List<String> victoryEffects,
        List<Rating> ratings,
        String notice,
        boolean error,
        long requestId
) implements CustomPacketPayload {
    public static final int MAX_VALUES = 64;
    public static final Type<MinigameProfilePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameProfilePayload> STREAM_CODEC =
            StreamCodec.of(MinigameProfilePayload::encode, MinigameProfilePayload::decode);

    public MinigameProfilePayload {
        level = Math.max(1, Math.min(100, level));
        experienceIntoLevel = Math.max(0L, experienceIntoLevel);
        experienceForNextLevel = Math.max(0L, experienceForNextLevel);
        matchesPlayed = Math.max(0L, matchesPlayed);
        matchesWon = Math.max(0L, Math.min(matchesPlayed, matchesWon));
        selectedTitle = PayloadBounds.string(selectedTitle, 48);
        selectedVictoryEffect = PayloadBounds.string(selectedVictoryEffect, 24);
        weeklyMatches = Math.max(0, Math.min(10_000, weeklyMatches));
        weeklyWins = Math.max(0, Math.min(weeklyMatches, weeklyWins));
        weeklyContribution = Math.max(0L, weeklyContribution);
        challengeMinigameId = PayloadBounds.string(challengeMinigameId, 64);
        challengeDisplayName = PayloadBounds.string(challengeDisplayName, 128);
        weeklyMatchesRequired = Math.max(1, Math.min(10_000, weeklyMatchesRequired));
        weeklyMatchesExperience = Math.max(0, Math.min(100_000, weeklyMatchesExperience));
        weeklyWinsRequired = Math.max(1, Math.min(10_000, weeklyWinsRequired));
        weeklyWinsExperience = Math.max(0, Math.min(100_000, weeklyWinsExperience));
        weeklyContributionRequired = Math.max(1L, Math.min(1_000_000_000L, weeklyContributionRequired));
        weeklyContributionExperience = Math.max(0, Math.min(100_000, weeklyContributionExperience));
        badges = boundedStrings(badges, 8, 48);
        titles = boundedStrings(titles, 16, 48);
        victoryEffects = boundedStrings(victoryEffects, 16, 24);
        ratings = ratings == null ? List.of() : List.copyOf(ratings.subList(0, Math.min(MAX_VALUES, ratings.size())));
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
    }

    private static List<String> boundedStrings(List<String> raw, int count, int length) {
        if (raw == null || raw.isEmpty()) return List.of();
        ArrayList<String> values = new ArrayList<>();
        for (String value : raw) {
            values.add(PayloadBounds.string(value, length));
            if (values.size() >= count) break;
        }
        return List.copyOf(values);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameProfilePayload payload) {
        buffer.writeVarInt(payload.level);
        buffer.writeVarLong(payload.experienceIntoLevel);
        buffer.writeVarLong(payload.experienceForNextLevel);
        buffer.writeVarLong(payload.matchesPlayed);
        buffer.writeVarLong(payload.matchesWon);
        buffer.writeUtf(payload.selectedTitle, 48);
        buffer.writeUtf(payload.selectedVictoryEffect, 24);
        buffer.writeVarInt(payload.weeklyMatches);
        buffer.writeVarInt(payload.weeklyWins);
        buffer.writeVarLong(payload.weeklyContribution);
        buffer.writeUtf(payload.challengeMinigameId, 64);
        buffer.writeUtf(payload.challengeDisplayName, 128);
        buffer.writeBoolean(payload.weeklyChallengesEnabled);
        buffer.writeVarInt(payload.weeklyMatchesRequired);
        buffer.writeVarInt(payload.weeklyMatchesExperience);
        buffer.writeVarInt(payload.weeklyWinsRequired);
        buffer.writeVarInt(payload.weeklyWinsExperience);
        buffer.writeVarLong(payload.weeklyContributionRequired);
        buffer.writeVarInt(payload.weeklyContributionExperience);
        writeStrings(buffer, payload.badges, 48);
        writeStrings(buffer, payload.titles, 48);
        writeStrings(buffer, payload.victoryEffects, 24);
        buffer.writeVarInt(payload.ratings.size());
        for (Rating rating : payload.ratings) rating.encode(buffer);
        buffer.writeUtf(payload.notice, 512);
        buffer.writeBoolean(payload.error);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameProfilePayload decode(RegistryFriendlyByteBuf buffer) {
        int level = buffer.readVarInt();
        long into = buffer.readVarLong();
        long next = buffer.readVarLong();
        long played = buffer.readVarLong();
        long won = buffer.readVarLong();
        String title = buffer.readUtf(48);
        String victory = buffer.readUtf(24);
        int weeklyMatches = buffer.readVarInt();
        int weeklyWins = buffer.readVarInt();
        long weeklyContribution = buffer.readVarLong();
        String challengeMinigameId = buffer.readUtf(64);
        String challengeDisplayName = buffer.readUtf(128);
        boolean weeklyChallengesEnabled = buffer.readBoolean();
        int weeklyMatchesRequired = buffer.readVarInt();
        int weeklyMatchesExperience = buffer.readVarInt();
        int weeklyWinsRequired = buffer.readVarInt();
        int weeklyWinsExperience = buffer.readVarInt();
        long weeklyContributionRequired = buffer.readVarLong();
        int weeklyContributionExperience = buffer.readVarInt();
        List<String> badges = readStrings(buffer, 8, 48);
        List<String> titles = readStrings(buffer, 16, 48);
        List<String> effects = readStrings(buffer, 16, 24);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Invalid minigame rating count.");
        ArrayList<Rating> ratings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) ratings.add(Rating.decode(buffer));
        return new MinigameProfilePayload(level, into, next, played, won, title, victory,
                weeklyMatches, weeklyWins, weeklyContribution, challengeMinigameId, challengeDisplayName,
                weeklyChallengesEnabled, weeklyMatchesRequired, weeklyMatchesExperience,
                weeklyWinsRequired, weeklyWinsExperience, weeklyContributionRequired,
                weeklyContributionExperience, badges, titles, effects, ratings,
                buffer.readUtf(512), buffer.readBoolean(), buffer.readVarLong());
    }

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values, int length) {
        buffer.writeVarInt(values.size());
        for (String value : values) buffer.writeUtf(PayloadBounds.string(value, length), length);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer, int maximum, int length) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid minigame profile list count.");
        ArrayList<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(buffer.readUtf(length));
        return List.copyOf(values);
    }

    public record Rating(String minigameId, String displayName, int rating) {
        public Rating {
            minigameId = PayloadBounds.string(minigameId, 64);
            displayName = PayloadBounds.string(displayName, 96);
            rating = Math.max(100, Math.min(4_000, rating));
        }
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(minigameId, 64);
            buffer.writeUtf(displayName, 96);
            buffer.writeVarInt(rating);
        }
        private static Rating decode(RegistryFriendlyByteBuf buffer) {
            return new Rating(buffer.readUtf(64), buffer.readUtf(96), buffer.readVarInt());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
