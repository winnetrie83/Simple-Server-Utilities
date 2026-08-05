package be.winnetrie.mod.simpleserverutilities.client.minigame;

import be.winnetrie.mod.simpleserverutilities.network.MinigameCastBarPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Bottom-center interruptible action bar used by minigames. */
public final class MinigameCastBarClientState {
    private static boolean visible;
    private static String label = "";
    private static float progress;
    private static int color = 0xFFFFFF;

    private MinigameCastBarClientState() {
    }

    public static synchronized void apply(MinigameCastBarPayload payload) {
        visible = payload != null && payload.visible();
        label = payload == null ? "" : payload.label();
        progress = payload == null ? 0.0F : payload.progress();
        color = payload == null ? 0xFFFFFF : payload.color();
    }

    public static synchronized void clear() {
        visible = false;
        label = "";
        progress = 0.0F;
        color = 0xFFFFFF;
    }

    public static synchronized void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visible || minecraft.player == null || minecraft.gui.screen() != null) return;
        int width = 182;
        int height = 12;
        int x = (minecraft.getWindow().getGuiScaledWidth() - width) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 58;
        graphics.fill(x, y, x + width, y + height, 0xD010141A);
        int fill = Math.round((width - 4) * Math.max(0.0F, Math.min(1.0F, progress)));
        graphics.fill(x + 2, y + 2, x + 2 + fill, y + height - 2, 0xFF000000 | color);
        graphics.outline(x, y, width, height, 0xFFE8EEF3);
        int textX = x + (width - minecraft.font.width(label)) / 2;
        graphics.text(minecraft.font, label, textX, y - 12, 0xFFFFFFFF, true);
    }
}
