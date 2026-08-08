package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.client.region.RegionSnapshotPreviewClientState;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Small, transparent placement controls for a realistic in-world snapshot preview. */
public final class RegionSnapshotPreviewScreen extends Screen {
    private static long nextRequestId = 1L;
    private boolean confirmCancel;
    private boolean busy;

    public RegionSnapshotPreviewScreen() {
        super(Component.literal("Snapshot preview"));
    }

    public void accept(RegionSnapshotPreviewPayload payload) {
        if (payload == null || !payload.active()) {
            if (minecraft != null) minecraft.setScreenAndShow(null);
        }
    }

    @Override
    protected void init() {
        final int bw = 28, bh = 18, gap = 3;
        final int cols = 6;
        final int totalW = cols * bw + (cols - 1) * gap;
        final int x = width - totalW - 10;
        final int bottom = height - 10;
        int y = bottom - bh;

        addAction(x, y, bw, "←", "preview_west", "Move preview -X / west");
        addAction(x + (bw + gap), y, bw, "→", "preview_east", "Move preview +X / east");
        addAction(x + 2 * (bw + gap), y, bw, "↑", "preview_north", "Move preview -Z / north");
        addAction(x + 3 * (bw + gap), y, bw, "↓", "preview_south", "Move preview +Z / south");
        addAction(x + 4 * (bw + gap), y, bw, "+Y", "preview_up", "Move preview up");
        addAction(x + 5 * (bw + gap), y, bw, "-Y", "preview_down", "Move preview down");

        y -= bh + gap;
        addAction(x, y, bw, "↶", "preview_rotate_left", "Rotate left 90°");
        addAction(x + (bw + gap), y, bw, "↷", "preview_rotate_right", "Rotate right 90°");
        addAction(x + 2 * (bw + gap), y, bw, "180", "preview_rotate_180", "Rotate 180°");
        addAction(x + 3 * (bw + gap), y, bw, "MX", "preview_mirror_x", "Mirror X / east-west");
        addAction(x + 4 * (bw + gap), y, bw, "MZ", "preview_mirror_z", "Mirror Z / north-south");
        Button free = addRenderableWidget(Button.builder(Component.literal("Free"), ignored -> enterFreeMode())
                .bounds(x + 5 * (bw + gap), y, bw, bh).build());
        free.setTooltip(Tooltip.create(Component.literal("Hide controls and move around; left-click returns")));
        free.active = !busy;

        y -= bh + gap;
        int half = (totalW - gap) / 2;
        Button confirm = addRenderableWidget(Button.builder(Component.literal("Place"), ignored -> send("preview_confirm"))
                .bounds(x, y, half, bh).build());
        confirm.setTooltip(Tooltip.create(Component.literal("Place this snapshot at the preview location")));
        Button cancel = addRenderableWidget(Button.builder(Component.literal(confirmCancel ? "Confirm cancel" : "Cancel"), ignored -> cancel())
                .bounds(x + half + gap, y, half, bh).build());
        confirm.active = !busy;
        cancel.active = !busy;
    }

    private void addAction(int x, int y, int width, String label, String operation, String tooltip) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> send(operation))
                .bounds(x, y, width, 18).build());
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        button.active = !busy;
    }

    private void send(String operation) {
        if (busy) return;
        if ("preview_confirm".equals(operation)) busy = true;
        ClientPacketDistributor.sendToServer(new RegionSetupActionPayload(operation, "", "", nextRequestId++));
    }

    private void cancel() {
        if (busy) return;
        if (!confirmCancel) {
            confirmCancel = true;
            rebuildWidgets();
            return;
        }
        ClientPacketDistributor.sendToServer(new RegionSetupActionPayload("preview_cancel", "", "", nextRequestId++));
        busy = true;
    }

    private void enterFreeMode() {
        if (busy) return;
        RegionSnapshotPreviewClientState.enterFreeMode();
        if (minecraft != null) minecraft.setScreenAndShow(null);
    }

    @Override
    public void onClose() {
        if (!confirmCancel) {
            confirmCancel = true;
            rebuildWidgets();
            return;
        }
        ClientPacketDistributor.sendToServer(new RegionSetupActionPayload("preview_cancel", "", "", nextRequestId++));
        busy = true;
    }

    /** Suppress vanilla's screen blur/dim extraction: preview mode must not put a haze over the world. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Intentionally empty.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var data = RegionSnapshotPreviewClientState.snapshot();
        String title = "Preview · " + data.snapshotName() + " · " + data.sizeX() + "×" + data.sizeY() + "×" + data.sizeZ();
        if (!data.complete()) title += " · " + data.receivedBlocks() + "/" + data.totalBlocks();
        int labelY = height - 90;
        graphics.text(font, title, width - font.width(title) - 10, labelY,
                data.complete() ? 0xFF6FE7FF : 0xFFFFD966, true);
        if (confirmCancel) {
            String warning = "Click Confirm cancel again to discard preview";
            graphics.text(font, warning, width - font.width(warning) - 10, labelY - 12, 0xFFFF8585, true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
