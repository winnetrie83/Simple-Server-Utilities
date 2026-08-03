package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** How a player-facing interaction is routed after the common NPC access checks. */
public enum NpcInteractionMode {
    DIALOGUE("dialogue", "Dialogue / fallback text"),
    DIRECT_SERVICE("direct_service", "Direct service"),
    SERVICE_MENU("service_menu", "Service menu");

    private final String id;
    private final String label;

    NpcInteractionMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public NpcInteractionMode next() {
        NpcInteractionMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static NpcInteractionMode parse(String raw) {
        if (raw == null) return DIALOGUE;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (NpcInteractionMode value : values()) if (value.id.equals(normalized)) return value;
        return DIALOGUE;
    }
}
