package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Requires an explicit close reason before a support ticket can be closed. */
public final class SupportCloseReasonScreen extends Screen {
    private static final int W = 420, H = 150;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;
    private final Screen parent;
    private final long ticketId;
    private final Consumer<String> closer;
    private EditBox reason;

    public SupportCloseReasonScreen(Screen parent, long ticketId, Consumer<String> closer) {
        super(Component.literal("Close ticket #" + ticketId));
        this.parent = parent;
        this.ticketId = ticketId;
        this.closer = closer;
    }

    @Override protected void init() {
        int x = left(), y = top();
        reason = new EditBox(font, x + 18, y + 54, W - 36, 20, Component.literal("Close reason"));
        reason.setMaxLength(512);
        addRenderableWidget(reason);
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(x + 18, y + H - 34, 72, 20).build());
        Button close = addRenderableWidget(Button.builder(Component.literal("Close ticket"), button -> {
            closer.accept(reason.getValue().trim());
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(x + W - 112, y + H - 34, 94, 20).build());
        reason.setResponder(value -> close.active = value != null && value.trim().length() >= 3);
        close.active = false;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, "Close ticket #" + ticketId, x + 18, y + 16, TEXT, true);
        g.drawString(font, "Give the player/staff a reason. It is stored in the conversation.", x + 18, y + 32, MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
}
