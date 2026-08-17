package be.winnetrie.mod.simpleserverutilities.npc;

import net.minecraft.resources.Identifier;

/** Legacy/future custom-geometry metadata helpers. Custom geometry is inactive in dev3.32. */
public final class NpcCustomModelAssets {
    public static final int MAX_RESOURCE_LENGTH = 256;
    public static final int MAX_ANIMATION_NAME_LENGTH = 128;

    private NpcCustomModelAssets() {}

    /** Normalises a namespaced logical resource retained in legacy/future custom-geometry data. */
    public static String normalizeLogicalResource(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return "";
        if (value.endsWith(".geo.json")) value = value.substring(0, value.length() - ".geo.json".length());
        if (value.endsWith(".animation.json")) value = value.substring(0, value.length() - ".animation.json".length());
        return validIdentifier(value) ? value : "";
    }

    /** Texture resources are normal resource IDs relative to assets/<namespace>/textures/. */
    public static String normalizeTextureResource(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return "";
        if (!value.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) return "";
        return validIdentifier(value) ? value : "";
    }

    public static String normalizeAnimationName(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return fallback;
        if (value.length() > MAX_ANIMATION_NAME_LENGTH) value = value.substring(0, MAX_ANIMATION_NAME_LENGTH);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) return fallback;
        }
        return value;
    }

    public static boolean validIdentifier(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_RESOURCE_LENGTH) return false;
        try {
            Identifier.parse(raw);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean complete(NpcDefinition definition) {
        return definition != null
                && !definition.customModelResource.isBlank()
                && !definition.customTextureResource.isBlank()
                && !definition.customAnimationResource.isBlank();
    }
}
