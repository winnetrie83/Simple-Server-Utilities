package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.client.region.RegionSnapshotPreviewClientState;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Minimal world-visible controls for non-destructive selection-snapshot placement. */
public final class RegionSnapshotPreviewScreen extends Screen {
    private static long nextRequestId = 1L;
    private RegionSnapshotPreviewPayload data;
    private boolean confirmCancel;
    private boolean busy;

    public RegionSnapshotPreviewScreen() {
        super(Component.literal("Snapshot preview"));
        this.data = RegionSnapshotPreviewClientState.snapshot();
    }

    public void accept(RegionSnapshotPreviewPayload payload) {
        this.data = payload;
        if (!payload.active() && minecraft != null) minecraft.setScreenAndShow(null);
    }

    @Override
    protected void init() {
        int gap = 4;
        int buttonWidth = Math.max(88, Math.min(112, (width - 32 - gap * 2) / 3));
        int total = buttonWidth * 3 + gap * 2;
        int x = (width - total) / 2;
        int y = Math.max(52, height - 124);

        addAction(x, y, buttonWidth, "Move -X", "preview_west");
        addAction(x + buttonWidth + gap, y, buttonWidth, "Move +X", "preview_east");
        addAction(x + 2 * (buttonWidth + gap), y, buttonWidth, "Move -Y", "preview_down");

        y += 24;
        addAction(x, y, buttonWidth, "Move +Y", "preview_up");
        addAction(x + buttonWidth + gap, y, buttonWidth, "Move -Z", "preview_north");
        addAction(x + 2 * (buttonWidth + gap), y, buttonWidth, "Move +Z", "preview_south");

        y += 24;
        addAction(x, y, buttonWidth, "Rotate left", "preview_rotate_left");
        addAction(x + buttonWidth + gap, y, buttonWidth, "Rotate right", "preview_rotate_right");
        addAction(x + 2 * (buttonWidth + gap), y, buttonWidth, "Rotate 180", "preview_rotate_180");

        y += 24;
        addAction(x, y, buttonWidth, "Mirror X", "preview_mirror_x");
        addAction(x + buttonWidth + gap, y, buttonWidth, "Mirror Z", "preview_mirror_z");
        Button free = addRenderableWidget(Button.builder(Component.literal("Free mode"), ignored -> enterFreeMode())
                .bounds(x + 2 * (buttonWidth + gap), y, buttonWidth, 20).build());
        free.active = !busy;

        y += 24;
        Button confirm = addRenderableWidget(Button.builder(Component.literal("Confirm placement"), ignored -> send("preview_confirm"))
                .bounds(x, y, (total - gap) / 2, 20).build());
        Button cancel = addRenderableWidget(Button.builder(Component.literal(confirmCancel ? "Confirm cancel" : "Cancel preview"), ignored -> cancel())
                .bounds(x + (total + gap) / 2, y, (total - gap) / 2, 20).build());
        confirm.active = !busy;
        cancel.active = !busy;
    }

    private void addAction(int x, int y, int width, String label, String operation) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> send(operation))
                .bounds(x, y, width, 20).build());
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        String name = data == null ? "" : data.snapshotName();
        String title = "Snapshot preview: " + name;
        int titleX = (width - font.width(title)) / 2;
        graphics.text(font, title, titleX, 12, 0xFF6FE7FF, true);
        String help = confirmCancel
                ? "Press Escape again or click Confirm cancel to discard the preview."
                : "Edit mode · Free mode lets you move around; left-click returns to these controls.";
        graphics.text(font, help, (width - font.width(help)) / 2, 26,
                confirmCancel ? 0xFFFF8585 : 0xFFF3F5F7, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
