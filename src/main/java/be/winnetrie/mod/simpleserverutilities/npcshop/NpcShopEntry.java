package be.winnetrie.mod.simpleserverutilities.npcshop;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcItemCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;

/** One exact item offer in a persistent NPC shop definition. */
public final class NpcShopEntry {
    public String id = "item";
    public JsonElement itemStack = JsonNull.INSTANCE;
    /** Legacy/manual fallback retained so hand-authored JSON remains practical. */
    public String itemId = "";
    /** Number of items bought by a normal left-click on the shop slot. */
    public int itemCount = 1;
    /** Legacy schema-2 purchase price, migrated once into the global item catalog. */
    public long buyPriceMinor;
    /** Legacy schema-2 sale price, migrated once into the global item catalog. */
    public long sellPriceMinor;
    /** Remaining item units. -1 means infinite stock. */
    public int stock = -1;
    /** Maximum item units. -1 means stock is not capped and never restocks. */
    public int maxStock = -1;
    /** Item units added per restock interval. */
    public int restockAmount;
    public int restockIntervalMinutes;
    public long nextRestockEpochMilli;
    /** Bit mask Monday..Sunday. Defaults to every day. */
    public int availableDaysMask = GameCalendar.ALL_DAYS_MASK;
    /** Legacy schema-3 shared time window, retained only for migration. */
    public int availabilityStartMinute;
    /** Legacy schema-3 shared time window, retained only for migration. */
    public int availabilityEndMinute = GameCalendar.MINUTES_PER_DAY;
    /** Per-weekday inclusive start minute. */
    public int[] availabilityStartMinutes = defaultStarts();
    /** Per-weekday exclusive end minute; end <= start supports an overnight window. */
    public int[] availabilityEndMinutes = defaultEnds();
    /** Per-weekday all-day flag. The weekday still has to be enabled in availableDaysMask. */
    public boolean[] availabilityAllDay = defaultAllDay();

    public NpcShopEntry normalize() {
        id = NpcDefinition.sanitizeId(id == null || id.isBlank() ? "item" : id);
        itemId = itemId == null ? "" : itemId.trim();
        itemCount = clamp(itemCount, 1, 64_000);
        buyPriceMinor = Math.max(0L, buyPriceMinor);
        sellPriceMinor = Math.max(0L, sellPriceMinor);
        if (maxStock < 0 || stock < 0) {
            stock = -1;
            maxStock = -1;
            restockAmount = 0;
            restockIntervalMinutes = 0;
            nextRestockEpochMilli = 0L;
        } else {
            maxStock = clamp(maxStock, 0, 1_000_000);
            stock = clamp(stock, 0, maxStock);
            restockAmount = clamp(restockAmount, 0, Math.max(0, maxStock));
            restockIntervalMinutes = clamp(restockIntervalMinutes, 0, 525_600);
            if (restockAmount <= 0 || restockIntervalMinutes <= 0) nextRestockEpochMilli = 0L;
            else nextRestockEpochMilli = Math.max(0L, nextRestockEpochMilli);
        }
        availableDaysMask &= GameCalendar.ALL_DAYS_MASK;
        availabilityStartMinute = clampMinute(availabilityStartMinute);
        availabilityEndMinute = clampMinute(availabilityEndMinute);
        availabilityStartMinutes = normalizedMinutes(availabilityStartMinutes, 0);
        availabilityEndMinutes = normalizedMinutes(availabilityEndMinutes, GameCalendar.MINUTES_PER_DAY);
        availabilityAllDay = normalizedAllDay(availabilityAllDay);
        if (itemStack == null) itemStack = JsonNull.INSTANCE;
        return this;
    }

