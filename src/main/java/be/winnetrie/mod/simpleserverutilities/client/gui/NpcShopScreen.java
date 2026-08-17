package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.NpcShopActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Click-only NPC shop: shop slots buy, inventory slots sell, and recent sales can be bought back. */
public final class NpcShopScreen extends Screen {
    private static final int WIDTH = 370;
    private static final int HEIGHT = 242;
    private static final int SLOT = 18;
    private static final int PANEL = 0xF0141920;
    private static final int SUBPANEL = 0xC01C2630;
    private static final int BORDER = 0xFF596B79;
    private static final int SLOT_BACKGROUND = 0xD00B1015;
    private static final int SLOT_HOVER = 0xD03C5364;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFBE72;
    private static final int ERROR = 0xFFFF8585;

    private NpcShopDataPayload data;
    private long nextRequestId;
    private String notice;
    private boolean noticeError;
    private boolean awaiting;
    private Tab tab = Tab.SHOP;
    private Button shopTabButton;
    private Button buybackTabButton;
    private Button previousButton;
    private Button nextButton;
    private final List<SlotBounds> shopSlots = new ArrayList<>();
    private final List<SlotBounds> inventorySlots = new ArrayList<>();
    private final List<SlotBounds> buybackSlots = new ArrayList<>();

    public NpcShopScreen(NpcShopDataPayload initial) {
        super(Component.literal(initial.shopName().isBlank() ? "NPC Shop" : initial.shopName()));
        data = initial;
        nextRequestId = Math.max(1L, initial.requestId() + 1L);
        notice = initial.notice();
        noticeError = initial.error();
    }

    public void acceptData(NpcShopDataPayload updated) {
        if (updated == null || updated.requestId() < data.requestId()) return;
        data = updated;
        nextRequestId = Math.max(nextRequestId, updated.requestId() + 1L);
        notice = updated.notice();
        noticeError = updated.error();
        awaiting = false;
        rebuildWidgets();
    }

