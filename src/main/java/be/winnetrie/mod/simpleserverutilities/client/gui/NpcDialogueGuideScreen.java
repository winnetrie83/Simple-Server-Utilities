package be.winnetrie.mod.simpleserverutilities.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Short in-editor explanation of the dialogue graph workflow. */
public final class NpcDialogueGuideScreen extends Screen {
    private static final int W = 500, H = 300;
    private final NpcDialogueEditorScreen parent;

    public NpcDialogueGuideScreen(NpcDialogueEditorScreen parent) {
        super(Component.literal("Dialogue editor guide"));
        this.parent = parent;
    }

    @Override protected void init() {
        int x = px(), y = py();
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(x + W - 76, y + H - 27, 64, 18).build());
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        g.fill(0, 0, width, height, 0xA9000000);
        g.fill(x, y, x + W, y + H, 0xF0161D25);
        g.outline(x, y, W, H, 0xFF586978);
        g.text(font, "Dialogue Editor — how the pages work", x + 12, y + 12, 0xFFF3F5F7, true);
        section(g, x, y + 40, "Node", "One dialogue page: speaker, text and the player choices attached to it.");
        section(g, x, y + 79, "Conditions", "Rules that decide whether the current player choice is available or hidden.");
        section(g, x, y + 118, "On open", "Actions executed once when the player enters the selected node.");
        section(g, x, y + 157, "Choice", "The clickable player reply and where it leads: another node, closing, or an SSU service.");
        section(g, x, y + 196, "On choose", "Actions executed after the player clicks the choice, before its next node or service.");
        g.text(font, "Parameters use key=value pairs separated by semicolons.", x + 12, y + 242, 0xFFAAB5BE, false);
        g.text(font, "Use Parameter guide to see known keys; custom keys remain available for extensions.",
                x + 12, y + 256, 0xFFAAB5BE, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void section(GuiGraphicsExtractor g, int x, int y, String title, String text) {
        g.text(font, title, x + 12, y, 0xFF83E39A, true);
        g.text(font, text, x + 116, y, 0xFFF3F5F7, false);
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
}
