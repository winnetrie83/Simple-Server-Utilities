package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import net.minecraft.resources.Identifier;

/** Server-validated settings for the first concrete SSU minigame mode. */
public final class SpleefRules {
    public static final int MAX_BREAKABLE_BLOCKS = 32;

    public List<String> breakableBlocks = new ArrayList<>(List.of(
            "minecraft:snow_block",
            "minecraft:snow",
            "minecraft:powder_snow"
    ));
    public String toolItem = "minecraft:diamond_shovel";
    public boolean requireConfiguredTool = true;
    public boolean allowPvp;
    public boolean removeBlockDrops = true;
    public int eliminationDepth = 2;

    /** Infinite single-block projectile, represented by a snowball. */
    public boolean standardProjectileEnabled = true;
    public int standardProjectileUnlockSeconds = 45;
    public int standardProjectileCooldownSeconds = 5;
    /** Finite cross-shaped projectile, represented by an egg. */
    public boolean burstProjectileEnabled = true;
    public int burstProjectileStartSeconds = 90;
    public int burstProjectileMinIntervalSeconds = 20;
    public int burstProjectileMaxIntervalSeconds = 45;
    public int burstProjectileMaximumStack = 3;

    public void normalize() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (breakableBlocks != null) {
            for (String raw : breakableBlocks) {
                String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                if (value.isBlank()) continue;
                Identifier.parse(value);
                normalized.add(value);
                if (normalized.size() >= MAX_BREAKABLE_BLOCKS) break;
            }
        }
        if (normalized.isEmpty()) normalized.add("minecraft:snow_block");
        breakableBlocks = new ArrayList<>(normalized);
        toolItem = normalizeIdentifier(toolItem, "minecraft:diamond_shovel");
        eliminationDepth = Math.max(0, Math.min(64, eliminationDepth));
        standardProjectileUnlockSeconds = Math.max(0, Math.min(3_600, standardProjectileUnlockSeconds));
        standardProjectileCooldownSeconds = Math.max(1, Math.min(300, standardProjectileCooldownSeconds));
        burstProjectileStartSeconds = Math.max(0, Math.min(3_600, burstProjectileStartSeconds));
        burstProjectileMinIntervalSeconds = Math.max(1, Math.min(3_600, burstProjectileMinIntervalSeconds));
        burstProjectileMaxIntervalSeconds = Math.max(burstProjectileMinIntervalSeconds,
                Math.min(3_600, burstProjectileMaxIntervalSeconds));
        burstProjectileMaximumStack = Math.max(1, Math.min(16, burstProjectileMaximumStack));
        // Spleef floor blocks are temporary arena state. They must disappear directly
        // instead of briefly spawning collectible or visually distracting item drops.
        removeBlockDrops = true;
    }

    public boolean canBreak(String blockId) {
        return blockId != null && breakableBlocks.contains(blockId.toLowerCase(Locale.ROOT));
    }

    private static String normalizeIdentifier(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim().toLowerCase(Locale.ROOT);
        Identifier.parse(value);
        return value;
    }
}
