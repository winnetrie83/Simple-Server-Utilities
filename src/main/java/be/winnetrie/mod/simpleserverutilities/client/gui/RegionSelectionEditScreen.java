package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionToolOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated World Edit GUI using the shared Region two-point selection infrastructure. */
public final class RegionSelectionEditScreen extends Screen {
    private static final int W = 680, H = 430;
    private static final int PANEL = 0xF0161D25, SUB_PANEL = 0xD00E141B, BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFD36A, ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF;
    private static final int MAX_FILL = 64, MAX_REPLACE_SOURCE = 16, MAX_REPLACE_TARGET = 16;
    private static final int ROWS = 7;

    private final RegionSelectionToolOpenPayload selection;
    private final Screen parent;
    private final List<MixEntry> fillMix = new ArrayList<>();
    private final List<Integer> replaceSources = new ArrayList<>();
    private final List<MixEntry> replaceTargets = new ArrayList<>();
    private int page, fillPage, sourcePage, targetPage, snapshotPage;
    private boolean clipboardAvailable, replaceTargetMode = true;
    private List<String> snapshots = new ArrayList<>();
    private String selectedSnapshot = "", snapshotName = "", notice = "", pendingTransform = "";
    private boolean noticeError, requestedSnapshotContext;
    private long nextRequestId = 1L;
    private EditBox snapshotNameBox, offsetX, offsetY, offsetZ;

    public RegionSelectionEditScreen(RegionSelectionToolOpenPayload selection, Screen parent) {
        super(Component.literal("World Edit Tool"));
        this.selection = selection;
        this.parent = parent;
        this.clipboardAvailable = selection.clipboardAvailable();
    }

