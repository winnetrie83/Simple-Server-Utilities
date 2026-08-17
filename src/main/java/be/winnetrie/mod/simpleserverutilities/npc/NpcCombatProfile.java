package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** First reusable SSU combat presets. More advanced attack-pattern graphs can build on this layer. */
public enum NpcCombatProfile {
    PASSIVE("passive", "Passive", 0.0D, 1.0D),
    MELEE("melee", "Melee", 1.0D, 1.0D),
    DEFENDER("defender", "Defender", 0.9D, 1.15D),
    AGGRESSIVE("aggressive", "Aggressive", 1.2D, 0.75D);

    private final String id;
    private final String label;
    private final double chaseSpeed;
    private final double attackCooldownMultiplier;

    NpcCombatProfile(String id, String label, double chaseSpeed, double attackCooldownMultiplier) {
        this.id = id; this.label = label; this.chaseSpeed = chaseSpeed; this.attackCooldownMultiplier = attackCooldownMultiplier;
    }
    public String id() { return id; }
    public String label() { return label; }
    public double chaseSpeed() { return chaseSpeed; }
    public double attackCooldownMultiplier() { return attackCooldownMultiplier; }
    public NpcCombatProfile next() { NpcCombatProfile[] v = values(); return v[(ordinal()+1)%v.length]; }

    public static NpcCombatProfile parse(String raw) {
        if (raw == null) return MELEE;
        String n = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcCombatProfile v : values()) if (v.id.equals(n)) return v;
        return MELEE;
    }
}
