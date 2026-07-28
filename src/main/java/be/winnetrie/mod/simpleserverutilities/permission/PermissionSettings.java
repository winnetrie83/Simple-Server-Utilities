package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.Locale;

/**
 * Server-wide permission settings. This intentionally only stores policy that
 * does not belong to a rank or to a player.
 */
public final class PermissionSettings {

    public static final int CURRENT_SCHEMA = 1;

    private int schema = CURRENT_SCHEMA;
    private String defaultRank = "default";
    private boolean assignDefaultRankOnFirstJoin = true;

    public PermissionSettings() {
        // Required for Gson.
    }

    public int getSchema() {
        return schema;
    }

    public void normalize() {
        schema = CURRENT_SCHEMA;
        defaultRank = normalizeRankName(defaultRank);
    }

    public String getDefaultRank() {
        return normalizeRankName(defaultRank);
    }

    public void setDefaultRank(String defaultRank) {
        this.defaultRank = normalizeRankName(defaultRank);
    }

    public boolean isAssignDefaultRankOnFirstJoin() {
        return assignDefaultRankOnFirstJoin;
    }

    public void setAssignDefaultRankOnFirstJoin(boolean assignDefaultRankOnFirstJoin) {
        this.assignDefaultRankOnFirstJoin = assignDefaultRankOnFirstJoin;
    }

    private static String normalizeRankName(String rankName) {
        if (rankName == null || rankName.isBlank()) {
            return "default";
        }
        return rankName.trim().toLowerCase(Locale.ROOT);
    }
}
