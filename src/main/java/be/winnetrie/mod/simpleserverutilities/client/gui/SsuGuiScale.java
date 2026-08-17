package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client-only scale controller for SSU screens.
 *
 * SSU layouts always keep their original logical width/height and coordinates.
 * The configured value is applied only as a final centered render transform,
 * with input coordinates mapped back into that same logical coordinate space.
 * Minecraft's own GUI Scale option is never changed.
 */
public final class SsuGuiScale {
    public static final int MIN_PERCENT = 60;
    public static final int MAX_PERCENT = 100;
    public static final int STEP_PERCENT = 10;

    private static final String GUI_PACKAGE = "be.winnetrie.mod.simpleserverutilities.client.gui.";

    private SsuGuiScale() {
    }

    public static boolean appliesTo(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(GUI_PACKAGE);
    }

    public static int percent() {
        return clampPercent(Config.SSU_GUI_SCALE_PERCENT.get());
    }

    public static float scale(Screen screen) {
        if (!appliesTo(screen)) return 1.0F;
        float configured = percent() / 100.0F;
        if (!(screen instanceof SsuFixedLogicalCanvas canvas)) return configured;

        int margin = Math.max(0, canvas.ssuLogicalCanvasMargin());
        int availableWidth = Math.max(1, screen.width - margin);
        int availableHeight = Math.max(1, screen.height - margin);
        int logicalWidth = Math.max(1, canvas.ssuLogicalCanvasWidth());
        int logicalHeight = Math.max(1, canvas.ssuLogicalCanvasHeight());
        float fitWidth = availableWidth / (float) logicalWidth;
        float fitHeight = availableHeight / (float) logicalHeight;
        float fit = Math.min(1.0F, Math.min(fitWidth, fitHeight));
        return Math.max(0.05F, Math.min(configured, fit));
    }

    public static boolean isScaled(Screen screen) {
        return scale(screen) < 0.999F;
    }

    public static void setPercent(int percent) {
        int safe = clampPercent(percent);
        if (safe == Config.SSU_GUI_SCALE_PERCENT.get()) return;
        Config.SSU_GUI_SCALE_PERCENT.set(safe);
        Config.SSU_GUI_SCALE_PERCENT.save();
    }

    public static int smallerPercent() {
        return clampPercent(percent() - STEP_PERCENT);
    }

    public static int largerPercent() {
        return clampPercent(percent() + STEP_PERCENT);
    }

    public static double logicalX(Screen screen, double physicalX) {
        float scale = scale(screen);
        if (scale >= 0.999F) return physicalX;
        double center = screen.width * 0.5D;
        return center + (physicalX - center) / scale;
    }

    public static double logicalY(Screen screen, double physicalY) {
        float scale = scale(screen);
        if (scale >= 0.999F) return physicalY;
        double center = screen.height * 0.5D;
        return center + (physicalY - center) / scale;
    }

    public static int logicalX(Screen screen, int physicalX) {
        return (int) Math.round(logicalX(screen, (double) physicalX));
    }

    public static int logicalY(Screen screen, int physicalY) {
        return (int) Math.round(logicalY(screen, (double) physicalY));
    }

    public static double logicalDelta(Screen screen, double physicalDelta) {
        float scale = scale(screen);
        return scale >= 0.999F ? physicalDelta : physicalDelta / scale;
    }

    /** Maps one logical SSU coordinate to the physical scaled screen position. */
    public static int physicalX(Screen screen, double logicalX) {
        float scale = scale(screen);
        if (scale >= 0.999F) return (int) Math.round(logicalX);
        double center = screen.width * 0.5D;
        return (int) Math.round(center + (logicalX - center) * scale);
    }

    /** Maps one logical SSU coordinate to the physical scaled screen position. */
    public static int physicalY(Screen screen, double logicalY) {
        float scale = scale(screen);
        if (scale >= 0.999F) return (int) Math.round(logicalY);
        double center = screen.height * 0.5D;
        return (int) Math.round(center + (logicalY - center) * scale);
    }

    /** Scales a logical widget length for renderers that bypass the screen pose transform. */
    public static int physicalLength(Screen screen, double logicalLength) {
        return Math.max(1, (int) Math.round(logicalLength * scale(screen)));
    }

    /**
     * Reduced-scale compatibility for legacy SSU screens that previously relied
     * on Screen's vanilla background instead of drawing an SSU backdrop. At
     * 100% this deliberately does nothing so their original behaviour remains
     * byte-for-byte equivalent from the user's perspective.
     */
    public static void fullscreenDimWhenScaled(GuiGraphics graphics, Screen screen, int color) {
        if (isScaled(screen)) fullscreenDim(graphics, screen, color);
    }

    public static void fullscreenDim(GuiGraphics graphics, Screen screen, int color) {
        float scale = scale(screen);
        if (scale >= 0.999F) {
            graphics.fill(0, 0, screen.width, screen.height, color);
            return;
        }
        float centerX = screen.width * 0.5F;
        float centerY = screen.height * 0.5F;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(1.0F / scale, 1.0F / scale, 1.0F);
        graphics.pose().translate(-centerX, -centerY, 0.0F);
        graphics.fill(0, 0, screen.width, screen.height, color);
        graphics.pose().popPose();
    }

    private static int clampPercent(int percent) {
        int safe = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
        // Keep the stored value on a predictable 10% grid. This also makes old or
        // manually edited client configs recover to a supported value cleanly.
        int steps = Math.round((safe - MIN_PERCENT) / (float) STEP_PERCENT);
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, MIN_PERCENT + steps * STEP_PERCENT));
    }
}
