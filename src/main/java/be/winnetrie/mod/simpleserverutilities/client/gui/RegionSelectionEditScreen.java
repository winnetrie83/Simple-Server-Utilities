package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.client.region.RegionSelectionClientStorage;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionClientTemplateUploadPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionToolOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Block-editing, clipboard and template GUI for one current region selection. */
public final class RegionSelectionEditScreen extends Screen {
    private static final int W = 650;
    private static final int H = 420;
    private static final int PANEL = 0xF0161D25;
    private static final int SUB_PANEL = 0xD00E141B;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFD36A;
    private static final int ERROR = 0xFFFF8585;
    private static final int MAX_MIX = 6;

    private final RegionSelectionToolOpenPayload selection;
    private final Screen parent;
    private final List<MixEntry> mix = new ArrayList<>();
    private final List<TemplateRow> templateRows = new ArrayList<>();
    private List<String> serverTemplates;
    private List<String> clientTemplates = List.of();
    private int page;
    private boolean clipboardAvailable;
    private boolean serverSource = true;
    private int templatePage;
    private String selectedTemplate = "";
    private String templateName = "";
    private EditBox templateNameBox;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;
    private boolean confirmClear;
    private boolean confirmPaste;
    private boolean confirmFill;
    private boolean confirmLoad;
    private boolean confirmSave;
    private String pendingTransform = "";

    public RegionSelectionEditScreen(RegionSelectionToolOpenPayload selection, Screen parent) {
        super(Component.literal("Edit Region Selection"));
        this.selection = selection;
        this.parent = parent;
        this.serverTemplates = new ArrayList<>(selection.serverTemplates());
        this.clipboardAvailable = selection.clipboardAvailable();
        refreshClientTemplates();
    }

