package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.MinigameDiagnosticsPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Scrollable Admin Center health report with safe integrity and orphan-cleanup actions. */
public final class MinigameDiagnosticsScreen extends Screen {
    private static final int W = 650, H = 390, ROWS = 15;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7,
            MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, WARN = 0xFFFFD36A, ERROR = 0xFFFF8585;
    private MinigameDiagnosticsPayload data;
    private final Screen parent;
    private int scroll;
    private long requestId = 1L;
    private boolean awaiting;

    public MinigameDiagnosticsScreen(MinigameDiagnosticsPayload data, Screen parent) {
        super(Component.literal("Minigame System Health"));
        this.data = data;
        this.parent = parent;
        this.requestId = Math.max(1L, data.requestId() + 1L);
    }

    public void accept(MinigameDiagnosticsPayload payload) {
        if (payload == null) return;
        data = payload;
        requestId = Math.max(requestId, payload.requestId() + 1L);
        awaiting = false;
        scroll = Math.min(scroll, Math.max(0, data.lines().size() - ROWS));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        addRenderableWidget(Button.builder(Component.literal("Run integrity check"), ignored -> request("integrity_check"))
                .bounds(x + 14, y + H - 30, 118, 20).build()).active = !awaiting;
        addRenderableWidget(Button.builder(Component.literal("Clean orphaned runtime data"), ignored -> request("clean_orphans"))
                .bounds(x + 138, y + H - 30, 158, 20).build()).active = !awaiting;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> request("diagnostics"))
                .bounds(x + 302, y + H - 30, 66, 20).build()).active = !awaiting;
        if (data.lines().size() > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> {
                scroll = Math.max(0, scroll - 1); rebuildWidgets();
            }).bounds(x + W - 128, y + H - 30, 26, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> {
                scroll = Math.min(Math.max(0, data.lines().size() - ROWS), scroll + 1); rebuildWidgets();
            }).bounds(x + W - 98, y + H - 30, 26, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 66, y + H - 30, 52, 20).build());
    }

    private void request(String action) {
        if (awaiting) return;
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameLobbyRequestPayload(action, "", requestId++));
        rebuildWidgets();
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, data.title().isBlank() ? "Minigame System Health" : data.title(), x + 14, y + 14, TEXT, true);
        String notice = awaiting ? "Processing…" : data.notice();
        if (!notice.isBlank()) g.text(font, trim(notice, 88), x + 14, y + 31, data.error() ? ERROR : GOOD, false);
        int boxY = y + 50, boxH = H - 90;
        g.fill(x + 12, boxY, x + W - 12, boxY + boxH, 0xB010151C);
        g.outline(x + 12, boxY, W - 24, boxH, BORDER);
        int end = Math.min(data.lines().size(), scroll + ROWS);
        for (int index = scroll; index < end; index++) {
            MinigameDiagnosticsPayload.Line line = data.lines().get(index);
            int row = index - scroll;
            int ry = boxY + 9 + row * 19;
            if ((row & 1) == 1) g.fill(x + 16, ry - 4, x + W - 16, ry + 12, 0x301F2A34);
            int color = switch (line.severity()) {
                case "error" -> ERROR;
                case "warning" -> WARN;
                case "ok" -> GOOD;
                default -> MUTED;
            };
            g.text(font, trim(line.label(), 34), x + 20, ry, color, false);
            g.text(font, trim(line.value(), 62), x + 250, ry, TEXT, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