    /**
     * Converts schema-1 offer units to schema-2 item units. The old total offer
     * price is divided over the configured stack while finite stock and restock
     * amounts are expanded to item counts.
     */
    void migrateLegacyOfferUnits() {
        int count = Math.max(1, itemCount);
        if (buyPriceMinor > 0L) buyPriceMinor = Math.max(1L, ceilDivide(buyPriceMinor, count));
        if (sellPriceMinor > 0L) sellPriceMinor = Math.max(1L, sellPriceMinor / count);
        if (stock >= 0 && maxStock >= 0) {
            stock = safeItemCount(stock, count);
            maxStock = safeItemCount(maxStock, count);
            restockAmount = safeItemCount(restockAmount, count);
        }
    }

    public ItemStack item(HolderLookup.Provider registries) {
        ItemStack decoded = NpcItemCodec.decode(registries, itemStack, itemId, itemCount);
        if (!decoded.isEmpty()) decoded.setCount(Math.max(1, Math.min(decoded.getMaxStackSize(), itemCount)));
        return decoded;
    }

    public void setItem(HolderLookup.Provider registries, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            itemStack = JsonNull.INSTANCE;
            itemId = "";
            itemCount = 1;
            return;
        }
        ItemStack copy = stack.copy();
        itemCount = Math.max(1, copy.getCount());
        itemStack = NpcItemCodec.encode(registries, copy);
        itemId = "";
    }

    public boolean configured(HolderLookup.Provider registries) {
        return !item(registries).isEmpty();
    }

    public boolean available(GameCalendar.Moment moment) {
        if (moment == null) return false;
        int day = moment.weekday().ordinal();
        int minute = moment.minuteOfDay();
        if (enabled(day)) {
            if (availabilityAllDay[day]) return true;
            int start = availabilityStartMinutes[day];
            int end = availabilityEndMinutes[day];
            if (start == end) return false;
            if (end > start) return minute >= start && minute < end;
            if (minute >= start) return true;
        }
        int previous = Math.floorMod(day - 1, 7);
        if (!enabled(previous) || availabilityAllDay[previous]) return false;
        int previousStart = availabilityStartMinutes[previous];
        int previousEnd = availabilityEndMinutes[previous];
        return previousEnd < previousStart && minute < previousEnd;
    }

    /** Migrates schema-3's one shared window into seven independent weekday windows. */
    void migrateLegacyAvailability() {
        int start = clampMinute(availabilityStartMinute);
        int end = clampMinute(availabilityEndMinute);
        // Schema 3 treated an empty day mask as "every day" and start == end as all day.
        // Preserve that exact meaning during the one-time migration; schema 4 may then use
        // an empty mask deliberately to disable an offer on every weekday.
        availableDaysMask = GameCalendar.normalizeDaysMask(availableDaysMask);
        availabilityStartMinutes = defaultStarts();
        availabilityEndMinutes = defaultEnds();
        availabilityAllDay = defaultAllDay();
        boolean allDay = start == end || (start == 0 && end == GameCalendar.MINUTES_PER_DAY);
        for (int day = 0; day < 7; day++) {
            availabilityStartMinutes[day] = start;
            availabilityEndMinutes[day] = end;
            availabilityAllDay[day] = allDay;
        }
    }

    public int startMinute(int day) { return availabilityStartMinutes[Math.floorMod(day, 7)]; }
    public int endMinute(int day) { return availabilityEndMinutes[Math.floorMod(day, 7)]; }
    public boolean allDay(int day) { return availabilityAllDay[Math.floorMod(day, 7)]; }
    public void setDayWindow(int day, boolean enabled, boolean allDay, int start, int end) {
        int index = Math.floorMod(day, 7);
        if (enabled) availableDaysMask |= 1 << index; else availableDaysMask &= ~(1 << index);
        availabilityAllDay[index] = allDay;
        availabilityStartMinutes[index] = clampMinute(start);
        availabilityEndMinutes[index] = clampMinute(end);
    }
    private boolean enabled(int day) { return (availableDaysMask & (1 << Math.floorMod(day, 7))) != 0; }

    public boolean infiniteStock() { return stock < 0 || maxStock < 0; }

    /** Applies all elapsed persisted restock intervals, capped without tick-by-tick loops. */
    public boolean applyRestock(long now) {
        normalize();
        if (infiniteStock() || restockAmount <= 0 || restockIntervalMinutes <= 0 || stock >= maxStock) return false;
        long interval;
        try { interval = Math.multiplyExact((long) restockIntervalMinutes, 60_000L); }
        catch (ArithmeticException exception) { interval = Long.MAX_VALUE; }
        if (nextRestockEpochMilli <= 0L) {
            nextRestockEpochMilli = safeAdd(now, interval);
            return true;
        }
        if (now < nextRestockEpochMilli) return false;
        long elapsed = 1L + Math.max(0L, now - nextRestockEpochMilli) / Math.max(1L, interval);
        long add;
        try { add = Math.multiplyExact(elapsed, (long) restockAmount); }
        catch (ArithmeticException exception) { add = Long.MAX_VALUE; }
        stock = (int) Math.min((long) maxStock, (long) stock + add);
        nextRestockEpochMilli = stock >= maxStock ? safeAdd(now, interval)
                : safeAdd(nextRestockEpochMilli, safeMultiply(elapsed, interval));
        return true;
    }

    public NpcShopEntry copy() {
        NpcShopEntry copy = new NpcShopEntry();
        copy.id = id;
        copy.itemStack = NpcItemCodec.safeCopy(itemStack);
        copy.itemId = itemId;
        copy.itemCount = itemCount;
        copy.buyPriceMinor = buyPriceMinor;
        copy.sellPriceMinor = sellPriceMinor;
        copy.stock = stock;
        copy.maxStock = maxStock;
        copy.restockAmount = restockAmount;
        copy.restockIntervalMinutes = restockIntervalMinutes;
        copy.nextRestockEpochMilli = nextRestockEpochMilli;
        copy.availableDaysMask = availableDaysMask;
        copy.availabilityStartMinute = availabilityStartMinute;
        copy.availabilityEndMinute = availabilityEndMinute;
        copy.availabilityStartMinutes = availabilityStartMinutes == null ? defaultStarts() : availabilityStartMinutes.clone();
        copy.availabilityEndMinutes = availabilityEndMinutes == null ? defaultEnds() : availabilityEndMinutes.clone();
        copy.availabilityAllDay = availabilityAllDay == null ? defaultAllDay() : availabilityAllDay.clone();
        return copy;
    }


    private static int clampMinute(int value) {
        return Math.max(0, Math.min(GameCalendar.MINUTES_PER_DAY, value));
    }
    private static int[] normalizedMinutes(int[] values, int fallback) {
        int[] result = new int[7];
        for (int index = 0; index < result.length; index++) {
            result[index] = clampMinute(values != null && index < values.length ? values[index] : fallback);
        }
        return result;
    }
    private static boolean[] normalizedAllDay(boolean[] values) {
        boolean[] result = defaultAllDay();
        if (values != null) for (int index = 0; index < result.length && index < values.length; index++) result[index] = values[index];
        return result;
    }
    private static int[] defaultStarts() { return new int[7]; }
    private static int[] defaultEnds() {
        int[] result = new int[7];
        java.util.Arrays.fill(result, GameCalendar.MINUTES_PER_DAY);
        return result;
    }
    private static boolean[] defaultAllDay() {
        boolean[] result = new boolean[7];
        java.util.Arrays.fill(result, true);
        return result;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static int safeItemCount(int value, int multiplier) {
        if (value <= 0) return Math.max(0, value);
        try { return Math.min(1_000_000, Math.multiplyExact(value, multiplier)); }
        catch (ArithmeticException exception) { return 1_000_000; }
    }
    private static long ceilDivide(long value, long divisor) {
        return value / divisor + (value % divisor == 0L ? 0L : 1L);
    }
    private static long safeAdd(long a, long b) {
        try { return Math.addExact(a, b); } catch (ArithmeticException exception) { return Long.MAX_VALUE; }
    }
    private static long safeMultiply(long a, long b) {
        try { return Math.multiplyExact(a, b); } catch (ArithmeticException exception) { return Long.MAX_VALUE; }
    }
}
