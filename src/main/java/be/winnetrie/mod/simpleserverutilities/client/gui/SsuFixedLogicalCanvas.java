package be.winnetrie.mod.simpleserverutilities.client.gui;

/**
 * Optional contract for SSU screens whose logical layout must never reflow to
 * match Minecraft's current GUI-scaled width/height.
 *
 * The screen keeps this canonical logical canvas and SsuGuiScale applies an
 * automatic fit scale when the physical/logical viewport is smaller. The
 * user's configured SSU GUI scale remains the upper bound.
 */
public interface SsuFixedLogicalCanvas {
    /** Canonical logical content/panel width that must fit inside the viewport. */
    int ssuLogicalCanvasWidth();

    /** Canonical logical content/panel height that must fit inside the viewport. */
    int ssuLogicalCanvasHeight();

    /** Small edge margin retained around the fitted canvas. */
    default int ssuLogicalCanvasMargin() {
        return 8;
    }
}
