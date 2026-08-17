package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.moderation.PlayerInventoryAdminMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PlayerInventoryAdminScreen extends AbstractContainerScreen<PlayerInventoryAdminMenu> {
    public PlayerInventoryAdminScreen(PlayerInventoryAdminMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 196;
        this.imageHeight = 250;
        titleLabelX = inventoryLabelX = -10000;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Close & save"),
                button -> minecraft.player.closeContainer())
                .bounds(leftPos + 96, topPos + imageHeight - 24, 86, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int rows = (menu.targetSlots() + 8) / 9;
        SsuGuiScale.fullscreenDim(graphics, this, 0xA5000000);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0161D25);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF586978);
        graphics.drawString(font, (menu.mode().equals("ender") ? "Ender chest — " : "Inventory & equipment — ")
                + menu.targetName(), leftPos + 12, topPos + 10, 0xFFF3F5F7, true);
        graphics.drawString(font, "Target", leftPos + 12, topPos + 23, 0xFFAAB5BE, false);
        for (int i = 0; i < menu.targetSlots(); i++) {
            int x = leftPos + 17 + (i % 9) * 18;
            int y = topPos + 34 + (i / 9) * 18;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF090D12);
            graphics.renderOutline(x - 1, y - 1, 18, 18, 0xFF485865);
        }
        graphics.drawString(font, "Administrator inventory", leftPos + 12,
                topPos + 34 + rows * 18 + 7, 0xFFAAB5BE, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
