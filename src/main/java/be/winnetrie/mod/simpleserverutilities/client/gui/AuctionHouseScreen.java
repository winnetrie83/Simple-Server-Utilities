package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.auction.AuctionCategory;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Searchable, categorized and paged Auction House browser. */
public final class AuctionHouseScreen extends Screen {
    private static final int PANEL = 0xF0141920;
    private static final int SUBPANEL = 0xA018222B;
    private static final int BORDER = 0xFF596B79;
    private static final int ROW = 0xC024303A;
    private static final int ROW_SELECTED = 0xE03C5364;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFBE72;
    private static final int ERROR = 0xFFFF8585;
    private static final int PAGE_SIZE = 8;

    private AuctionHouseDataPayload data;
    private long nextRequestId;
    private int selectedIndex = -1;
    private EditBox searchBox;
    private EditBox buyQuantity;
    private EditBox blacklistIdBox;
    private String blacklistIdDraft = "";
    private Button addInventoryBlacklistButton;
    private int selectedInventorySlot = -1;
    private String adminDialogAction = "";
    private String adminDialogTarget = "";
    private String adminReasonDraft = "";
    private EditBox adminReasonBox;
    private boolean adminActionPending;
    private boolean buyDialog;
    private boolean purchasePending;
    private String notice = "";
    private boolean noticeError;
    private final List<RowBounds> rows = new ArrayList<>();

    public AuctionHouseScreen(AuctionHouseDataPayload initial) {
        super(Component.literal("Auction House"));
        data = initial;
        nextRequestId = Math.max(1L, initial.requestId() + 1L);
        notice = initial.notice();
        noticeError = initial.error();
    }

    public void acceptData(AuctionHouseDataPayload updated) {
        if (updated == null || updated.requestId() < data.requestId()) return;
        String selectedId = selected() == null ? "" : selected().id();
        data = updated;
        clearAdminDialogState();
        if (!"blacklist".equals(updated.mode())) {
            selectedInventorySlot = -1;
            blacklistIdDraft = "";
        }
        nextRequestId = Math.max(nextRequestId, updated.requestId() + 1L);
        selectedIndex = indexOf(selectedId);
        if (selectedIndex < 0 && !data.entries().isEmpty()) selectedIndex = 0;
        notice = updated.notice();
        noticeError = updated.error();
        buyDialog = false;
        purchasePending = false;
        rebuildWidgets();
    }

