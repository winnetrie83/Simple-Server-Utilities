package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;

/** Shared shop definition referenced by one or more NPCs. */
public final class NpcShopDefinition {
    public static final int SCHEMA_VERSION = 4;
    public static final int MAX_ENTRIES = 128;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "shop";
    public String displayName = "Shop";
    public boolean enabled = true;
    public List<NpcShopEntry> entries = new ArrayList<>();
    /** Optional allowed item IDs or #item-tags for player sales. Empty means all items. */
    public List<String> sellWhitelist = new ArrayList<>();
    /** Denied item IDs or #item-tags. Blacklist always wins. */
    public List<String> sellBlacklist = new ArrayList<>();

    public NpcShopDefinition normalize() {
        int sourceSchema = schemaVersion;
        id = NpcDefinition.sanitizeId(id == null || id.isBlank() ? "shop" : id);
        displayName = limit(displayName == null || displayName.isBlank() ? "Shop" : displayName.trim(), 64);
        if (entries == null) entries = new ArrayList<>();
        ArrayList<NpcShopEntry> safe = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (NpcShopEntry raw : entries) {
            NpcShopEntry entry = raw == null ? new NpcShopEntry() : raw.copy();
            if (sourceSchema < 2) entry.migrateLegacyOfferUnits();
            if (sourceSchema < 4) entry.migrateLegacyAvailability();
            entry.normalize();
            if (!seen.add(entry.id)) continue;
            safe.add(entry);
            if (safe.size() >= MAX_ENTRIES) break;
        }
        entries = safe;
        sellWhitelist = normalizeFilters(sellWhitelist);
        sellBlacklist = normalizeFilters(sellBlacklist);
        schemaVersion = SCHEMA_VERSION;
        return this;
    }

    public NpcShopEntry entry(String rawId) {
        String wanted = NpcDefinition.sanitizeId(rawId == null ? "" : rawId);
        for (NpcShopEntry entry : entries) if (entry.id.equals(wanted)) return entry;
        return null;
    }

    public NpcShopDefinition copy() {
        NpcShopDefinition copy = new NpcShopDefinition();
        copy.schemaVersion = schemaVersion;
        copy.id = id;
        copy.displayName = displayName;
        copy.enabled = enabled;
        copy.entries = entries.stream().map(NpcShopEntry::copy).toList();
        copy.sellWhitelist = new ArrayList<>(sellWhitelist);
        copy.sellBlacklist = new ArrayList<>(sellBlacklist);
        return copy.normalize();
    }

    private static List<String> normalizeFilters(List<String> raw) {
        if (raw == null) return new ArrayList<>();
        ArrayList<String> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String value : raw) {
            String safe = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            if (safe.isBlank() || safe.length() > 160 || !seen.add(safe)) continue;
            result.add(safe);
            if (result.size() >= 256) break;
        }
        return result;
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
