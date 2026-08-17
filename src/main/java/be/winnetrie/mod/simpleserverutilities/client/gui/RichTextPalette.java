package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Shared compact palette for SSU's legacy-compatible rich-text editors. */
final class RichTextPalette {
    static final List<String> NAMES = List.of(
            "Black", "Dark Blue", "Dark Green", "Dark Aqua",
            "Dark Red", "Dark Purple", "Gold", "Gray",
            "Dark Gray", "Blue", "Green", "Aqua",
            "Red", "Light Purple", "Yellow", "White"
    );

    private RichTextPalette() { }

    static int rgb(int index) { return HologramRichText.minecraftColorRgb(Math.max(0, Math.min(15, index))); }
    static int argb(int index) { return 0xFF000000 | rgb(index); }
    /** Tooltip labels normally preview their color; Black uses white so the label remains readable. */
    static int labelRgb(int index) { return Math.max(0, Math.min(15, index)) == 0 ? 0xFFFFFF : rgb(index); }
    static String name(int index) { return NAMES.get(Math.max(0, Math.min(15, index))); }

    static SwatchButton button(int x, int y, int size, int index, Button.OnPress press) {
        return new SwatchButton(x, y, size, index, press);
    }

    static final class SwatchButton extends Button {
        private final int swatch;

        SwatchButton(int x, int y, int size, int index, OnPress press) {
            super(x, y, size, size, Component.empty(), press, DEFAULT_NARRATION);
            this.swatch = argb(index);
            Component tooltipText = Component.literal(name(index))
                    .withStyle(style -> style.withColor(labelRgb(index)));
            setTooltip(Tooltip.create(tooltipText));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.fill(getX() + 3, getY() + 3, getRight() - 3, getBottom() - 3, swatch);
            graphics.renderOutline(getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4,
                    isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF20242B);
        }
    }
}
