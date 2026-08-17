package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueValidation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only paged report for Dialogue Editor 2.0 structural and catalogue validation. */
public final class NpcDialogueValidationScreen extends Screen {
    private static final int W = 600, H = 390;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585, WARNING = 0xFFFFD27A;
    private static final int PAGE_SIZE = 15;

    private final Screen parent;
    private final NpcDialogueValidation.Report report;
    private int page;

    public NpcDialogueValidationScreen(Screen parent, NpcDialogueValidation.Report report) {
        super(Component.literal("Dialogue validation"));
        this.parent = parent;
        this.report = report == null ? new NpcDialogueValidation.Report(List.of()) : report;
    }

    @Override
    protected void init() {
        int x = px(), y = py();
        int pages = Math.max(1, (report.issues().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹ Previous"), button -> {
            page = Math.max(0, page - 1); rebuildWidgets();
        }).bounds(x + 12, y + H - 26, 82, 18).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("Next ›"), button -> {
            page = Math.min(pages - 1, page + 1); rebuildWidgets();
        }).bounds(x + 102, y + H - 26, 72, 18).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("Back to editor"), button -> onClose())
                .bounds(x + W - 112, y + H - 26, 100, 18).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        SsuGuiScale.fullscreenDim(graphics, this, 0xA9000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, "Dialogue validation", x + 12, y + 12, TEXT, true);
        graphics.text(font, report.summary(), x + 12, y + 30,
                report.errorCount() > 0 ? ERROR : report.warningCount() > 0 ? WARNING : GOOD, false);

        if (report.issues().isEmpty()) {
            graphics.text(font, "The graph, registered handlers and known service targets passed validation.",
                    x + 12, y + 62, GOOD, false);
        } else {
            int start = page * PAGE_SIZE;
            int end = Math.min(report.issues().size(), start + PAGE_SIZE);
            for (int index = start; index < end; index++) {
                NpcDialogueValidation.Issue issue = report.issues().get(index);
                int rowY = y + 58 + (index - start) * 20;
                String prefix = issue.severity() == NpcDialogueValidation.Severity.ERROR ? "ERROR" : "WARN";
                int colour = issue.severity() == NpcDialogueValidation.Severity.ERROR ? ERROR : WARNING;
                graphics.text(font, prefix, x + 12, rowY, colour, true);
                graphics.text(font, trim(issue.location(), 34), x + 58, rowY, MUTED, false);
                graphics.text(font, trim(issue.message(), 78), x + 190, rowY, TEXT, false);
            }
            int pages = Math.max(1, (report.issues().size() + PAGE_SIZE - 1) / PAGE_SIZE);
            graphics.text(font, "Page " + (page + 1) + "/" + pages, x + 190, y + H - 21, MUTED, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum - 1) + "…";
    }
}
