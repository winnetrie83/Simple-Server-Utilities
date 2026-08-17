package be.winnetrie.mod.simpleserverutilities.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Maps 1.21.1 physical screen mouse coordinates back into SSU's logical
 * coordinate system before a scaled SSU screen receives the input.
 *
 * <p>The 26.2 implementation could hook the later MouseButtonEvent input
 * object directly. Minecraft 1.21.1 does not have that class, but NeoForge's
 * cancellable ScreenEvent pre-events provide the same boundary. We dispatch
 * the transformed event once ourselves and cancel the original physical one.</p>
 */
public final class SsuGuiScaleInputEvents {
    private SsuGuiScaleInputEvents() {}

    private static boolean scaled(Screen screen) {
        return SsuGuiScale.appliesTo(screen) && SsuGuiScale.isScaled(screen);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!scaled(screen)) return;
        screen.mouseClicked(
                SsuGuiScale.logicalX(screen, event.getMouseX()),
                SsuGuiScale.logicalY(screen, event.getMouseY()),
                event.getButton());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Screen screen = event.getScreen();
        if (!scaled(screen)) return;
        screen.mouseReleased(
                SsuGuiScale.logicalX(screen, event.getMouseX()),
                SsuGuiScale.logicalY(screen, event.getMouseY()),
                event.getButton());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        Screen screen = event.getScreen();
        if (!scaled(screen)) return;
        screen.mouseDragged(
                SsuGuiScale.logicalX(screen, event.getMouseX()),
                SsuGuiScale.logicalY(screen, event.getMouseY()),
                event.getMouseButton(),
                SsuGuiScale.logicalDelta(screen, event.getDragX()),
                SsuGuiScale.logicalDelta(screen, event.getDragY()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        Screen screen = event.getScreen();
        if (!scaled(screen)) return;
        screen.mouseScrolled(
                SsuGuiScale.logicalX(screen, event.getMouseX()),
                SsuGuiScale.logicalY(screen, event.getMouseY()),
                event.getScrollDeltaX(),
                event.getScrollDeltaY());
        event.setCanceled(true);
    }
}
