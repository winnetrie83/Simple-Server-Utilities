package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextComponents;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueChoice;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Safe client-only graph preview. It never evaluates conditions or executes actions/services. */
public final class NpcDialoguePreviewScreen extends Screen {
    private static final int W = 560, H = 412;
    private static final int PANEL = 0xF0181E25, BORDER = 0xFF637887, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, WARNING = 0xFFFFD27A;

    private final Screen parent;
    private final NpcDialogueDefinition dialogue;
    private final String npcName;
    private String nodeId;
    private String notice = "Preview mode: conditions pass; actions and services are not executed.";
    private boolean finished;

    public NpcDialoguePreviewScreen(Screen parent, NpcDialogueDefinition dialogue, String npcName, String startAt) {
        super(Component.literal("Dialogue preview"));
        this.parent = parent;
        this.dialogue = dialogue == null ? new NpcDialogueDefinition() : dialogue.copy();
        this.npcName = npcName == null || npcName.isBlank() ? "Preview NPC" : npcName;
        try {
            this.dialogue.normalize();
        } catch (RuntimeException exception) {
            this.finished = true;
            this.notice = exception.getMessage() == null ? "Preview data is invalid." : exception.getMessage();
        }
        String requested = startAt == null ? "" : startAt;
        this.nodeId = this.dialogue.node(requested) == null ? this.dialogue.startNode : be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(requested);
        if (!finished) enterNode();
    }

    @Override
    protected void init() {
        int x = px(), y = py();
        NpcDialogueNode node = currentNode();
        if (!finished && node != null) {
            int rowY = y + 176;
            int shown = 0;
            List<NpcDialogueChoice> choices = node.choices == null ? List.of() : node.choices;
            for (NpcDialogueChoice choice : choices) {
                if (choice == null || shown >= 8) continue;
                Button button = addRenderableWidget(Button.builder(Component.literal(choice.text == null || choice.text.isBlank() ? "Continue" : choice.text),
                        ignored -> choose(choice)).bounds(x + 20, rowY + shown * 24, W - 40, 20).build());
                String condition = conditionSummary(choice.condition);
                String tooltip = "Preview assumes this condition passes: " + condition;
                if (!choice.service.isBlank()) tooltip += "\nService: " + choice.service + (choice.serviceTarget.isBlank() ? "" : " → " + choice.serviceTarget);
                if (!choice.actions.isEmpty()) tooltip += "\nWould execute " + choice.actions.size() + " choice action(s).";
                button.setTooltip(Tooltip.create(Component.literal(tooltip)));
                shown++;
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Reset"), ignored -> reset())
                .bounds(x + 20, y + H - 26, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back to editor"), ignored -> onClose())
                .bounds(x + W - 120, y + H - 26, 100, 20).build());
    }

    private void choose(NpcDialogueChoice choice) {
        StringBuilder result = new StringBuilder();
        if (choice.actions != null && !choice.actions.isEmpty()) result.append("Would execute ").append(choice.actions.size()).append(" choice action(s). ");
        if (choice.service != null && !choice.service.isBlank()) {
            result.append("Would call ").append(choice.service);
            if (choice.serviceTarget != null && !choice.serviceTarget.isBlank()) result.append(" → ").append(choice.serviceTarget);
            result.append(". ");
        }
        String next = choice.nextNode == null ? "" : choice.nextNode;
        if (choice.closeDialogue) {
            finished = true;
            notice = result + "Dialogue would close.";
        } else if (!next.isBlank() && dialogue.node(next) != null) {
            nodeId = next;
            notice = result + "Moved to node '" + next + "'.";
            enterNode();
        } else if (!next.isBlank()) {
            finished = true;
            notice = result + "Preview stopped: next node '" + next + "' is missing.";
        } else {
            notice = result + "No next node: runtime would remain on the current node unless the service closes it.";
        }
        rebuildWidgets();
    }

    private void enterNode() {
        NpcDialogueNode node = currentNode();
        if (node == null) {
            finished = true;
            notice = "Preview stopped: node '" + nodeId + "' does not exist.";
            return;
        }
        finished = false;
        if (node.enterActions != null && !node.enterActions.isEmpty()) {
            notice = "Entered '" + node.id + "'; would execute " + node.enterActions.size() + " entry action(s).";
        }
    }

    private void reset() {
        nodeId = dialogue.startNode;
        notice = "Preview reset. Conditions pass; actions and services are not executed.";
        finished = false;
        enterNode();
        rebuildWidgets();
    }

    private NpcDialogueNode currentNode() {
        return dialogue.node(nodeId);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        SsuGuiScale.fullscreenDim(graphics, this, 0xA5000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, "Dialogue preview • " + trim(dialogue.displayName, 52), x + 20, y + 14, TEXT, true);
        NpcDialogueNode node = currentNode();
        if (node == null || finished) {
            graphics.text(font, finished ? "Preview branch finished" : "Missing preview node", x + 20, y + 42, WARNING, true);
            graphics.text(font, trim(notice, 84), x + 20, y + 66, TEXT, false);
        } else {
            graphics.text(font, node.speaker == null || node.speaker.isBlank() ? npcName : node.speaker, x + 20, y + 38, GOOD, false);
            List<FormattedCharSequence> lines = font.split(SsuRichTextComponents.parse(node.text == null ? "" : node.text), W - 40);
            for (int index = 0; index < Math.min(10, lines.size()); index++) {
                graphics.text(font, lines.get(index), x + 20, y + 56 + index * 10, TEXT, false);
            }
            graphics.text(font, trim(notice, 84), x + 20, y + 160, MUTED, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static String conditionSummary(ContentCondition condition) {
        if (condition == null) return "always";
        String summary = condition.type();
        if (!condition.parameters().isEmpty()) summary += " " + condition.parameters();
        if (!condition.children().isEmpty()) summary += " +" + condition.children().size();
        return summary;
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum - 1) + "…";
    }
}
