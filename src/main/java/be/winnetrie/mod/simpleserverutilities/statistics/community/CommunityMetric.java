package be.winnetrie.mod.simpleserverutilities.statistics.community;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;

/**
 * Stable built-in community metrics collected independently from administrator-defined statistics.
 * Raw values are integral; scale=100 means the website should divide by 100 for display.
 */
public enum CommunityMetric {
    SESSIONS("sessions", ContentEventTypes.PLAYER_LOGIN, "Sessions", "activity", "sessions", 1, true, false),
    PLAY_TIME("play_time_seconds", ContentEventTypes.PLAY_TIME, "Play time", "activity", "seconds", 1, true, false),
    BLOCKS_BROKEN("blocks_broken", ContentEventTypes.BLOCK_BROKEN, "Blocks broken", "world", "blocks", 1, false, true),
    BLOCKS_PLACED("blocks_placed", ContentEventTypes.BLOCK_PLACED, "Blocks placed", "world", "blocks", 1, false, true),
    ENTITIES_KILLED("entities_killed", ContentEventTypes.ENTITY_KILLED, "Entities killed", "combat", "kills", 1, false, true),
    PLAYER_DEATHS("player_deaths", ContentEventTypes.PLAYER_DEATH, "Deaths", "combat", "deaths", 1, true, false),
    DAMAGE_DEALT("damage_dealt", ContentEventTypes.DAMAGE_DEALT, "Damage dealt", "combat", "damage", 100, false, true),
    DAMAGE_TAKEN("damage_taken", ContentEventTypes.DAMAGE_TAKEN, "Damage taken", "combat", "damage", 100, false, true),
    ITEMS_CRAFTED("items_crafted", ContentEventTypes.ITEM_CRAFTED, "Items crafted", "crafting", "items", 1, false, true),
    ITEMS_USED("items_used", ContentEventTypes.ITEM_USED, "Items used", "crafting", "uses", 1, false, true),
    ITEMS_CONSUMED("items_consumed", ContentEventTypes.ITEM_CONSUMED, "Items consumed", "crafting", "items", 1, false, true),
    DISTANCE_TRAVELLED("distance_travelled", ContentEventTypes.DISTANCE_TRAVELLED, "Distance travelled", "exploration", "blocks", 1, false, true),
    DIMENSION_VISITS("dimension_visits", ContentEventTypes.DIMENSION_VISITED, "Dimension visits", "exploration", "visits", 1, false, true),
    BIOME_VISITS("biome_visits", ContentEventTypes.BIOME_VISITED, "Biome visits", "exploration", "visits", 1, false, true),
    CLAIM_GROUPS_CREATED("claim_groups_created", ContentEventTypes.CLAIM_GROUP_CREATED, "Claim groups created", "claims", "groups", 1, false, false),
    CLAIM_CHUNKS_ADDED("claim_chunks_added", ContentEventTypes.CLAIM_CHUNK_ADDED, "Claim chunks added", "claims", "chunks", 1, false, true),
    AUCTION_SALES("auction_sales", ContentEventTypes.AUCTION_SALE, "Auction sales", "economy", "sales", 1, true, true),
    AUCTION_REVENUE("auction_revenue", ContentEventTypes.AUCTION_REVENUE, "Auction revenue", "economy", "money", 100, true, true),
    AUCTION_PURCHASES("auction_purchases", ContentEventTypes.AUCTION_PURCHASE, "Auction purchases", "economy", "purchases", 1, true, true),
    QUESTS_STARTED("quests_started", ContentEventTypes.QUEST_STARTED, "Quests started", "quests", "quests", 1, true, true),
    QUESTS_COMPLETED("quests_completed", ContentEventTypes.QUEST_COMPLETED, "Quests completed", "quests", "quests", 1, true, true),
    NPC_INTERACTIONS("npc_interactions", ContentEventTypes.NPC_INTERACTED, "NPC interactions", "npcs", "interactions", 1, false, true),
    DIALOGUES_OPENED("dialogues_opened", ContentEventTypes.DIALOGUE_OPENED, "Dialogues opened", "npcs", "dialogues", 1, false, true),
    DIALOGUE_CHOICES("dialogue_choices", ContentEventTypes.DIALOGUE_CHOICE, "Dialogue choices", "npcs", "choices", 1, false, true),
    NPC_SERVICES_USED("npc_services_used", ContentEventTypes.NPC_SERVICE_USED, "NPC services used", "npcs", "uses", 1, false, true),
    ACHIEVEMENTS_COMPLETED("achievements_completed", ContentEventTypes.ACHIEVEMENT_COMPLETED, "Achievements completed", "progression", "achievements", 1, true, true),
    MINIGAME_QUEUE_JOINS("minigame_queue_joins", ContentEventTypes.MINIGAME_QUEUE_JOINED, "Minigame queue joins", "minigames", "joins", 1, false, true),
    MINIGAME_MATCHES_STARTED("minigame_participations", ContentEventTypes.MINIGAME_STARTED, "Minigame participations", "minigames", "matches", 1, true, true),
    MINIGAME_KILLS("minigame_kills", ContentEventTypes.MINIGAME_KILL, "Minigame kills", "minigames", "kills", 1, true, true),
    MINIGAME_DEATHS("minigame_deaths", ContentEventTypes.MINIGAME_DEATH, "Minigame deaths", "minigames", "deaths", 1, true, true),
    MINIGAME_ASSISTS("minigame_assists", ContentEventTypes.MINIGAME_ASSIST, "Minigame assists", "minigames", "assists", 1, true, true),
    MINIGAME_DAMAGE("minigame_damage", ContentEventTypes.MINIGAME_DAMAGE, "Minigame damage", "minigames", "damage", 100, true, true),
    MINIGAME_HEALING("minigame_healing", ContentEventTypes.MINIGAME_HEALING, "Minigame healing", "minigames", "healing", 100, true, true),
    MINIGAME_CAPTURES("minigame_captures", ContentEventTypes.MINIGAME_CAPTURE, "Minigame captures", "minigames", "captures", 1, true, true),
    MINIGAME_DEFENSES("minigame_defenses", ContentEventTypes.MINIGAME_DEFENSE, "Minigame defenses", "minigames", "defenses", 1, true, true),
    MINIGAME_OBJECTIVE_TIME("minigame_objective_time", ContentEventTypes.MINIGAME_OBJECTIVE_TIME, "Objective time", "minigames", "seconds", 1, true, true),
    MINIGAME_WINS("minigame_wins", ContentEventTypes.MINIGAME_WON, "Minigame wins", "minigames", "wins", 1, true, true),
    MINIGAME_MATCHES_COMPLETED("minigame_matches_completed", ContentEventTypes.MINIGAME_COMPLETED, "Minigame matches completed", "minigames", "matches", 1, true, true),
    MINIGAME_BOOSTS("minigame_boosts_collected", ContentEventTypes.MINIGAME_BOOST_COLLECTED, "Minigame boosts collected", "minigames", "boosts", 1, true, true),
    MINIGAME_LEVELS("minigame_levels_gained", ContentEventTypes.MINIGAME_LEVEL_UP, "Minigame levels gained", "minigames", "levels", 1, true, true),
    DUNGEON_QUEUE_JOINS("dungeon_queue_joins", ContentEventTypes.DUNGEON_QUEUE_JOINED, "Dungeon queue joins", "dungeons", "joins", 1, false, true),
    DUNGEON_RUNS_STARTED("dungeon_runs_started", ContentEventTypes.DUNGEON_STARTED, "Dungeon runs started", "dungeons", "runs", 1, true, true),
    DUNGEON_STAGES_COMPLETED("dungeon_stages_completed", ContentEventTypes.DUNGEON_STAGE_COMPLETED, "Dungeon stages completed", "dungeons", "stages", 1, true, true),
    DUNGEONS_COMPLETED("dungeons_completed", ContentEventTypes.DUNGEON_COMPLETED, "Dungeons completed", "dungeons", "dungeons", 1, true, true),
    DUNGEON_RUNS_FAILED("dungeon_runs_failed", ContentEventTypes.DUNGEON_FAILED, "Dungeon runs failed", "dungeons", "runs", 1, true, true);

