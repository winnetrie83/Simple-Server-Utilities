package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorResultPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Central Admin Center shop manager with search, creation, editing and guarded deletion. */
public final class NpcShopAdminScreen extends Screen {
    private static final int W = 680, H = 390, ROW_HEIGHT = 27;
    private static final int PANEL = 0xF0141920, SUBPANEL = 0xD01C2630, BORDER = 0xFF596B79;
    private static final int ROW = 0xC024303A, SELECTED = 0xE03C5364;
    private static final int TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;

    private NpcShopAdminDataPayload data;
    private final Screen parent;
    private long nextRequestId;
    private int selectedIndex = -1;
    private EditBox searchBox;
    private EditBox createBox;
    private Button editButton;
    private Button deleteButton;
    private String notice = "";
    private boolean noticeError;
    private String deletePendingId = "";
    private final List<RowBounds> rows = new ArrayList<>();

    public NpcShopAdminScreen(NpcShopAdminDataPayload initial, Screen parent) {
        super(Component.literal("Shop Manager"));
        this.data = initial;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, initial.requestId() + 1L);
        this.notice = initial.notice();
        this.noticeError = initial.error();
        if (!initial.entries().isEmpty()) selectedIndex = 0;
    }

    public void accept(NpcShopAdminDataPayload updated) {
        if (updated == null || updated.requestId() < data.requestId()) return;
        String selectedId = selected() == null ? "" : selected().id();
        data = updated;
        nextRequestId = Math.max(nextRequestId, updated.requestId() + 1L);
        notice = updated.notice();
        noticeError = updated.error();
        deletePendingId = "";
        selectedIndex = indexOf(selectedId);
        if (selectedIndex < 0 && !data.entries().isEmpty()) selectedIndex = 0;
        rebuildWidgets();
    }

    public void acceptEditorResult(NpcShopEditorResultPayload result) {
        if (result == null) return;
        nextRequestId = Math.max(nextRequestId, result.requestId() + 1L);
        notice = result.message();
        noticeError = !result.successful();
        rebuildWidgets();
    }

    public void refreshFromEditor(String message) {
        notice = message == null ? "" : message;
        noticeError = false;
        request(data.pageIndex());
    }

    @Override protected void init() {
        rows.clear();
        int left = left(), top = top();
        searchBox = new EditBox(font, left + 16, top + 38, 250, 20, Component.literal("Search shops"));
        searchBox.setMaxLength(64); searchBox.setValue(data.query()); addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> request(0))
                .bounds(left + 272, top + 38, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("All shops"), button -> {
            if (searchBox != null) searchBox.setValue(""); request(0);
        }).bounds(left + 344, top + 38, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> request(data.pageIndex()))
                .bounds(left + 432, top + 38, 72, 20).build());

        createBox = new EditBox(font, left + 16, top + H - 58, 190, 20, Component.literal("New shop ID"));
        createBox.setMaxLength(64); createBox.setHint(Component.literal("new_shop_id")); addRenderableWidget(createBox);
        addRenderableWidget(Button.builder(Component.literal("Create"), button -> create())
                .bounds(left + 212, top + H - 58, 70, 20).build());
        editButton = addRenderableWidget(Button.builder(Component.literal("Edit"), button -> edit())
                .bounds(left + 292, top + H - 58, 70, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"), button -> delete())
                .bounds(left + 476, top + H - 58, 76, 20).build());

        addRenderableWidget(Button.builder(Component.literal("<"), button -> request(data.pageIndex() - 1))
                .bounds(left + 16, top + H - 30, 28, 18).build()).active = data.pageIndex() > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> request(data.pageIndex() + 1))
                .bounds(left + 48, top + H - 30, 28, 18).build()).active = data.pageIndex() + 1 < data.pageCount();
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + W - 82, top + H - 30, 66, 18).build());
        updateButtons();
    }

    private void request(int page) {
        String query = searchBox == null ? data.query() : searchBox.getValue();
        ClientPacketDistributor.sendToServer(new NpcShopAdminRequestPayload(query, Math.max(0, page), nextRequestId++));
    }

    private void create() {
        String id = createBox == null ? "" : createBox.getValue().trim();
        if (id.isBlank()) { setNotice("Enter a new shop ID first.", true); return; }
        ClientPacketDistributor.sendToServer(new NpcShopAdminActionPayload("new", "", id,
                searchBox == null ? data.query() : searchBox.getValue(), data.pageIndex(), nextRequestId++));
    }

    private void edit() {
        NpcShopAdminDataPayload.Entry entry = selected();
        if (entry == null) return;
        ClientPacketDistributor.sendToServer(new NpcShopAdminActionPayload("open", entry.id(), "",
                data.query(), data.pageIndex(), nextRequestId++));
    }

    private void delete() {
        NpcShopAdminDataPayload.Entry entry = selected();
        if (entry == null) return;
        if (entry.npcDefinitionCount() > 0) {
            setNotice("Unlink this shop from its NPC templates before deleting it.", true);
            return;
        }
        if (!entry.id().equals(deletePendingId)) {
            deletePendingId = entry.id();
            setNotice("Click Delete again to permanently remove '" + entry.id() + "'.", true);
            return;
        }
        ClientPacketDistributor.sendToServer(new NpcShopAdminActionPayload("delete", entry.id(), "",
                data.query(), data.pageIndex(), nextRequestId++));
        deletePendingId = "";
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = left(), top = top();
        g.fill(0, 0, width, height, 0xA9000000);
        g.fill(left, top, left + W, top + H, PANEL);
        g.outline(left, top, W, H, BORDER);
        g.text(font, "Shop Manager", left + 16, top + 13, TEXT, true);
        g.text(font, data.totalShops() + " shared shop(s)", left + W - 150, top + 14, MUTED, false);

        int listTop = top + 68, listBottom = top + H - 72;
        g.fill(left + 12, listTop, left + W - 12, listBottom, SUBPANEL);
        g.outline(left + 12, listTop, W - 24, listBottom - listTop, BORDER);
        rows.clear();
        for (int index = 0; index < data.entries().size(); index++) {
            int y = listTop + 5 + index * ROW_HEIGHT;
            RowBounds row = new RowBounds(index, left + 17, y, W - 34, ROW_HEIGHT - 2);
            rows.add(row);
            NpcShopAdminDataPayload.Entry entry = data.entries().get(index);
            g.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
                    index == selectedIndex ? SELECTED : ROW);
            g.text(font, trim(entry.displayName(), 26), row.x() + 8, row.y() + 5, TEXT, false);
            g.text(font, trim(entry.id(), 22), row.x() + 186, row.y() + 5, MUTED, false);
            g.text(font, entry.offerCount() + " offers", row.x() + 342, row.y() + 5, MUTED, false);
            String usage = entry.npcDefinitionCount() + " tpl · " + entry.npcPlacementCount() + " NPC";
            g.text(font, usage, row.x() + 418, row.y() + 5,
                    entry.npcDefinitionCount() > 0 ? GOOD : MUTED, false);
            g.text(font, entry.enabled() ? "Enabled" : "Disabled", row.x() + row.width() - 62, row.y() + 5,
                    entry.enabled() ? GOOD : ERROR, false);
        }
        if (data.entries().isEmpty()) g.text(font, "No shops match this search.", left + 28, listTop + 24, MUTED, false);
        g.text(font, "Page " + (data.pageIndex() + 1) + "/" + data.pageCount(), left + 86, top + H - 26, MUTED, false);
        if (!notice.isBlank()) g.text(font, trim(notice, 82), left + 292, top + H - 25,
                noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        for (RowBounds row : rows) {
            if (row.contains((int) event.x(), (int) event.y())) {
                selectedIndex = row.index(); deletePendingId = ""; notice = ""; updateButtons();
                if (doubleClick) edit();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void updateButtons() {
        boolean selected = selected() != null;
        if (editButton != null) editButton.active = selected;
        if (deleteButton != null) deleteButton.active = selected;
    }
    private void setNotice(String message, boolean error) { notice = message; noticeError = error; }
    private NpcShopAdminDataPayload.Entry selected() {
        return selectedIndex >= 0 && selectedIndex < data.entries().size() ? data.entries().get(selectedIndex) : null;
    }
    private int indexOf(String id) {
        for (int index = 0; index < data.entries().size(); index++) if (data.entries().get(index).id().equals(id)) return index;
        return -1;
    }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, Math.max(0, maximum - 1)) + "…";
    }
    private record RowBounds(int index, int x, int y, int width, int height) {
        boolean contains(int mx, int my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    }
}
