package be.winnetrie.mod.simpleserverutilities.client.minigame;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameHudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Right-aligned scoreboard visible only while the server marks a player in a match. */
public final class MinigameHudClientState {
    private static boolean serverVisible;
    private static boolean userHidden;
    private static String title = "";
    private static List<String> lines = List.of();

    private MinigameHudClientState() {}

    public static void apply(MinigameHudPayload payload) {
        boolean wasVisible = serverVisible;
        serverVisible = payload != null && payload.visible();
        if (wasVisible && !serverVisible) userHidden = false;
        title = payload == null ? "" : payload.title();
        lines = payload == null ? List.of() : payload.lines();
    }

    public static boolean toggleVisibility() {
        if (!serverVisible) return false;
        userHidden = !userHidden;
        return !userHidden;
    }

    public static boolean isServerVisible() {
        return serverVisible;
    }

    public static void clear() {
        serverVisible = false;
        userHidden = false;
        title = "";
        lines = List.of();
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!serverVisible || userHidden || minecraft.player == null || minecraft.gui.screen() != null) return;
        int width = Math.max(150, minecraft.font.width(title) + 24);
        for (String line : lines) width = Math.max(width, minecraft.font.width(line) + 24);
        int height = 26 + lines.size() * 13;
        int x = minecraft.getWindow().getGuiScaledWidth() - width - 10;
        int y = 38;
        graphics.fill(x, y, x + width, y + height, 0xC9161D25);
        graphics.outline(x, y, width, height, 0xFF586978);
        graphics.text(minecraft.font, title, x + 10, y + 8, 0xFFFFD65A, true);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(minecraft.font, lines.get(i), x + 10, y + 23 + i * 13, 0xFFF3F5F7, false);
        }
    }
}
