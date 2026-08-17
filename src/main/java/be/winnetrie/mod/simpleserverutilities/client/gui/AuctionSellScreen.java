package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.auction.AuctionSellMenu;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Inventory-backed Auction House listing creator. */
public final class AuctionSellScreen extends AbstractContainerScreen<AuctionSellMenu> {
    private static final int SCREEN_WIDTH = 368;
    private static final int SCREEN_HEIGHT = 286;
    private static final int PANEL = 0xF0181E25;
    private static final int SUBPANEL = 0xD010151A;
    private static final int SLOT_BACKGROUND = 0xFF090D12;
    private static final int SLOT_BORDER = 0xFF485865;
    private static final int OFFER_BORDER = 0xFFFFD75A;
    private static final int BORDER = 0xFF596B79;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;

    private EditBox price;
    private EditBox quantity;
    private String priceDraft = "";
    private String quantityDraft = "";
    private int durationHours;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;
    private boolean submitting;

    public AuctionSellScreen(AuctionSellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
        durationHours = menu.defaultDurationHours();
    }

    @Override
    protected void init() {
        super.init();
        price = new EditBox(font, leftPos + 18, topPos + 120, 96, 18, Component.literal("Price per item"));
        price.setHint(Component.literal("Price/unit"));
        price.setMaxLength(64);
        price.setValue(priceDraft);
        addRenderableWidget(price);
        quantity = new EditBox(font, leftPos + 18, topPos + 146, 96, 18, Component.literal("Quantity"));
        quantity.setHint(Component.literal("Quantity"));
        quantity.setMaxLength(9);
        quantity.setValue(quantityDraft);
        addRenderableWidget(quantity);

        addRenderableWidget(durationButton(12, leftPos + 18, topPos + 184));
        addRenderableWidget(durationButton(24, leftPos + 52, topPos + 184));
        addRenderableWidget(durationButton(48, leftPos + 86, topPos + 184));
        Button createButton = Button.builder(Component.literal(submitting ? "Creating…" : "Create auction"), ignored -> create())
                .bounds(leftPos + 18, topPos + 226, 96, 20).build();
        createButton.active = !submitting;
        addRenderableWidget(createButton);
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> back())
                .bounds(leftPos + 18, topPos + 253, 96, 20).build());
    }

    private Button durationButton(int hours, int x, int y) {
        String label = durationHours == hours ? "[" + hours + "]" : Integer.toString(hours);
        return Button.builder(Component.literal(label), ignored -> {
            captureDrafts();
            durationHours = hours;
            rebuildWidgets();
        }).bounds(x, y, 26, 20).build();
    }

    private void create() {
        if (submitting) return;
        captureDrafts();
        int count;
        try { count = Integer.parseInt(quantityDraft.trim()); }
        catch (NumberFormatException exception) {
            notice = "Enter a valid quantity."; noticeError = true; return;
        }
        long id = nextRequestId++;
        submitting = true;
        ClientPacketDistributor.sendToServer(new AuctionHouseActionPayload("create",
                Integer.toString(menu.containerId), count, priceDraft, durationHours, id));
        rebuildWidgets();
    }

    private void back() {
        if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new AuctionHouseRequestPayload("browse", "all", "",
                "name_asc", 0, 8, id));
    }

    public void acceptResult(AuctionHouseActionResultPayload result) {
        if (result == null) return;
        captureDrafts();
        nextRequestId = Math.max(nextRequestId, result.requestId() + 1L);
        notice = result.message();
        noticeError = !result.successful();
        submitting = false;
        rebuildWidgets();
    }


    private void captureDrafts() {
        if (price != null) priceDraft = price.getValue();
        if (quantity != null) quantityDraft = quantity.getValue();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        g.outline(leftPos, topPos, imageWidth, imageHeight, BORDER);

        g.text(font, "Create Auction", leftPos + 14, topPos + 11, TEXT, false);
        g.text(font, "Balance: " + menu.formattedBalance(), leftPos + 150, topPos + 11, GOOD, false);
        g.text(font, "Active auctions: " + menu.activeAuctions() + "/" + menu.maxAuctions(),
                leftPos + 150, topPos + 26, MUTED, false);

        drawControlPanel(g);
        drawInventoryPanel(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawControlPanel(GuiGraphicsExtractor g) {
        int x = leftPos + 10;
        int y = topPos + 42;
        int width = 120;
        int height = 234;
        g.fill(x, y, x + width, y + height, SUBPANEL);
        g.outline(x, y, width, height, BORDER);
        g.text(font, "Item to sell", x + 10, y + 10, TEXT, false);

        int slotX = leftPos + AuctionSellMenu.OFFER_SLOT_X;
        int slotY = topPos + AuctionSellMenu.OFFER_SLOT_Y;
        g.fill(slotX - 7, slotY - 7, slotX + 23, slotY + 23, 0xFF202A33);
        g.outline(slotX - 7, slotY - 7, 30, 30, OFFER_BORDER);
        drawSlot(g, slotX, slotY, OFFER_BORDER);

        g.text(font, "Available: " + menu.availableMatchingCount(), x + 8, topPos + 105, MUTED, false);
        g.text(font, "Duration (hours)", x + 8, topPos + 174, MUTED, false);
        g.text(font, "Tax: " + formatTax(menu.taxPermille()) + "%", x + 8, topPos + 214, MUTED, false);
    }

    private void drawInventoryPanel(GuiGraphicsExtractor g) {
        int x = leftPos + 140;
        int y = topPos + 42;
        int width = 218;
        int height = 234;
        g.fill(x, y, x + width, y + height, SUBPANEL);
        g.outline(x, y, width, height, BORDER);
        g.text(font, "Player inventory", x + 10, y + 10, TEXT, false);

        int inventoryX = leftPos + AuctionSellMenu.PLAYER_INVENTORY_X;
        int inventoryY = topPos + AuctionSellMenu.PLAYER_INVENTORY_Y;
        drawGrid(g, inventoryX, inventoryY, 9, 3);

        int hotbarY = topPos + AuctionSellMenu.PLAYER_HOTBAR_Y;
        drawGrid(g, inventoryX, hotbarY, 9, 1);

        if (!notice.isBlank()) {
            g.text(font, trim(notice, 35), x + 10, topPos + 184, noticeError ? ERROR : GOOD, false);
        }
    }

    private static void drawGrid(GuiGraphicsExtractor g, int x, int y, int columns, int rows) {
        g.fill(x - 3, y - 3, x + columns * 18 + 1, y + rows * 18 + 1, 0xFF1B242C);
        g.outline(x - 3, y - 3, columns * 18 + 4, rows * 18 + 4, BORDER);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                drawSlot(g, x + column * 18, y + row * 18, SLOT_BORDER);
            }
        }
    }

    private static void drawSlot(GuiGraphicsExtractor g, int x, int y, int border) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BACKGROUND);
        g.outline(x - 1, y - 1, 18, 18, border);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static String formatTax(int permille) {
        return java.math.BigDecimal.valueOf(permille, 1).stripTrailingZeros().toPlainString();
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
