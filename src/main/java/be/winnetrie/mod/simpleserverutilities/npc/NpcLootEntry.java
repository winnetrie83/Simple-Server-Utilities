package be.winnetrie.mod.simpleserverutilities.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** One independently rolled entry in an NPC's custom loot table. */
public final class NpcLootEntry {
    /** Exact encoded item stack, including custom name, enchantments and other components. */
    public JsonElement stack = JsonNull.INSTANCE;
    /** Legacy schema-4 migration fields; retained so pre-release test data remains readable. */
    public String itemId = "";
    public int count = 1;
    /** Chance in hundredths of a percent: 1 = 0.01%, 10_000 = 100.00%. */
    public int chanceHundredthPercent = 10_000;

    public NpcLootEntry normalize() {
        stack = NpcItemCodec.safeCopy(stack);
        itemId = itemId == null || itemId.isBlank() ? "" : limit(itemId.trim().toLowerCase(), 128);
        count = Math.max(1, Math.min(99, count));
        chanceHundredthPercent = Math.max(1, Math.min(10_000, chanceHundredthPercent));
        return this;
    }

    public NpcLootEntry copy() {
        NpcLootEntry copy = new NpcLootEntry();
        copy.stack = NpcItemCodec.safeCopy(stack);
        copy.itemId = itemId;
        copy.count = count;
        copy.chanceHundredthPercent = chanceHundredthPercent;
        return copy;
    }

    public boolean configured() {
        return NpcItemCodec.configured(stack, itemId);
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
