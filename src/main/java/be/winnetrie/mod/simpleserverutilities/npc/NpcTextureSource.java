package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Source used for an optional SSU NPC texture override. */
public enum NpcTextureSource {
    NONE("none", "Default texture"),
    LOCAL("local", "Local server PNG"),
    URL("url", "HTTPS URL");

    private final String id;
    private final String label;

    NpcTextureSource(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }
    public boolean custom() { return this != NONE; }

    public static NpcTextureSource parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (NpcTextureSource source : values()) if (source.id.equals(value)) return source;
        return NONE;
    }

    public NpcTextureSource next() {
        NpcTextureSource[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