    public void acceptResult(AuctionHouseActionResultPayload result) {
        if (result == null) return;
        nextRequestId = Math.max(nextRequestId, result.requestId() + 1L);
        notice = result.message();
        noticeError = !result.successful();
        purchasePending = false;
        adminActionPending = false;
        boolean closedAdminDialog = hasAdminDialog() && result.successful();
        if (closedAdminDialog) {
            clearAdminDialogState();
            rebuildWidgets();
        }
        if (result.playPurchaseSound()) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
        }
        if (result.refresh()) request(data.mode(), data.category(), data.search(), data.sort(), data.pageIndex());
        else if (!closedAdminDialog) rebuildWidgets();
    }

    @Override
    protected void init() {
        rows.clear();
        blacklistIdBox = null;
        addInventoryBlacklistButton = null;
        adminReasonBox = null;
        Layout l = layout();
        if (hasAdminDialog()) {
            int modalRowHeight = Math.max(32, (l.listBottom() - l.listTop()) / PAGE_SIZE);
            for (int i = 0; i < data.entries().size(); i++) {
                rows.add(new RowBounds(i, l.contentLeft(), l.listTop() + i * modalRowHeight,
                        l.contentWidth(), modalRowHeight - 3));
            }
            addAdminReasonDialog();
            return;
        }
        int top = l.top() + 32;
        int tabX = l.contentLeft();
        addRenderableWidget(Button.builder(Component.literal("Browse"), ignored -> request("browse", "all", "", data.sort(), 0))
                .bounds(tabX, top, 56, 20).build()).active = !"browse".equals(data.mode());
        tabX += 60;
        addRenderableWidget(Button.builder(Component.literal("My Auctions"), ignored -> request("my", "all", "", "time_asc", 0))
                .bounds(tabX, top, 76, 20).build()).active = !"my".equals(data.mode());
        tabX += 80;
        Button sell = Button.builder(Component.literal("Sell"), ignored -> action("open_sell", "", 0, "", 0))
                .bounds(tabX, top, 44, 20).build();
        sell.active = data.canCreate();
        addRenderableWidget(sell);
        tabX += 48;
        if (data.administrator()) {
            addRenderableWidget(Button.builder(Component.literal("Admin"), ignored ->
                            request("admin", "all", "", "time_asc", 0))
                    .bounds(tabX, top, 52, 20).build()).active = !"admin".equals(data.mode());
            tabX += 56;
            addRenderableWidget(Button.builder(Component.literal("Blacklist"), ignored ->
                            request("blacklist", "all", "", "name_asc", 0))
                    .bounds(tabX, top, 64, 20).build()).active = !"blacklist".equals(data.mode());
        }
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> refresh())
                .bounds(l.right() - 148, top, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(l.right() - 78, top, 66, 20).build());

        int searchY = top + 26;
        if (isBlacklistMode()) {
            int searchWidth = Math.max(100, (int)Math.floor((l.contentWidth() - 118) * 0.75D));
            int controlsX = l.contentLeft() + searchWidth + 8;
            searchBox = new EditBox(font, l.contentLeft(), searchY, searchWidth, 20,
                    Component.literal("Search blacklisted item name"));
            searchBox.setHint(Component.literal("Search blacklisted items…"));
            searchBox.setMaxLength(96);
            searchBox.setValue(data.search());
            addRenderableWidget(searchBox);
            addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> search())
                    .bounds(controlsX, searchY, 58, 20).build());
            addRenderableWidget(sortButton("Name", "name", controlsX + 62, searchY, 52));
        } else {
            int searchWidth = Math.max(100, (int)Math.floor((l.contentWidth() - 252) * 0.75D));
            int controlsX = l.contentLeft() + searchWidth + 8;
            searchBox = new EditBox(font, l.contentLeft(), searchY, searchWidth, 20,
                    Component.literal("Search item name"));
            searchBox.setHint(Component.literal("Search by item name…"));
            searchBox.setMaxLength(96);
            searchBox.setValue(data.search());
            addRenderableWidget(searchBox);
            addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> search())
                    .bounds(controlsX, searchY, 58, 20).build());
            addRenderableWidget(sortButton("Name", "name", controlsX + 62, searchY, 42));
            addRenderableWidget(sortButton("Qty", "quantity", controlsX + 108, searchY, 40));
            addRenderableWidget(sortButton("Price", "price", controlsX + 152, searchY, 44));
            addRenderableWidget(sortButton("Time", "time", controlsX + 200, searchY, 44));
        }

        int rowHeight = Math.max(32, (l.listBottom() - l.listTop()) / PAGE_SIZE);
        for (int i = 0; i < data.entries().size(); i++) {
            rows.add(new RowBounds(i, l.contentLeft(), l.listTop() + i * rowHeight,
                    l.contentWidth(), rowHeight - 3));
        }

        int footer = l.bottom() - 27;
        Button previous = Button.builder(Component.literal("< Previous"), ignored -> requestPage(data.pageIndex() - 1))
                .bounds(l.contentLeft(), footer, 84, 20).build();
        previous.active = data.pageIndex() > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal("Next >"), ignored -> requestPage(data.pageIndex() + 1))
                .bounds(l.contentLeft() + 88, footer, 66, 20).build();
        next.active = data.pageIndex() + 1 < pageCount();
        addRenderableWidget(next);

        AuctionHouseDataPayload.Entry selected = selected();
        if (selected != null) {
            if (isBlacklistMode()) {
                addRenderableWidget(Button.builder(Component.literal("Remove from blacklist"), ignored ->
                                action("unblacklist", selected.id(), 0, "", 0))
                        .bounds(l.contentRight() - 154, footer, 154, 20).build());
            } else if (isAdminMode()) {
                addRenderableWidget(Button.builder(Component.literal("Blacklist"), ignored ->
                                action("blacklist_listing", selected.id(), 0, "", 0))
                        .bounds(l.contentRight() - 272, footer, 88, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Admin cancel"), ignored ->
                                openAdminReasonDialog("admin_cancel", selected))
                        .bounds(l.contentRight() - 180, footer, 88, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Seize"), ignored ->
                                openAdminReasonDialog("seize", selected))
                        .bounds(l.contentRight() - 88, footer, 88, 20).build());
            } else if ("my".equals(data.mode()) || selected.ownAuction()) {
                addRenderableWidget(Button.builder(Component.literal("Cancel auction"), ignored ->
                                action("cancel", selected.id(), 0, "", 0))
                        .bounds(l.contentRight() - 116, footer, 116, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("Buy"), ignored -> openBuyDialog())
                        .bounds(l.contentRight() - 72, footer, 72, 20).build());
            }
        }

        if (isBlacklistMode()) addBlacklistControls(l);
        if (buyDialog && selected != null && !selected.ownAuction() && !isAdminMode() && !isBlacklistMode()) {
            addBuyDialog(l, selected);
        }
    }

    private void addBlacklistControls(Layout l) {
        int x = l.left() + 12;
        int controlWidth = l.categoryWidth() - 24;
        addInventoryBlacklistButton = Button.builder(Component.literal("Add selected item"), ignored -> {
                    if (selectedInventorySlot < 0) {
                        notice = "Select a non-empty inventory slot first.";
                        noticeError = true;
                        return;
                    }
                    action("blacklist_inventory", Integer.toString(selectedInventorySlot), 0, "", 0);
                })
                .bounds(x, l.top() + 166, controlWidth, 20).build();
        addInventoryBlacklistButton.active = !clientInventoryItem(selectedInventorySlot).isEmpty();
        addRenderableWidget(addInventoryBlacklistButton);

        blacklistIdBox = new EditBox(font, x, l.top() + 207, controlWidth, 20,
                Component.literal("Registered item ID"));
        blacklistIdBox.setHint(Component.literal("minecraft:diamond"));
        blacklistIdBox.setMaxLength(256);
        blacklistIdBox.setValue(blacklistIdDraft);
        blacklistIdBox.setResponder(value -> blacklistIdDraft = value);
        addRenderableWidget(blacklistIdBox);
        addRenderableWidget(Button.builder(Component.literal("Add item ID"), ignored ->
                        action("blacklist_id", blacklistIdBox.getValue(), 0, "", 0))
                .bounds(x, l.top() + 258, controlWidth, 20).build());
    }

    private void openAdminReasonDialog(String action, AuctionHouseDataPayload.Entry selected) {
        if (selected == null) return;
        adminDialogAction = action == null ? "" : action;
        adminDialogTarget = selected.id();
        adminReasonDraft = "";
        adminActionPending = false;
        notice = "";
        noticeError = false;
        rebuildWidgets();
    }

    private boolean hasAdminDialog() {
        return !adminDialogAction.isBlank() && !adminDialogTarget.isBlank();
    }

    private void addAdminReasonDialog() {
        int dialogWidth = 380;
        int dialogHeight = 150;
        int x = (width - dialogWidth) / 2;
        int y = (height - dialogHeight) / 2;
        adminReasonBox = new EditBox(font, x + 18, y + 55, dialogWidth - 36, 20,
                Component.literal("Administrative reason"));
        adminReasonBox.setHint(Component.literal("Reason is required…"));
        adminReasonBox.setMaxLength(200);
        adminReasonBox.setValue(adminReasonDraft);
        adminReasonBox.setResponder(value -> adminReasonDraft = value);
        addRenderableWidget(adminReasonBox);
        setInitialFocus(adminReasonBox);
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> {
                    clearAdminDialogState();
                    rebuildWidgets();
                }).bounds(x + 18, y + 105, 80, 20).build());
        String label = adminActionPending ? "Processing…"
                : "seize".equals(adminDialogAction) ? "Seize items" : "Cancel auction";
        Button confirm = Button.builder(Component.literal(label), ignored -> confirmAdminAction())
                .bounds(x + dialogWidth - 138, y + 105, 120, 20).build();
        confirm.active = !adminActionPending;
        addRenderableWidget(confirm);
    }

    private void confirmAdminAction() {
        if (adminActionPending || !hasAdminDialog()) return;
        String reason = adminReasonBox == null ? adminReasonDraft : adminReasonBox.getValue();
        reason = reason == null ? "" : reason.trim();
        adminReasonDraft = reason;
        if (reason.isBlank()) {
            notice = "Enter a reason before continuing.";
            noticeError = true;
            return;
        }
        adminActionPending = true;
        action(adminDialogAction, adminDialogTarget, 0, reason, 0);
        rebuildWidgets();
    }

    private void clearAdminDialogState() {
        adminDialogAction = "";
        adminDialogTarget = "";
        adminReasonDraft = "";
        adminReasonBox = null;
        adminActionPending = false;
    }

    private Button sortButton(String label, String key, int x, int y, int width) {
        String suffix = data.sort().equals(key + "_asc") ? " ↑" : data.sort().equals(key + "_desc") ? " ↓" : "";
        return Button.builder(Component.literal(label + suffix), ignored -> sort(key)).bounds(x, y, width, 20).build();
    }

    private void addBuyDialog(Layout l, AuctionHouseDataPayload.Entry selected) {
        int width = 260;
        int x = (this.width - width) / 2;
        int y = (this.height - 126) / 2;
        buyQuantity = new EditBox(font, x + 18, y + 53, width - 36, 20, Component.literal("Quantity"));
        buyQuantity.setHint(Component.literal("Quantity (1–" + selected.quantity() + ")"));
        buyQuantity.setMaxLength(9);
        buyQuantity.setValue("1");
        addRenderableWidget(buyQuantity);
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> { buyDialog = false; rebuildWidgets(); })
                .bounds(x + 18, y + 88, 72, 20).build());
        Button confirm = Button.builder(Component.literal(purchasePending ? "Processing…" : "Buy now"),
                        ignored -> confirmBuy(selected))
                .bounds(x + width - 100, y + 88, 82, 20).build();
        confirm.active = !purchasePending;
        addRenderableWidget(confirm);
    }

    private void openBuyDialog() {
        buyDialog = true;
        rebuildWidgets();
    }

    private void confirmBuy(AuctionHouseDataPayload.Entry selected) {
        if (purchasePending) return;
        try {
            int quantity = Integer.parseInt(buyQuantity.getValue().trim());
            if (quantity < 1 || quantity > selected.quantity()) throw new NumberFormatException();
            purchasePending = true;
            action("buy", selected.id(), quantity, "", 0);
            rebuildWidgets();
        } catch (NumberFormatException exception) {
            notice = "Enter a quantity between 1 and " + selected.quantity() + ".";
            noticeError = true;
        }
    }

    private void search() {
        request(data.mode(), data.category(), searchBox.getValue(), data.sort(), 0);
    }

    private void sort(String key) {
        String next = data.sort().equals(key + "_asc") ? key + "_desc" : key + "_asc";
        request(data.mode(), data.category(), searchBox == null ? data.search() : searchBox.getValue(), next, 0);
    }

    private void refresh() {
        request(data.mode(), data.category(), data.search(), data.sort(), data.pageIndex());
    }

    private void requestPage(int page) {
        request(data.mode(), data.category(), data.search(), data.sort(), Math.max(0, page));
    }

    private void request(String mode, String category, String search, String sort, int page) {
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new AuctionHouseRequestPayload(mode, category, search, sort,
                Math.max(0, page), PAGE_SIZE, id));
    }

    private void action(String action, String target, int quantity, String value, int duration) {
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new AuctionHouseActionPayload(action, target, quantity, value, duration, id));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        g.fill(0, 0, width, height, 0xA9000000);
        g.fill(l.left(), l.top(), l.right(), l.bottom(), PANEL);
        g.outline(l.left(), l.top(), l.width(), l.height(), BORDER);
        String title = isBlacklistMode() ? "Auction House — Blacklist"
                : isAdminMode() ? "Auction House — Admin Overview" : "Auction House";
        g.text(font, title, l.left() + 10, l.top() + 10, TEXT, false);
        g.text(font, "Balance: " + data.formattedBalance(), l.contentLeft(), l.top() + 11, GOOD, false);
        g.text(font, "Active: " + data.activeAuctions() + "/" + data.maxAuctions(), l.contentRight() - 112,
                l.top() + 11, MUTED, false);

        drawCategories(g, l, mouseX, mouseY);
        g.fill(l.contentLeft() - 3, l.listTop() - 3, l.contentRight() + 3, l.listBottom(), SUBPANEL);
        g.outline(l.contentLeft() - 3, l.listTop() - 3, l.contentWidth() + 6,
                l.listBottom() - l.listTop() + 3, BORDER);
        if (!data.accessAllowed()) {
            g.text(font, data.notice().isBlank() ? "Auction House access is locked." : data.notice(),
                    l.contentLeft() + 12, l.listTop() + 12, ERROR, false);
        } else if (data.entries().isEmpty()) {
            String empty = isBlacklistMode() ? "No blacklisted items." : "No matching auctions.";
            g.text(font, empty, l.contentLeft() + 12, l.listTop() + 12, MUTED, false);
        }
        for (RowBounds row : rows) drawRow(g, row, mouseX, mouseY);
        g.text(font, "Page " + (data.pageIndex() + 1) + "/" + pageCount(),
                l.contentLeft() + 164, l.bottom() - 21, MUTED, false);
        if (!notice.isBlank()) g.text(font, trim(notice, 72), l.contentLeft() + 236, l.bottom() - 21,
                noticeError ? ERROR : GOOD, false);

        if (buyDialog && selected() != null && !isAdminMode() && !isBlacklistMode()) {
            drawBuyDialog(g, selected());
        }
        if (hasAdminDialog()) drawAdminReasonDialog(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawCategories(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        g.fill(l.left() + 5, l.top() + 31, l.left() + l.categoryWidth() - 4, l.listBottom(), SUBPANEL);
        g.outline(l.left() + 5, l.top() + 31, l.categoryWidth() - 9,
                l.listBottom() - (l.top() + 31), BORDER);
        if (isBlacklistMode()) {
            drawBlacklistManager(g, l, mouseX, mouseY);
        } else {
            g.text(font, "Categories", l.left() + 12, l.top() + 39, TEXT, false);
            int y = l.top() + 54;
            drawCategory(g, l, "all", "All Items", y, mouseX, mouseY);
            y += 17;
            for (AuctionCategory category : AuctionCategory.ordered()) {
                drawCategory(g, l, category.id(), category.label(), y, mouseX, mouseY);
                y += 17;
            }
        }
    }

    private void drawBlacklistManager(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        int x = l.left() + 12;
        g.text(font, "Blacklist management", x, l.top() + 39, TEXT, false);
        g.text(font, "Choose from inventory", x, l.top() + 56, MUTED, false);
        drawBlacklistInventory(g, l, mouseX, mouseY);

        ItemStack selected = clientInventoryItem(selectedInventorySlot);
        String selectedText = selected.isEmpty() ? "Selected: none"
                : "Selected: " + trim(selected.getHoverName().getString(), 20);
        g.text(font, selectedText, x, l.top() + 151, selected.isEmpty() ? MUTED : GOOD, false);

        g.text(font, "Or enter an item ID", x, l.top() + 194, MUTED, false);
        int previewX = x;
        int previewY = l.top() + 233;
        g.fill(previewX, previewY, previewX + 20, previewY + 20, 0xD00B1015);
        g.outline(previewX, previewY, 20, 20, BORDER);
        ItemStack preview = blacklistIdPreview();
        String rawId = blacklistIdBox == null ? "" : blacklistIdBox.getValue().trim();
        if (!preview.isEmpty()) {
            g.item(preview, previewX + 2, previewY + 2);
            g.text(font, trim(preview.getHoverName().getString(), 21), previewX + 26, previewY + 2, GOOD, false);
            g.text(font, trim(clientItemId(preview), 24), previewX + 26, previewY + 12, MUTED, false);
            if (mouseX >= previewX && mouseX < previewX + 20 && mouseY >= previewY && mouseY < previewY + 20) {
                g.setTooltipForNextFrame(font, preview, mouseX, mouseY);
            }
        } else {
            g.text(font, rawId.isBlank() ? "Item preview" : "Unknown item ID",
                    previewX + 26, previewY + 6, rawId.isBlank() ? MUTED : ERROR, false);
        }
        g.text(font, "New auctions cannot use", x, l.top() + 286, WARNING, false);
        g.text(font, "blacklisted items.", x, l.top() + 298, WARNING, false);
    }

    private void drawBlacklistInventory(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        int startX = l.left() + 14;
        int startY = l.top() + 69;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = 9 + row * 9 + column;
                drawBlacklistInventorySlot(g, slot, startX + column * 18, startY + row * 18, mouseX, mouseY);
            }
        }
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++) {
            drawBlacklistInventorySlot(g, column, startX + column * 18, hotbarY, mouseX, mouseY);
        }
    }

    private void drawBlacklistInventorySlot(GuiGraphicsExtractor g, int slot, int x, int y, int mouseX, int mouseY) {
        boolean selected = slot == selectedInventorySlot;
        g.fill(x, y, x + 18, y + 18, selected ? 0xD05B4A16 : 0xD00B1015);
        g.outline(x, y, 18, 18, selected ? 0xFFFFD45A : BORDER);
        ItemStack stack = clientInventoryItem(slot);
        if (!stack.isEmpty()) {
            g.item(stack, x + 1, y + 1);
            g.itemDecorations(font, stack, x + 1, y + 1);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
    }

    private void drawAdminReasonDialog(GuiGraphicsExtractor g) {
        int dialogWidth = 380;
        int dialogHeight = 150;
        int x = (width - dialogWidth) / 2;
        int y = (height - dialogHeight) / 2;
        g.fill(0, 0, width, height, 0x99000000);
        g.fill(x, y, x + dialogWidth, y + dialogHeight, PANEL);
        g.outline(x, y, dialogWidth, dialogHeight, BORDER);
        String title = "seize".equals(adminDialogAction) ? "Seize auction items" : "Cancel auction as administrator";
        g.text(font, title, x + 18, y + 15, TEXT, false);
        g.text(font, "A reason is required and will be sent to the seller.", x + 18, y + 32, MUTED, false);
        int remaining = Math.max(0, 200 - adminReasonDraft.length());
        g.text(font, remaining + " characters remaining", x + 18, y + 82, MUTED, false);
        if (!notice.isBlank() && noticeError) g.text(font, trim(notice, 52), x + 18, y + 94, ERROR, false);
    }

    private void drawCategory(GuiGraphicsExtractor g, Layout l, String id, String label, int y, int mouseX, int mouseY) {
        int x = l.left() + 8;
        int w = l.categoryWidth() - 15;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        boolean selected = id.equals(data.category())
                && ("browse".equals(data.mode()) || "admin".equals(data.mode()));
        if (selected || hover) g.fill(x, y, x + w, y + 16, selected ? ROW_SELECTED : ROW);
        g.text(font, trim(label, 19), x + 4, y + 4, selected ? GOOD : TEXT, false);
    }

    private void drawRow(GuiGraphicsExtractor g, RowBounds row, int mouseX, int mouseY) {
        AuctionHouseDataPayload.Entry entry = data.entries().get(row.index());
        g.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
                row.index() == selectedIndex ? ROW_SELECTED : ROW);
        int itemX = row.x() + 6;
        int itemY = row.y() + Math.max(2, (row.height() - 16) / 2);
        if (!entry.item().isEmpty()) {
            g.item(entry.item(), itemX, itemY);
            g.itemDecorations(font, entry.item(), itemX, itemY);
        }
        g.text(font, trim(entry.name(), 32), row.x() + 29, row.y() + 5, TEXT, false);
        if (isBlacklistMode()) {
            g.text(font, trim(entry.id(), 54), row.x() + 29, row.y() + 18, MUTED, false);
            g.text(font, "Blocked from new auctions", row.x() + row.width() - 148, row.y() + 11,
                    WARNING, false);
        } else {
            g.text(font, "Price/unit: " + entry.formattedUnitPrice(), row.x() + 29, row.y() + 18, GOOD, false);
            int middle = row.x() + Math.max(230, row.width() / 2);
            g.text(font, "Qty: " + entry.quantity(), middle, row.y() + 5, TEXT, false);
            g.text(font, "Seller: " + trim(entry.seller(), 18), middle, row.y() + 18, MUTED, false);
            g.text(font, timeLeft(entry.expiresAtEpochMilli()), row.x() + row.width() - 96, row.y() + 11,
                    WARNING, false);
        }
        if (!entry.item().isEmpty() && mouseX >= itemX && mouseX < itemX + 18
                && mouseY >= itemY && mouseY < itemY + 18) {
            g.setTooltipForNextFrame(font, entry.item(), mouseX, mouseY);
        }
    }

    private void drawBuyDialog(GuiGraphicsExtractor g, AuctionHouseDataPayload.Entry entry) {
        int w = 260;
        int x = (width - w) / 2;
        int y = (height - 126) / 2;
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(x, y, x + w, y + 126, PANEL);
        g.outline(x, y, w, 126, BORDER);
        g.text(font, "Buy " + trim(entry.name(), 27), x + 18, y + 15, TEXT, false);
        g.text(font, entry.formattedUnitPrice() + " per item • " + entry.quantity() + " available",
                x + 18, y + 31, MUTED, false);
        String raw = buyQuantity == null ? "1" : buyQuantity.getValue();
        try {
            int quantity = Integer.parseInt(raw);
            long total = Math.multiplyExact(entry.unitPriceMinor(), quantity);
            g.text(font, "Total: " + formatMoney(total), x + 18, y + 76, GOOD, false);
        } catch (Exception ignored) {
            g.text(font, "Enter a valid quantity.", x + 18, y + 76, ERROR, false);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (hasAdminDialog()) {
            int dialogWidth = 380;
            int dialogHeight = 150;
            int dialogX = (width - dialogWidth) / 2;
            int dialogY = (height - dialogHeight) / 2;
            boolean inside = event.x() >= dialogX && event.x() < dialogX + dialogWidth
                    && event.y() >= dialogY && event.y() < dialogY + dialogHeight;
            return inside ? super.mouseClicked(event, doubleClick) : true;
        }
        if (buyDialog) {
            int dialogWidth = 260;
            int dialogX = (width - dialogWidth) / 2;
            int dialogY = (height - 126) / 2;
            boolean inside = event.x() >= dialogX && event.x() < dialogX + dialogWidth
                    && event.y() >= dialogY && event.y() < dialogY + 126;
            return inside ? super.mouseClicked(event, doubleClick) : true;
        }
        Layout l = layout();
        if (isBlacklistMode()) {
            int inventorySlot = blacklistInventorySlotAt(l, (int) event.x(), (int) event.y());
            if (inventorySlot >= 0) {
                selectedInventorySlot = clientInventoryItem(inventorySlot).isEmpty() ? -1 : inventorySlot;
                if (addInventoryBlacklistButton != null) {
                    addInventoryBlacklistButton.active = !clientInventoryItem(selectedInventorySlot).isEmpty();
                }
                return true;
            }
        }
        if (!isBlacklistMode()) {
            int y = l.top() + 54;
            if (categoryClicked("all", y, event)) return true;
            y += 17;
            for (AuctionCategory category : AuctionCategory.ordered()) {
                if (categoryClicked(category.id(), y, event)) return true;
                y += 17;
            }
        }
        for (RowBounds row : rows) {
            if (row.contains((int) event.x(), (int) event.y())) {
                selectedIndex = row.index();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean categoryClicked(String id, int y, net.minecraft.client.input.MouseButtonEvent event) {
        Layout l = layout();
        int x = l.left() + 8;
        int w = l.categoryWidth() - 15;
        if (event.x() >= x && event.x() < x + w && event.y() >= y && event.y() < y + 16) {
            String mode = isAdminMode() ? "admin" : "browse";
            request(mode, id, searchBox == null ? "" : searchBox.getValue(), data.sort(), 0);
            return true;
        }
        return false;
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!buyDialog && scrollY != 0.0D) {
            Layout l = layout();
            if (mouseX >= l.contentLeft() && mouseX < l.contentRight()
                    && mouseY >= l.listTop() && mouseY < l.listBottom()) {
                int page = data.pageIndex() + (scrollY < 0.0D ? 1 : -1);
                if (page >= 0 && page < pageCount() && page != data.pageIndex()) {
                    requestPage(page);
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        action("close", "", 0, "", 0);
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    private boolean isAdminMode() {
        return "admin".equals(data.mode());
    }

    private boolean isBlacklistMode() {
        return "blacklist".equals(data.mode());
    }

    private AuctionHouseDataPayload.Entry selected() {
        return selectedIndex >= 0 && selectedIndex < data.entries().size() ? data.entries().get(selectedIndex) : null;
    }

    private int indexOf(String id) {
        if (id == null || id.isBlank()) return -1;
        for (int i = 0; i < data.entries().size(); i++) if (id.equals(data.entries().get(i).id())) return i;
        return -1;
    }

    private int pageCount() {
        return Math.max(1, (data.totalEntries() + data.pageSize() - 1) / data.pageSize());
    }

    private Layout layout() {
        int panelWidth = Math.min(920, Math.max(620, width - 24));
        int panelHeight = Math.min(510, Math.max(390, height - 24));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int categoryWidth = isBlacklistMode() ? 190 : 152;
        int contentLeft = left + categoryWidth + 8;
        int contentRight = left + panelWidth - 10;
        return new Layout(left, top, panelWidth, panelHeight, categoryWidth, contentLeft, contentRight,
                top + 88, top + panelHeight - 38);
    }


    private int blacklistInventorySlotAt(Layout l, int mouseX, int mouseY) {
        int startX = l.left() + 14;
        int startY = l.top() + 69;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int x = startX + column * 18;
                int y = startY + row * 18;
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    return 9 + row * 9 + column;
                }
            }
        }
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++) {
            int x = startX + column * 18;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= hotbarY && mouseY < hotbarY + 18) {
                return column;
            }
        }
        return -1;
    }

    private ItemStack clientInventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private ItemStack blacklistIdPreview() {
        if (blacklistIdBox == null) return ItemStack.EMPTY;
        String raw = blacklistIdBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) return ItemStack.EMPTY;
        try {
            return BuiltInRegistries.ITEM.getOptional(Identifier.parse(raw))
                    .map(registeredItem -> new ItemStack(registeredItem))
                    .filter(stack -> !stack.isEmpty())
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    private static String clientItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private String formatMoney(long minorUnits) {
        int decimals = data.currencyDecimalPlaces();
        BigDecimal value = BigDecimal.valueOf(minorUnits, decimals);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("nl-BE"));
        String pattern = decimals <= 0 ? "#,##0" : "#,##0." + "0".repeat(decimals);
        String number = new DecimalFormat(pattern, symbols).format(value);
        return data.currencySymbol().isBlank() ? number : data.currencySymbol() + " " + number;
    }

    private static String timeLeft(long expiry) {
        long millis = Math.max(0L, expiry - Instant.now().toEpochMilli());
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours >= 24) return (hours / 24) + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(0, minutes) + "m";
    }

    private static String formatTax(int permille) {
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(permille, 1).stripTrailingZeros();
        return value.toPlainString();
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private record RowBounds(int index, int x, int y, int width, int height) {
        boolean contains(int px, int py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    private record Layout(int left, int top, int width, int height, int categoryWidth,
                          int contentLeft, int contentRight, int listTop, int listBottom) {
        int right() { return left + width; }
        int bottom() { return top + height; }
        int contentWidth() { return contentRight - contentLeft; }
    }
}
