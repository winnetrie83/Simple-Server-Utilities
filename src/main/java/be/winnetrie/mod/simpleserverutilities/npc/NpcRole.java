package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Descriptive occupation shown in the NPC editor and overhead identity label. */
public enum NpcRole {
    CITIZEN("citizen", "Citizen"),
    QUEST_GIVER("quest_giver", "Quest giver"),
    MERCHANT("merchant", "Merchant"),
    AUCTIONEER("auctioneer", "Auctioneer"),
    POSTMASTER("postmaster", "Postmaster"),
    HEALER("healer", "Healer"),
    BANKER("banker", "Banker"),
    WARP_MASTER("warp_master", "Warp master"),
    MINIGAME_HOST("minigame_host", "Minigame host"),
    DUNGEON_MASTER("dungeon_master", "Dungeon master"),
    GUARD("guard", "Guard"),
    TRAINER("trainer", "Trainer"),
    BLACKSMITH("blacksmith", "Blacksmith"),
    INNKEEPER("innkeeper", "Innkeeper");

    private final String id;
    private final String label;

    NpcRole(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public NpcRole next() { return offset(1); }
    public NpcRole previous() { return offset(-1); }

    public NpcRole offset(int delta) {
        NpcRole[] values = values();
        return values[Math.floorMod(ordinal() + delta, values.length)];
    }

    public static NpcRole parse(String raw) {
        if (raw == null) return CITIZEN;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcRole value : values()) if (value.id.equals(normalized)) return value;
        return CITIZEN;
    }
}