    @Override protected void init() {
        int left = left(), top = top();
        String[] labels = {"Clipboard", "Fill", "Replace", "Snapshots", "Transform"};
        int tabW = 104;
        for (int i = 0; i < labels.length; i++) {
            int target = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(labels[i]), ignored -> {
                page = target; notice = ""; pendingTransform = ""; rebuildWidgets();
            }).bounds(left + 16 + i * (tabW + 5), top + 50, tabW, 20).build());
            b.active = page != i;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(left + W - 84, top + H - 30, 68, 20).build());
        if (page == 0) initClipboard(left, top);
        else if (page == 1) initFill(left, top);
        else if (page == 2) initReplace(left, top);
        else if (page == 3) initSnapshots(left, top);
        else initTransform(left, top);
    }

    private void initClipboard(int left, int top) {
        int x = left + 24, y = top + 100;
        addRenderableWidget(Button.builder(Component.literal("Copy"), ignored -> send("copy", "", List.of(), List.of())).bounds(x, y, 128, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Cut"), ignored -> send("cut", "", List.of(), List.of())).bounds(x + 140, y, 128, 24).build());
        Button paste = addRenderableWidget(Button.builder(Component.literal("Paste at Point 1"), ignored -> send("paste", "", List.of(), List.of())).bounds(x + 280, y, 156, 24).build());
        paste.active = clipboardAvailable;
        addRenderableWidget(Button.builder(Component.literal("Undo"), ignored -> send("undo", "", List.of(), List.of())).bounds(x, y + 48, 128, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Redo"), ignored -> send("redo", "", List.of(), List.of())).bounds(x + 140, y + 48, 128, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Clear to air"), ignored -> send("clear_blocks", "", List.of(), List.of())).bounds(x + 280, y + 48, 156, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Fill water"), ignored -> send("fill_water", "", List.of(), List.of())).bounds(x, y + 96, 128, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Fill lava"), ignored -> send("fill_lava", "", List.of(), List.of())).bounds(x + 140, y + 96, 128, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Clear selection"), ignored -> send("clear_selection", "", List.of(), List.of())).bounds(x + 280, y + 96, 156, 24).build());
    }

    private void initFill(int left, int top) {
        int listX = left + 22, listY = top + 114;
        int from = fillPage * ROWS, to = Math.min(fillMix.size(), from + ROWS);
        for (int i = from; i < to; i++) {
            MixEntry entry = fillMix.get(i); int index = i, row = i - from, y = listY + row * 32;
            EditBox pct = new EditBox(font, listX + 224, y + 2, 48, 20, Component.literal("%"));
            pct.setMaxLength(3); pct.setFilter(v -> v.isEmpty() || v.matches("\\d{0,3}")); pct.setValue(Integer.toString(entry.percentage));
            pct.setResponder(v -> entry.percentage = parsePct(v)); addRenderableWidget(pct);
            addRenderableWidget(Button.builder(Component.literal("×"), ignored -> { fillMix.remove(index); clampFillPage(); rebuildWidgets(); })
                    .bounds(listX + 278, y + 2, 24, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Equalize %"), ignored -> { equalize(fillMix); rebuildWidgets(); }).bounds(left + 22, top + 352, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fill selection"), ignored -> submitFill()).bounds(left + 122, top + 352, 112, 20).build());
        addPageButtons(left + 246, top + 352, () -> { fillPage--; rebuildWidgets(); }, () -> { fillPage++; rebuildWidgets(); },
                fillPage > 0, (fillPage + 1) * ROWS < fillMix.size());
    }

    private void initReplace(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal(replaceTargetMode ? "Inventory adds: TARGET" : "Inventory adds: SOURCE"), ignored -> {
            replaceTargetMode = !replaceTargetMode; rebuildWidgets();
        }).bounds(left + 438, top + 88, 202, 20).build());
        int sourceFrom = sourcePage * ROWS, sourceTo = Math.min(replaceSources.size(), sourceFrom + ROWS);
        for (int i = sourceFrom; i < sourceTo; i++) {
            int index = i, row = i - sourceFrom, y = top + 124 + row * 30;
            addRenderableWidget(Button.builder(Component.literal("×"), ignored -> { replaceSources.remove(index); clampReplacePages(); rebuildWidgets(); })
                    .bounds(left + 202, y, 24, 20).build());
        }
        int targetFrom = targetPage * ROWS, targetTo = Math.min(replaceTargets.size(), targetFrom + ROWS);
        for (int i = targetFrom; i < targetTo; i++) {
            MixEntry entry = replaceTargets.get(i); int index = i, row = i - targetFrom, y = top + 124 + row * 30;
            EditBox pct = new EditBox(font, left + 376, y, 46, 20, Component.literal("%"));
            pct.setMaxLength(3); pct.setFilter(v -> v.isEmpty() || v.matches("\\d{0,3}")); pct.setValue(Integer.toString(entry.percentage));
            pct.setResponder(v -> entry.percentage = parsePct(v)); addRenderableWidget(pct);
            addRenderableWidget(Button.builder(Component.literal("×"), ignored -> { replaceTargets.remove(index); clampReplacePages(); rebuildWidgets(); })
                    .bounds(left + 426, y, 24, 20).build());
        }
        addPageButtons(left + 22, top + 352, () -> { sourcePage--; rebuildWidgets(); }, () -> { sourcePage++; rebuildWidgets(); },
                sourcePage > 0, (sourcePage + 1) * ROWS < replaceSources.size());
        addPageButtons(left + 148, top + 352, () -> { targetPage--; rebuildWidgets(); }, () -> { targetPage++; rebuildWidgets(); },
                targetPage > 0, (targetPage + 1) * ROWS < replaceTargets.size());
        addRenderableWidget(Button.builder(Component.literal("Equalize target %"), ignored -> { equalize(replaceTargets); rebuildWidgets(); }).bounds(left + 238, top + 352, 132, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Replace blocks"), ignored -> submitReplace()).bounds(left + 378, top + 352, 112, 20).build());
    }

    private void initSnapshots(int left, int top) {
        snapshotNameBox = new EditBox(font, left + 22, top + 92, 220, 20, Component.literal("Snapshot name"));
        snapshotNameBox.setHint(Component.literal("Snapshot name")); snapshotNameBox.setMaxLength(64); snapshotNameBox.setValue(snapshotName);
        snapshotNameBox.setResponder(v -> snapshotName = v); addRenderableWidget(snapshotNameBox);
        addRenderableWidget(Button.builder(Component.literal("Save full snapshot"), ignored -> saveSnapshot()).bounds(left + 250, top + 92, 132, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestSnapshotContext(true)).bounds(left + 390, top + 92, 76, 20).build());
        int pages = Math.max(1, (snapshots.size() + 7) / 8); snapshotPage = Math.max(0, Math.min(snapshotPage, pages - 1));
        int from = snapshotPage * 8, to = Math.min(snapshots.size(), from + 8);
        for (int i = from; i < to; i++) {
            String value = snapshots.get(i); int y = top + 132 + (i - from) * 27;
            Button row = addRenderableWidget(Button.builder(Component.literal(trim(value, 34)), ignored -> { selectedSnapshot = value; snapshotName = value; rebuildWidgets(); })
                    .bounds(left + 22, y, 300, 21).build());
            row.active = !value.equals(selectedSnapshot);
        }
        addPageButtons(left + 22, top + 352, () -> { snapshotPage--; rebuildWidgets(); }, () -> { snapshotPage++; rebuildWidgets(); },
                snapshotPage > 0, snapshotPage + 1 < pages);
        Button preview = addRenderableWidget(Button.builder(Component.literal("Preview"), ignored -> previewSnapshot()).bounds(left + 350, top + 190, 116, 24).build());
        Button load = addRenderableWidget(Button.builder(Component.literal("Load at Point 1"), ignored -> loadSnapshot()).bounds(left + 350, top + 222, 150, 24).build());
        preview.active = load.active = !selectedSnapshot.isBlank();
        if (!requestedSnapshotContext) requestSnapshotContext(false);
    }

    private void initTransform(int left, int top) {
        int x = left + 24, y = top + 102;
        addTransform(x, y, "Rotate left 90°", "rotate_left"); addTransform(x + 180, y, "Rotate right 90°", "rotate_right");
        addTransform(x, y + 38, "Rotate 180°", "rotate_180"); addTransform(x + 180, y + 38, "Mirror east/west", "mirror_x");
        addTransform(x, y + 76, "Mirror north/south", "mirror_z"); addTransform(x + 180, y + 76, "Flip vertically", "flip_vertical");
        offsetX = numberBox(left + 24, top + 250, 72, "0"); offsetY = numberBox(left + 116, top + 250, 72, "0"); offsetZ = numberBox(left + 208, top + 250, 72, "0");
        addRenderableWidget(Button.builder(Component.literal("Move selection"), ignored -> send("offset",
                offsetX.getValue().trim() + "," + offsetY.getValue().trim() + "," + offsetZ.getValue().trim(), List.of(), List.of()))
                .bounds(left + 300, top + 250, 126, 20).build());
    }

    private EditBox numberBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty()); box.setMaxLength(8);
        box.setFilter(v -> v.isEmpty() || v.matches("-?\\d{0,7}")); box.setValue(value); addRenderableWidget(box); return box;
    }

    private void addTransform(int x, int y, String label, String operation) {
        boolean confirm = operation.equals(pendingTransform);
        addRenderableWidget(Button.builder(Component.literal(confirm ? "Confirm " + label : label), ignored -> {
            if (confirm) { pendingTransform = ""; send(operation, "", List.of(), List.of()); }
            else { pendingTransform = operation; setNotice("Click the same transform again to confirm.", true); rebuildWidgets(); }
        }).bounds(x, y, 166, 24).build());
    }

    private void addPageButtons(int x, int y, Runnable previous, Runnable next, boolean canPrevious, boolean canNext) {
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> previous.run()).bounds(x, y, 28, 20).build()); prev.active = canPrevious;
        Button nxt = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> next.run()).bounds(x + 34, y, 28, 20).build()); nxt.active = canNext;
    }

    private void submitFill() {
        if (fillMix.isEmpty()) { setNotice("Add at least one block or fluid from your inventory.", true); return; }
        int total = fillMix.stream().mapToInt(v -> v.percentage).sum();
        if (total < 1 || total > 100 || fillMix.stream().anyMatch(v -> v.percentage < 1 || v.percentage > 100)) {
            setNotice("Fill percentages must each be 1-100 and total at most 100%.", true); return;
        }
        send("fill", "", fillMix.stream().map(v -> v.inventorySlot).toList(), fillMix.stream().map(v -> v.percentage).toList());
    }

    private void submitReplace() {
        if (replaceSources.isEmpty()) { setNotice("Add at least one source block.", true); return; }
        if (replaceTargets.isEmpty()) { setNotice("Add at least one replacement block.", true); return; }
        int total = replaceTargets.stream().mapToInt(v -> v.percentage).sum();
        if (total != 100 || replaceTargets.stream().anyMatch(v -> v.percentage < 1 || v.percentage > 100)) {
            setNotice("Replacement target percentages must total exactly 100%.", true); return;
        }
        ArrayList<Integer> slots = new ArrayList<>(), percentages = new ArrayList<>();
        for (int source : replaceSources) { slots.add(source); percentages.add(0); }
        for (MixEntry target : replaceTargets) { slots.add(target.inventorySlot); percentages.add(target.percentage); }
        send("replace", "", slots, percentages);
    }

    private void saveSnapshot() {
        String name = snapshotName.trim();
        if (!validName(name)) { setNotice("Use 1-64 letters, numbers, dots, underscores or dashes.", true); return; }
        requestedSnapshotContext = true;
        PacketDistributor.sendToServer(new RegionSetupActionPayload("save_selection_snapshot", "", name, nextRequestId++));
        setNotice("Full snapshot capture requested…", false);
    }

    private void previewSnapshot() {
        if (selectedSnapshot.isBlank()) return;
        PacketDistributor.sendToServer(new RegionSetupActionPayload("preview_snapshot", "", selectedSnapshot, nextRequestId++));
        setNotice("Loading ghost preview…", false);
    }

    private void loadSnapshot() { if (!selectedSnapshot.isBlank()) send("load_snapshot", selectedSnapshot, List.of(), List.of()); }

    private void requestSnapshotContext(boolean force) {
        if (requestedSnapshotContext && !force) return;
        requestedSnapshotContext = true;
        PacketDistributor.sendToServer(new RegionSetupRequestPayload("selection", "", nextRequestId++));
    }

    public void acceptSetupContext(RegionSetupOpenPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        snapshots = new ArrayList<>(payload.selectionSnapshots());
        if (!payload.notice().isBlank()) setNotice(payload.notice(), payload.error());
        rebuildWidgets();
    }

    public void acceptResult(RegionSelectionActionResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        clipboardAvailable = payload.clipboardAvailable(); setNotice(payload.message(), !payload.successful());
        if (payload.selectionCleared()) { if (minecraft != null) minecraft.setScreen(null); return; }
        rebuildWidgets();
    }

    private void send(String operation, String name, List<Integer> slots, List<Integer> percentages) {
        long requestId = nextRequestId++;
        PacketDistributor.sendToServer(new RegionSelectionActionPayload(operation, name, slots, percentages, requestId));
        setNotice("Request sent…", false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int)mouseX, my = (int)mouseY;
        if (page == 1 && button == 0) {
            int slot = inventorySlotAt(mx, my, 1); if (slot >= 0) { addFill(slot); return true; }
        }
        if (page == 2 && button == 0) {
            int slot = inventorySlotAt(mx, my, 2); if (slot >= 0) { addReplace(slot); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addFill(int slot) {
        if (!validWorldEditItem(slot, true)) return;
        if (fillMix.stream().anyMatch(v -> v.inventorySlot == slot)) { setNotice("That inventory slot is already in the fill palette.", true); return; }
        if (fillMix.size() >= MAX_FILL) { setNotice("The fill palette supports up to " + MAX_FILL + " entries.", true); return; }
        fillMix.add(new MixEntry(slot, 1)); equalize(fillMix); fillPage = (fillMix.size() - 1) / ROWS; rebuildWidgets();
    }

    private void addReplace(int slot) {
        if (!validWorldEditItem(slot, replaceTargetMode)) return;
        if (replaceTargetMode) {
            if (replaceTargets.stream().anyMatch(v -> v.inventorySlot == slot)) { setNotice("That target inventory slot is already selected.", true); return; }
            if (replaceTargets.size() >= MAX_REPLACE_TARGET) { setNotice("Replacement target list is full.", true); return; }
            replaceTargets.add(new MixEntry(slot, 1)); equalize(replaceTargets); targetPage = (replaceTargets.size() - 1) / ROWS;
        } else {
            if (replaceSources.contains(slot)) { setNotice("That source inventory slot is already selected.", true); return; }
            if (replaceSources.size() >= MAX_REPLACE_SOURCE) { setNotice("Replace source list is full.", true); return; }
            replaceSources.add(slot); sourcePage = (replaceSources.size() - 1) / ROWS;
        }
        rebuildWidgets();
    }

    private boolean validWorldEditItem(int slot, boolean allowFluids) {
        ItemStack stack = inventoryItem(slot);
        if (stack.isEmpty()) { setNotice("That inventory slot is empty.", true); return false; }
        if (stack.getItem() instanceof BlockItem) return true;
        if (allowFluids && (stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET))) return true;
        setNotice(allowFluids ? "Choose a block, water bucket or lava bucket." : "Choose a block item.", true); return false;
    }

    private void setNotice(String message, boolean error) {
        notice = message == null ? "" : message;
        noticeError = error;
    }

    private static void equalize(List<MixEntry> entries) {
        if (entries.isEmpty()) return; int base = 100 / entries.size(), rem = 100 % entries.size();
        for (int i = 0; i < entries.size(); i++) entries.get(i).percentage = base + (i < rem ? 1 : 0);
    }

    private void clampFillPage() { fillPage = Math.max(0, Math.min(fillPage, Math.max(0, (fillMix.size() - 1) / ROWS))); }
    private void clampReplacePages() {
        sourcePage = Math.max(0, Math.min(sourcePage, Math.max(0, (replaceSources.size() - 1) / ROWS)));
        targetPage = Math.max(0, Math.min(targetPage, Math.max(0, (replaceTargets.size() - 1) / ROWS)));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = left(), top = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000); g.fill(left, top, left + W, top + H, PANEL); g.renderOutline(left, top, W, H, BORDER);
        g.drawString(font, "World Edit Tool", left + 16, top + 13, TEXT, true);
        BlockPos p1 = BlockPos.of(selection.point1()), p2 = BlockPos.of(selection.point2());
        g.drawString(font, compact(p1) + " → " + compact(p2) + " · " + selection.volume() + " blocks · " + shortDim(selection.dimension()), left + 16, top + 30, MUTED, false);
        if (page == 0) renderClipboard(g, left, top);
        else if (page == 1) renderFill(g, left, top, mouseX, mouseY);
        else if (page == 2) renderReplace(g, left, top, mouseX, mouseY);
        else if (page == 3) renderSnapshots(g, left, top);
        else renderTransform(g, left, top);
        if (!notice.isBlank()) g.drawString(font, trim(notice, 84), left + 16, top + H - 48, noticeError ? ERROR : GOOD, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderClipboard(GuiGraphics g, int left, int top) {
        panel(g, left + 20, top + 88, 460, 178);
        g.drawString(font, "Clipboard & history", left + 32, top + 78, TEXT, true);
        g.drawString(font, "Copy/paste uses the temporary server clipboard. Cut, paste, fill, clear, replace and transforms create bounded undo history.", left + 32, top + 246, MUTED, false);
        g.drawString(font, clipboardAvailable ? "Clipboard: ready" : "Clipboard: empty", left + 500, top + 104, clipboardAvailable ? GOOD : WARNING, false);
        g.drawString(font, "Water, lava and air edits are performed as safe batched jobs.", left + 500, top + 126, MUTED, false);
    }

    private void renderFill(GuiGraphics g, int left, int top, int mx, int my) {
        panel(g, left + 16, top + 84, 330, 258); panel(g, left + 360, top + 84, 304, 160);
        g.drawString(font, "Weighted fill palette — up to " + MAX_FILL + " entries", left + 26, top + 91, TEXT, true);
        g.drawString(font, "Click inventory blocks to add. Unused % becomes air.", left + 26, top + 105, MUTED, false);
        int from = fillPage * ROWS, to = Math.min(fillMix.size(), from + ROWS), listY = top + 126;
        for (int i = from; i < to; i++) drawMixRow(g, fillMix.get(i), left + 24, listY + (i - from) * 32, mx, my, true);
        int total = fillMix.stream().mapToInt(v -> v.percentage).sum();
        g.drawString(font, "Total " + total + "% · Air " + Math.max(0, 100 - total) + "% · " + fillMix.size() + "/" + MAX_FILL, left + 390, top + 218, total <= 100 ? GOOD : ERROR, false);
        g.drawString(font, "Inventory", left + 372, top + 91, TEXT, true); renderInventory(g, left + 374, top + 112, mx, my, 1);
    }

    private void renderReplace(GuiGraphics g, int left, int top, int mx, int my) {
        panel(g, left + 16, top + 84, 210, 258); panel(g, left + 234, top + 84, 218, 258); panel(g, left + 460, top + 118, 204, 160);
        g.drawString(font, "Source blocks", left + 26, top + 92, TEXT, true); g.drawString(font, "Replaced only when matched", left + 26, top + 106, MUTED, false);
        int sf = sourcePage * ROWS, st = Math.min(replaceSources.size(), sf + ROWS);
        for (int i = sf; i < st; i++) drawSimpleRow(g, replaceSources.get(i), left + 24, top + 124 + (i - sf) * 30, mx, my);
        g.drawString(font, "Replacement palette", left + 244, top + 92, TEXT, true); g.drawString(font, "Targets must total exactly 100%", left + 244, top + 106, MUTED, false);
        int tf = targetPage * ROWS, tt = Math.min(replaceTargets.size(), tf + ROWS);
        for (int i = tf; i < tt; i++) drawMixRow(g, replaceTargets.get(i), left + 242, top + 124 + (i - tf) * 30, mx, my, false);
        g.drawString(font, "Inventory", left + 472, top + 126, TEXT, true); renderInventory(g, left + 474, top + 146, mx, my, 2);
        int total = replaceTargets.stream().mapToInt(v -> v.percentage).sum();
        g.drawString(font, "Targets: " + total + "%", left + 504, top + 302, total == 100 ? GOOD : WARNING, false);
    }

    private void renderSnapshots(GuiGraphics g, int left, int top) {
        panel(g, left + 16, top + 120, 310, 220);
        panel(g, left + 340, top + 120, 300, 168);
        g.drawString(font, "Portable full snapshots", left + 26, top + 126, TEXT, true);
        g.drawString(font, "Preserves blocks, inventories/block entities,", left + 350, top + 128, MUTED, false);
        g.drawString(font, "and structural entities.", left + 350, top + 141, MUTED, false);
        g.drawString(font, "Preview renders the real snapshot as a translucent", left + 350, top + 158, MUTED, false);
        g.drawString(font, "ghost before anything is placed.", left + 350, top + 171, MUTED, false);
        g.drawString(font, selectedSnapshot.isBlank() ? "Selected: none" : "Selected: " + trim(selectedSnapshot, 30),
                left + 350, top + 254, selectedSnapshot.isBlank() ? MUTED : GOOD, false);
    }

    private void renderTransform(GuiGraphics g, int left, int top) {
        panel(g, left + 16, top + 84, 520, 210);
        g.drawString(font, "Rotate / mirror / flip current selection", left + 26, top + 92, TEXT, true);
        g.drawString(font, "The current minimum corner remains the transform anchor. Undo history is captured first.", left + 26, top + 108, MUTED, false);
        g.drawString(font, "Move / offset selection", left + 26, top + 226, TEXT, true);
        g.drawString(font, "X", left + 26, top + 240, MUTED, false); g.drawString(font, "Y", left + 118, top + 240, MUTED, false); g.drawString(font, "Z", left + 210, top + 240, MUTED, false);
    }

    private void drawMixRow(GuiGraphics g, MixEntry entry, int x, int y, int mx, int my, boolean wide) {
        ItemStack stack = inventoryItem(entry.inventorySlot); int width = wide ? 210 : 126;
        g.fill(x, y, x + width, y + 24, SUB_PANEL); g.renderOutline(x, y, width, 24, BORDER);
        if (!stack.isEmpty()) { g.renderItem(stack, x + 4, y + 4); g.drawString(font, trim(stack.getHoverName().getString(), wide ? 20 : 10), x + 26, y + 8, TEXT, false);
            if (inside(mx, my, x, y, 24, 24)) g.renderTooltip(font, stack, mx, my); }
        g.drawString(font, entry.percentage + "%", x + width - 34, y + 8, MUTED, false);
    }

    private void drawSimpleRow(GuiGraphics g, int slot, int x, int y, int mx, int my) {
        ItemStack stack = inventoryItem(slot); g.fill(x, y, x + 174, y + 22, SUB_PANEL); g.renderOutline(x, y, 174, 22, BORDER);
        if (!stack.isEmpty()) { g.renderItem(stack, x + 3, y + 3); g.drawString(font, trim(stack.getHoverName().getString(), 17), x + 25, y + 7, TEXT, false);
            if (inside(mx, my, x, y, 22, 22)) g.renderTooltip(font, stack, mx, my); }
    }

    private void renderInventory(GuiGraphics g, int sx, int sy, int mx, int my, int context) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) drawInventorySlot(g, 9 + row * 9 + col, sx + col * 18, sy + row * 18, mx, my);
        int hotbarY = sy + 60; for (int col = 0; col < 9; col++) drawInventorySlot(g, col, sx + col * 18, hotbarY, mx, my);
    }

    private void drawInventorySlot(GuiGraphics g, int slot, int x, int y, int mx, int my) {
        boolean hover = inside(mx, my, x, y, 18, 18); g.fill(x, y, x + 18, y + 18, 0xD00B1015); g.renderOutline(x, y, 18, 18, hover ? GOOD : BORDER);
        ItemStack stack = inventoryItem(slot); if (!stack.isEmpty()) { g.renderItem(stack, x + 1, y + 1); g.renderItemDecorations(font, stack, x + 1, y + 1); if (hover) g.renderTooltip(font, stack, mx, my); }
    }

    private int inventorySlotAt(int mx, int my, int context) {
        int sx = context == 1 ? left() + 374 : left() + 474, sy = context == 1 ? top() + 112 : top() + 146;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) if (inside(mx, my, sx + col * 18, sy + row * 18, 18, 18)) return 9 + row * 9 + col;
        int hotbarY = sy + 60; for (int col = 0; col < 9; col++) if (inside(mx, my, sx + col * 18, hotbarY, 18, 18)) return col;
        return -1;
    }

    private ItemStack inventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot); return stack == null ? ItemStack.EMPTY : stack;
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) { g.fill(x, y, x + w, y + h, SUB_PANEL); g.renderOutline(x, y, w, h, BORDER); }
    private int left() { return (width - W) / 2; } private int top() { return (height - H) / 2; }
    private static int parsePct(String v) { try { return Math.max(0, Math.min(100, Integer.parseInt(v.trim()))); } catch (RuntimeException ignored) { return 0; } }
    private static boolean validName(String v) { return v != null && v.matches("[A-Za-z0-9._-]{1,64}") && !v.equals(".") && !v.equals(".."); }
    private static boolean inside(double px, double py, int x, int y, int w, int h) { return px >= x && px < x + w && py >= y && py < y + h; }
    private static String compact(BlockPos p) { return p.getX() + "," + p.getY() + "," + p.getZ(); }
    private static String shortDim(String value) { int i = value == null ? -1 : value.indexOf(':'); return i >= 0 ? value.substring(i + 1) : value; }
    private static String trim(String v, int max) { String s = v == null ? "" : v; return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…"; }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static final class MixEntry {
        private final int inventorySlot; private int percentage;
        private MixEntry(int inventorySlot, int percentage) { this.inventorySlot = inventorySlot; this.percentage = percentage; }
    }
}
