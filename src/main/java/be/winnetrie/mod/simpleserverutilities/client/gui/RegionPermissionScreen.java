package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Searchable, paged editor for contextual permission overrides on one server region. */
public final class RegionPermissionScreen extends Screen {

    private static final int PANEL = 0xF012171E;
    private static final int BORDER = 0xFF52606D;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFA5B0BA;
    private static final int GOOD = 0xFF84E39A;
    private static final int WARNING = 0xFFFFB86B;
    private static final int ERROR = 0xFFFF8080;
    private static final int ROW_HEIGHT = 29;
    private static final int PAGE_SIZE = 8;

    private SsuPermissionEditorDataPayload data;
    private final Screen parent;
    private final Map<String, EditBox> valueBoxes = new HashMap<>();
    private final List<RowBounds> rowBounds = new ArrayList<>();
    private EditBox searchBox;
    private String query = "";
    private String notice = "";
    private boolean noticeError;
    private long nextRequestId;
    private long latestActionRequest;

    public RegionPermissionScreen(SsuPermissionEditorDataPayload data, Screen parent) {
        super(Component.literal("Region permissions — " + data.selectedLabel()));
        this.data = data;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, data.requestId() + 1L);
    }

    public void acceptData(SsuPermissionEditorDataPayload updated) {
        if (updated == null || !"region".equals(updated.mode())) return;
        if (!updated.selectedTarget().equalsIgnoreCase(data.selectedTarget())) return;
        if (updated.requestId() < data.requestId()) return;
        data = updated;
        if (!updated.notice().isBlank()) {
            notice = updated.notice();
            noticeError = updated.error();
        }
        rebuildWidgets();
    }

    public void acceptActionResult(SsuMenuActionResultPayload result) {
        if (result == null || result.requestId() < latestActionRequest) return;
        latestActionRequest = result.requestId();
        notice = result.message();
        noticeError = !result.successful();
        if (result.successful()) requestPage(data.pageIndex());
        else rebuildWidgets();
    }

    @Override
    protected void init() {
        valueBoxes.clear();
        rowBounds.clear();
        Layout l = layout();

        searchBox = new EditBox(font, l.left() + 10, l.top() + 43, l.width() - 104, 20,
                Component.literal("Search permissions"));
        searchBox.setMaxLength(96);
        searchBox.setValue(query);
        searchBox.setResponder(value -> query = value);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> requestPage(0))
                .bounds(l.right() - 88, l.top() + 43, 78, 20).build());

        for (int index = 0; index < data.permissions().size(); index++) {
            SsuPermissionEditorDataPayload.PermissionEntry entry = data.permissions().get(index);
            int y = l.listTop() + index * ROW_HEIGHT;
            rowBounds.add(new RowBounds(entry, l.left() + 8, y, l.width() - 16, ROW_HEIGHT - 2));
            addPermissionWidgets(entry, l, y);
        }

        int footerY = l.bottom() - 28;
        Button previous = Button.builder(Component.literal("< Previous"), ignored -> requestPage(data.pageIndex() - 1))
                .bounds(l.left() + 10, footerY, 82, 20).build();
        previous.active = data.pageIndex() > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal("Next >"), ignored -> requestPage(data.pageIndex() + 1))
                .bounds(l.left() + 98, footerY, 68, 20).build();
        next.active = data.pageIndex() + 1 < pageCount();
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestPage(data.pageIndex()))
                .bounds(l.right() - 154, footerY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(l.right() - 78, footerY, 68, 20).build());
    }

    private void addPermissionWidgets(SsuPermissionEditorDataPayload.PermissionEntry entry, Layout l, int y) {
        int controlWidth = Math.min(310, Math.max(210, l.width() / 2));
        int controlX = l.right() - controlWidth - 10;

        if ("boolean".equals(entry.valueType())) {
            int gap = 4;
            int buttonWidth = (controlWidth - gap * 2) / 3;
            Button inherit = Button.builder(Component.literal("Default"), ignored -> unset(entry.key()))
                    .bounds(controlX, y + 3, buttonWidth, 20).build();
            Button allow = Button.builder(Component.literal("Allow"), ignored -> set(entry.key(), "true"))
                    .bounds(controlX + buttonWidth + gap, y + 3, buttonWidth, 20).build();
            Button deny = Button.builder(Component.literal("Deny"), ignored -> set(entry.key(), "false"))
                    .bounds(controlX + (buttonWidth + gap) * 2, y + 3, buttonWidth, 20).build();
            inherit.active = !entry.directValue().isBlank();
            allow.active = !"true".equalsIgnoreCase(entry.directValue());
            deny.active = !"false".equalsIgnoreCase(entry.directValue());
            addRenderableWidget(inherit);
            addRenderableWidget(allow);
            addRenderableWidget(deny);
            return;
        }

        int resetWidth = 52;
        int setWidth = 42;
        int boxWidth = controlWidth - resetWidth - setWidth - 10;
        EditBox box = new EditBox(font, controlX, y + 3, boxWidth, 20, Component.literal(entry.key()));
        box.setMaxLength("integer".equals(entry.valueType()) ? 32 : 128);
        box.setValue(entry.directValue());
        valueBoxes.put(entry.key(), box);
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("Set"), ignored -> set(entry.key(), box.getValue()))
                .bounds(controlX + boxWidth + 4, y + 3, setWidth, 20).build());
        Button reset = Button.builder(Component.literal("Reset"), ignored -> unset(entry.key()))
                .bounds(controlX + boxWidth + setWidth + 8, y + 3, resetWidth, 20).build();
        reset.active = !entry.directValue().isBlank();
        addRenderableWidget(reset);
    }

    private void set(String key, String value) {
        long id = nextRequestId++;
        latestActionRequest = id;
        ClientPacketDistributor.sendToServer(new SsuMenuActionPayload(
                "permission_region_set", data.selectedTarget(), key, value, id));
    }

    private void unset(String key) {
        long id = nextRequestId++;
        latestActionRequest = id;
        ClientPacketDistributor.sendToServer(new SsuMenuActionPayload(
                "permission_region_unset", data.selectedTarget(), key, "", id));
    }

    private void requestPage(int pageIndex) {
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuPermissionEditorRequestPayload(
                "region", data.selectedTarget(), "", "", query,
                Math.max(0, pageIndex), PAGE_SIZE, id));
    }

    private int pageCount() {
        return Math.max(1, (data.totalPermissions() + data.pageSize() - 1) / data.pageSize());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(l.left(), l.top(), l.right(), l.bottom(), PANEL);
        g.outline(l.left(), l.top(), l.width(), l.height(), BORDER);
        g.text(font, "Region permissions — " + data.selectedLabel(), l.left() + 10, l.top() + 10, TEXT, false);
        g.text(font, data.targetSummary(), l.left() + 10, l.top() + 25, MUTED, false);

        if (data.permissions().isEmpty()) {
            g.text(font, "No permissions match this search.", l.left() + 12, l.listTop() + 8, MUTED, false);
        }
        for (int index = 0; index < data.permissions().size(); index++) {
            SsuPermissionEditorDataPayload.PermissionEntry entry = data.permissions().get(index);
            int y = l.listTop() + index * ROW_HEIGHT;
            if ((index & 1) == 0) g.fill(l.left() + 6, y, l.right() - 6, y + ROW_HEIGHT - 2, 0x401F2A34);
            int keyColor = entry.directValue().isBlank() ? TEXT
                    : "false".equalsIgnoreCase(entry.directValue()) ? WARNING : GOOD;
            g.text(font, trim(entry.key(), 46), l.left() + 12, y + 9, keyColor, false);
        }

        g.text(font, "Page " + (data.pageIndex() + 1) + " / " + pageCount(),
                l.left() + 176, l.bottom() - 22, MUTED, false);
        if (!notice.isBlank()) {
            g.text(font, trim(notice, 88), l.left() + 10, l.bottom() - 45,
                    noticeError ? ERROR : GOOD, false);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        for (RowBounds row : rowBounds) {
            if (!row.contains(mouseX, mouseY)) continue;
            SsuPermissionEditorDataPayload.PermissionEntry entry = row.entry();
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(entry.description()));
            tooltip.add(Component.literal("Type: " + entry.valueType()));
            if ("integer".equals(entry.valueType())) {
                tooltip.add(Component.literal("Allowed: " + entry.minimum() + " to " + entry.maximum()));
            }
            tooltip.add(Component.literal("Region override: "
                    + (entry.directValue().isBlank() ? "Default / none" : entry.directValue())));
            tooltip.add(Component.literal("Fallback preview: " + blank(entry.defaultValue())));
            tooltip.add(Component.literal("Resolved source: " + entry.source()));
            g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            break;
        }
    }

    private Layout layout() {
        int panelWidth = Math.min(760, Math.max(500, width - 32));
        int panelHeight = Math.min(370, Math.max(330, height - 30));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        return new Layout(left, top, panelWidth, panelHeight, top + 70);
    }

    @Override
    public void onClose() {
        if (parent != null) minecraft.setScreenAndShow(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private record Layout(int left, int top, int width, int height, int listTop) {
        int right() { return left + width; }
        int bottom() { return top + height; }
    }

    private record RowBounds(SsuPermissionEditorDataPayload.PermissionEntry entry,
                             int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
