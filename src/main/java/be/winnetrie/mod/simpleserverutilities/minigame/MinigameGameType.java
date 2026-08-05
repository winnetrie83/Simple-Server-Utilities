package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;

/** Published minigame modes. Only modes marked implemented may run matches. */
public enum MinigameGameType {
    GENERIC("generic", "Generic", true),
    SPLEEF("spleef", "Spleef", true),
    KING_OF_THE_HILL("king_of_the_hill", "King of the Hill", false),
    CAPTURE_THE_FLAG("capture_the_flag", "Capture the Flag", true),
    DOMINATION("domination", "Domination", true),
    TEAM_DEATHMATCH("team_deathmatch", "Team Deathmatch", false),
    PARKOUR_RACE("parkour_race", "Parkour Race", false),
    PROP_HUNT("prop_hunt", "Prop Hunt", false);

    private final String id;
    private final String label;
    private final boolean implemented;

    MinigameGameType(String id, String label, boolean implemented) {
        this.id = id;
        this.label = label;
        this.implemented = implemented;
    }

    public String id() { return id; }
    public String label() { return label; }
    public boolean implemented() { return implemented; }

    public static MinigameGameType parse(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (MinigameGameType value : values()) if (value.id.equals(normalized)) return value;
        return GENERIC;
    }

    public MinigameGameType nextImplemented() {
        MinigameGameType[] values = values();
        int start = ordinal();
        for (int offset = 1; offset <= values.length; offset++) {
            MinigameGameType candidate = values[(start + offset) % values.length];
            if (candidate.implemented) return candidate;
        }
        return GENERIC;
    }
}
