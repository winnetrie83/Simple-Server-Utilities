package be.winnetrie.mod.simpleserverutilities.auction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.resources.Identifier;

public final class AuctionHouseSettings {
    public static final int SCHEMA_VERSION = 2;

    private int schemaVersion = SCHEMA_VERSION;
    private int saleTaxPermille = 50;
    private int defaultDurationHours = 48;
    private List<String> blacklistedItemIds = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public int getSaleTaxPermille() {
        return Math.max(0, Math.min(1_000, saleTaxPermille));
    }

    public void setSaleTaxPermille(int saleTaxPermille) {
        this.saleTaxPermille = Math.max(0, Math.min(1_000, saleTaxPermille));
    }

    public int getDefaultDurationHours() {
        return normalizeDuration(defaultDurationHours);
    }

    public void setDefaultDurationHours(int defaultDurationHours) {
        this.defaultDurationHours = normalizeDuration(defaultDurationHours);
    }

    public List<String> getBlacklistedItemIds() {
        return List.copyOf(blacklistedItemIds == null ? List.of() : blacklistedItemIds);
    }

    public boolean isBlacklisted(String itemId) {
        if (itemId == null || itemId.isBlank() || blacklistedItemIds == null) return false;
        String normalized = itemId.trim().toLowerCase(Locale.ROOT);
        return blacklistedItemIds.contains(normalized);
    }

    public boolean addBlacklistedItem(String itemId) {
        String normalized = normalizeItemId(itemId);
        if (normalized.isBlank()) return false;
        if (blacklistedItemIds == null) blacklistedItemIds = new ArrayList<>();
        if (blacklistedItemIds.contains(normalized)) return false;
        blacklistedItemIds.add(normalized);
        blacklistedItemIds.sort(String::compareTo);
        return true;
    }

    public boolean removeBlacklistedItem(String itemId) {
        if (itemId == null || blacklistedItemIds == null) return false;
        return blacklistedItemIds.remove(itemId.trim().toLowerCase(Locale.ROOT));
    }

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        saleTaxPermille = getSaleTaxPermille();
        defaultDurationHours = getDefaultDurationHours();
        Set<String> normalized = new LinkedHashSet<>();
        if (blacklistedItemIds != null) {
            for (String raw : blacklistedItemIds) {
                String itemId = normalizeItemId(raw);
                if (!itemId.isBlank()) normalized.add(itemId);
            }
        }
        blacklistedItemIds = new ArrayList<>(normalized);
        blacklistedItemIds.sort(String::compareTo);
    }

    private static String normalizeItemId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            return Identifier.parse(raw.trim().toLowerCase(Locale.ROOT)).toString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    public static int normalizeDuration(int hours) {
        if (hours <= 12) return 12;
        if (hours <= 24) return 24;
        return 48;
    }
}
