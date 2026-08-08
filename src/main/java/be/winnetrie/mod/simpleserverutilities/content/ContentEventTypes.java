package be.winnetrie.mod.simpleserverutilities.content;

/** Stable built-in event names; feature modules may register additional names. */
public final class ContentEventTypes {
    public static final String PLAYER_LOGIN = "player_login";
    public static final String PLAYER_LOGOUT = "player_logout";
    public static final String NPC_INTERACTED = "npc_interacted";
    public static final String DIALOGUE_OPENED = "dialogue_opened";
    public static final String DIALOGUE_CHOICE = "dialogue_choice";
    public static final String NPC_SERVICE_USED = "npc_service_used";
    public static final String BLOCK_BROKEN = "block_broken";
    public static final String BLOCK_PLACED = "block_placed";
    public static final String ENTITY_KILLED = "entity_killed";
    public static final String PLAYER_DEATH = "player_death";
    public static final String DAMAGE_DEALT = "damage_dealt";
    public static final String DAMAGE_TAKEN = "damage_taken";
    public static final String PLAY_TIME = "play_time";
    public static final String ITEM_CRAFTED = "item_crafted";
    public static final String ITEM_USED = "item_used";
    public static final String ITEM_CONSUMED = "item_consumed";
    public static final String DISTANCE_TRAVELLED = "distance_travelled";
    public static final String DIMENSION_VISITED = "dimension_visited";
    public static final String BIOME_VISITED = "biome_visited";
    public static final String CLAIM_GROUP_CREATED = "claim_group_created";
    public static final String CLAIM_CHUNK_ADDED = "claim_chunk_added";
    public static final String MONEY_EARNED = "money_earned";
    public static final String MONEY_SPENT = "money_spent";
    public static final String AUCTION_SALE = "auction_sale";
    public static final String AUCTION_REVENUE = "auction_revenue";
    public static final String AUCTION_PURCHASE = "auction_purchase";
    public static final String ACHIEVEMENT_COMPLETED = "achievement_completed";
    public static final String QUEST_STARTED = "quest_started";
    public static final String QUEST_COMPLETED = "quest_completed";
    public static final String MINIGAME_QUEUE_JOINED = "minigame_queue_joined";
    public static final String MINIGAME_QUEUE_LEFT = "minigame_queue_left";
    public static final String MINIGAME_STARTED = "minigame_started";
    public static final String MINIGAME_WON = "minigame_won";
    public static final String MINIGAME_COMPLETED = "minigame_completed";
    public static final String MINIGAME_KILL = "minigame_kill";
    public static final String MINIGAME_DEATH = "minigame_death";
    public static final String MINIGAME_ASSIST = "minigame_assist";
    public static final String MINIGAME_HEALING = "minigame_healing";
    public static final String MINIGAME_DAMAGE = "minigame_damage";
    public static final String MINIGAME_CAPTURE = "minigame_capture";
    public static final String MINIGAME_DEFENSE = "minigame_defense";
    public static final String MINIGAME_OBJECTIVE_TIME = "minigame_objective_time";
    public static final String MINIGAME_BOOST_COLLECTED = "minigame_boost_collected";
    public static final String MINIGAME_LEVEL_UP = "minigame_level_up";
    public static final String DUNGEON_QUEUE_JOINED = "dungeon_queue_joined";
    public static final String DUNGEON_QUEUE_LEFT = "dungeon_queue_left";
    public static final String DUNGEON_STARTED = "dungeon_started";
    public static final String DUNGEON_STAGE_COMPLETED = "dungeon_stage_completed";
    public static final String DUNGEON_COMPLETED = "dungeon_completed";
    public static final String DUNGEON_FAILED = "dungeon_failed";

    private ContentEventTypes() {
    }
}
