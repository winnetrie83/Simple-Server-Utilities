package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolConfigurePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameValidationPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Scrollable green/orange/red arena validation report with issue teleport buttons. */
public final class MinigameValidationScreen extends Screen {
    private static final int W = 600, H = 360;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7,
            MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, WARN = 0xFFFFD36A, ERROR = 0xFFFF8585;
    private final MinigameValidationPayload data;
    private final Screen parent;
    private int scroll;
    private long requestId;

    public MinigameValidationScreen(MinigameValidationPayload data, Screen parent) {
        super(Component.literal("Arena Validation"));
        this.data = data;
        this.parent = parent;
        this.requestId = Math.max(1L, data.requestId() + 1L);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        int shown = Math.min(12, Math.max(0, data.issues().size() - scroll));
        for (int row = 0; row < shown; row++) {
            int index = scroll + row;
            var issue = data.issues().get(index);
            if (!issue.hasLocation()) continue;
            addRenderableWidget(Button.builder(Component.literal("Go"), ignored -> teleport(index))
                    .bounds(x + W - 52, y + 58 + row * 22, 34, 18).build());
        }
        if (data.issues().size() > 12) {
            addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); })
                    .bounds(x + W - 92, y + H - 29, 28, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> {
                scroll = Math.min(Math.max(0, data.issues().size() - 12), scroll + 1); rebuildWidgets();
            }).bounds(x + W - 60, y + H - 29, 28, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + 14, y + H - 29, 62, 20).build());
    }

    private void teleport(int issueIndex) {
        PacketDistributor.sendToServer(new MinigameSetupToolConfigurePayload(
                "teleport_issue", data.minigameId(), data.arenaId(), "", 1, issueIndex, requestId++));
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, "Arena Validation", x + 14, y + 14, TEXT, true);
        long errors = data.issues().stream().filter(issue -> "error".equals(issue.severity())).count();
        long warnings = data.issues().stream().filter(issue -> "warning".equals(issue.severity())).count();
        g.drawString(font, errors + " error(s) • " + warnings + " warning(s)", x + 158, y + 15,
                errors > 0 ? ERROR : warnings > 0 ? WARN : GOOD, false);
        g.drawString(font, "Errors block readiness; warnings are advisory and do not prevent a test match.",
                x + 14, y + 31, MUTED, false);
        int shown = Math.min(12, Math.max(0, data.issues().size() - scroll));
        for (int row = 0; row < shown; row++) {
            var issue = data.issues().get(scroll + row);
            int ry = y + 58 + row * 22;
            int color = "error".equals(issue.severity()) ? ERROR : "warning".equals(issue.severity()) ? WARN : GOOD;
            String marker = "error".equals(issue.severity()) ? "✖" : "warning".equals(issue.severity()) ? "!" : "✔";
            g.drawString(font, marker, x + 16, ry + 5, color, true);
            g.drawString(font, trim(issue.message(), 78), x + 34, ry + 5, TEXT, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
    @Override public boolean isPauseScreen() { return false; }
}
