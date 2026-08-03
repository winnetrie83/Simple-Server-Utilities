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
    public static final String QUEST_STARTED = "quest_started";
    public static final String QUEST_COMPLETED = "quest_completed";
    public static final String MINIGAME_QUEUE_JOINED = "minigame_queue_joined";
    public static final String MINIGAME_QUEUE_LEFT = "minigame_queue_left";
    public static final String MINIGAME_STARTED = "minigame_started";
    public static final String MINIGAME_WON = "minigame_won";
    public static final String MINIGAME_COMPLETED = "minigame_completed";
    public static final String DUNGEON_QUEUE_JOINED = "dungeon_queue_joined";
    public static final String DUNGEON_QUEUE_LEFT = "dungeon_queue_left";
    public static final String DUNGEON_STARTED = "dungeon_started";
    public static final String DUNGEON_STAGE_COMPLETED = "dungeon_stage_completed";
    public static final String DUNGEON_COMPLETED = "dungeon_completed";
    public static final String DUNGEON_FAILED = "dungeon_failed";

    private ContentEventTypes() {
    }
}
