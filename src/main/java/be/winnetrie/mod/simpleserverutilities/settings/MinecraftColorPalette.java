package be.winnetrie.mod.simpleserverutilities.settings;

import java.util.List;

/**
 * The fixed sixteen-colour Minecraft palette used by SSU player-facing colour selectors.
 * Names and order are intentionally stable because they are part of the GUI contract.
 */
public final class MinecraftColorPalette {
    public static final List<Entry> COLORS = List.of(
            new Entry("White", 0xFFF9FFFE),
            new Entry("Light Gray", 0xFF9D9D97),
            new Entry("Gray", 0xFF474F52),
            new Entry("Black", 0xFF1D1D21),
            new Entry("Brown", 0xFF835432),
            new Entry("Red", 0xFFB02E26),
            new Entry("Orange", 0xFFF9801D),
            new Entry("Yellow", 0xFFFED83D),
            new Entry("Lime", 0xFF80C71F),
            new Entry("Green", 0xFF5E7C16),
            new Entry("Cyan", 0xFF169C9C),
            new Entry("Light Blue", 0xFF3AB3DA),
            new Entry("Blue", 0xFF3C44AA),
            new Entry("Purple", 0xFF8932B8),
            new Entry("Magenta", 0xFFC74EBD),
            new Entry("Pink", 0xFFF38BAA)
    );

    private MinecraftColorPalette() {
    }

    public static int nearest(int color) {
        int rgb = color & 0x00FFFFFF;
        Entry nearest = COLORS.getFirst();
        long nearestDistance = Long.MAX_VALUE;
        for (Entry entry : COLORS) {
            int candidate = entry.argb & 0x00FFFFFF;
            int red = (rgb >>> 16 & 0xFF) - (candidate >>> 16 & 0xFF);
            int green = (rgb >>> 8 & 0xFF) - (candidate >>> 8 & 0xFF);
            int blue = (rgb & 0xFF) - (candidate & 0xFF);
            long distance = (long) red * red + (long) green * green + (long) blue * blue;
            if (distance < nearestDistance) {
                nearest = entry;
                nearestDistance = distance;
            }
        }
        return nearest.argb;
    }

    public static int next(int color) {
        int normalized = nearest(color);
        for (int index = 0; index < COLORS.size(); index++) {
            if (COLORS.get(index).argb == normalized) {
                return COLORS.get((index + 1) % COLORS.size()).argb;
            }
        }
        return COLORS.getFirst().argb;
    }

    public static String name(int color) {
        int normalized = nearest(color);
        for (Entry entry : COLORS) if (entry.argb == normalized) return entry.name;
        return COLORS.getFirst().name;
    }

    public record Entry(String name, int argb) {
    }
}
