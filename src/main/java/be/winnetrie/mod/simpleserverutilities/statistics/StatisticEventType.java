package be.winnetrie.mod.simpleserverutilities.statistics;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;

/** Supported Content Core event sources for custom player statistics. */
public enum StatisticEventType {
    PLAYER_LOGIN(ContentEventTypes.PLAYER_LOGIN, false, false, "logins"),
    PLAYER_LOGOUT(ContentEventTypes.PLAYER_LOGOUT, false, false, "logouts"),
    BLOCK_BROKEN(ContentEventTypes.BLOCK_BROKEN, true, false, "blocks"),
    BLOCK_PLACED(ContentEventTypes.BLOCK_PLACED, true, false, "blocks"),
    ENTITY_KILLED(ContentEventTypes.ENTITY_KILLED, true, false, "kills"),
    PLAYER_DEATH(ContentEventTypes.PLAYER_DEATH, false, false, "deaths"),
    DAMAGE_DEALT(ContentEventTypes.DAMAGE_DEALT, true, true, "damage"),
    DAMAGE_TAKEN(ContentEventTypes.DAMAGE_TAKEN, true, true, "damage"),
    PLAY_TIME(ContentEventTypes.PLAY_TIME, false, false, "seconds"),
    ITEM_CRAFTED(ContentEventTypes.ITEM_CRAFTED, true, false, "items"),
    ITEM_USED(ContentEventTypes.ITEM_USED, true, false, "uses"),
    ITEM_CONSUMED(ContentEventTypes.ITEM_CONSUMED, true, false, "items"),
    DISTANCE_TRAVELLED(ContentEventTypes.DISTANCE_TRAVELLED, true, false, "blocks"),
    DIMENSION_VISITED(ContentEventTypes.DIMENSION_VISITED, true, false, "visits"),
    BIOME_VISITED(ContentEventTypes.BIOME_VISITED, true, false, "visits"),
    CLAIM_GROUP_CREATED(ContentEventTypes.CLAIM_GROUP_CREATED, false, false, "groups"),
    CLAIM_CHUNK_ADDED(ContentEventTypes.CLAIM_CHUNK_ADDED, true, false, "chunks"),
    AUCTION_SALE(ContentEventTypes.AUCTION_SALE, true, false, "sales"),
    AUCTION_REVENUE(ContentEventTypes.AUCTION_REVENUE, true, true, "money"),
    AUCTION_PURCHASE(ContentEventTypes.AUCTION_PURCHASE, true, false, "purchases"),
    QUEST_COMPLETED(ContentEventTypes.QUEST_COMPLETED, true, false, "quests"),
    DUNGEON_COMPLETED(ContentEventTypes.DUNGEON_COMPLETED, true, false, "dungeons"),
    NPC_INTERACTION(ContentEventTypes.NPC_INTERACTED, true, false, "interactions"),
    DIALOGUE_OPENED(ContentEventTypes.DIALOGUE_OPENED, true, false, "dialogues"),
    DIALOGUE_CHOICE(ContentEventTypes.DIALOGUE_CHOICE, true, false, "choices"),
    NPC_SERVICE_USED(ContentEventTypes.NPC_SERVICE_USED, true, false, "uses"),
    ACHIEVEMENT_COMPLETED(ContentEventTypes.ACHIEVEMENT_COMPLETED, true, false, "achievements"),
    MINIGAME_STARTED(ContentEventTypes.MINIGAME_STARTED, true, false, "matches"),
    MINIGAME_KILL(ContentEventTypes.MINIGAME_KILL, true, false, "kills"),
    MINIGAME_DEATH(ContentEventTypes.MINIGAME_DEATH, true, false, "deaths"),
    MINIGAME_ASSIST(ContentEventTypes.MINIGAME_ASSIST, true, false, "assists"),
    MINIGAME_DAMAGE(ContentEventTypes.MINIGAME_DAMAGE, true, true, "damage"),
    MINIGAME_HEALING(ContentEventTypes.MINIGAME_HEALING, true, true, "healing"),
    MINIGAME_CAPTURE(ContentEventTypes.MINIGAME_CAPTURE, true, false, "captures"),
    MINIGAME_DEFENSE(ContentEventTypes.MINIGAME_DEFENSE, true, false, "defenses"),
    MINIGAME_OBJECTIVE_TIME(ContentEventTypes.MINIGAME_OBJECTIVE_TIME, true, false, "seconds"),
    MINIGAME_WIN(ContentEventTypes.MINIGAME_WON, true, false, "wins"),
    MINIGAME_COMPLETED(ContentEventTypes.MINIGAME_COMPLETED, true, false, "matches"),
    MINIGAME_BOOST_COLLECTED(ContentEventTypes.MINIGAME_BOOST_COLLECTED, true, false, "boosts"),
    MINIGAME_LEVEL_UP(ContentEventTypes.MINIGAME_LEVEL_UP, true, false, "levels"),
    DUNGEON_STARTED(ContentEventTypes.DUNGEON_STARTED, true, false, "runs"),
    DUNGEON_STAGE_COMPLETED(ContentEventTypes.DUNGEON_STAGE_COMPLETED, true, false, "stages"),
    DUNGEON_FAILED(ContentEventTypes.DUNGEON_FAILED, true, false, "runs");

    private static final Map<String, StatisticEventType> BY_CONTENT_TYPE = new HashMap<>();
    static { for (StatisticEventType type : values()) BY_CONTENT_TYPE.put(type.contentEventType, type); }

    private final String contentEventType;
    private final boolean targetSupported;
    private final boolean decimal;
    private final String defaultUnit;

    StatisticEventType(String contentEventType, boolean targetSupported, boolean decimal, String defaultUnit) {
        this.contentEventType = contentEventType;
        this.targetSupported = targetSupported;
        this.decimal = decimal;
        this.defaultUnit = defaultUnit;
    }

    public String contentEventType() { return contentEventType; }
    public boolean targetSupported() { return targetSupported; }
    public boolean decimal() { return decimal; }
    public String defaultUnit() { return defaultUnit; }

    public static StatisticEventType fromContentEvent(String raw) {
        return raw == null ? null : BY_CONTENT_TYPE.get(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static StatisticEventType parse(String raw) {
        if (raw == null || raw.isBlank()) return BLOCK_BROKEN;
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
