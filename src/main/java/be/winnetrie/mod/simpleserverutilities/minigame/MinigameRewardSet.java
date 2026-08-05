package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;

/**
 * One administrator-configured minigame reward package.
 *
 * <p>Item stacks and money are always delivered through SSU Mail. Direct
 * actions are applied immediately and are also described in the reward mail.</p>
 *
 * <p>The item list preserves nine visible ghost-inventory positions. Empty
 * positions are stored as JSON null values so administrators can place stacks
 * in any slot without the remaining rewards shifting around.</p>
 */
public final class MinigameRewardSet {
    public static final int MAX_ITEM_STACKS = 9;
    public static final int MAX_DIRECT_ACTIONS = 64;

    /** Exact registry-aware ItemStack JSON values, including components and stack counts. */
    public List<JsonElement> itemStacks = new ArrayList<>();
    /** Exact Economy Core minor units. The editor presents this as a normal formatted amount. */
    public long moneyMinor;
    /** Permission, progression and other non-item actions applied immediately. */
    public List<ContentAction> directActions = new ArrayList<>();

    public void normalize() {
        ArrayList<JsonElement> normalizedItems = new ArrayList<>();
        if (itemStacks != null) {
            int limit = Math.min(itemStacks.size(), MAX_ITEM_STACKS);
            for (int index = 0; index < limit; index++) {
                JsonElement encoded = itemStacks.get(index);
                normalizedItems.add(encoded == null || encoded.isJsonNull()
                        ? JsonNull.INSTANCE : encoded.deepCopy());
            }
        }
        trimTrailingEmptySlots(normalizedItems);
        itemStacks = normalizedItems;
        moneyMinor = Math.max(0L, moneyMinor);

        ArrayList<ContentAction> normalizedActions = new ArrayList<>();
        if (directActions != null) {
            for (ContentAction action : directActions) {
                if (action == null) continue;
                normalizedActions.add(action.normalize());
                if (normalizedActions.size() >= MAX_DIRECT_ACTIONS) break;
            }
        }
        directActions = normalizedActions;
    }

    public JsonElement itemAt(int index) {
        if (index < 0 || index >= itemStacks.size()) return null;
        JsonElement encoded = itemStacks.get(index);
        return encoded == null || encoded.isJsonNull() ? null : encoded;
    }

    public int itemCount() {
        int count = 0;
        for (JsonElement encoded : itemStacks) {
            if (encoded != null && !encoded.isJsonNull()) count++;
        }
        return count;
    }

    public void setItem(int index, JsonElement encoded) {
        if (index < 0 || index >= MAX_ITEM_STACKS) {
            throw new IllegalArgumentException("Reward item slot must be between 1 and " + MAX_ITEM_STACKS + ".");
        }
        if (encoded == null || encoded.isJsonNull()) {
            throw new IllegalArgumentException("An empty item cannot be used as a minigame reward.");
        }
        while (itemStacks.size() <= index) itemStacks.add(JsonNull.INSTANCE);
        itemStacks.set(index, encoded.deepCopy());
        normalize();
    }

    public void removeItem(int index) {
        if (index < 0 || index >= itemStacks.size()) return;
        itemStacks.set(index, JsonNull.INSTANCE);
        trimTrailingEmptySlots(itemStacks);
    }

    public boolean empty() {
        return itemCount() == 0 && moneyMinor <= 0L && directActions.isEmpty();
    }

    private static void trimTrailingEmptySlots(List<JsonElement> values) {
        while (!values.isEmpty()) {
            JsonElement last = values.get(values.size() - 1);
            if (last != null && !last.isJsonNull()) break;
            values.remove(values.size() - 1);
        }
    }
}
