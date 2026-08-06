package be.winnetrie.mod.simpleserverutilities.client.minigame;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameHudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Right-aligned scoreboard visible only while the server marks a player in a match. */
public final class MinigameHudClientState {
    private static boolean serverVisible;
    /** 0 compact, 1 expanded, 2 hidden. */
    private static int displayMode;
    private static String title = "";
    private static List<String> lines = List.of();

    private MinigameHudClientState() {}

    public static void apply(MinigameHudPayload payload) {
        boolean wasVisible = serverVisible;
        serverVisible = payload != null && payload.visible();
        if (!wasVisible && serverVisible || wasVisible && !serverVisible) displayMode = 0;
        title = payload == null ? "" : payload.title();
        lines = payload == null ? List.of() : payload.lines();
    }

    public static String cycleDisplayMode() {
        if (!serverVisible) return "unavailable";
        displayMode = (displayMode + 1) % 3;
        return switch (displayMode) {
            case 1 -> "expanded";
            case 2 -> "hidden";
            default -> "compact";
        };
    }

    public static boolean isServerVisible() {
        return serverVisible;
    }

    public static void clear() {
        serverVisible = false;
        displayMode = 0;
        title = "";
        lines = List.of();
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!serverVisible || displayMode == 2 || minecraft.player == null || minecraft.gui.screen() != null) return;
        List<String> visibleLines = displayMode == 1 ? lines : compactLines(lines);
        int width = Math.max(150, minecraft.font.width(title) + 24);
        for (String line : visibleLines) width = Math.max(width, minecraft.font.width(line) + 24);
        int height = 26 + visibleLines.size() * 13;
        int x = minecraft.getWindow().getGuiScaledWidth() - width - 10;
        int y = 38;
        graphics.fill(x, y, x + width, y + height, 0xC9161D25);
        graphics.outline(x, y, width, height, 0xFF586978);
        graphics.text(minecraft.font, title, x + 10, y + 8, 0xFFFFD65A, true);
        for (int i = 0; i < visibleLines.size(); i++) {
            graphics.text(minecraft.font, visibleLines.get(i), x + 10, y + 23 + i * 13, 0xFFF3F5F7, false);
        }
    }

    private static List<String> compactLines(List<String> source) {
        java.util.ArrayList<String> compact = new java.util.ArrayList<>();
        for (String line : source) {
            if (line == null) continue;
            if (line.startsWith("Time:") || line.startsWith("Starts in:") || line.startsWith("Respawning in:")
                    || line.startsWith("Flags:") || line.startsWith("Nodes:") || line.startsWith("Alive:")
                    || line.contains(" · ") && !line.startsWith("Mode:")) {
                compact.add(line);
                if (compact.size() >= 3) break;
            }
        }
        if (compact.isEmpty()) {
            for (String line : source) {
                if (line == null || line.startsWith("Mode:") || line.startsWith("State:")) continue;
                compact.add(line);
                if (compact.size() >= 3) break;
            }
        }
        return List.copyOf(compact);
    }
}
