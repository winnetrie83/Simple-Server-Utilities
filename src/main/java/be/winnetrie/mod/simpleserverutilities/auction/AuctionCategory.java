package be.winnetrie.mod.simpleserverutilities.auction;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public enum AuctionCategory {
    WEAPONS("weapons", "Weapons"),
    ARMOR("armor", "Armor"),
    TOOLS("tools", "Tools"),
    BUILDING_BLOCKS("building_blocks", "Building Blocks"),
    PLANTS("plants", "Plants"),
    SEEDS("seeds", "Seeds"),
    FOOD("food", "Food"),
    ENCHANTS("enchants", "Enchants"),
    POTIONS("potions", "Potions"),
    ORES("ores", "Ores"),
    METALS("metals", "Metals"),
    LOGS("logs", "Logs"),
    MACHINES("machines", "Machines"),
    MISCELLANEOUS("miscellaneous", "Miscellaneous");

    private final String id;
    private final String label;

    AuctionCategory(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public static List<AuctionCategory> ordered() {
        return List.of(values());
    }

    public static AuctionCategory byId(String raw) {
        if (raw != null) {
            for (AuctionCategory value : values()) {
                if (value.id.equalsIgnoreCase(raw.trim())) return value;
            }
        }
        return MISCELLANEOUS;
    }

    public static AuctionCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return MISCELLANEOUS;
        var item = stack.getItem();
        var key = BuiltInRegistries.ITEM.getKey(item);
        String namespace = key == null ? "minecraft" : key.getNamespace().toLowerCase(Locale.ROOT);
        String path = key == null ? "" : key.getPath().toLowerCase(Locale.ROOT);
        String className = item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String display = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String words = path + " " + display + " " + className;

        BlockState blockState = null;
        if (item instanceof BlockItem blockItem) blockState = blockItem.getBlock().defaultBlockState();

        if (blockState != null && blockState.is(BlockTags.LOGS)) return LOGS;
        if (containsAny(words, "seed", "seeds", "pit", "kernel")) return SEEDS;
        if (containsAny(words, "enchanted_book", "enchantment", "enchanted book")) return ENCHANTS;
        if (containsAny(words, "potion", "elixir", "flask")) return POTIONS;
        if (containsAny(words, "helmet", "chestplate", "leggings", "boots", "elytra", "shield", "armoritem")) return ARMOR;
        if (containsAny(words, "sword", "bow", "crossbow", "trident", "mace", "spear", "weapon")) return WEAPONS;
        if (containsAny(words, "pickaxe", "shovel", "hoe", "shears", "fishing_rod", "fishing rod", "brush",
                "flint_and_steel", "flint and steel", "wrench", "hammer", "toolitem", "diggeritem")
                || (containsAny(words, "axe") && !containsAny(words, "waxed"))) return TOOLS;
        if (path.endsWith("_ore") || containsAny(words, "ancient_debris", "raw_ore", "ore block")) return ORES;
        if (path.endsWith("_ingot") || path.endsWith("_nugget") || path.startsWith("raw_")
                || (path.endsWith("_block") && containsAny(path, "iron", "gold", "copper", "netherite", "metal",
                        "steel", "bronze", "brass", "aluminum", "aluminium", "tin", "lead", "silver", "nickel",
                        "uranium"))
                || containsAny(words, "metal", "steel", "bronze", "brass", "aluminum", "aluminium", "tin ingot",
                        "lead ingot", "silver ingot", "nickel ingot", "uranium ingot")) return METALS;
        if (isFood(words)) return FOOD;
        if (blockState != null && (blockState.is(BlockTags.FLOWERS) || blockState.is(BlockTags.LEAVES) || containsAny(words, "flower", "sapling", "mushroom", "cactus",
                        "bamboo", "vine", "lily", "fern", "grass", "crop", "plant"))) return PLANTS;
        if (blockState != null && !"minecraft".equals(namespace)
                && containsAny(words, "machine", "generator", "crusher", "furnace", "alloy", "press", "mill",
                        "reactor", "turbine", "charger", "assembler", "factory", "pump", "engine", "controller")) {
            return MACHINES;
        }
        if (blockState != null) return BUILDING_BLOCKS;
        return MISCELLANEOUS;
    }

    private static boolean isFood(String words) {
        return containsAny(words, "apple", "bread", "beef", "pork", "chicken", "mutton", "rabbit", "cod", "salmon",
                "cookie", "cake", "pie", "stew", "soup", "carrot", "potato", "beetroot", "melon", "berries",
                "berry", "honey_bottle", "dried_kelp", "golden_apple", "fooditem", "edible");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
