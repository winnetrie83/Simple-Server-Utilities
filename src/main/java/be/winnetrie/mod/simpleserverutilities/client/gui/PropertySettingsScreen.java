package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final int DROPDOWN_ROW = 0xFF293540;
    private static final int DROPDOWN_HOVER = 0xFF415868;
    private static final int ROW_HEIGHT = 27;
    private static final int PAGE_SIZE = 8;
    private static final int DROPDOWN_VISIBLE_ROWS = 8;
    private static final int DROPDOWN_ROW_HEIGHT = 14;

    private SsuPropertySettingsDataPayload data;
    private final Screen parent;
    private int pageIndex;
    private long nextRequestId;
    private final Map<String, EditBox> valueBoxes = new HashMap<>();
    private final List<RowBounds> rowBounds = new ArrayList<>();
    private final Map<String, String> selectedOptionValues = new HashMap<>();
    private final Map<String, Button> optionButtons = new HashMap<>();
    private final Map<String, Rect> optionAnchors = new HashMap<>();
    private final List<OptionBounds> optionBounds = new ArrayList<>();
    private String expandedOptionKey = "";
    private int optionScroll;

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
        expandedOptionKey = "";
        optionScroll = 0;
        selectedOptionValues.keySet().removeIf(key -> data.entries().stream().noneMatch(entry -> entry.key().equals(key)));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        valueBoxes.clear();
        rowBounds.clear();
        optionButtons.clear();
        optionAnchors.clear();
        optionBounds.clear();

        Layout layout = layout();
        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(data.entries().size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            SsuPropertySettingsDataPayload.Entry entry = data.entries().get(index);
            int row = index - from;
            int y = layout.listTop() + row * ROW_HEIGHT;
            rowBounds.add(new RowBounds(entry, layout.left() + 10, y, layout.width() - 20, ROW_HEIGHT - 2));
            addEntryWidget(entry, layout, y);
        }

        int footerY = layout.bottom() - 28;
        Button previous = Button.builder(Component.literal("< Previous"), ignored -> changePage(-1))
                .bounds(layout.left() + 10, footerY, 82, 20).build();
        previous.active = pageIndex > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal("Next >"), ignored -> changePage(1))
                .bounds(layout.left() + 98, footerY, 68, 20).build();
        next.active = pageIndex + 1 < pageCount();
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestRefresh())
                .bounds(layout.right() - 154, footerY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(layout.right() - 78, footerY, 68, 20).build());
    }

    private void addEntryWidget(SsuPropertySettingsDataPayload.Entry entry, Layout layout, int y) {
        int controlWidth = Math.min(230, Math.max(140, layout.width() / 2));
        int controlX = layout.right() - controlWidth - 10;
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
            case "player_action" -> addPlayerAction(entry, controlX, y, controlWidth, enabled);
            case "navigate" -> {
                Button navigate = Button.builder(Component.literal(entry.value()), ignored -> openNavigation(entry.key()))
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

    private void addPlayerAction(
            SsuPropertySettingsDataPayload.Entry entry,
            int controlX,
            int y,
            int controlWidth,
            boolean enabled
    ) {
        List<SsuPropertySettingsDataPayload.Option> options = entry.options();
        String storedValue = selectedOptionValues.get(entry.key());
        String selectedValue = storedValue;
        if (storedValue == null || options.stream().noneMatch(option -> option.value().equals(storedValue))) {
            selectedValue = options.isEmpty() ? "" : options.getFirst().value();
            selectedOptionValues.put(entry.key(), selectedValue);
        }

        int applyWidth = 54;
        int pickerWidth = controlWidth - applyWidth - 6;
        Button picker = Button.builder(optionButtonLabel(entry, selectedValue), ignored -> {
                    if (entry.options().isEmpty()) return;
                    expandedOptionKey = expandedOptionKey.equals(entry.key()) ? "" : entry.key();
                    optionScroll = 0;
                })
                .bounds(controlX, y + 2, pickerWidth, 20).build();
        picker.active = enabled && !options.isEmpty();
        optionButtons.put(entry.key(), picker);
        optionAnchors.put(entry.key(), new Rect(controlX, y + 2, pickerWidth, 20));
        addRenderableWidget(picker);

        Button apply = Button.builder(Component.literal("Apply"), ignored ->
                        send(entry.key(), selectedOptionValues.getOrDefault(entry.key(), "")))
                .bounds(controlX + pickerWidth + 6, y + 2, applyWidth, 20).build();
        apply.active = enabled && !selectedValue.isBlank();
        addRenderableWidget(apply);
    }

    private Component optionButtonLabel(SsuPropertySettingsDataPayload.Entry entry, String selectedValue) {
        if (entry.options().isEmpty()) return Component.literal("No players");
        String label = entry.options().stream()
                .filter(option -> option.value().equals(selectedValue))
                .map(SsuPropertySettingsDataPayload.Option::label)
                .findFirst()
                .orElse("Choose player");
        return Component.literal(trim(label, 18) + " ▼");
    }

    private void send(String key, String value) {
        expandedOptionKey = "";
        optionScroll = 0;
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuPropertySettingsActionPayload(
                data.kind(), data.target(), key, value, id));
    }

    private void requestRefresh() {
        expandedOptionKey = "";
        optionScroll = 0;
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuPropertySettingsRequestPayload(data.kind(), data.target(), id));
    }

    void refreshFromChild() {
        requestRefresh();
    }

    private void openNavigation(String destination) {
        long id = nextRequestId++;
        if ("region_permissions".equals(destination)) {
            ClientPacketDistributor.sendToServer(new SsuPermissionEditorRequestPayload(
                    "region", data.target(), "", "", "", 0, 8, id));
        } else if ("trusted_players".equals(destination) && "claim".equals(data.kind())) {
            ClientPacketDistributor.sendToServer(new SsuTrustedPlayersRequestPayload(data.target(), "", id));
        } else if ("homes".equals(destination) && "claim".equals(data.kind())) {
            if (parent instanceof SsuDashboardScreen dashboard) {
                dashboard.openHomesForClaim(data.target());
                minecraft.setScreenAndShow(dashboard);
            } else if (parent instanceof ClaimMapScreen claimMap) {
                claimMap.openHomesForClaim(data.target());
            }
        }
    }

    private void changePage(int delta) {
        expandedOptionKey = "";
        optionScroll = 0;
        pageIndex = Math.max(0, Math.min(pageCount() - 1, pageIndex + delta));
        rebuildWidgets();
    }

    private int pageCount() {
        return Math.max(1, (data.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!expandedOptionKey.isBlank()) {
            for (OptionBounds bounds : optionBounds) {
                if (!bounds.contains(event.x(), event.y())) continue;
                selectedOptionValues.put(expandedOptionKey, bounds.option().value());
                Button picker = optionButtons.get(expandedOptionKey);
                SsuPropertySettingsDataPayload.Entry entry = findEntry(expandedOptionKey);
                if (picker != null && entry != null) {
                    picker.setMessage(optionButtonLabel(entry, bounds.option().value()));
                }
                expandedOptionKey = "";
                optionScroll = 0;
                return true;
            }
            Rect dropdown = dropdownBounds();
            if (dropdown != null && dropdown.contains(event.x(), event.y())) return true;
            Rect anchor = optionAnchors.get(expandedOptionKey);
            if (anchor != null && anchor.contains(event.x(), event.y())) {
                expandedOptionKey = "";
                optionScroll = 0;
                return true;
            }
            expandedOptionKey = "";
            optionScroll = 0;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double rawMouseX = mouseX;
        double rawMouseY = mouseY;
        mouseX = SsuGuiScale.logicalX(this, mouseX);
        mouseY = SsuGuiScale.logicalY(this, mouseY);
        if (!expandedOptionKey.isBlank()) {
            Rect bounds = dropdownBounds();
            SsuPropertySettingsDataPayload.Entry entry = findEntry(expandedOptionKey);
            if (bounds != null && entry != null && bounds.contains(mouseX, mouseY)) {
                int maxScroll = Math.max(0, entry.options().size() - DROPDOWN_VISIBLE_ROWS);
                if (scrollY < 0) optionScroll = Math.min(maxScroll, optionScroll + 1);
                else if (scrollY > 0) optionScroll = Math.max(0, optionScroll - 1);
                return true;
            }
        }
        return super.mouseScrolled(rawMouseX, rawMouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout();
        SsuGuiScale.fullscreenDim(graphics, this, 0xA5000000);
        graphics.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), PANEL);
        graphics.outline(layout.left(), layout.top(), layout.width(), layout.height(), BORDER);
        graphics.text(font, data.title(), layout.left() + 10, layout.top() + 10, TEXT, false);
        graphics.text(font, data.canEdit() ? "Changes are validated and saved by the server." : "Read-only",
                layout.left() + 10, layout.top() + 27, data.canEdit() ? MUTED : ERROR, false);

        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(data.entries().size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            SsuPropertySettingsDataPayload.Entry entry = data.entries().get(index);
            int y = layout.listTop() + (index - from) * ROW_HEIGHT;
            if (((index - from) & 1) == 0) {
                graphics.fill(layout.left() + 6, y, layout.right() - 6, y + ROW_HEIGHT - 2, 0x401F2A34);
            }
            graphics.text(font, entry.label(), layout.left() + 12, y + 8, entry.editable() ? TEXT : MUTED, false);
        }

        graphics.text(font, "Page " + (pageIndex + 1) + " / " + pageCount(),
                layout.left() + 176, layout.bottom() - 22, MUTED, false);
        if (!data.notice().isBlank()) {
            graphics.text(font, data.notice(), layout.left() + 10, layout.bottom() - 45,
                    data.error() ? ERROR : GOOD, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        Rect dropdown = dropdownBounds();
        if (dropdown == null || !dropdown.contains(mouseX, mouseY)) {
            for (RowBounds row : rowBounds) {
                if (!row.contains(mouseX, mouseY)) continue;
                SsuPropertySettingsDataPayload.Entry entry = row.entry();
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(entry.description()));
                if ("integer".equals(entry.type())) {
                    tooltip.add(Component.literal("Allowed: " + entry.minimum() + " to " + entry.maximum()));
                } else if ("player_action".equals(entry.type())) {
                    tooltip.add(Component.literal("Available players: " + entry.options().size()));
                }
                if (!entry.defaultValue().isBlank()) {
                    tooltip.add(Component.literal("Default: " + entry.defaultValue()));
                }
                if (!entry.value().isBlank() && !"player_action".equals(entry.type())) {
                    tooltip.add(Component.literal("Current: " + entry.value()));
                }
                graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                break;
            }
        }

        drawOptionDropdown(graphics, mouseX, mouseY);
    }

    private void drawOptionDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        optionBounds.clear();
        if (expandedOptionKey.isBlank()) return;
        SsuPropertySettingsDataPayload.Entry entry = findEntry(expandedOptionKey);
        Rect bounds = dropdownBounds();
        if (entry == null || bounds == null || entry.options().isEmpty()) return;

        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF121922);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), BORDER);

        int maxScroll = Math.max(0, entry.options().size() - DROPDOWN_VISIBLE_ROWS);
        optionScroll = Math.max(0, Math.min(optionScroll, maxScroll));
        int shown = Math.min(DROPDOWN_VISIBLE_ROWS, entry.options().size() - optionScroll);
        for (int row = 0; row < shown; row++) {
            SsuPropertySettingsDataPayload.Option option = entry.options().get(optionScroll + row);
            int rowY = bounds.y() + 4 + row * DROPDOWN_ROW_HEIGHT;
            boolean hovered = mouseX >= bounds.x() + 4 && mouseX < bounds.right() - 8
                    && mouseY >= rowY && mouseY < rowY + 12;
            graphics.fill(bounds.x() + 4, rowY, bounds.right() - 8, rowY + 12,
                    hovered ? DROPDOWN_HOVER : DROPDOWN_ROW);
            graphics.outline(bounds.x() + 4, rowY, bounds.width() - 12, 12, BORDER);
            graphics.text(font, trim(option.label(), 22), bounds.x() + 8, rowY + 2, TEXT, false);
            optionBounds.add(new OptionBounds(option, bounds.x() + 4, rowY, bounds.width() - 12, 12));
        }

        if (entry.options().size() > DROPDOWN_VISIBLE_ROWS) {
            int trackX = bounds.right() - 5;
            int trackY = bounds.y() + 4;
            int trackHeight = bounds.height() - 8;
            graphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0xFF596670);
            int thumbHeight = Math.max(12, trackHeight * DROPDOWN_VISIBLE_ROWS / entry.options().size());
            int thumbY = trackY + (trackHeight - thumbHeight) * optionScroll / Math.max(1, maxScroll);
            graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF9BA8B4);
        }
    }

    private SsuPropertySettingsDataPayload.Entry findEntry(String key) {
        return data.entries().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
    }

    private Rect dropdownBounds() {
        if (expandedOptionKey.isBlank()) return null;
        Rect anchor = optionAnchors.get(expandedOptionKey);
        SsuPropertySettingsDataPayload.Entry entry = findEntry(expandedOptionKey);
        if (anchor == null || entry == null || entry.options().isEmpty()) return null;
        int rows = Math.min(DROPDOWN_VISIBLE_ROWS, entry.options().size());
        int height = 8 + rows * DROPDOWN_ROW_HEIGHT;
        int below = anchor.bottom() + 2;
        int above = anchor.y() - height - 2;
        int y = below + height <= this.height - 6 ? below : Math.max(6, above);
        return new Rect(anchor.x(), y, anchor.width(), height);
    }

    private Layout layout() {
        int panelWidth = Math.min(660, Math.max(360, this.width - 36));
        int panelHeight = Math.min(330, Math.max(250, this.height - 40));
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        return new Layout(left, top, panelWidth, panelHeight, top + 52);
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

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 1)) + "…";
    }

    private record Layout(int left, int top, int width, int height, int listTop) {
        int right() { return left + width; }
        int bottom() { return top + height; }
    }

    private record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }

    private record RowBounds(SsuPropertySettingsDataPayload.Entry entry, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record OptionBounds(
            SsuPropertySettingsDataPayload.Option option,
            int x,
            int y,
            int width,
            int height
    ) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
