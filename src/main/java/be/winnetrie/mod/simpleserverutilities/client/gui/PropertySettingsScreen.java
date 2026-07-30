package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Paged, server-authoritative editor for claim and server-region settings. */
public final class PropertySettingsScreen extends Screen {
    private static final int PANEL = 0xF012171E;
    private static final int BORDER = 0xFF52606D;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFA5B0BA;
    private static final int GOOD = 0xFF84E39A;
    private static final int ERROR = 0xFFFF8080;
    private static final int ROW_HEIGHT = 27;
    private static final int PAGE_SIZE = 8;

    private SsuPropertySettingsDataPayload data;
    private final Screen parent;
    private int pageIndex;
    private long nextRequestId;
    private final Map<String, EditBox> valueBoxes = new HashMap<>();
    private final List<RowBounds> rowBounds = new ArrayList<>();

    public PropertySettingsScreen(SsuPropertySettingsDataPayload data, Screen parent) {
        super(Component.literal(data.title()));
        this.data = data;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, data.requestId() + 1L);
    }

    public void acceptData(SsuPropertySettingsDataPayload updated) {
        if (!updated.kind().equals(data.kind()) || !updated.target().equalsIgnoreCase(data.target())) return;
        if (updated.requestId() < data.requestId()) return;
        data = updated;
        int pages = pageCount();
        pageIndex = Math.max(0, Math.min(pageIndex, pages - 1));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        valueBoxes.clear();
        rowBounds.clear();

        Layout l = layout();
        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(data.entries().size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            SsuPropertySettingsDataPayload.Entry entry = data.entries().get(index);
            int row = index - from;
            int y = l.listTop() + row * ROW_HEIGHT;
            rowBounds.add(new RowBounds(entry, l.left() + 10, y, l.width() - 20, ROW_HEIGHT - 2));
            addEntryWidget(entry, l, y);
        }

        int footerY = l.bottom() - 28;
        Button previous = Button.builder(Component.literal("< Previous"), ignored -> changePage(-1))
                .bounds(l.left() + 10, footerY, 82, 20).build();
        previous.active = pageIndex > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal("Next >"), ignored -> changePage(1))
                .bounds(l.left() + 98, footerY, 68, 20).build();
        next.active = pageIndex + 1 < pageCount();
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestRefresh())
                .bounds(l.right() - 154, footerY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(l.right() - 78, footerY, 68, 20).build());
    }

    private void addEntryWidget(SsuPropertySettingsDataPayload.Entry entry, Layout l, int y) {
        int controlWidth = Math.min(230, Math.max(140, l.width() / 2));
        int controlX = l.right() - controlWidth - 10;
        boolean enabled = data.canEdit() && entry.editable();

        switch (entry.type()) {
            case "boolean" -> {
                boolean current = Boolean.parseBoolean(entry.value());
                Button button = Button.builder(Component.literal(current ? "ON" : "OFF"), ignored ->
                        send(entry.key(), Boolean.toString(!current)))
                        .bounds(controlX, y + 2, 72, 20).build();
                button.active = enabled;
                addRenderableWidget(button);
            }
            case "integer", "text" -> {
                int buttonWidth = 48;
                EditBox box = new EditBox(font, controlX, y + 2, controlWidth - buttonWidth - 6, 20,
                        Component.literal(entry.label()));
                box.setMaxLength("text".equals(entry.type()) ? 256 : 32);
                box.setValue(entry.value());
                box.active = enabled;
                valueBoxes.put(entry.key(), box);
                addRenderableWidget(box);
                Button set = Button.builder(Component.literal("Set"), ignored -> send(entry.key(), box.getValue()))
                        .bounds(controlX + controlWidth - buttonWidth, y + 2, buttonWidth, 20).build();
                set.active = enabled;
                addRenderableWidget(set);
            }
            case "readonly" -> {
                Button value = Button.builder(Component.literal(entry.value()), ignored -> {})
                        .bounds(controlX, y + 2, controlWidth, 20).build();
                value.active = false;
                addRenderableWidget(value);
            }
            case "action" -> {
                Button action = Button.builder(Component.literal(entry.value()), ignored -> send(entry.key(), "true"))
                        .bounds(controlX, y + 2, Math.min(controlWidth, 110), 20).build();
                action.active = enabled && !"Not set".equalsIgnoreCase(entry.value());
                if ("set_spawn".equals(entry.key())) action.active = enabled;
                addRenderableWidget(action);
            }
            case "navigate" -> {
                Button navigate = Button.builder(Component.literal(entry.value()), ignored -> openPermissionEditor(entry.key()))
                        .bounds(controlX, y + 2, Math.min(controlWidth, 110), 20).build();
                navigate.active = enabled;
                addRenderableWidget(navigate);
            }
            default -> {
                Button unsupported = Button.builder(Component.literal(entry.value()), ignored -> {})
                        .bounds(controlX, y + 2, Math.min(controlWidth, 110), 20).build();
                unsupported.active = false;
                addRenderableWidget(unsupported);
            }
        }
    }

    private void send(String key, String value) {
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuPropertySettingsActionPayload(
                data.kind(), data.target(), key, value, id));
    }

    private void requestRefresh() {
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuPropertySettingsRequestPayload(data.kind(), data.target(), id));
    }

    private void openPermissionEditor(String destination) {
        if (!"region_permissions".equals(destination)) return;
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuPermissionEditorRequestPayload(
                "region", data.target(), "", "", 0, 8, id));
    }

    private void changePage(int delta) {
        pageIndex = Math.max(0, Math.min(pageCount() - 1, pageIndex + delta));
        rebuildWidgets();
    }

    private int pageCount() {
        return Math.max(1, (data.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(l.left(), l.top(), l.right(), l.bottom(), PANEL);
        g.outline(l.left(), l.top(), l.width(), l.height(), BORDER);
        g.text(font, data.title(), l.left() + 10, l.top() + 10, TEXT, false);
        g.text(font, data.canEdit() ? "Changes are validated and saved by the server." : "Read-only",
                l.left() + 10, l.top() + 27, data.canEdit() ? MUTED : ERROR, false);

        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(data.entries().size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            SsuPropertySettingsDataPayload.Entry entry = data.entries().get(index);
            int y = l.listTop() + (index - from) * ROW_HEIGHT;
            if (((index - from) & 1) == 0) g.fill(l.left() + 6, y, l.right() - 6, y + ROW_HEIGHT - 2, 0x401F2A34);
            g.text(font, entry.label(), l.left() + 12, y + 8, entry.editable() ? TEXT : MUTED, false);
        }

        g.text(font, "Page " + (pageIndex + 1) + " / " + pageCount(), l.left() + 176, l.bottom() - 22, MUTED, false);
        if (!data.notice().isBlank()) {
            g.text(font, data.notice(), l.left() + 10, l.bottom() - 45, data.error() ? ERROR : GOOD, false);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        for (RowBounds row : rowBounds) {
            if (!row.contains(mouseX, mouseY)) continue;
            SsuPropertySettingsDataPayload.Entry entry = row.entry();
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(entry.description()));
            tooltip.add(Component.literal("Type: " + entry.type()));
            if ("integer".equals(entry.type())) {
                tooltip.add(Component.literal("Allowed: " + entry.minimum() + " to " + entry.maximum()));
            }
            if (!entry.defaultValue().isBlank()) tooltip.add(Component.literal("Default: " + entry.defaultValue()));
            tooltip.add(Component.literal("Current: " + entry.value()));
            g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            break;
        }
    }

    private Layout layout() {
        int width = Math.min(660, Math.max(360, this.width - 36));
        int height = Math.min(330, Math.max(250, this.height - 40));
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;
        return new Layout(left, top, width, height, top + 52);
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

    private record Layout(int left, int top, int width, int height, int listTop) {
        int right() { return left + width; }
        int bottom() { return top + height; }
    }

    private record RowBounds(SsuPropertySettingsDataPayload.Entry entry, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
