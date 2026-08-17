package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextComponents;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueChoicePayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueViewPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Player-facing graph dialogue screen. Choices are filtered and authorized by the server. */
public final class NpcDialogueScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 412;
    private static final int PANEL = 0xF0181E25;
    private static final int BORDER = 0xFF637887;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int ERROR = 0xFFFF8585;
    private static final int GOOD = 0xFF83E39A;

    private NpcDialogueViewPayload data;
    private final Screen parent;
    private long nextRequestId = 1L;
    private boolean awaiting;
    private boolean serverClosed;

    public NpcDialogueScreen(NpcDialogueViewPayload data, Screen parent) {
        super(Component.literal(data.npcName().isBlank() ? "NPC Dialogue" : data.npcName()));
        this.data = data;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        int rowY = y + 176;
        int shown = 0;
        for (NpcDialogueViewPayload.ChoiceEntry choice : data.choices()) {
            if (shown >= 8) break;
            Button button = addRenderableWidget(Button.builder(Component.literal(choice.text()),
                    ignored -> choose(choice)).bounds(x + 20, rowY + shown * 24, PANEL_WIDTH - 40, 20).build());
            button.active = choice.enabled() && !awaiting;
            if (!choice.enabled() && !choice.lockReason().isBlank()) button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(choice.lockReason())));
            shown++;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + PANEL_WIDTH - 90, y + PANEL_HEIGHT - 26, 70, 20).build());
    }

    private void choose(NpcDialogueViewPayload.ChoiceEntry choice) {
        if (awaiting || !choice.enabled()) return;
        awaiting = true;
        rebuildWidgets();
        PacketDistributor.sendToServer(new NpcDialogueChoicePayload(data.sessionId(), choice.id(), nextRequestId++));
    }

    public void accept(NpcDialogueViewPayload payload) {
        if (payload == null || (!data.sessionId().isBlank() && !payload.sessionId().equals(data.sessionId()))) return;
        if (payload.closed()) {
            serverClosed = true;
            if (minecraft != null) minecraft.setScreen(parent);
            return;
        }
        data = payload;
        awaiting = false;
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (!serverClosed && !data.sessionId().isBlank()) {
            serverClosed = true;
            PacketDistributor.sendToServer(new NpcDialogueChoicePayload(
                    data.sessionId(), "", nextRequestId++));
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        g.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        g.drawString(font, data.npcName(), x + 20, y + 14, TEXT, true);
        g.drawString(font, data.speaker().isBlank() ? data.npcName() : data.speaker(), x + 20, y + 38, GOOD, false);
        List<FormattedCharSequence> lines = font.split(SsuRichTextComponents.parse(data.text()), PANEL_WIDTH - 40);
        for (int i = 0; i < Math.min(10, lines.size()); i++) g.drawString(font, lines.get(i), x + 20, y + 56 + i * 10, TEXT, false);
        if (!data.notice().isBlank()) g.drawString(font, trim(data.notice(), 82), x + 20, y + 160, data.error() ? ERROR : GOOD, false);
        else if (awaiting) g.drawString(font, "Processing choice…", x + 20, y + 160, MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int panelX() { return (width - PANEL_WIDTH) / 2; }
    private int panelY() { return (height - PANEL_HEIGHT) / 2; }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 3) + "..."; }
}
