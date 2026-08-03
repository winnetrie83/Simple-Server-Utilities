package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted sparse base-price overrides. The visible item list comes from the live registry. */
public final class NpcItemPriceCatalog {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PRICES = 65_536;
    public int schemaVersion = SCHEMA_VERSION;
    public Map<String, NpcItemPrice> prices = new LinkedHashMap<>();

    public NpcItemPriceCatalog normalize() {
        if (prices == null) prices = new LinkedHashMap<>();
        LinkedHashMap<String, NpcItemPrice> safe = new LinkedHashMap<>();
        prices.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
            NpcItemPrice price = entry.getValue() == null ? new NpcItemPrice() : entry.getValue().copy();
            price.normalize();
            if (!id.isBlank() && id.length() <= 160 && price.configured() && safe.size() < MAX_PRICES) safe.put(id, price);
        });
        prices = safe;
        schemaVersion = SCHEMA_VERSION;
        return this;
    }

    public NpcItemPrice get(String itemId) {
        NpcItemPrice value = prices.get(itemId == null ? "" : itemId.trim().toLowerCase(java.util.Locale.ROOT));
        return value == null ? new NpcItemPrice() : value.copy();
    }

    public void set(String itemId, long buyPriceMinor, long sellPriceMinor) {
        String id = itemId == null ? "" : itemId.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isBlank()) return;
        NpcItemPrice value = new NpcItemPrice();
        value.buyPriceMinor = buyPriceMinor;
        value.sellPriceMinor = sellPriceMinor;
        value.normalize();
        if (value.configured()) prices.put(id, value); else prices.remove(id);
    }

    public void clear() { prices.clear(); }
}
