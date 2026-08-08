package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionResultPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Transparent, world-visible World Edit controls. It intentionally behaves like a tiny HUD palette
 * instead of opening the full editor, so an administrator can keep the build in view while nudging
 * or transforming the current selection.
 */
public final class WorldEditCompactOverlayScreen extends Screen {
    private static long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;

    public WorldEditCompactOverlayScreen() {
        super(Component.literal("World Edit compact tools"));
    }

    @Override
    protected void init() {
        final int bw = 28;
        final int bh = 18;
        final int gap = 3;
        final int columns = 6;
        final int totalW = columns * bw + (columns - 1) * gap;
        final int x = width - totalW - 10;
        final int bottom = height - 10;
        final int transformY = bottom - bh;
        final int moveY = transformY - bh - gap;

        addAction(x, moveY, bw, bh, "←", "offset", "-1,0,0", "Move -X / west");
        addAction(x + (bw + gap), moveY, bw, bh, "→", "offset", "1,0,0", "Move +X / east");
        addAction(x + 2 * (bw + gap), moveY, bw, bh, "↑", "offset", "0,0,-1", "Move -Z / north");
        addAction(x + 3 * (bw + gap), moveY, bw, bh, "↓", "offset", "0,0,1", "Move +Z / south");
        addAction(x + 4 * (bw + gap), moveY, bw, bh, "+Y", "offset", "0,1,0", "Move selection up");
        addAction(x + 5 * (bw + gap), moveY, bw, bh, "-Y", "offset", "0,-1,0", "Move selection down");

        addAction(x, transformY, bw, bh, "↶", "rotate_left", "", "Rotate left 90°");
        addAction(x + (bw + gap), transformY, bw, bh, "↷", "rotate_right", "", "Rotate right 90°");
        addAction(x + 2 * (bw + gap), transformY, bw, bh, "180", "rotate_180", "", "Rotate 180°");
        addAction(x + 3 * (bw + gap), transformY, bw, bh, "MX", "mirror_x", "", "Mirror X / east-west");
        addAction(x + 4 * (bw + gap), transformY, bw, bh, "MZ", "mirror_z", "", "Mirror Z / north-south");
        addAction(x + 5 * (bw + gap), transformY, bw, bh, "FY", "flip_vertical", "", "Flip vertically");
    }

    private void addAction(int x, int y, int width, int height, String label,
                           String operation, String value, String tooltip) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> send(operation, value))
                .bounds(x, y, width, height).build());
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private void send(String operation, String value) {
        ClientPacketDistributor.sendToServer(new RegionSelectionActionPayload(
                operation,
                value == null ? "" : value,
                List.of(),
                List.of(),
                nextRequestId++
        ));
    }

    public void acceptResult(RegionSelectionActionResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        notice = payload.message();
        noticeError = !payload.successful();
        if (payload.selectionCleared() && minecraft != null) minecraft.setScreenAndShow(null);
    }

    /** No vanilla menu blur/dim layer: the world must remain fully readable behind the controls. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Intentionally empty.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int labelY = height - 64;
        String title = "World Edit · compact tools";
        int titleX = width - font.width(title) - 10;
        graphics.text(font, title, titleX, labelY, 0xFF6FE7FF, true);
        if (!notice.isBlank()) {
            int max = 190;
            String clipped = font.width(notice) <= max ? notice : font.plainSubstrByWidth(notice, max - 8) + "…";
            graphics.text(font, clipped, width - font.width(clipped) - 10, labelY - 12,
                    noticeError ? 0xFFFF8585 : 0xFF7DFF9B, true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
