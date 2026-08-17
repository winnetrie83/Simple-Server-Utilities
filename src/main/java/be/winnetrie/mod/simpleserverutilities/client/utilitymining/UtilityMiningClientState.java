package be.winnetrie.mod.simpleserverutilities.client.utilitymining;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningPreviewPayload;
import be.winnetrie.mod.simpleserverutilities.utilitymining.UtilityMiningType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** Latest validated utility-mining preview received from the server. */
public final class UtilityMiningClientState {
    private static final long STALE_AFTER_NANOS = 750_000_000L;

    private static Preview preview = Preview.empty();

    private UtilityMiningClientState() {
    }

    public static synchronized void apply(UtilityMiningPreviewPayload payload) {
        if (payload.miningType() == UtilityMiningType.NONE || payload.blocks().isEmpty()) {
            preview = Preview.empty();
            return;
        }
        preview = new Preview(
                payload.miningType(),
                payload.dimension(),
                payload.outlineColor(),
                payload.brightness(),
                payload.showInfo(),
                payload.blockName(),
                payload.blocks().stream().map(BlockPos::of).toList(),
                System.nanoTime()
        );
    }

    public static synchronized Preview snapshot() {
        if (!preview.isVisible() || System.nanoTime() - preview.updatedAtNanos() > STALE_AFTER_NANOS) {
            preview = Preview.empty();
        }
        return preview;
    }

    public static synchronized void clear() {
        preview = Preview.empty();
    }

    /** Two-line crosshair HUD using the same RGB value as the active outline. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;

        Preview current = snapshot();
        if (!current.isVisible() || !current.showInfo()
                || !minecraft.level.dimension().location().toString().equals(current.dimension())) return;

        String title = current.type() == UtilityMiningType.TREECAPITATOR
                ? "Treecapitator active" : "Veinminer active";
        String blockName = current.blockName().isBlank() ? "Unknown block" : current.blockName();
        String detail = "Mining " + blockName + " x " + current.blocks().size();

        int paddingX = 6;
        int paddingY = 4;
        int lineHeight = 11;
        int width = Math.max(minecraft.font.width(title), minecraft.font.width(detail)) + paddingX * 2;
        int height = paddingY * 2 + lineHeight * 2 - 1;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + 18;
        int color = 0xFF000000 | (current.color() & 0x00FFFFFF);

        graphics.fill(x, y, x + width, y + height, 0xB0000000);
        graphics.renderOutline(x, y, width, height, 0x78000000 | (current.color() & 0x00FFFFFF));
        graphics.drawString(minecraft.font, title,
                x + (width - minecraft.font.width(title)) / 2,
                y + paddingY, color, true);
        graphics.drawString(minecraft.font, detail,
                x + (width - minecraft.font.width(detail)) / 2,
                y + paddingY + lineHeight, color, false);
    }

    public record Preview(
            UtilityMiningType type,
            String dimension,
            int color,
            int brightness,
            boolean showInfo,
            String blockName,
            List<BlockPos> blocks,
            long updatedAtNanos
    ) {
        public Preview {
            type = type == null ? UtilityMiningType.NONE : type;
            dimension = dimension == null ? "" : dimension;
            blockName = blockName == null ? "" : blockName.trim();
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            brightness = Math.max(10, Math.min(100, brightness));
        }

        public static Preview empty() {
            return new Preview(UtilityMiningType.NONE, "", 0, 10,
                    false, "", List.of(), 0L);
        }

        public boolean isVisible() {
            return type != UtilityMiningType.NONE && !blocks.isEmpty();
        }
    }
}
