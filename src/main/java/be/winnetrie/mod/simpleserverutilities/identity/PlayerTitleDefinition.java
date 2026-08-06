package be.winnetrie.mod.simpleserverutilities.identity;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.settings.MinecraftColorPalette;

/** Persistent server-defined title and its acquisition rule. */
public final class PlayerTitleDefinition {
    public static final int CURRENT_SCHEMA = 1;

    public int schema = CURRENT_SCHEMA;
    public String id = "title";
    public String displayName = "Title";
    public int color = 0xFFF9FFFE;
    public TitleUnlockType unlockType = TitleUnlockType.FREE;
    public long requirement;
    public String requirementValue = "";
    public boolean enabled = true;

    public PlayerTitleDefinition() {
    }

    public PlayerTitleDefinition(String id, String displayName, int color, TitleUnlockType unlockType,
                                 long requirement, String requirementValue) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.unlockType = unlockType;
        this.requirement = requirement;
        this.requirementValue = requirementValue;
        normalize();
    }

    public void normalize() {
        schema = CURRENT_SCHEMA;
        id = normalizeId(id);
        displayName = bound(displayName, 48, "Title");
        color = MinecraftColorPalette.nearest(color);
        if (unlockType == null) unlockType = TitleUnlockType.FREE;
        requirement = Math.max(0L, Math.min(1_000_000_000L, requirement));
        requirementValue = bound(requirementValue, 128, "");
    }

    public String acquisitionDescription() {
        return switch (unlockType) {
            case FREE -> "Available to every player";
            case MINIGAME_LEVEL -> "Reach minigame level " + requirement;
            case MINIGAME_WINS -> "Win " + requirement + " minigame match" + (requirement == 1 ? "" : "es");
            case RANK -> "Have rank " + (requirementValue.isBlank() ? "(not configured)" : requirementValue);
            case PERMISSION -> "Have permission " + (requirementValue.isBlank() ? "(not configured)" : requirementValue);
            case MANUAL -> "Granted manually by an administrator";
        };
    }

    public PlayerTitleDefinition copy() {
        return new PlayerTitleDefinition(id, displayName, color, unlockType, requirement, requirementValue);
    }

    public static String normalizeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
        value = value.replaceAll("[^a-z0-9_./]", "");
        if (value.length() > 64) value = value.substring(0, 64);
        return value.isBlank() ? "title" : value;
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
