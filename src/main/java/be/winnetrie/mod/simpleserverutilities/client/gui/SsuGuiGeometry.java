package be.winnetrie.mod.simpleserverutilities.client.gui;

/** Shared geometry and index helpers used by SSU editor screens. */
public final class SsuGuiGeometry {
    private SsuGuiGeometry() {
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + Math.max(0, width)
                && mouseY >= y && mouseY < y + Math.max(0, height);
    }

    public static int clampIndex(int index, int size) {
        if (size <= 0) return -1;
        return Math.max(0, Math.min(index, size - 1));
    }

    public static int wrapIndex(int index, int size) {
        if (size <= 0) return -1;
        return Math.floorMod(index, size);
    }
}
