package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.kits.KitEditorMenu;
import be.winnetrie.mod.simpleserverutilities.network.KitContentsResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.KitContentsSavePayload;
import be.winnetrie.mod.simpleserverutilities.network.KitRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Inventory-backed kit contents editor with nine ghost slots. */
public final class KitEditorScreen extends AbstractContainerScreen<KitEditorMenu> {
    private static final int W = 196, H = 220;
    private static Screen returnParent;
    private String notice = "";
    private long req = 1L;

    public static void prepareReturn(Screen parent) { returnParent = parent; }

    public KitEditorScreen(KitEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        titleLabelX = inventoryLabelX = -10000;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Save"), button ->
                ClientPacketDistributor.sendToServer(new KitContentsSavePayload(menu.containerId, req++)))
                .bounds(leftPos + W - 72, topPos + H - 26, 58, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> backToKits())
                .bounds(leftPos + 14, topPos + H - 26, 58, 18).build());
    }

    private void backToKits() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.closeContainer();
        if (returnParent != null) minecraft.setScreenAndShow(returnParent);
        ClientPacketDistributor.sendToServer(new KitRequestPayload(true, menu.kitId(), req++));
    }

    public void accept(KitContentsResultPayload payload) { notice = payload.message(); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(leftPos, topPos, leftPos + W, topPos + H, 0xF0161D25);
        g.outline(leftPos, topPos, W, H, 0xFF586978);
        g.text(font, "Kit contents — " + menu.kitId(), leftPos + 14, topPos + 12, 0xFFF3F5F7, true);
        drawGrid(g, leftPos + 16, topPos + 41, 9, 1);
        drawGrid(g, leftPos + KitEditorMenu.INV_X - 1, topPos + KitEditorMenu.INV_Y - 1, 9, 3);
        drawGrid(g, leftPos + KitEditorMenu.INV_X - 1, topPos + KitEditorMenu.HOTBAR_Y - 1, 9, 1);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!notice.isBlank()) g.text(font, trim(notice, 34), leftPos + 14, topPos + 174, 0xFF83E39A, false);
    }

    private void drawGrid(GuiGraphicsExtractor g, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            int sx = x + column * 18, sy = y + row * 18;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF090D12);
            g.outline(sx, sy, 18, 18, 0xFF586978);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
