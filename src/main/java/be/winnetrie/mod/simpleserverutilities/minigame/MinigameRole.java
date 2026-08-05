package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;

/** Fixed tactical roles shared by Capture the Flag and Domination. */
public enum MinigameRole {
    DPS("dps", "DPS"),
    TANK("tank", "Tank"),
    HEALER("healer", "Healer");

    private final String id;
    private final String label;

    MinigameRole(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public static MinigameRole parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (MinigameRole role : values()) if (role.id.equals(value)) return role;
        return DPS;
    }
}
