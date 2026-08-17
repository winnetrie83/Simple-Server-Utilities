package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.client.mapmarker.MapMarkerClientState;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Remote edit/delete list for all personal map markers. */
public final class MapMarkerManagementScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private final Screen parent;
    private int page;
    private UUID pendingDelete;
    private String status = "";
    private boolean statusError;

    public MapMarkerManagementScreen(Screen parent) {
        super(Component.literal("Map markers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<MapMarkerSyncPayload.Entry> markers = MapMarkerClientState.markers();
        int pages = Math.max(1, (markers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(pages - 1, page));
        int panelWidth = Math.min(560, width - 28);
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, (height - 300) / 2);
        int start = page * PAGE_SIZE;
        int end = Math.min(markers.size(), start + PAGE_SIZE);
        int y = top + 40;
        for (int index = start; index < end; index++) {
            MapMarkerSyncPayload.Entry marker = markers.get(index);
            Button edit = Button.builder(Component.literal(marker.name()), ignored -> edit(marker))
                    .bounds(left + 44, y, panelWidth - 174, 22).build();
            edit.setTooltip(Tooltip.create(Component.literal(
                    marker.dimension() + "  X " + marker.x() + " Y " + marker.y() + " Z " + marker.z())));
            addRenderableWidget(edit);
            addRenderableWidget(Button.builder(Component.literal("✎"), ignored -> edit(marker))
                    .bounds(left + panelWidth - 124, y, 42, 22).build());
            String deleteLabel = marker.id().equals(pendingDelete) ? "!" : "×";
            addRenderableWidget(Button.builder(Component.literal(deleteLabel), ignored -> delete(marker))
                    .bounds(left + panelWidth - 76, y, 42, 22).build());
            y += 27;
        }

        Button previous = Button.builder(Component.literal("‹"), ignored -> { page--; rebuildWidgets(); })
                .bounds(left + 14, top + 254, 32, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal("›"), ignored -> { page++; rebuildWidgets(); })
                .bounds(left + 50, top + 254, 32, 20).build();
        next.active = page + 1 < pages;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> closeToParent())
                .bounds(left + panelWidth - 74, top + 254, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        SsuGuiScale.fullscreenDim(graphics, this, 0xA5000000);
        List<MapMarkerSyncPayload.Entry> markers = MapMarkerClientState.markers();
        int pages = Math.max(1, (markers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int panelWidth = Math.min(560, width - 28);
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, (height - 300) / 2);
        graphics.fill(left, top, left + panelWidth, top + 286, 0xE5121720);
        graphics.renderOutline(left, top, panelWidth, 286, 0xFF64778D);
        graphics.drawCenteredString(font, title, left + panelWidth / 2, top + 13, 0xFFFFD66B);
        int start = page * PAGE_SIZE;
        int end = Math.min(markers.size(), start + PAGE_SIZE);
        int y = top + 46;
        for (int index = start; index < end; index++) {
            MapMarkerSyncPayload.Entry marker = markers.get(index);
            graphics.fill(left + 18, y - 3, left + 34, y + 13, marker.colorArgb());
            graphics.renderOutline(left + 18, y - 3, 16, 16, 0xFFFFFFFF);
            y += 27;
        }
        if (markers.isEmpty()) {
            graphics.drawCenteredString(font, "No personal markers yet.", left + panelWidth / 2, top + 120, 0xFFB8C4D2);
        }
        graphics.drawString(font, "Page " + (page + 1) + " / " + pages, left + 94, top + 260, 0xFFB8C4D2);
        if (!status.isBlank()) {
            graphics.drawString(font, status, left + 14, top + 279, statusError ? 0xFFFF6B6B : 0xFF6BFF88);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    public void acceptResult(MapMarkerActionResultPayload result) {
        status = result.message();
        statusError = !result.success();
        if (result.success()) {
            pendingDelete = null;
            rebuildWidgets();
        }
    }

    private void edit(MapMarkerSyncPayload.Entry marker) {
        if (minecraft != null) minecraft.setScreen(new MapMarkerEditorScreen(
                this, marker, marker.dimension(), marker.x(), marker.y(), marker.z(), false));
    }

    private void delete(MapMarkerSyncPayload.Entry marker) {
        if (!marker.id().equals(pendingDelete)) {
            pendingDelete = marker.id();
            status = "Click × again to delete " + marker.name() + ".";
            statusError = false;
            rebuildWidgets();
            return;
        }
        PacketDistributor.sendToServer(new MapMarkerActionPayload(
                "delete", marker.id(), "", marker.dimension(), marker.x(), marker.y(), marker.z(), marker.colorArgb(), false));
        status = "Deleting…";
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public void onClose() { closeToParent(); }
    @Override public boolean isPauseScreen() { return false; }
}
