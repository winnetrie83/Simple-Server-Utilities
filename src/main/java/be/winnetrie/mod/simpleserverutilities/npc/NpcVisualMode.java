package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/**
 * Visual family used by an SSU NPC.
 *
 * Custom geometry is intentionally parked for now: SSU renders either Minecraft's player
 * model through SSU's native player NPC runtime or the selected living entity's own renderer/model.
 * The legacy CUSTOM_MODEL value is kept only so dev3.31 data remains readable and is
 * migrated back to ENTITY by {@link NpcDefinition#normalize()}.
 */
public enum NpcVisualMode {
    ENTITY("entity", "Entity"),
    PLAYER_SKIN("player_skin", "Player"),
    CUSTOM_MODEL("custom_model", "Legacy custom model");

    private final String id;
    private final String label;

    NpcVisualMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    /** Only the two dependency-free render modes are exposed by the editor. */
    public NpcVisualMode next() {
        return this == ENTITY ? PLAYER_SKIN : ENTITY;
    }

    public static NpcVisualMode parse(String raw) {
        if (raw == null || raw.isBlank()) return ENTITY;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (NpcVisualMode mode : values()) if (mode.id.equals(value)) return mode;
        return ENTITY;
    }
}
