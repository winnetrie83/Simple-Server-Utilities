package be.winnetrie.mod.simpleserverutilities.serverops;

import java.util.Locale;

/** Fixed support categories shared by the client UI and authoritative server validation. */
public enum SupportTicketCategory {
    HELP("Help", false),
    BUG("Bug", false),
    PLAYER_REPORT("Player report", true),
    SUGGESTION("Suggestion", false),
    APPEAL("Appeal", false),
    OTHER("Other", false);

    private final String label;
    private final boolean requiresTarget;

    SupportTicketCategory(String label, boolean requiresTarget) {
        this.label = label;
        this.requiresTarget = requiresTarget;
    }

    public String label() {
        return label;
    }

    public boolean requiresTarget() {
        return requiresTarget;
    }

    public static SupportTicketCategory parse(String raw) {
        if (raw == null || raw.isBlank()) return HELP;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (SupportTicketCategory category : values()) {
            if (category.name().equals(normalized) || category.label.toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized)) {
                return category;
            }
        }
        return OTHER;
    }
}
