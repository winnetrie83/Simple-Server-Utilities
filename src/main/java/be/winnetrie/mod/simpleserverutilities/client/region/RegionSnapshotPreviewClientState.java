package be.winnetrie.mod.simpleserverutilities.client.region;

import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSnapshotPreviewScreen;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Client-only state and free-inspection controller for snapshot previews. */
public final class RegionSnapshotPreviewClientState {
    private static RegionSnapshotPreviewPayload snapshot = empty();
    private static boolean freeMode;
    private static boolean previousAttackDown;

    private RegionSnapshotPreviewClientState() { }

    public static synchronized void accept(RegionSnapshotPreviewPayload payload) {
        snapshot = payload == null ? empty() : payload;
        if (!snapshot.active()) {
            freeMode = false;
            previousAttackDown = false;
        }
    }

    public static synchronized RegionSnapshotPreviewPayload snapshot() { return snapshot; }
    public static synchronized boolean active() { return snapshot.active(); }
    public static synchronized boolean freeMode() { return freeMode && snapshot.active(); }

    public static synchronized void enterFreeMode() {
        if (!snapshot.active()) return;
        freeMode = true;
        previousAttackDown = Minecraft.getInstance().options.keyAttack.isDown();
    }

    public static synchronized void exitFreeMode() {
        freeMode = false;
        previousAttackDown = false;
    }

    /** Returns true while normal client key workflows should stay suppressed. */
    public static boolean tick(Minecraft minecraft) {
        if (!active()) return false;
        if (!freeMode()) return true;
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return true;
        }
        if (minecraft.gui.screen() != null) {
            minecraft.setScreenAndShow(null);
            return true;
        }
        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (attackDown && !previousAttackDown) {
            exitFreeMode();
            minecraft.options.keyAttack.setDown(false);
            minecraft.setScreenAndShow(new RegionSnapshotPreviewScreen());
            previousAttackDown = false;
            return true;
        }
        previousAttackDown = attackDown;
        return true;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!freeMode() || minecraft.player == null || minecraft.gui.screen() != null) return;
        String title = "Snapshot preview · Free mode";
        String help = "Move and inspect freely · Left-click to return to edit controls";
        graphics.text(minecraft.font, title, (graphics.guiWidth() - minecraft.font.width(title)) / 2,
                10, 0xFF6FE7FF, true);
        graphics.text(minecraft.font, help, (graphics.guiWidth() - minecraft.font.width(help)) / 2,
                23, 0xFFF3F5F7, true);
    }

    public static synchronized void clear() {
        snapshot = empty();
        freeMode = false;
        previousAttackDown = false;
    }

    private static RegionSnapshotPreviewPayload empty() {
        return new RegionSnapshotPreviewPayload(false, "", "", 0L, 0, 0, 0, 0, false, java.util.List.of());
    }
}
