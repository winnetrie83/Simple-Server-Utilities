package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** World actions performed by the dedicated SSU Minigame Setup Tool. */
public enum MinigameSetupAction {
    NEW_ARENA_BOUNDS("new_arena_bounds", "New arena bounds", "Left-click two opposite corners, then create a new game from the tool menu.", false, true),
    ARENA_BOUNDS("arena_bounds", "Resize arena", "Left-click two opposite corners. The managed region is resized and a new reset snapshot is captured.", true, true),
    EDIT_BLOCKS("edit_blocks", "Edit arena blocks", "Swap to normal blocks/tools and build inside the selected arena. The arena is disabled until its snapshot is saved.", true, false),
    SAVE_SNAPSHOT("save_snapshot", "Save arena snapshot", "Left-click anywhere inside the arena after building to capture the new reset state.", true, false),
    LOBBY("lobby", "Set lobby", "Left-click the block where queued players should wait before a match.", true, false),
    SPECTATOR_SPAWN("spectator_spawn", "Set spectator spawn", "Left-click the block where eliminated players should be teleported.", true, false),
    SPECTATOR_BOUNDS("spectator_bounds", "Set spectator bounds", "Left-click two corners of the area spectators are allowed to move inside.", true, true),
    TEAM_SPAWN("team_spawn", "Set team/player spawn", "Choose a team and slot, then left-click the spawn block.", true, false),
    SPLEEF_FLOOR("spleef_floor", "Set Spleef playfloor", "Left-click two corners of the floor volume. Only blocks inside this area may be broken.", true, true),
    CTF_FLAG("ctf_flag", "Set team flag", "Choose red or blue, then left-click the physical flag block position.", true, false),
    BOOST_SPAWN("boost_spawn", "Set boost spawn", "Choose a boost slot, then left-click the block above which a boost may appear in manual placement mode.", true, false),
    DOMINATION_NODE("domination_node", "Set Domination node", "Choose a node, then left-click its physical capture-banner position.", true, false),
    DOMINATION_NODE_SPAWN("domination_node_spawn", "Set Domination node spawn", "Choose a node, then left-click the linked respawn location used while your team controls it.", true, false);

    private final String id;
    private final String label;
    private final String description;
    private final boolean needsTarget;
    private final boolean twoPoint;

    MinigameSetupAction(String id, String label, String description, boolean needsTarget, boolean twoPoint) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.needsTarget = needsTarget;
        this.twoPoint = twoPoint;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String description() { return description; }
    public boolean needsTarget() { return needsTarget; }
    public boolean twoPoint() { return twoPoint; }

    public boolean availableFor(MinigameGameType type) {
        return switch (this) {
            case NEW_ARENA_BOUNDS -> true;
            case SPLEEF_FLOOR -> type == MinigameGameType.SPLEEF;
            case CTF_FLAG -> type == MinigameGameType.CAPTURE_THE_FLAG;
            case BOOST_SPAWN -> type == MinigameGameType.CAPTURE_THE_FLAG || type == MinigameGameType.DOMINATION;
            case DOMINATION_NODE, DOMINATION_NODE_SPAWN -> type == MinigameGameType.DOMINATION;
            default -> type != null && type.implemented() && type != MinigameGameType.GENERIC;
        };
    }

    public static MinigameSetupAction parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (MinigameSetupAction action : values()) if (action.id.equals(value)) return action;
        return NEW_ARENA_BOUNDS;
    }

    public static List<MinigameSetupAction> available(MinigameGameType type, boolean hasTarget) {
        ArrayList<MinigameSetupAction> result = new ArrayList<>();
        for (MinigameSetupAction action : values()) {
            if (action.needsTarget && !hasTarget) continue;
            if (action.availableFor(type)) result.add(action);
        }
        return List.copyOf(result);
    }
}
