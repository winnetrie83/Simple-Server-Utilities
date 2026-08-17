package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.TitleManagerActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.TitleManagerDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.TitleManagerRequestPayload;
import be.winnetrie.mod.simpleserverutilities.settings.MinecraftColorPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Player title picker and administrator title-catalogue editor. */
public final class TitleManagerScreen extends Screen {
    private static final int W = 680, H = 410, VISIBLE_ROWS = 11;
    private static final int PANEL = 0xF0161D25, SUB = 0xD010151C, BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, ACCENT = 0xFF7FC8FF;
    private static final int GOOD = 0xFF83E39A, ERROR = 0xFFFF8585, WARN = 0xFFFFD36A;

    private TitleManagerDataPayload data;
    private final Screen parent;
    private int page;
    private int selectedIndex = -1;
    private long requestId;
    private String notice = "";
    private boolean noticeError;

    private EditBox idBox;
    private EditBox nameBox;
    private EditBox requirementBox;
    private EditBox requirementValueBox;
    private EditBox targetPlayerBox;
    private int selectedColor;
    private String selectedUnlockType = "FREE";
    private String draftId = "";
    private String draftName = "";
    private String draftRequirement = "0";
    private String draftRequirementValue = "";
    private String pendingSelectionId = "";

    public TitleManagerScreen(TitleManagerDataPayload data, Screen parent) {
        super(Component.literal(data.adminView() ? "Title Administration" : "Titles"));
        this.data = data;
        this.parent = parent;
        this.requestId = Math.max(1L, data.requestId() + 1L);
        this.notice = data.notice();
        this.noticeError = data.error();
        this.selectedIndex = initialSelection(data.titles(), data.selectedTitleId());
        loadSelectedDraft();
    }

