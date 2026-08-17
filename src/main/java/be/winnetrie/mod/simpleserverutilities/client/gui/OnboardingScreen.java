package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.identity.RichTextComponents;
import be.winnetrie.mod.simpleserverutilities.network.OnboardingActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.OnboardingStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Mandatory first-join rules and optional introduction screen. */
public final class OnboardingScreen extends Screen {
    private static final int W = 540;
    private static final int H = 340;
    private static final int VISIBLE_LINES = 18;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;

    private OnboardingStatePayload data;
    private long request = 1L;
    private int lineOffset;
    private boolean confirmDecline;

    public OnboardingScreen(OnboardingStatePayload data) {
        super(Component.literal("Welcome"));
        this.data = data;
    }

    public void accept(OnboardingStatePayload next) {
        boolean changedPage = data == null || data.pageIndex() != next.pageIndex();
        boolean changedKind = data == null || rulesStage(data) != rulesStage(next);
        data = next;
        if (changedPage || changedKind) lineOffset = 0;
        if ("complete".equals(next.stage())) {
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        int maxOffset = maxLineOffset();
        lineOffset = Math.max(0, Math.min(maxOffset, lineOffset));

        addRenderableWidget(Button.builder(Component.literal(confirmDecline ? "Confirm leave" : "Decline & leave"), button -> {
            if (confirmDecline) {
                send("decline_leave", data.pageIndex());
                return;
            }
            confirmDecline = true;
            rebuildWidgets();
        }).bounds(x + W - 126, y + 12, 108, 20).build());

        Button previousText = addRenderableWidget(Button.builder(Component.literal("Text ▲"), button -> {
            lineOffset = Math.max(0, lineOffset - VISIBLE_LINES);
            rebuildWidgets();
        }).bounds(x + 16, y + H - 30, 72, 20).build());
        previousText.active = lineOffset > 0;

        Button nextText = addRenderableWidget(Button.builder(Component.literal("Text ▼"), button -> {
            lineOffset = Math.min(maxLineOffset(), lineOffset + VISIBLE_LINES);
            rebuildWidgets();
        }).bounds(x + 94, y + H - 30, 72, 20).build());
        nextText.active = lineOffset < maxOffset;

        if (rulesStage(data)) {
            boolean confirmation = "rules_confirm".equals(data.stage());
            addRenderableWidget(Button.builder(Component.literal(confirmation ? "Confirm acceptance" : "Accept rules"),
                    button -> PacketDistributor.sendToServer(new OnboardingActionPayload(
                            confirmation ? "accept_rules_confirm" : "accept_rules_first", 0, request++)))
                    .bounds(x + W - 170, y + H - 30, 154, 20).build());
            return;
        }

        Button previousPage = addRenderableWidget(Button.builder(Component.literal("Previous"),
                button -> send("intro_previous", data.pageIndex()))
                .bounds(x + 176, y + H - 30, 84, 20).build());
        previousPage.active = data.pageIndex() > 0;

        boolean finalPage = data.pageIndex() + 1 >= data.pageCount();
        addRenderableWidget(Button.builder(Component.literal(finalPage ? "Finish" : "Next"),
                button -> send(finalPage ? "complete" : "intro_next", data.pageIndex()))
                .bounds(x + W - 100, y + H - 30, 84, 20).build());
        if (data.skippable()) {
            addRenderableWidget(Button.builder(Component.literal("Skip"),
                    button -> send("intro_skip", data.pageIndex()))
                    .bounds(x + W - 190, y + H - 30, 76, 20).build());
        }
    }

    private void send(String action, int page) {
        PacketDistributor.sendToServer(new OnboardingActionPayload(action, page, request++));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        boolean rules = rulesStage(data);
        SsuGuiScale.fullscreenDim(graphics, this, 0xB0000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.renderOutline(x, y, W, H, BORDER);
        graphics.drawString(font, "Welcome to the server", x + 18, y + 16, TEXT, true);
        String subtitle = rules
                ? ("rules_confirm".equals(data.stage())
                        ? "Confirm that you accept these rules."
                        : "Read the complete rules before accepting.")
                : "Introduction " + (data.pageIndex() + 1) + " / " + Math.max(1, data.pageCount());
        graphics.drawString(font, subtitle, x + 18, y + 35, MUTED, false);
        if (confirmDecline) {
            graphics.drawString(font, "Click Confirm leave to decline the rules and disconnect.", x + 18, y + 50, ERROR, false);
        }

        List<FormattedCharSequence> lines = bodyLines();
        int from = Math.max(0, Math.min(lineOffset, Math.max(0, lines.size() - 1)));
        int to = Math.min(lines.size(), from + VISIBLE_LINES);
        for (int index = from; index < to; index++) {
            graphics.drawString(font, lines.get(index), x + 20, y + 62 + (index - from) * 12, TEXT, false);
        }
        if (lines.size() > VISIBLE_LINES) {
            graphics.drawString(font, "Text " + (from + 1) + "–" + to + " / " + lines.size(), x + 178, y + H - 25,
                    MUTED, false);
        }
        if (!data.notice().isBlank()) {
            graphics.drawString(font, data.notice(), x + 18, y + H - 52, data.error() ? ERROR : GOOD, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private List<FormattedCharSequence> bodyLines() {
        Component body = RichTextComponents.fromEncoded(rulesStage(data) ? data.rules() : data.introduction());
        return font.split(body, W - 40);
    }

    private int maxLineOffset() {
        return Math.max(0, bodyLines().size() - VISIBLE_LINES);
    }

    private static boolean rulesStage(OnboardingStatePayload payload) {
        return payload != null && ("rules".equals(payload.stage()) || "rules_confirm".equals(payload.stage()));
    }

    @Override
    public void onClose() {
        // Mandatory until the server marks the onboarding state complete.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }
}