    @Override
    protected void init() {
        int left = left();
        int top = top();
        int tabWidth = 112;
        addTab(left + 16, top + 52, tabWidth, "Actions", 0);
        addTab(left + 132, top + 52, tabWidth, "Fill mix", 1);
        addTab(left + 248, top + 52, tabWidth, "Templates", 2);
        addTab(left + 364, top + 52, tabWidth, "Transform", 3);
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(left + 16, top + H - 31, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> {
                    if (minecraft != null) minecraft.setScreenAndShow(null);
                }).bounds(left + W - 88, top + H - 31, 72, 20).build());
        if (page == 0) addActions(left, top);
        else if (page == 1) addFill(left, top);
        else if (page == 2) addTemplates(left, top);
        else addTransforms(left, top);
    }

    private void addTab(int x, int y, int width, String label, int target) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
            page = target;
            notice = "";
            confirmClear = false;
            confirmPaste = false;
            confirmFill = false;
            confirmLoad = false;
            confirmSave = false;
            pendingTransform = "";
            rebuildWidgets();
        }).bounds(x, y, width, 20).build());
        button.active = page != target;
    }

    private void addActions(int left, int top) {
        int x = left + 28;
        int y = top + 104;
        addRenderableWidget(Button.builder(Component.literal("Copy selection"), ignored -> send("copy", "", List.of(), List.of()))
                .bounds(x, y, 180, 24).build());
        Button paste = addRenderableWidget(Button.builder(
                        Component.literal(confirmPaste ? "Confirm paste at point 1" : "Paste clipboard at point 1"), ignored -> {
                    if (confirmPaste) {
                        confirmPaste = false;
                        send("paste", "", List.of(), List.of());
                    } else {
                        confirmPaste = true;
                        notice = "Paste replaces all destination blocks; container contents there are deleted without drops.";
                        noticeError = true;
                        rebuildWidgets();
                    }
                }).bounds(x, y + 34, 180, 24).build());
        paste.active = clipboardAvailable;
        addRenderableWidget(Button.builder(Component.literal(confirmClear ? "Confirm clear blocks" : "Clear selected blocks"), ignored -> {
                    if (confirmClear) {
                        confirmClear = false;
                        send("clear_blocks", "", List.of(), List.of());
                    } else {
                        confirmClear = true;
                        notice = "Clear replaces the selection with air; container contents are deleted without drops.";
                        noticeError = true;
                        rebuildWidgets();
                    }
                }).bounds(x, y + 68, 180, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Clear selection points"), ignored -> send("clear_selection", "", List.of(), List.of()))
                .bounds(x, y + 102, 180, 24).build());
    }

    private void addFill(int left, int top) {
        int listX = left + 26;
        int listY = top + 142;
        for (int i = 0; i < mix.size(); i++) {
            MixEntry entry = mix.get(i);
            int row = i;
            int y = listY + i * 31;
            EditBox percentage = new EditBox(font, listX + 214, y + 2, 52, 20, Component.literal("Percent"));
            percentage.setMaxLength(3);
            percentage.setFilter(value -> value.isEmpty() || value.matches("\\d{0,3}"));
            percentage.setValue(Integer.toString(entry.percentage));
            percentage.setResponder(value -> {
                entry.percentage = parsePercentage(value);
                confirmFill = false;
            });
            addRenderableWidget(percentage);
            addRenderableWidget(Button.builder(Component.literal("Remove"), ignored -> {
                        mix.remove(row);
                        confirmFill = false;
                        equalizePercentages();
                        rebuildWidgets();
                    }).bounds(listX + 272, y + 2, 62, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Equalize %"), ignored -> {
                    confirmFill = false;
                    equalizePercentages();
                    rebuildWidgets();
                }).bounds(left + 28, top + 356, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal(confirmFill ? "Confirm fill" : "Fill selection"), ignored -> {
                    if (confirmFill) {
                        confirmFill = false;
                        submitFill();
                    } else {
                        confirmFill = true;
                        notice = "Fill replaces every selected block; container contents are deleted without drops.";
                        noticeError = true;
                        rebuildWidgets();
                    }
                }).bounds(left + 124, top + 356, 110, 20).build());
    }

    private void addTransforms(int left, int top) {
        int x = left + 28;
        int y = top + 104;
        addTransformButton(x, y, "Rotate left 90°", "rotate_left");
        addTransformButton(x + 190, y, "Rotate right 90°", "rotate_right");
        addTransformButton(x, y + 36, "Rotate 180°", "rotate_180");
        addTransformButton(x + 190, y + 36, "Mirror east / west", "mirror_x");
        addTransformButton(x, y + 72, "Mirror north / south", "mirror_z");
        addTransformButton(x + 190, y + 72, "Flip vertically", "flip_vertical");
    }

    private void addTransformButton(int x, int y, String label, String operation) {
        boolean confirming = pendingTransform.equals(operation);
        addRenderableWidget(Button.builder(Component.literal(confirming ? "Confirm " + label : label), ignored -> {
            if (confirming) {
                pendingTransform = "";
                send(operation, "", List.of(), List.of());
            } else {
                pendingTransform = operation;
                notice = "This replaces the selected blocks in place. Click the same transform again to confirm.";
                noticeError = true;
                rebuildWidgets();
            }
        }).bounds(x, y, 176, 24).build());
    }

    private void addTemplates(int left, int top) {
        int y = top + 96;
        addRenderableWidget(Button.builder(Component.literal(serverSource ? "Storage: SERVER" : "Storage: CLIENT"), ignored -> {
                    serverSource = !serverSource;
                    templatePage = 0;
                    selectedTemplate = "";
                    confirmLoad = false;
                    confirmSave = false;
                    if (!serverSource) refreshClientTemplates();
                    rebuildWidgets();
                }).bounds(left + 26, y, 132, 20).build());
        templateNameBox = new EditBox(font, left + 166, y, 220, 20, Component.literal("Template name"));
        templateNameBox.setHint(Component.literal("Template name"));
        templateNameBox.setMaxLength(64);
        templateNameBox.setValue(templateName);
        templateNameBox.setResponder(value -> {
            templateName = value;
            confirmSave = false;
        });
        addRenderableWidget(templateNameBox);
        addRenderableWidget(Button.builder(Component.literal(confirmSave ? "Confirm overwrite" : "Save selection"), ignored -> saveTemplate())
                .bounds(left + 394, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> {
                    if (serverSource) send("refresh", "", List.of(), List.of());
                    else { refreshClientTemplates(); rebuildWidgets(); }
                }).bounds(left + 500, y, 70, 20).build());

        List<String> templates = activeTemplates();
        int pages = Math.max(1, (templates.size() + 6) / 7);
        templatePage = Math.max(0, Math.min(templatePage, pages - 1));
        int from = templatePage * 7;
        int to = Math.min(templates.size(), from + 7);
        templateRows.clear();
        for (int index = from; index < to; index++) {
            int local = index - from;
            int rowY = top + 154 + local * 28;
            String name = templates.get(index);
            templateRows.add(new TemplateRow(name, left + 26, rowY, 360, 22));
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
                    templatePage--;
                    rebuildWidgets();
                }).bounds(left + 478, top + 356, 28, 20).build());
        previous.active = templatePage > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
                    templatePage++;
                    rebuildWidgets();
                }).bounds(left + 510, top + 356, 28, 20).build());
        next.active = templatePage + 1 < pages;
        Button load = addRenderableWidget(Button.builder(
                        Component.literal(confirmLoad ? "Confirm load at point 1" : "Load at point 1"), ignored -> {
                    if (confirmLoad) {
                        confirmLoad = false;
                        loadTemplate();
                    } else {
                        confirmLoad = true;
                        notice = "Loading replaces all destination blocks; container contents there are deleted without drops.";
                        noticeError = true;
                        rebuildWidgets();
                    }
                }).bounds(left + 394, top + 326, 144, 22).build());
        load.active = !selectedTemplate.isBlank();
    }

    private void submitFill() {
        if (mix.stream().anyMatch(entry -> entry.percentage < 1 || entry.percentage > 100)) {
            setNotice("Each block percentage must be between 1 and 100%.", true);
            return;
        }
        int total = mix.stream().mapToInt(entry -> entry.percentage).sum();
        if (total > 100) {
            setNotice("Percentages may total at most 100%. Current total: " + total + "%.", true);
            return;
        }
        send("fill", "", mix.stream().map(entry -> entry.inventorySlot).toList(),
                mix.stream().map(entry -> entry.percentage).toList());
    }

    private void saveTemplate() {
        String name = templateName.trim();
        if (!name.matches("[A-Za-z0-9._-]{1,64}") || name.equals(".") || name.equals("..")) {
            setNotice("Use 1-64 letters, numbers, dots, underscores or dashes.", true);
            return;
        }
        boolean exists = activeTemplates().stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
        if (exists && !confirmSave) {
            confirmSave = true;
            notice = "Template '" + name + "' already exists. Click Confirm overwrite to replace it.";
            noticeError = true;
            rebuildWidgets();
            return;
        }
        confirmSave = false;
        send(serverSource ? "save_server" : "save_client", name, List.of(), List.of());
    }

    private void loadTemplate() {
        if (selectedTemplate.isBlank()) return;
        if (serverSource) {
            send("load_server", selectedTemplate, List.of(), List.of());
            return;
        }
        try {
            byte[] data = RegionSelectionClientStorage.load(selectedTemplate);
            long requestId = nextRequestId++;
            ClientPacketDistributor.sendToServer(new RegionSelectionClientTemplateUploadPayload(selectedTemplate, data, requestId));
            setNotice("Uploading client template '" + selectedTemplate + "'…", false);
        } catch (IOException | IllegalArgumentException exception) {
            setNotice(exception.getMessage(), true);
        }
    }

    private void send(String operation, String name, List<Integer> slots, List<Integer> percentages) {
        long requestId = nextRequestId++;
        ClientPacketDistributor.sendToServer(new RegionSelectionActionPayload(operation, name, slots, percentages, requestId));
        setNotice("Request sent…", false);
    }

    public void acceptResult(RegionSelectionActionResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        clipboardAvailable = payload.clipboardAvailable();
        serverTemplates = new ArrayList<>(payload.serverTemplates());
        notice = payload.message();
        noticeError = !payload.successful();
        if (payload.selectionCleared()) {
            if (minecraft != null) minecraft.setScreenAndShow(null);
            return;
        }
        rebuildWidgets();
    }

    public void acceptClientTemplate(String name, byte[] data, long requestId) {
        nextRequestId = Math.max(nextRequestId, requestId + 1L);
        try {
            RegionSelectionClientStorage.save(name, data);
            refreshClientTemplates();
            selectedTemplate = name;
            templateName = name;
            notice = "Saved client template '" + name + "'.";
            noticeError = false;
        } catch (IOException | IllegalArgumentException exception) {
            notice = "Client template could not be saved: " + exception.getMessage();
            noticeError = true;
        }
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (page == 1 && event.buttonInfo().button() == 0) {
            int slot = inventorySlotAt((int) event.x(), (int) event.y());
            if (slot >= 0) {
                ItemStack stack = inventoryItem(slot);
                if (stack.isEmpty()) setNotice("That inventory slot is empty.", true);
                else if (!(stack.getItem() instanceof BlockItem)
                        && !stack.is(Items.WATER_BUCKET) && !stack.is(Items.LAVA_BUCKET)) {
                    setNotice("Use a block item, water bucket or lava bucket.", true);
                } else addMixSlot(slot);
                return true;
            }
        }
        if (page == 2 && event.buttonInfo().button() == 0) {
            for (TemplateRow row : templateRows) {
                if (row.contains(event.x(), event.y())) {
                    selectedTemplate = row.name;
                    templateName = row.name;
                    confirmLoad = false;
                    confirmSave = false;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void addMixSlot(int slot) {
        if (mix.stream().anyMatch(entry -> entry.inventorySlot == slot)) {
            setNotice("That inventory block is already in the fill list.", true);
            return;
        }
        if (mix.size() >= MAX_MIX) {
            setNotice("The fill list supports up to " + MAX_MIX + " block entries.", true);
            return;
        }
        mix.add(new MixEntry(slot, 0));
        confirmFill = false;
        equalizePercentages();
        notice = "Block added. Adjust percentages or use Equalize %.";
        noticeError = false;
        rebuildWidgets();
    }

    private void equalizePercentages() {
        if (mix.isEmpty()) return;
        int base = 100 / mix.size();
        int remainder = 100 % mix.size();
        for (int i = 0; i < mix.size(); i++) mix.get(i).percentage = base + (i < remainder ? 1 : 0);
    }

    private void refreshClientTemplates() {
        clientTemplates = RegionSelectionClientStorage.list();
    }

    private List<String> activeTemplates() { return serverSource ? serverTemplates : clientTemplates; }

    private void setNotice(String message, boolean error) {
        notice = message == null ? "" : message;
        noticeError = error;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = left();
        int top = top();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(left, top, left + W, top + H, PANEL);
        g.outline(left, top, W, H, BORDER);
        g.text(font, "Edit Region Selection", left + 16, top + 14, TEXT, true);
        BlockPos p1 = BlockPos.of(selection.point1());
        BlockPos p2 = BlockPos.of(selection.point2());
        g.text(font, compact(p1) + " → " + compact(p2) + " · " + selection.volume() + " block(s)",
                left + 16, top + 30, MUTED, false);
        if (page == 0) renderActions(g, left, top);
        else if (page == 1) renderFill(g, left, top, mouseX, mouseY);
        else if (page == 2) renderTemplates(g, left, top, mouseX, mouseY);
        else renderTransforms(g, left, top);
        if (!notice.isBlank()) g.text(font, trim(notice, 70), left + 98, top + H - 25,
                noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderActions(GuiGraphicsExtractor g, int left, int top) {
        panel(g, left + 244, top + 94, 370, 184);
        g.text(font, "Copy", left + 260, top + 110, TEXT, true);
        g.text(font, "Only block states are copied to the temporary clipboard.", left + 260, top + 126, MUTED, false);
        g.text(font, "Block entities, inventories and entities are excluded.", left + 260, top + 139, MUTED, false);
        g.text(font, "Paste", left + 260, top + 163, TEXT, true);
        g.text(font, "Pastes at point 1 and resizes the selection outline.", left + 260, top + 179, MUTED, false);
        g.text(font, "Destination container contents are discarded.", left + 260, top + 192, WARNING, false);
        g.text(font, "Clear", left + 260, top + 216, TEXT, true);
        g.text(font, "Replaces the selected blocks with air without item drops.", left + 260, top + 232, MUTED, false);
        g.text(font, "Container contents are discarded.", left + 260, top + 245, WARNING, false);
        g.text(font, clipboardAvailable ? "Clipboard ready" : "Clipboard empty", left + 260, top + 262,
                clipboardAvailable ? GOOD : WARNING, false);
    }

    private void renderFill(GuiGraphicsExtractor g, int left, int top, int mouseX, int mouseY) {
        panel(g, left + 18, top + 90, 350, 264);
        panel(g, left + 380, top + 90, 252, 236);
        g.text(font, "Fill block list", left + 28, top + 98, TEXT, true);
        g.text(font, "Click blocks or water/lava buckets to build a random mix.", left + 28, top + 112, MUTED, false);
        g.text(font, "Any percentage below 100% is automatically filled with air.", left + 28, top + 125, GOOD, false);
        g.text(font, "Items are not consumed. Container contents are discarded.", left + 28, top + 138, WARNING, false);
        int listX = left + 26;
        int listY = top + 154;
        for (int i = 0; i < mix.size(); i++) {
            MixEntry entry = mix.get(i);
            int y = listY + i * 31;
            ItemStack stack = inventoryItem(entry.inventorySlot);
            g.fill(listX, y, listX + 202, y + 26, SUB_PANEL);
            g.outline(listX, y, 202, 26, BORDER);
            if (!stack.isEmpty()) {
                g.item(stack, listX + 4, y + 5);
                g.itemDecorations(font, stack, listX + 4, y + 5);
                g.text(font, trim(stack.getHoverName().getString(), 23), listX + 26, y + 8, TEXT, false);
                if (inside(mouseX, mouseY, listX, y, 24, 26)) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            } else g.text(font, "Inventory item no longer available", listX + 8, y + 8, ERROR, false);
            g.text(font, "%", listX + 269, y + 8, MUTED, false);
        }
        if (mix.isEmpty()) g.text(font, "No entries selected: the result will be 100% air.", left + 38, top + 160, MUTED, false);
        int total = mix.stream().mapToInt(entry -> entry.percentage).sum();
        int air = Math.max(0, 100 - total);
        g.text(font, "Used: " + total + "%  •  Air: " + air + "%", left + 246, top + 361, total <= 100 ? GOOD : ERROR, false);
        g.text(font, "Player inventory", left + 390, top + 98, TEXT, true);
        renderInventory(g, left + 393, top + 122, mouseX, mouseY);
        g.text(font, "Blocks plus water/lava buckets are accepted.", left + 390, top + 278, MUTED, false);
    }

    private void renderTransforms(GuiGraphicsExtractor g, int left, int top) {
        panel(g, left + 18, top + 90, 614, 230);
        g.text(font, "Transform current selection", left + 28, top + 98, TEXT, true);
        g.text(font, "Transforms keep the selection minimum corner anchored and resize the outline when needed.",
                left + 28, top + 114, MUTED, false);
        g.text(font, "Rotate and mirror operations also transform compatible block-state directions.",
                left + 28, top + 128, MUTED, false);
        g.text(font, "Vertical flip swaps common top/bottom, up/down and floor/ceiling properties.",
                left + 28, top + 142, MUTED, false);
        g.text(font, "Inventories, block-entity data and entities are never copied. Destination containers are cleared.",
                left + 28, top + 286, WARNING, false);
    }

    private void renderTemplates(GuiGraphicsExtractor g, int left, int top, int mouseX, int mouseY) {
        panel(g, left + 18, top + 124, 360, 224);
        panel(g, left + 386, top + 124, 246, 184);
        g.text(font, serverSource ? "Server templates" : "Client templates", left + 28, top + 132, TEXT, true);
        if (activeTemplates().isEmpty()) g.text(font, "No templates saved in this location.", left + 34, top + 160, MUTED, false);
        for (TemplateRow row : templateRows) {
            boolean selected = row.name.equals(selectedTemplate);
            g.fill(row.x, row.y, row.x + row.width, row.y + row.height, selected ? 0xD0344C40 : SUB_PANEL);
            g.outline(row.x, row.y, row.width, row.height, selected ? GOOD : BORDER);
            g.text(font, trim(row.name, 34), row.x + 8, row.y + 7, TEXT, false);
        }
        g.text(font, "Save", left + 398, top + 138, TEXT, true);
        g.text(font, "Captures block states from the current", left + 398, top + 154, MUTED, false);
        g.text(font, "selection at the chosen storage location.", left + 398, top + 167, MUTED, false);
        g.text(font, "Load", left + 398, top + 200, TEXT, true);
        g.text(font, "Pastes at point 1. Client files are", left + 398, top + 216, MUTED, false);
        g.text(font, "validated by the server before use.", left + 398, top + 229, MUTED, false);
        g.text(font, selectedTemplate.isBlank() ? "Selected: none" : "Selected: " + trim(selectedTemplate, 24),
                left + 398, top + 252, selectedTemplate.isBlank() ? MUTED : TEXT, false);
        g.text(font, serverSource ? "Shared with server administrators" : "Stored in this Minecraft installation",
                left + 398, top + 278, serverSource ? WARNING : GOOD, false);
        g.text(font, "Click a template row to select it.", left + 398, top + 294, MUTED, false);
    }

    private void renderInventory(GuiGraphicsExtractor g, int startX, int startY, int mouseX, int mouseY) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawInventorySlot(g, 9 + row * 9 + column, startX + column * 18, startY + row * 18, mouseX, mouseY);
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++)
            drawInventorySlot(g, column, startX + column * 18, hotbarY, mouseX, mouseY);
    }

    private void drawInventorySlot(GuiGraphicsExtractor g, int slot, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 18, 18);
        g.fill(x, y, x + 18, y + 18, hovered ? 0xD0344C40 : 0xD00B1015);
        g.outline(x, y, 18, 18, hovered ? GOOD : BORDER);
        ItemStack stack = inventoryItem(slot);
        if (!stack.isEmpty()) {
            g.item(stack, x + 1, y + 1);
            g.itemDecorations(font, stack, x + 1, y + 1);
            if (hovered) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private int inventorySlotAt(int mouseX, int mouseY) {
        int startX = left() + 393;
        int startY = top() + 122;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            int x = startX + column * 18;
            int y = startY + row * 18;
            if (inside(mouseX, mouseY, x, y, 18, 18)) return 9 + row * 9 + column;
        }
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++) {
            if (inside(mouseX, mouseY, startX + column * 18, hotbarY, 18, 18)) return column;
        }
        return -1;
    }

    private ItemStack inventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, SUB_PANEL);
        g.outline(x, y, width, height, BORDER);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
    private static String compact(BlockPos pos) { return pos.getX() + "," + pos.getY() + "," + pos.getZ(); }
    private static String trim(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, Math.max(0, max - 1)) + "…";
    }
    private static int parsePercentage(String value) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(value.trim()))); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static final class MixEntry {
        private final int inventorySlot;
        private int percentage;
        private MixEntry(int inventorySlot, int percentage) {
            this.inventorySlot = inventorySlot;
            this.percentage = percentage;
        }
    }

    private record TemplateRow(String name, int x, int y, int width, int height) {
        boolean contains(double px, double py) { return inside(px, py, x, y, width, height); }
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