    private static final Map<String, CommunityMetric> BY_EVENT = new LinkedHashMap<>();
    static { for (CommunityMetric metric : values()) BY_EVENT.put(metric.eventType, metric); }

    private final String id;
    private final String eventType;
    private final String displayName;
    private final String category;
    private final String unit;
    private final int scale;
    private final boolean leaderboardSafe;
    private final boolean subjectBreakdown;

    CommunityMetric(String id, String eventType, String displayName, String category, String unit,
                    int scale, boolean leaderboardSafe, boolean subjectBreakdown) {
        this.id = id;
        this.eventType = eventType;
        this.displayName = displayName;
        this.category = category;
        this.unit = unit;
        this.scale = scale;
        this.leaderboardSafe = leaderboardSafe;
        this.subjectBreakdown = subjectBreakdown;
    }

    public String id() { return id; }
    public String eventType() { return eventType; }
    public String displayName() { return displayName; }
    public String category() { return category; }
    public String unit() { return unit; }
    public int scale() { return scale; }
    public boolean leaderboardSafe() { return leaderboardSafe; }
    public boolean subjectBreakdown() { return subjectBreakdown; }

    public Descriptor descriptor() {
        return new Descriptor(id, displayName, category, unit, scale, leaderboardSafe, false);
    }

    public static CommunityMetric fromEvent(String rawType) {
        if (rawType == null) return null;
        return BY_EVENT.get(rawType.trim().toLowerCase(Locale.ROOT));
    }

    public static Map<String, Descriptor> catalog() {
        LinkedHashMap<String, Descriptor> result = new LinkedHashMap<>();
        for (CommunityMetric metric : values()) result.put(metric.id, metric.descriptor());
        result.put("active_days", new Descriptor("active_days", "Active days", "activity", "days", 1, true, true));
        result.put("active_players", new Descriptor("active_players", "Active players", "community", "players", 1, true, true));
        result.put("player_active_days", new Descriptor("player_active_days", "Player active days", "community", "player-days", 1, true, true));
        result.put("unique_biomes", new Descriptor("unique_biomes", "Unique biomes", "exploration", "biomes", 1, true, true));
        result.put("unique_dimensions", new Descriptor("unique_dimensions", "Unique dimensions", "exploration", "dimensions", 1, true, true));
        return Map.copyOf(result);
    }

    public record Descriptor(String id, String displayName, String category, String unit,
                             int scale, boolean leaderboardSafe, boolean derived) { }
}
