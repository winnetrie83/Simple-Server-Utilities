package be.winnetrie.mod.simpleserverutilities.hologram;

import java.util.Locale;

/** Persistent server-side hologram definition. */
public final class HologramDefinition {
    public int schemaVersion = 4;
    public String id = "hologram";
    public HologramType type = HologramType.TEXT;
    public String dimension = "minecraft:overworld";
    public double x;
    public double y;
    public double z;
    public String text = "Hologram";
    public int color = 0xFFFFFFFF;
    /** ARGB background; alpha 0 disables the background. */
    public int backgroundColor = 0x00000000;
    public float scale = 1.0F;
    public boolean bold;
    public boolean italic;
    public boolean underlined;
    public boolean strikethrough;
    public boolean shadow;
    public boolean seeThrough = true;
    public boolean enabled = true;
    public double viewDistance = 64.0D;

    public String url = "";
    public String imageSource = "";
    public float imageWidth = 2.0F;
    public float imageHeight = 2.0F;

    public String objective = "";
    public HologramScoreboardMode scoreboardMode = HologramScoreboardMode.TOP;
    public int maxLines = 10;
    public int updateIntervalTicks = 20;

    public HologramDefinition normalize() {
        // Dev3 used 0.025 scale units, making its maximum value (8) roughly the
        // useful baseline size. Dev4 makes that visual size the new scale 1.
        // Persisted definitions are migrated once so existing holograms do not
        // suddenly become eight times larger after updating.
        if (schemaVersion < 2) {
            scale = Math.max(1.0F, scale / 8.0F);
        }
        if (schemaVersion < 4) {
            text = HologramRichText.migrateWholeTextStyles(
                    text, bold, italic, underlined, strikethrough);
        }
        schemaVersion = 4;
        id = sanitizeId(id);
        type = type == null ? HologramType.TEXT : type;
        dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.trim();
        text = HologramRichText.normalize(text);
        // Whole-text style flags are retained in the save format for backwards
        // compatibility, but dev6 migrates them to selectable inline formatting.
        bold = false;
        italic = false;
        underlined = false;
        strikethrough = false;
        color |= 0xFF000000;
        shadow = false; // 26.2 TextGizmo has no native single-pass shadow style.
        scale = Float.isFinite(scale) ? Math.max(1.0F, Math.min(8.0F, scale)) : 1.0F;
        viewDistance = Double.isFinite(viewDistance)
                ? Math.max(4.0D, Math.min(512.0D, viewDistance)) : 64.0D;
        url = url == null ? "" : limit(url.trim(), 2048);
        imageSource = imageSource == null ? "" : limit(imageSource.trim(), 2048);
        imageWidth = Float.isFinite(imageWidth)
                ? Math.max(0.1F, Math.min(32.0F, imageWidth)) : 2.0F;
        imageHeight = Float.isFinite(imageHeight)
                ? Math.max(0.1F, Math.min(32.0F, imageHeight)) : 2.0F;
        objective = objective == null ? "" : limit(objective.trim(), 64);
        scoreboardMode = scoreboardMode == null ? HologramScoreboardMode.TOP : scoreboardMode;
        maxLines = Math.max(1, Math.min(64, maxLines));
        updateIntervalTicks = Math.max(10, Math.min(72_000, updateIntervalTicks));
        return this;
    }

    public static String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "hologram";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
