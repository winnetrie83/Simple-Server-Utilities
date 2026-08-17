package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowUpdatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Normal admin workflow for linking quests to one NPC. No condition-graph knowledge is required. */
public final class NpcQuestWorkflowScreen extends Screen {
    private static final int W = 500, H = 354, ROWS = 8;
    private NpcQuestWorkflowOpenPayload data;
    private final Screen parent;
    private EditBox search;
    private String query = "";
    private int page, selected = -1;
    private long requestId = 1L;

    public NpcQuestWorkflowScreen(NpcQuestWorkflowOpenPayload data, Screen parent) {
        super(Component.literal("NPC Quests"));
        this.data = data;
        this.parent = parent;
    }

    public void accept(NpcQuestWorkflowOpenPayload payload) {
        if (payload == null || !payload.instanceId().equals(data.instanceId())) return;
        data = payload;
        selected = -1;
        rebuildWidgets();
    }

    public void refresh() { ClientPacketDistributor.sendToServer(new NpcQuestWorkflowRequestPayload(data.instanceId())); }

    @Override protected void init() {
        int x = left(), y = top();
        search = new EditBox(font, x + 16, y + 58, 360, 18, Component.literal("Search quests"));
        search.setHint(Component.literal("Search quest title or ID…"));
        search.setValue(query);
        search.setResponder(v -> query = v);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Find"), b -> { page = 0; selected = -1; rebuildWidgets(); })
                .bounds(x + 384, y + 58, 60, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Access: " + accessLabel(data.questAccessMode())), b -> cycleAccess())
                .bounds(x + 300, y + 20, 144, 18).build());

        List<NpcQuestWorkflowOpenPayload.Entry> list = filtered();
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * ROWS, to = Math.min(list.size(), from + ROWS);
        for (int i = from; i < to; i++) {
            var e = list.get(i);
            int local = i - from;
            String prefix = relationLabel(e.relation());
            Button row = addRenderableWidget(Button.builder(Component.literal(prefix + "  •  " + trim(e.title(), 42)), v -> {
                selected = local; rebuildWidgets();
            }).bounds(x + 16, y + 88 + local * 22, W - 32, 18).build());
            row.active = selected != local;
        }

        Button prev = addRenderableWidget(Button.builder(Component.literal("‹"), b -> { page--; selected = -1; rebuildWidgets(); })
                .bounds(x + 16, y + 270, 32, 18).build());
        prev.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> { page++; selected = -1; rebuildWidgets(); })
                .bounds(x + 52, y + 270, 32, 18).build());
        next.active = page + 1 < pages;

        NpcQuestWorkflowOpenPayload.Entry current = current();
        relationButton(x + 96, y + 270, 82, "Offer", "offer", current);
        relationButton(x + 184, y + 270, 82, "Turn-in", "turnin", current);
        relationButton(x + 272, y + 270, 82, "Both", "both", current);
        relationButton(x + 360, y + 270, 82, "Unlink", "none", current);

        Button config = addRenderableWidget(Button.builder(Component.literal("Configure dialogue"), b -> configure())
                .bounds(x + 96, y + 294, 150, 18).build());
        config.active = current != null && !"none".equals(current.relation());
        addRenderableWidget(Button.builder(Component.literal("+ Create quest"), b -> createQuest())
                .bounds(x + 254, y + 294, 118, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x + 16, y + H - 28, 70, 18).build());
    }

    private void relationButton(int x, int y, int width, String label, String relation, NpcQuestWorkflowOpenPayload.Entry current) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), b -> setRelation(relation))
                .bounds(x, y, width, 18).build());
        button.active = current != null && !relation.equals(current.relation());
    }

    private List<NpcQuestWorkflowOpenPayload.Entry> filtered() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) return data.quests();
        ArrayList<NpcQuestWorkflowOpenPayload.Entry> out = new ArrayList<>();
        for (var e : data.quests()) if (e.questId().toLowerCase(Locale.ROOT).contains(q)
                || e.title().toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        return out;
    }

    private NpcQuestWorkflowOpenPayload.Entry current() {
        List<NpcQuestWorkflowOpenPayload.Entry> list = filtered();
        int i = page * ROWS + selected;
        return selected < 0 || i < 0 || i >= list.size() ? null : list.get(i);
    }

    private void setRelation(String relation) {
        var e = current(); if (e == null) return;
        if ("menu".equalsIgnoreCase(data.questAccessMode()) && !"none".equals(relation)) {
            showAccessPrompt(() -> sendSave(e, relation, "npc"), () -> sendSave(e, relation, "both"));
            return;
        }
        sendSave(e, relation, "");
    }

    private void configure() {
        var e = current();
        if (e != null && minecraft != null) minecraft.setScreenAndShow(new NpcQuestWorkflowDialogueScreen(this, e));
    }

    public void saveConfigured(NpcQuestWorkflowOpenPayload.Entry e) {
        if (minecraft != null) minecraft.setScreenAndShow(this);
        if ("menu".equalsIgnoreCase(data.questAccessMode()) && !"none".equals(e.relation())) {
            showAccessPrompt(() -> sendSave(e, e.relation(), "npc"), () -> sendSave(e, e.relation(), "both"));
            return;
        }
        sendSave(e, e.relation(), "");
    }

    private void createQuest() {
        if ("menu".equalsIgnoreCase(data.questAccessMode())) {
            showAccessPrompt(() -> sendCreate("npc"), () -> sendCreate("both")); return;
        }
        sendCreate("");
    }

    private void showAccessPrompt(Runnable npc, Runnable both) {
        if (minecraft != null) minecraft.setScreenAndShow(new NpcQuestAccessPromptScreen(this, npc, both));
    }

    private void sendCreate(String access) {
        ClientPacketDistributor.sendToServer(new NpcQuestWorkflowUpdatePayload(data.instanceId(), "create", "", "both", access,
                "", "", "", "", "", "", true, true, true, requestId++));
    }

    private void sendSave(NpcQuestWorkflowOpenPayload.Entry e, String relation, String access) {
        ClientPacketDistributor.sendToServer(new NpcQuestWorkflowUpdatePayload(data.instanceId(), "save", e.questId(), relation, access,
                e.availableText(), e.acceptText(), e.activeText(), e.readyText(), e.turnInText(), e.completedText(),
                e.showAvailable(), e.showActive(), e.showReady(), requestId++));
    }

    private void cycleAccess() {
        String next = switch (data.questAccessMode().toLowerCase(Locale.ROOT)) {
            case "menu" -> "npc"; case "npc" -> "both"; default -> "menu";
        };
        ClientPacketDistributor.sendToServer(new NpcQuestWorkflowUpdatePayload(data.instanceId(), "access", "", "", next,
                "", "", "", "", "", "", true, true, true, requestId++));
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, 0xF0161D25);
        g.outline(x, y, W, H, 0xFF586978);
        g.text(font, data.npcName() + " — Quests", x + 16, y + 18, 0xFFF3F5F7, true);
        g.text(font, "Select a quest, choose Offer / Turn-in / Both, then optionally edit its dialogue.", x + 16, y + 42, 0xFFAAB5BE, false);
        if (!data.notice().isBlank()) g.text(font, trim(data.notice(), 70), x + 94, y + H - 23, 0xFF83E39A, false);
        super.extractRenderState(g, mx, my, pt);
    }

    private int left() { return Math.max(4, (width - W) / 2); }
    private int top() { return Math.max(4, (height - H) / 2); }
    private static String trim(String s, int max) { String v = s == null ? "" : s; return v.length() <= max ? v : v.substring(0, max - 1) + "…"; }
    private static String relationLabel(String r) { return switch (r) { case "both" -> "Offer + turn-in"; case "offer" -> "Offer"; case "turnin" -> "Turn-in"; default -> "Not linked"; }; }
    private static String accessLabel(String m) { return switch (m.toLowerCase(Locale.ROOT)) { case "npc" -> "NPCs"; case "both" -> "Both"; default -> "Quest Menu"; }; }
}
