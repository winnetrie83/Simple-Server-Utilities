package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Searchable administrator GUI for global vanilla and modded item base prices. */
public final class NpcItemPriceCatalogScreen extends Screen {
    private static final int W = 615, H = 375, ROW_HEIGHT = 16;
    private static final int PANEL = 0xF0141920, SUBPANEL = 0xD01C2630, BORDER = 0xFF596B79;
    private static final int ROW = 0xC024303A, SELECTED = 0xE03C5364;
    private static final int TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;

    private NpcItemPriceCatalogDataPayload data;
    private final Screen parent;
    private EditBox searchBox, buyBox, sellBox;
    private int selectedIndex = -1;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;
    private final List<RowBounds> rows = new ArrayList<>();

    public NpcItemPriceCatalogScreen(NpcItemPriceCatalogDataPayload data, Screen parent) {
        super(Component.literal("Item Price Catalog"));
        this.data = data;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, data.requestId() + 1L);
        this.notice = data.notice();
        this.noticeError = data.error();
        if (!data.entries().isEmpty()) selectedIndex = 0;
    }

    public void accept(NpcItemPriceCatalogDataPayload updated) {
        if (updated == null || updated.requestId() < data.requestId()) return;
        String selectedId = selected() == null ? "" : selected().itemId();
        data = updated;
        nextRequestId = Math.max(nextRequestId, updated.requestId() + 1L);
        notice = updated.notice();
        noticeError = updated.error();
        selectedIndex = indexOf(selectedId);
        if (selectedIndex < 0 && !data.entries().isEmpty()) selectedIndex = 0;
        rebuildWidgets();
    }

    @Override protected void init() {
        rows.clear();
        int left = left(), top = top();
        searchBox = new EditBox(font, left + 14, top + 36, 226, 20, Component.literal("Search items"));
        searchBox.setMaxLength(96);
        searchBox.setValue(data.query());
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> request(0))
                .bounds(left + 246, top + 36, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("All items"), button -> {
            searchBox.setValue(""); request(0);
        }).bounds(left + 310, top + 36, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> request(data.pageIndex()))
                .bounds(left + 380, top + 36, 64, 20).build());

        NpcItemPriceCatalogDataPayload.Entry selected = selected();
        buyBox = new EditBox(font, left + 218, top + H - 57, 104, 20, Component.literal("Player buys/item"));
        sellBox = new EditBox(font, left + 330, top + H - 57, 104, 20, Component.literal("Player sells/item"));
        buyBox.setMaxLength(32); sellBox.setMaxLength(32);
        buyBox.setValue(selected == null ? "0" : formatMoney(selected.buyPriceMinor()));
        sellBox.setValue(selected == null ? "0" : formatMoney(selected.sellPriceMinor()));
        addRenderableWidget(buyBox); addRenderableWidget(sellBox);
        Button save = addRenderableWidget(Button.builder(Component.literal("Save prices"), button -> save())
                .bounds(left + 442, top + H - 57, 86, 20).build());
        Button clear = addRenderableWidget(Button.builder(Component.literal("Clear"), button -> clear())
                .bounds(left + 536, top + H - 57, 64, 20).build());
        save.active = selected != null; clear.active = selected != null;

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> request(data.pageIndex() - 1))
                .bounds(left + 14, top + H - 28, 28, 18).build());
        previous.active = data.pageIndex() > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> request(data.pageIndex() + 1))
                .bounds(left + 46, top + H - 28, 28, 18).build());
        next.active = data.pageIndex() + 1 < data.pageCount();
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + W - 80, top + H - 28, 66, 18).build());
    }

    private void request(int page) {
        ClientPacketDistributor.sendToServer(new NpcItemPriceCatalogRequestPayload(
                searchBox == null ? data.query() : searchBox.getValue(), Math.max(0, page), nextRequestId++));
    }

    private void save() {
        NpcItemPriceCatalogDataPayload.Entry entry = selected();
        if (entry == null) return;
        try {
            long buy = parseMoney(buyBox.getValue());
            long sell = parseMoney(sellBox.getValue());
            ClientPacketDistributor.sendToServer(new NpcItemPriceCatalogActionPayload(entry.itemId(), buy, sell,
                    searchBox == null ? data.query() : searchBox.getValue(), data.pageIndex(), nextRequestId++));
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage(); noticeError = true;
        }
    }

    private void clear() {
        if (buyBox != null) buyBox.setValue("0");
        if (sellBox != null) sellBox.setValue("0");
        save();
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = left(), top = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000);
        g.fill(left, top, left + W, top + H, PANEL);
        g.outline(left, top, W, H, BORDER);
        g.text(font, "Item Price Catalog", left + 16, top + 14, TEXT, true);
        g.text(font, data.totalItems() + " registered item(s)", left + W - 158, top + 14, MUTED, false);
        int listTop = top + 64, listBottom = top + H - 68;
        g.fill(left + 12, listTop, left + W - 12, listBottom, SUBPANEL);
        g.outline(left + 12, listTop, W - 24, listBottom - listTop, BORDER);
        g.text(font, "Item", left + 40, listTop + 4, MUTED, false);
        g.text(font, "Registry ID", left + 190, listTop + 4, MUTED, false);
        g.text(font, "Buys", left + 430, listTop + 4, MUTED, false);
        g.text(font, "Sells", left + 510, listTop + 4, MUTED, false);
        rows.clear();
        for (int index = 0; index < data.entries().size(); index++) {
            int y = listTop + 16 + index * ROW_HEIGHT;
            RowBounds row = new RowBounds(index, left + 15, y, W - 30, ROW_HEIGHT - 1);
            rows.add(row);
            var entry = data.entries().get(index);
            g.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), index == selectedIndex ? SELECTED : ROW);
            ItemStack stack = item(entry.itemId());
            if (!stack.isEmpty()) g.item(stack, row.x(), row.y() - 1);
            g.text(font, trim(entry.displayName(), 22), row.x() + 22, row.y() + 3, TEXT, false);
            g.text(font, trim(entry.itemId(), 31), row.x() + 174, row.y() + 3, MUTED, false);
            g.text(font, entry.buyPriceMinor() <= 0 ? "Off" : money(entry.buyPriceMinor()), row.x() + 414, row.y() + 3,
                    entry.buyPriceMinor() > 0 ? GOOD : MUTED, false);
            g.text(font, entry.sellPriceMinor() <= 0 ? "Off" : money(entry.sellPriceMinor()), row.x() + 494, row.y() + 3,
                    entry.sellPriceMinor() > 0 ? GOOD : MUTED, false);
        }
        if (data.entries().isEmpty()) g.text(font, "No registered items match this search.", left + 28, listTop + 30, MUTED, false);
        g.text(font, "Player buys/item", left + 218, top + H - 68, MUTED, false);
        g.text(font, "Player sells/item", left + 330, top + H - 68, MUTED, false);
        g.text(font, "Page " + (data.pageIndex() + 1) + "/" + data.pageCount(), left + 84, top + H - 24, MUTED, false);
        if (!notice.isBlank()) g.text(font, trim(notice, 54), left + 158, top + H - 24, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (RowBounds row : rows) {
            if (row.contains((int) event.x(), (int) event.y())) {
                selectedIndex = row.index();
                notice = "";
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private NpcItemPriceCatalogDataPayload.Entry selected() {
        return selectedIndex >= 0 && selectedIndex < data.entries().size() ? data.entries().get(selectedIndex) : null;
    }

    private int indexOf(String itemId) {
        for (int index = 0; index < data.entries().size(); index++) if (data.entries().get(index).itemId().equals(itemId)) return index;
        return -1;
    }

    private ItemStack item(String rawId) {
        try {
            return BuiltInRegistries.ITEM.getOptional(Identifier.parse(rawId))
                    .map(item -> item.getDefaultInstance()).orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) { return ItemStack.EMPTY; }
    }

    private long parseMoney(String raw) {
        String value = raw == null ? "" : raw.trim().replace(" ", "").replace(data.currencySymbol(), "");
        if (value.isBlank()) value = "0";
        int comma = value.lastIndexOf(','), dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            value = comma > dot ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
        } else if (comma >= 0) {
            value = value.replace(',', '.');
        }
        try {
            BigDecimal decimal = new BigDecimal(value);
            if (decimal.signum() < 0) throw new IllegalArgumentException("Prices cannot be negative.");
            return decimal.movePointRight(data.decimalPlaces()).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid price with at most " + data.decimalPlaces() + " decimal places.");
        }
    }

    private String formatMoney(long minor) {
        return BigDecimal.valueOf(Math.max(0L, minor), data.decimalPlaces()).toPlainString().replace('.', ',');
    }

    private String money(long minor) {
        return data.currencySymbol().isBlank() ? formatMoney(minor) : data.currencySymbol() + " " + formatMoney(minor);
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
