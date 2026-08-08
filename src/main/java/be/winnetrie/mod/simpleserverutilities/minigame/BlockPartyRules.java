package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.Identifier;

/** Free-for-all Block Party round timing and floor palette. */
public final class BlockPartyRules {
    public static final int MAX_PALETTE = 16;
    public List<String> paletteBlocks = new ArrayList<>(List.of(
            "minecraft:white_concrete", "minecraft:orange_concrete", "minecraft:magenta_concrete",
            "minecraft:light_blue_concrete", "minecraft:yellow_concrete", "minecraft:lime_concrete",
            "minecraft:pink_concrete", "minecraft:gray_concrete", "minecraft:light_gray_concrete",
            "minecraft:cyan_concrete", "minecraft:purple_concrete", "minecraft:blue_concrete",
            "minecraft:brown_concrete", "minecraft:green_concrete", "minecraft:red_concrete",
            "minecraft:black_concrete"));
    public int initialRoundSeconds = 6;
    public int minimumRoundSeconds = 2;
    /** Amount removed from the safe-color countdown after every completed round. */
    public double speedupSecondsPerRound = 0.25D;
    /** Time non-safe blocks remain absent before the next floor is painted. */
    public int dropSeconds = 3;
    public int tileSize = 3;
    public int eliminationDepth = 4;

    public void normalize() {
        LinkedHashSet<String> palette = new LinkedHashSet<>();
        if (paletteBlocks != null) for (String raw : paletteBlocks) {
            String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (value.isBlank()) continue;
            Identifier.parse(value);
            palette.add(value);
            if (palette.size() >= MAX_PALETTE) break;
        }
        if (palette.size() < 2) {
            palette.clear();
            palette.add("minecraft:white_concrete");
            palette.add("minecraft:red_concrete");
            palette.add("minecraft:blue_concrete");
            palette.add("minecraft:lime_concrete");
        }
        paletteBlocks = new ArrayList<>(palette);
        initialRoundSeconds = Math.max(2, Math.min(60, initialRoundSeconds));
        minimumRoundSeconds = Math.max(1, Math.min(initialRoundSeconds, minimumRoundSeconds));
        speedupSecondsPerRound = Math.max(0.0D, Math.min(5.0D, speedupSecondsPerRound));
        dropSeconds = Math.max(1, Math.min(30, dropSeconds));
        tileSize = Math.max(1, Math.min(8, tileSize));
        eliminationDepth = Math.max(1, Math.min(64, eliminationDepth));
    }
}
