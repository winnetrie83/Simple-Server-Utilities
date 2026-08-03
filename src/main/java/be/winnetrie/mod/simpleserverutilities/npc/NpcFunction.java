package be.winnetrie.mod.simpleserverutilities.npc;

/** One player-facing function exposed directly or through an NPC service menu. */
public final class NpcFunction {
    public static final int MAX_FUNCTIONS = 8;

    public String id = "function";
    public String label = "Service";
    public String service = "";
    public String target = "";
    public boolean enabled = true;

    public NpcFunction normalize() {
        id = id == null || id.isBlank() ? "function" : NpcDefinition.sanitizeId(id);
        label = limit(label == null || label.isBlank() ? "Service" : label.trim(), 64);
        service = service == null || service.isBlank() ? "" : NpcDefinition.sanitizeId(service);
        target = limit(target == null ? "" : target.trim(), 256);
        return this;
    }

    public boolean configured() {
        return enabled && !service.isBlank();
    }

    public NpcFunction copy() {
        NpcFunction copy = new NpcFunction();
        copy.id = id;
        copy.label = label;
        copy.service = service;
        copy.target = target;
        copy.enabled = enabled;
        return copy;
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
