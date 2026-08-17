package be.winnetrie.mod.simpleserverutilities.npc;

/** One NPC-specific reference to a reusable server-wide ability. */
public final class NpcAbilityAssignment {
    public static final int MAX_ASSIGNMENTS = 24;

    public String abilityId = "";
    /** Optional NPC/boss-specific phase gate. Blank means every phase. */
    public String phaseId = "";

    public NpcAbilityAssignment() { }

    public NpcAbilityAssignment(String abilityId, String phaseId) {
        this.abilityId = abilityId;
        this.phaseId = phaseId;
        normalize();
    }

    public NpcAbilityAssignment normalize() {
        abilityId = sanitize(abilityId, 64);
        phaseId = sanitize(phaseId, 48);
        return this;
    }

    public NpcAbilityAssignment copy() {
        NpcAbilityAssignment copy = new NpcAbilityAssignment();
        copy.abilityId = abilityId;
        copy.phaseId = phaseId;
        return copy.normalize();
    }

    public boolean configured() { return abilityId != null && !abilityId.isBlank(); }

    private static String sanitize(String raw, int max) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
        return value.length() <= max ? value : value.substring(0, max);
    }
}
