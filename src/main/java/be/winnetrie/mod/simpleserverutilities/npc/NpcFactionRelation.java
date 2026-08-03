package be.winnetrie.mod.simpleserverutilities.npc;

/** One explicit outgoing relation from an NPC faction to another faction. */
public final class NpcFactionRelation {
    public String factionId = "";
    public String attitude = NpcAttitude.NEUTRAL.id();

    public NpcFactionRelation normalize() {
        factionId = factionId == null || factionId.isBlank() ? "" : NpcDefinition.sanitizeId(factionId);
        attitude = NpcAttitude.parse(attitude).id();
        return this;
    }

    public NpcFactionRelation copy() {
        NpcFactionRelation copy = new NpcFactionRelation();
        copy.factionId = factionId;
        copy.attitude = attitude;
        return copy;
    }

    public boolean configured() {
        return factionId != null && !factionId.isBlank();
    }
}