    @Override protected void init() {
        shopSlots.clear();
        inventorySlots.clear();
        buybackSlots.clear();
        int left = left();
        int top = top();
        if (!data.accessAllowed()) {
            addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                    .bounds(left + WIDTH - 70, top + HEIGHT - 27, 58, 18).build());
            return;
        }
        shopTabButton = addRenderableWidget(Button.builder(Component.literal("Buy / Sell"), button -> switchTab(Tab.SHOP))
                .bounds(left + 12, top + 27, 102, 18).build());
        buybackTabButton = addRenderableWidget(Button.builder(Component.literal("Buy-back"), button -> switchTab(Tab.BUYBACK))
                .bounds(left + 118, top + 27, 92, 18).build());
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> request(data.pageIndex() - 1))
                .bounds(left + 270, top + 47, 24, 16).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> request(data.pageIndex() + 1))
                .bounds(left + 297, top + 47, 24, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↻"), button -> request(data.pageIndex()))
                .bounds(left + WIDTH - 72, top + 6, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(left + WIDTH - 42, top + 6, 30, 18).build());
        updateButtons();
    }

    private void switchTab(Tab target) {
        if (tab == target) return;
        tab = target;
        notice = "";
        updateButtons();
    }

    private void updateButtons() {
        if (shopTabButton != null) shopTabButton.active = tab != Tab.SHOP;
        if (buybackTabButton != null) buybackTabButton.active = tab != Tab.BUYBACK;
        boolean shop = tab == Tab.SHOP;
        if (previousButton != null) previousButton.active = shop && !awaiting && data.pageIndex() > 0;
        if (nextButton != null) nextButton.active = shop && !awaiting && hasNextPage();
    }

    private void request(int page) {
        if (awaiting) return;
        awaiting = true;
        updateButtons();
        long request = nextRequestId++;
        ClientPacketDistributor.sendToServer(new NpcShopRequestPayload(
                data.instanceId(), data.shopId(), Math.max(0, page), request));
    }

    private void action(String action, String entryId, int inventorySlot) {
        if (awaiting) return;
        awaiting = true;
        updateButtons();
        long request = nextRequestId++;
        ClientPacketDistributor.sendToServer(new NpcShopActionPayload(action, data.instanceId(), data.shopId(),
                entryId, Math.max(0, inventorySlot), data.pageIndex(), request));
    }

    @Override public void onClose() {
        ClientPacketDistributor.sendToServer(new NpcShopActionPayload("close", data.instanceId(), data.shopId(),
                "", 0, data.pageIndex(), nextRequestId++));
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = left();
        int top = top();
        SsuGuiScale.fullscreenDim(graphics, this, 0xA9000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.outline(left, top, WIDTH, HEIGHT, BORDER);
        graphics.text(font, trim(data.shopName().isBlank() ? "NPC Shop" : data.shopName(), 22),
                left + 12, top + 9, TEXT, true);
        graphics.text(font, trim(data.formattedBalance(), 20), left + 174, top + 10, GOOD, false);

        if (!data.accessAllowed()) {
            graphics.text(font, trim(data.notice(), 52), left + 18, top + 62, ERROR, false);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.fill(left + 8, top + 48, left + WIDTH - 8, top + HEIGHT - 6, SUBPANEL);
        graphics.outline(left + 8, top + 48, WIDTH - 16, HEIGHT - 54, BORDER);
        if (tab == Tab.SHOP) renderShop(graphics, mouseX, mouseY);
        else renderBuyback(graphics, mouseX, mouseY);
        renderInventory(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderShop(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = left();
        int top = top();
        int gridX = left + 18;
        int gridY = top + 65;
        graphics.text(font, "Shop items", gridX, top + 52, TEXT, false);
        int pages = Math.max(1, (data.totalEntries() + data.pageSize() - 1) / data.pageSize());
        graphics.text(font, "Page " + (data.pageIndex() + 1) + "/" + pages, left + 250, top + 52, MUTED, false);
        shopSlots.clear();
        for (int index = 0; index < NpcShopDataPayload.MAX_ENTRIES; index++) {
            int x = gridX + (index % 9) * SLOT;
            int y = gridY + (index / 9) * SLOT;
            SlotBounds bounds = new SlotBounds(index, x, y);
            shopSlots.add(bounds);
            NpcShopDataPayload.Entry entry = index < data.entries().size() ? data.entries().get(index) : null;
            drawItemSlot(graphics, bounds, entry == null ? ItemStack.EMPTY : entry.item(), mouseX, mouseY,
                    entry != null && entry.canBuy());
        }
        NpcShopDataPayload.Entry hovered = hoveredShop(mouseX, mouseY);
        int hoveredInventorySlot = inventorySlotAt(mouseX, mouseY);
        NpcShopDataPayload.InventorySaleQuote saleQuote = saleQuote(hoveredInventorySlot);
        ItemStack hoveredInventoryItem = clientInventoryItem(hoveredInventorySlot);
        if (hovered != null) {
            int offeredCount = Math.max(1, hovered.item().getCount());
            graphics.text(font, trim(hovered.name(), 30), gridX, top + 104, TEXT, false);
            String stock = hovered.stock() < 0 ? "Infinite stock" : "Stock: " + hovered.stock() + "/" + hovered.maxStock();
            graphics.text(font, trim(stock, 22), gridX + 196, top + 104, hovered.stock() == 0 ? WARNING : MUTED, false);
            long stackPrice = safeMultiply(hovered.buyPriceMinor(), offeredCount);
            boolean canAffordOne = data.balanceMinor() >= hovered.buyPriceMinor();
            boolean canAffordStack = data.balanceMinor() >= stackPrice;
            graphics.text(font, "Right-click 1: " + hovered.formattedBuyPrice(), gridX, top + 116,
                    !hovered.canBuy() ? MUTED : canAffordOne ? GOOD : ERROR, false);
            graphics.text(font, "Left-click " + offeredCount + ": " + hovered.formattedStackBuyPrice(), gridX, top + 128,
                    !hovered.canBuy() ? MUTED : canAffordStack ? GOOD : ERROR, false);
        } else if (!hoveredInventoryItem.isEmpty()) {
            graphics.text(font, trim(hoveredInventoryItem.getHoverName().getString(), 42), gridX, top + 104, TEXT, false);
            if (saleQuote != null && saleQuote.canSell()) {
                graphics.text(font, "Sell/item: " + saleQuote.formattedUnitPrice(), gridX, top + 116, GOOD, false);
                graphics.text(font, "Left-click sells the stack; right-click sells one.", gridX, top + 128, MUTED, false);
            } else {
                String reason = saleQuote == null ? "Waiting for refreshed shop price data."
                        : saleQuote.reason();
                graphics.text(font, trim(reason, 52), gridX, top + 116, WARNING, false);
            }
        } else {
            graphics.text(font, "Shop: left-click stack, right-click one item.", gridX, top + 108, MUTED, false);
            graphics.text(font, "Inventory: left-click stack, right-click one item to sell.", gridX, top + 122, MUTED, false);
        }
        renderNotice(graphics, gridX, top + 139);
    }

    private void renderBuyback(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = left();
        int top = top();
        int gridX = left + 18;
        int gridY = top + 65;
        graphics.text(font, "Recent sales", gridX, top + 52, TEXT, false);
        buybackSlots.clear();
        for (int index = 0; index < NpcShopDataPayload.MAX_BUYBACK_ENTRIES; index++) {
            int x = gridX + index * SLOT;
            SlotBounds bounds = new SlotBounds(index, x, gridY);
            buybackSlots.add(bounds);
            NpcShopDataPayload.BuybackEntry entry = index < data.buybackEntries().size()
                    ? data.buybackEntries().get(index) : null;
            drawItemSlot(graphics, bounds, entry == null ? ItemStack.EMPTY : entry.item(), mouseX, mouseY, entry != null);
        }
        NpcShopDataPayload.BuybackEntry hovered = hoveredBuyback(mouseX, mouseY);
        if (hovered != null) {
            graphics.text(font, trim(hovered.name(), 42), gridX, top + 88, TEXT, false);
            graphics.text(font, "Right-click 1: " + hovered.formattedUnitPrice(), gridX, top + 102, GOOD, false);
            graphics.text(font, "Left-click buys the shown stack • expires in "
                    + timeLeft(hovered.expiresAtEpochMilli()), gridX, top + 116, WARNING, false);
        } else if (data.buybackEntries().isEmpty()) {
            graphics.text(font, "No recent sales are available for buy-back.", gridX, top + 92, MUTED, false);
        } else {
            graphics.text(font, "Left-click buys back the shown stack; right-click buys back one.",
                    gridX, top + 96, MUTED, false);
        }
        renderNotice(graphics, gridX, top + 139);
    }

    private void renderInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = left();
        int top = top();
        int startX = left + 18;
        int startY = top + 164;
        graphics.text(font, tab == Tab.SHOP ? "Your inventory — click an item to sell" : "Your inventory",
                startX, top + 151, TEXT, false);
        inventorySlots.clear();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = 9 + row * 9 + column;
                SlotBounds bounds = new SlotBounds(slot, startX + column * SLOT, startY + row * SLOT);
                inventorySlots.add(bounds);
                NpcShopDataPayload.InventorySaleQuote quote = saleQuote(slot);
                drawItemSlot(graphics, bounds, clientInventoryItem(slot), mouseX, mouseY,
                        tab == Tab.SHOP && quote != null && quote.canSell());
            }
        }
        int hotbarY = startY + 57;
        for (int column = 0; column < 9; column++) {
            int slot = column;
            SlotBounds bounds = new SlotBounds(slot, startX + column * SLOT, hotbarY);
            inventorySlots.add(bounds);
            NpcShopDataPayload.InventorySaleQuote quote = saleQuote(slot);
            drawItemSlot(graphics, bounds, clientInventoryItem(slot), mouseX, mouseY,
                    tab == Tab.SHOP && quote != null && quote.canSell());
        }
    }

    private void drawItemSlot(GuiGraphicsExtractor graphics, SlotBounds bounds, ItemStack stack,
                              int mouseX, int mouseY, boolean enabled) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        graphics.fill(bounds.x, bounds.y, bounds.x + SLOT, bounds.y + SLOT,
                hovered && enabled ? SLOT_HOVER : SLOT_BACKGROUND);
        graphics.outline(bounds.x, bounds.y, SLOT, SLOT, enabled ? BORDER : 0xFF39444D);
        if (stack != null && !stack.isEmpty()) {
            graphics.item(stack, bounds.x + 1, bounds.y + 1);
            graphics.itemDecorations(font, stack, bounds.x + 1, bounds.y + 1);
            if (hovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private void renderNotice(GuiGraphicsExtractor graphics, int x, int y) {
        if (!notice.isBlank()) graphics.text(font, trim(notice, 52), x, y, noticeError ? ERROR : GOOD, false);
        else if (awaiting) graphics.text(font, "Processing transaction…", x, y, MUTED, false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int button = event.buttonInfo().button();
        if (awaiting || (button != 0 && button != 1)) return false;
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (tab == Tab.SHOP) {
            for (SlotBounds bounds : shopSlots) {
                if (!bounds.contains(mouseX, mouseY) || bounds.index >= data.entries().size()) continue;
                NpcShopDataPayload.Entry entry = data.entries().get(bounds.index);
                if (!entry.canBuy()) {
                    notice = "That shop item cannot currently be purchased.";
                    noticeError = true;
                    return true;
                }
                action(button == 0 ? "buy_stack" : "buy_one", entry.id(), 0);
                return true;
            }
            for (SlotBounds bounds : inventorySlots) {
                if (!bounds.contains(mouseX, mouseY)) continue;
                ItemStack stack = clientInventoryItem(bounds.index);
                if (stack.isEmpty()) return true;
                NpcShopDataPayload.InventorySaleQuote quote = saleQuote(bounds.index);
                if (quote == null || !quote.canSell()) {
                    notice = quote == null ? "Waiting for refreshed shop price data." : quote.reason();
                    noticeError = true;
                    return true;
                }
                action(button == 0 ? "sell_stack" : "sell_one", "", bounds.index);
                return true;
            }
        } else {
            for (SlotBounds bounds : buybackSlots) {
                if (!bounds.contains(mouseX, mouseY) || bounds.index >= data.buybackEntries().size()) continue;
                NpcShopDataPayload.BuybackEntry entry = data.buybackEntries().get(bounds.index);
                action(button == 0 ? "buyback_stack" : "buyback_one", entry.id(), 0);
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.SHOP && !awaiting && scrollY != 0.0D) {
            int target = data.pageIndex() + (scrollY < 0.0D ? 1 : -1);
            if (target >= 0 && target < pageCount() && target != data.pageIndex()) request(target);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private NpcShopDataPayload.Entry hoveredShop(int mouseX, int mouseY) {
        for (SlotBounds bounds : shopSlots) {
            if (bounds.contains(mouseX, mouseY) && bounds.index < data.entries().size()) return data.entries().get(bounds.index);
        }
        return null;
    }

    private NpcShopDataPayload.BuybackEntry hoveredBuyback(int mouseX, int mouseY) {
        for (SlotBounds bounds : buybackSlots) {
            if (bounds.contains(mouseX, mouseY) && bounds.index < data.buybackEntries().size()) {
                return data.buybackEntries().get(bounds.index);
            }
        }
        return null;
    }


    private NpcShopDataPayload.InventorySaleQuote saleQuote(int inventorySlot) {
        if (inventorySlot < 0) return null;
        for (NpcShopDataPayload.InventorySaleQuote quote : data.inventorySaleQuotes()) {
            if (quote.inventorySlot() == inventorySlot) return quote;
        }
        return null;
    }

    private int inventorySlotAt(int mouseX, int mouseY) {
        int startX = left() + 18;
        int startY = top() + 164;
        if (mouseX < startX || mouseX >= startX + 9 * SLOT) return -1;
        int column = (mouseX - startX) / SLOT;
        for (int row = 0; row < 3; row++) {
            int y = startY + row * SLOT;
            if (mouseY >= y && mouseY < y + SLOT) return 9 + row * 9 + column;
        }
        int hotbarY = startY + 57;
        if (mouseY >= hotbarY && mouseY < hotbarY + SLOT) return column;
        return -1;
    }

    private ItemStack clientInventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private boolean hasNextPage() { return data.pageIndex() + 1 < pageCount(); }
    private int pageCount() { return Math.max(1, (data.totalEntries() + data.pageSize() - 1) / data.pageSize()); }
    private int left() { return Math.max(0, (width - WIDTH) / 2); }
    private int top() { return Math.max(0, (height - HEIGHT) / 2); }

    private static long safeMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier <= 0) return 0L;
        try { return Math.multiplyExact(value, (long) multiplier); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private static String timeLeft(long expiresAt) {
        long seconds = Math.max(0L, (expiresAt - System.currentTimeMillis() + 999L) / 1_000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes + ":" + (remainder < 10L ? "0" : "") + remainder;
    }

    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, Math.max(0, maximum - 1)) + "…";
    }

    private enum Tab { SHOP, BUYBACK }

    private static final class SlotBounds {
        private final int index;
        private final int x;
        private final int y;

        private SlotBounds(int index, int x, int y) {
            this.index = index;
            this.x = x;
            this.y = y;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
        }
    }
}
