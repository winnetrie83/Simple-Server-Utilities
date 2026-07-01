package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClaimMapScreen extends Screen {

    private final ClaimMapDataPayload payload;

    public ClaimMapScreen(ClaimMapDataPayload payload) {
        super(Component.literal("Claim Map"));
        this.payload = payload;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int startX = this.width / 2 - 80;
        int startY = 40;
        int cellSize = 12;

        graphics.text(
                this.font,
                "Claim map: " + payload.selectedClaimGroup(),
                startX,
                20,
                0xFFFFFF
        );

        int radius = payload.radius();

        for (ClaimMapDataPayload.Entry entry : payload.chunks()) {
            int dx = entry.chunkX() - payload.centerChunkX();
            int dz = entry.chunkZ() - payload.centerChunkZ();

            int x = startX + (dx + radius) * cellSize;
            int y = startY + (dz + radius) * cellSize;

            int color = getColor(entry);

            graphics.fill(x, y, x + cellSize - 1, y + cellSize - 1, color);

            if (entry.currentChunk()) {
                graphics.text(this.font, "P", x + 3, y + 2, 0xFFFFFF);
            }
        }
    }

    private int getColor(ClaimMapDataPayload.Entry entry) {
        if (entry.currentChunk()) {
            return 0xFF00AAFF;
        }

        return switch (entry.status()) {
            case WILDERNESS -> 0xFF333333;
            case OWNED_BY_SELF -> 0xFF00AA00;
            case OWNED_BY_TRUSTED -> 0xFFAAAA00;
            case OWNED_BY_OTHER -> 0xFFAA0000;
            case REGION -> 0xFFAA00AA;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}