    public void accept(TitleManagerDataPayload payload) {
        if (payload == null) return;
        String previousId = !pendingSelectionId.isBlank()
                ? pendingSelectionId
                : selectedEntry() == null ? "" : selectedEntry().id();
        pendingSelectionId = "";
        data = payload;
        requestId = Math.max(requestId, payload.requestId() + 1L);
        notice = payload.notice();
        noticeError = payload.error();
        selectedIndex = findIndex(payload.titles(), previousId);
        if (selectedIndex < 0) selectedIndex = initialSelection(payload.titles(), payload.selectedTitleId());
        clampPage();
        loadSelectedDraft();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        int listX = x + 14, listY = y + 48, listW = data.adminView() ? 296 : 330;
        int start = page * VISIBLE_ROWS;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = start + row;
            if (index >= data.titles().size()) break;
            TitleManagerDataPayload.Entry entry = data.titles().get(index);
            String marker = entry.selected() ? "✓ " : entry.unlocked() ? "◆ " : "◇ ";
            String label = marker + entry.displayName() + (entry.enabled() ? "" : " (disabled)");
            Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> selectIndex(index))
                    .bounds(listX, listY + row * 25, listW, 21).build());
            button.active = selectedIndex != index;
        }
        int pages = Math.max(1, (data.titles().size() + VISIBLE_ROWS - 1) / VISIBLE_ROWS);
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> { page--; rebuildWidgets(); })
                .bounds(listX, y + H - 54, 26, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> { page++; rebuildWidgets(); })
                .bounds(listX + 62, y + H - 54, 26, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> refresh())
                .bounds(listX + 98, y + H - 54, 72, 20).build());

        if (data.adminView()) initAdminControls(x, y);
        else initPlayerControls(x, y);

        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 76, y + H - 30, 62, 20).build());
    }

    private void initPlayerControls(int x, int y) {
        TitleManagerDataPayload.Entry entry = selectedEntry();
        Button select = addRenderableWidget(Button.builder(Component.literal("Select title"), ignored -> {
                    if (entry != null) action("select", entry.id());
                }).bounds(x + 374, y + 270, 142, 22).build());
        select.active = entry != null && entry.enabled() && entry.unlocked() && !entry.selected();
    }

    private void initAdminControls(int x, int y) {
        int fx = x + 330, fy = y + 62;
        idBox = field(fx, fy, 154, "Title ID", 64, draftId);
        // The player-facing title name now has the full editor-column width instead of half a row.
        nameBox = field(fx, fy + 30, 320, "Title display name", 48, draftName);
        requirementBox = field(fx, fy + 68, 100, "Amount", 12, draftRequirement);
        requirementValueBox = field(fx + 108, fy + 68, 212, "Rank / permission", 128, draftRequirementValue);
        targetPlayerBox = field(fx, fy + 286, 212, "Online player", 64, "");

        addRenderableWidget(Button.builder(Component.literal("Color: " + MinecraftColorPalette.name(selectedColor)), ignored -> {
                    captureDraft(); selectedColor = MinecraftColorPalette.next(selectedColor); rebuildWidgets();
                }).bounds(fx, fy + 106, 154, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unlock: " + unlockLabel(selectedUnlockType)), ignored -> {
                    captureDraft(); selectedUnlockType = nextUnlockType(selectedUnlockType); rebuildWidgets();
                }).bounds(fx + 162, fy + 106, 158, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> saveDraft())
                .bounds(fx, fy + 144, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("New"), ignored -> newDraft())
                .bounds(fx + 80, fy + 144, 76, 20).build());
        Button toggle = addRenderableWidget(Button.builder(Component.literal(selectedEntry() != null && selectedEntry().enabled()
                        ? "Disable" : "Enable"), ignored -> action("toggle", selectedEntryId()))
                .bounds(fx + 160, fy + 144, 76, 20).build());
        toggle.active = selectedEntry() != null;
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> action("delete", selectedEntryId()))
                .bounds(fx + 240, fy + 144, 80, 20).build());
        delete.active = selectedEntry() != null && !"rookie".equals(selectedEntry().id());

        Button grant = addRenderableWidget(Button.builder(Component.literal("Grant"), ignored -> grantRevoke("grant"))
                .bounds(fx + 220, fy + 286, 48, 20).build());
        Button revoke = addRenderableWidget(Button.builder(Component.literal("Revoke"), ignored -> grantRevoke("revoke"))
                .bounds(fx + 272, fy + 286, 48, 20).build());
        grant.active = revoke.active = selectedEntry() != null;
    }

    private EditBox field(int x, int y, int width, String hint, int maximum, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maximum);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private void selectIndex(int index) {
        selectedIndex = index;
        loadSelectedDraft();
        rebuildWidgets();
    }

    private void loadSelectedDraft() {
        TitleManagerDataPayload.Entry entry = selectedEntry();
        if (entry == null) {
            selectedColor = MinecraftColorPalette.COLORS.getFirst().argb();
            selectedUnlockType = "FREE";
            draftId = "";
            draftName = "";
            draftRequirement = "0";
            draftRequirementValue = "";
        } else {
            selectedColor = MinecraftColorPalette.nearest(entry.color());
            selectedUnlockType = entry.unlockType();
            draftId = entry.id();
            draftName = entry.displayName();
            draftRequirement = Long.toString(entry.requirement());
            draftRequirementValue = entry.requirementValue();
        }
    }

    private void captureDraft() {
        if (idBox != null) draftId = idBox.getValue();
        if (nameBox != null) draftName = nameBox.getValue();
        if (requirementBox != null) draftRequirement = requirementBox.getValue();
        if (requirementValueBox != null) draftRequirementValue = requirementValueBox.getValue();
    }

    private void newDraft() {
        selectedIndex = -1;
        selectedColor = MinecraftColorPalette.COLORS.getFirst().argb();
        selectedUnlockType = "FREE";
        draftId = "";
        draftName = "";
        draftRequirement = "0";
        draftRequirementValue = "";
        notice = "Enter a new title ID, display name and acquisition rule.";
        noticeError = false;
        rebuildWidgets();
    }

    private void saveDraft() {
        String id = idBox == null ? "" : idBox.getValue();
        String name = nameBox == null ? "" : nameBox.getValue();
        long requirement;
        try { requirement = Long.parseLong(requirementBox == null || requirementBox.getValue().isBlank()
                ? "0" : requirementBox.getValue()); }
        catch (NumberFormatException exception) { notice = "Requirement must be a whole number."; noticeError = true; return; }
        draftId = id;
        draftName = name;
        draftRequirement = Long.toString(requirement);
        draftRequirementValue = requirementValueBox == null ? "" : requirementValueBox.getValue();
        pendingSelectionId = normalizeId(id);
        send(new TitleManagerActionPayload("save", id, name, selectedColor, selectedUnlockType,
                requirement, draftRequirementValue, "", requestId++));
    }

    private void grantRevoke(String action) {
        String target = targetPlayerBox == null ? "" : targetPlayerBox.getValue();
        send(new TitleManagerActionPayload(action, selectedEntryId(), "", selectedColor, selectedUnlockType,
                0L, "", target, requestId++));
    }

    private void action(String action, String id) {
        send(new TitleManagerActionPayload(action, id, "", selectedColor, selectedUnlockType,
                0L, "", "", requestId++));
    }

    private void send(TitleManagerActionPayload payload) {
        PacketDistributor.sendToServer(payload);
        notice = "Updating…";
        noticeError = false;
    }

    private void refresh() {
        PacketDistributor.sendToServer(new TitleManagerRequestPayload(data.adminView(), requestId++));
        notice = "Refreshing…";
        noticeError = false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL); g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, data.adminView() ? "Title Administration" : "Player Titles", x + 14, y + 14, TEXT, true);
        g.drawString(font, data.adminView()
                ? "Create global titles and define how players unlock them."
                : "Choose one unlocked title for your player profile and nameplate.", x + 14, y + 29, MUTED, false);
        int divider = data.adminView() ? x + 320 : x + 356;
        g.fill(divider, y + 46, divider + 1, y + H - 42, BORDER);

        TitleManagerDataPayload.Entry entry = selectedEntry();
        if (entry != null) {
            int dx = data.adminView() ? x + 330 : x + 374;
            int dy = data.adminView() ? y + 238 : y + 70;
            g.drawString(font, entry.displayName(), dx, dy, entry.color(), true);
            g.drawString(font, "ID: " + entry.id(), dx, dy + 18, MUTED, false);
            g.drawString(font, "Status: " + (entry.enabled() ? "Enabled" : "Disabled"), dx, dy + 34,
                    entry.enabled() ? GOOD : ERROR, false);
            g.drawString(font, "Unlocked: " + (entry.unlocked() ? "Yes" : "No"), dx, dy + 50,
                    entry.unlocked() ? GOOD : WARN, false);
            g.drawString(font, "Obtained: " + trim(entry.acquisition(), 42), dx, dy + 66, TEXT, false);
            if (!data.adminView()) {
                g.drawString(font, entry.selected() ? "Currently selected" : "", dx, dy + 88, GOOD, false);
            } else {
                g.drawString(font, "Manual player grant / revoke", dx, y + 332, ACCENT, true);
            }
        } else {
            int dx = data.adminView() ? x + 330 : x + 374;
            g.drawString(font, "No title selected.", dx, y + 70, MUTED, false);
        }
        if (!notice.isBlank()) g.drawString(font, trim(notice, 80), x + 14, y + H - 26,
                noticeError ? ERROR : GOOD, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private void clampPage() { page = Math.max(0, Math.min(page, Math.max(0, (data.titles().size() - 1) / VISIBLE_ROWS))); }
    private TitleManagerDataPayload.Entry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < data.titles().size() ? data.titles().get(selectedIndex) : null;
    }
    private String selectedEntryId() { return selectedEntry() == null ? "" : selectedEntry().id(); }
    private static int initialSelection(List<TitleManagerDataPayload.Entry> entries, String selected) {
        int index = findIndex(entries, selected); return index >= 0 ? index : entries.isEmpty() ? -1 : 0;
    }
    private static int findIndex(List<TitleManagerDataPayload.Entry> entries, String id) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equalsIgnoreCase(id)) return i;
        return -1;
    }
    private static String nextUnlockType(String value) {
        String[] types = {"FREE", "MINIGAME_LEVEL", "MINIGAME_WINS", "RANK", "PERMISSION", "MANUAL"};
        for (int i = 0; i < types.length; i++) if (types[i].equalsIgnoreCase(value)) return types[(i + 1) % types.length];
        return "FREE";
    }
    private static String unlockLabel(String value) { return value.toLowerCase(Locale.ROOT).replace('_', ' '); }
    private static String normalizeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        value = value.replaceAll("[^a-z0-9_./]", "");
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
    private static String trim(String value, int maximum) {
        if (value == null) return ""; return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }
}
