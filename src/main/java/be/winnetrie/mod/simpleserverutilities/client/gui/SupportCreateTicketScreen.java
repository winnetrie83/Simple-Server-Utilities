package be.winnetrie.mod.simpleserverutilities.client.gui;

import com.google.gson.JsonArray;

import be.winnetrie.mod.simpleserverutilities.serverops.SupportRichText;
import be.winnetrie.mod.simpleserverutilities.serverops.SupportTicketCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Focused ticket composer: category/target first, rich description second, then submit. */
public final class SupportCreateTicketScreen extends Screen {
    private static final int W = 455, H = 220;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A, WARNING = 0xFFFFB86B;

    private final ServerOperationsScreen parent;
    private final JsonArray reportTargets;
    private SupportTicketCategory category = SupportTicketCategory.HELP;
    private String body = "";
    private String reportTargetId = "";
    private String reportTargetName = "";

    public SupportCreateTicketScreen(ServerOperationsScreen parent, JsonArray reportTargets) {
        super(Component.literal("Create support ticket"));
        this.parent = parent;
        this.reportTargets = reportTargets == null ? new JsonArray() : reportTargets.deepCopy();
    }

    @Override protected void init() {
        int x = left(), y = top();
        addRenderableWidget(Button.builder(Component.literal("Category: " + category.label()), button -> {
            SupportTicketCategory[] values = SupportTicketCategory.values();
            category = values[(category.ordinal() + 1) % values.length];
            if (!category.requiresTarget()) {
                reportTargetId = "";
                reportTargetName = "";
            }
            rebuildWidgets();
        }).bounds(x + 18, y + 54, 160, 20).build());

        if (category.requiresTarget()) {
            addRenderableWidget(Button.builder(Component.literal(reportTargetName.isBlank() ? "Choose player" : "Player: " + trim(reportTargetName, 20)),
                    button -> minecraft.setScreenAndShow(new SupportPlayerPickerScreen(this, reportTargets, (id, name) -> {
                        reportTargetId = id;
                        reportTargetName = name;
                    }))).bounds(x + 188, y + 54, 150, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal(body.isBlank() ? "Write description" : "Edit description"), button ->
                minecraft.setScreenAndShow(new RichTextValueEditorScreen(this, "Ticket description",
                        "Describe the issue clearly. Rich text is supported.", body,
                        SupportRichText::normalize, SupportRichText.MAX_VISIBLE_CHARACTERS,
                        SupportRichText.MAX_STORED_CHARACTERS, SupportRichText.MAX_LINES,
                        value -> body = value)))
                .bounds(x + 18, y + 92, 150, 20).build());

        Button send = addRenderableWidget(Button.builder(Component.literal("Send ticket"), button -> {
            parent.createTicket(category, body, reportTargetId);
            if (minecraft != null) minecraft.setScreenAndShow(parent);
        }).bounds(x + W - 112, y + H - 34, 94, 20).build());
        send.active = SupportRichText.plainText(body).trim().length() >= 3
                && (!category.requiresTarget() || !reportTargetId.isBlank());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(x + 18, y + H - 34, 72, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "Create support ticket", x + 18, y + 16, TEXT, true);
        g.text(font, "Choose a category and write the first message.", x + 18, y + 32, MUTED, false);
        String preview = SupportRichText.compactPreview(body, 62);
        g.text(font, preview.isBlank() ? "Description: not written yet" : "Description: " + preview,
                x + 18, y + 122, preview.isBlank() ? WARNING : GOOD, false);
        if (category.requiresTarget()) {
            g.text(font, reportTargetName.isBlank() ? "A reported player is required." : "Reported player: " + reportTargetName,
                    x + 18, y + 140, reportTargetName.isBlank() ? WARNING : MUTED, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
