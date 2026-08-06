package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.Locale;

/** Per-claim access role assigned by the claim owner. */
public enum ClaimAccessRole {
    CO_OWNER("Co-owner"),
    MEMBER("Member");

    private final String displayName;

    ClaimAccessRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ClaimAccessRole parse(String raw) {
        if (raw == null) return MEMBER;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "co_owner".equals(normalized) || "coowner".equals(normalized) ? CO_OWNER : MEMBER;
    }
}
