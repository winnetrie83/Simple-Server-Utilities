package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.statistics.StatisticEventType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Create/edit screen for an indexed custom player statistic definition. */
public final class StatisticEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 486;
    private static final int PANEL_HEIGHT = 258;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;

    private final StatisticEditorOpenPayload initial;
    private final Screen parent;
    private EditBox id;
    private EditBox displayName;
    private EditBox target;
    private EditBox unit;
    private StatisticEventType eventType;
    private boolean enabled;
    private Button typeButton;
    private Button enabledButton;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;

    public StatisticEditorScreen(StatisticEditorOpenPayload initial, Screen parent) {
        super(Component.literal(initial.editing() ? "Edit Player Statistic" : "Create Player Statistic"));
        this.initial = initial;
        this.parent = parent;
        this.eventType = initial.eventType();
        this.enabled = initial.enabled();
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        id = field(x + 16, y + 48, 214, "Unique ID", 64, initial.id());
        displayName = field(x + 242, y + 48, 228, "Display name", 64, initial.displayName());
        typeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleType())
                .bounds(x + 16, y + 98, 214, 20).build());
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            enabled = !enabled;
            updateLabels();
        }).bounds(x + 242, y + 98, 228, 20).build());
        target = field(x + 16, y + 148, 304, "* or registry ID", 128, initial.target());
        unit = field(x + 332, y + 148, 138, "Unit", 24, initial.unit());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 16, y + 220, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal(initial.editing() ? "Save changes" : "Create statistic"), ignored -> submit())
                .bounds(x + PANEL_WIDTH - 146, y + 220, 130, 20).build());
        updateLabels();
        setInitialFocus(id);
    }

    private EditBox field(int x, int y, int width, String hint, int maximum, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maximum);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private void cycleType() {
        StatisticEventType[] values = StatisticEventType.values();
        eventType = values[(eventType.ordinal() + 1) % values.length];
        if (!eventType.targetSupported()) target.setValue("*");
        if (unit.getValue().isBlank() || isKnownDefaultUnit(unit.getValue())) unit.setValue(eventType.defaultUnit());
        updateLabels();
    }

    private static boolean isKnownDefaultUnit(String value) {
        for (StatisticEventType type : StatisticEventType.values()) {
            if (type.defaultUnit().equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private void updateLabels() {
        if (typeButton == null) return;
        typeButton.setMessage(Component.literal("Event: " + label(eventType)));
        enabledButton.setMessage(Component.literal("Tracking: " + (enabled ? "ACTIVE" : "PAUSED")));
        target.setEditable(eventType.targetSupported());
    }

    private void submit() {
        String rawId = id.getValue().trim();
        String rawName = displayName.getValue().trim();
        if (!rawId.matches("[A-Za-z0-9._-]{1,64}")) {
            notice = "Use 1-64 letters, numbers, dots, underscores or dashes for the ID.";
            noticeError = true;
            return;
        }
        if (rawName.isBlank()) {
            notice = "Enter a display name.";
            noticeError = true;
            return;
        }
        String rawTarget = eventType.targetSupported() ? target.getValue().trim() : "*";
        if (rawTarget.isBlank()) rawTarget = "*";
        if (!"*".equals(rawTarget) && !rawTarget.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            notice = "Target must be * or a registry ID such as minecraft:diamond_ore.";
            noticeError = true;
            return;
        }
        long requestId = nextRequestId++;
        PacketDistributor.sendToServer(new StatisticEditorSubmitPayload(
                initial.originalId(), rawId, rawName, eventType, rawTarget, unit.getValue(), enabled, requestId));
        notice = "Saving…";
        noticeError = false;
    }

    public void acceptResult(StatisticEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        if (!payload.successful()) {
            notice = payload.message();
            noticeError = true;
            return;
        }
        if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(payload.message()));
        if (parent instanceof SsuDashboardScreen dashboard) dashboard.refreshRemotePage();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        g.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        g.drawString(font, initial.editing() ? "Edit Custom Player Statistic" : "Create Custom Player Statistic", x + 16, y + 14, TEXT, true);
        g.drawString(font, "ID", x + 16, y + 36, MUTED, false);
        g.drawString(font, "Display name", x + 242, y + 36, MUTED, false);
        g.drawString(font, "Event and tracking state", x + 16, y + 84, MUTED, false);
        g.drawString(font, eventType.targetSupported() ? "Target filter (* = all)" : "Target filter (not used by this event)", x + 16, y + 136, MUTED, false);
        g.drawString(font, "Unit", x + 332, y + 136, MUTED, false);
        g.drawString(font, "Damage values are stored with 0.01 precision; all other event types use whole counts.", x + 16, y + 182, MUTED, false);
        g.drawString(font, "Floating Text: objective ssu:<id>, or tokens {{stat:<id>}} and {{rank:<id>}}.", x + 16, y + 194, MUTED, false);
        if (!notice.isBlank()) g.drawString(font, trim(notice, 68), x + 112, y + 226, noticeError ? ERROR : GOOD, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int panelX() { return (width - PANEL_WIDTH) / 2; }
    private int panelY() { return (height - PANEL_HEIGHT) / 2; }

    private static String label(StatisticEventType type) {
        String value = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 3)) + "...";
    }
}
