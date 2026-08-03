package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.dimension.DimensionPreset;
import be.winnetrie.mod.simpleserverutilities.dimension.ManagedDimensionDefinition;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerSubmitPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Admin editor for world-local datapack dimensions managed by SSU. */
public final class ManagedDimensionScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int PANEL = 0xF012171E;
    private static final int CARD = 0xE01B232D;
    private static final int BORDER = 0xFF52606D;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFA5B0BA;
    private static final int GOOD = 0xFF84E39A;
    private static final int WARNING = 0xFFFFB86B;
    private static final int ERROR = 0xFFFF8080;
    private static final int LIST_ROWS = 8;

    private final Screen parent;
    private SsuDimensionManagerDataPayload data;
    private ManagedDimensionDefinition draft;
    private String originalId = "";
    private Tab tab = Tab.GENERAL;
    private int listPage;
    private long nextRequestId;
    private boolean confirmDelete;

    private EditBox idBox;
    private EditBox nameBox;
    private final List<EditBox> draftFields = new ArrayList<>();
    private final List<FieldLabel> draftLabels = new ArrayList<>();

    public ManagedDimensionScreen(SsuDimensionManagerDataPayload data, Screen parent) {
        super(Component.literal("Dimensions"));
        this.data = data;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, data.requestId() + 1L);
        acceptSelection(data);
    }

    public void acceptData(SsuDimensionManagerDataPayload updated) {
        if (updated.requestId() < data.requestId()) return;
        data = updated;
        acceptSelection(updated);
        confirmDelete = false;
        rebuildWidgets();
    }

    private void acceptSelection(SsuDimensionManagerDataPayload payload) {
        originalId = payload.selectedId();
        if (!payload.selectedDefinitionJson().isBlank()) {
            try {
                draft = GSON.fromJson(payload.selectedDefinitionJson(), ManagedDimensionDefinition.class);
                if (draft != null) draft.normalize();
            } catch (RuntimeException ignored) {
                draft = null;
            }
        } else if (!originalId.isBlank()) {
            draft = null; // vanilla or external dimension: inspect only
        } else if (draft == null) {
            newDraft();
        }
    }

    @Override
    protected void init() {
        draftFields.clear();
        draftLabels.clear();
        idBox = null;
        nameBox = null;
        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        int h = panelHeight();
        int listW = Math.min(205, Math.max(165, w / 3));
        int rightX = x + listW + 18;
        int rightW = x + w - 14 - rightX;

        addRenderableWidget(Button.builder(Component.literal("×"), ignored -> onClose())
                .bounds(x + w - 34, y + 10, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("New"), ignored -> {
                    newDraft();
                    originalId = "";
                    tab = Tab.GENERAL;
                    confirmDelete = false;
                    rebuildWidgets();
                }).bounds(x + 12, y + 38, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> request(originalId))
                .bounds(x + 78, y + 38, 72, 20).build());

        int from = listPage * LIST_ROWS;
        int to = Math.min(data.dimensions().size(), from + LIST_ROWS);
        for (int index = from; index < to; index++) {
            var entry = data.dimensions().get(index);
            int rowY = y + 68 + (index - from) * 25;
            String state = entry.managed() ? entry.loaded() ? "● " : "○ " : "◇ ";
            Button select = Button.builder(Component.literal(state + trim(entry.displayName(), 21)), ignored -> request(entry.id()))
                    .bounds(x + 12, rowY, listW - 24, 20).build();
            select.active = !entry.id().equals(data.selectedId());
            addRenderableWidget(select);
        }
        int pages = Math.max(1, (data.dimensions().size() + LIST_ROWS - 1) / LIST_ROWS);
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changeListPage(-1))
                .bounds(x + 12, y + h - 30, 28, 20).build());
        prev.active = listPage > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changeListPage(1))
                .bounds(x + 44, y + h - 30, 28, 20).build());
        next.active = listPage + 1 < pages;

        int tabW = Math.max(74, (rightW - 8) / 3);
        addTabButton(Tab.GENERAL, rightX, y + 38, tabW);
        addTabButton(Tab.ENVIRONMENT, rightX + tabW + 4, y + 38, tabW);
        addTabButton(Tab.GENERATOR, rightX + (tabW + 4) * 2, y + 38, tabW);

        if (draft == null) {
            addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                    .bounds(x + w - 86, y + h - 30, 72, 20).build());
            return;
        }

        switch (tab) {
            case GENERAL -> initGeneral(rightX, y + 70, rightW);
            case ENVIRONMENT -> initEnvironment(rightX, y + 70, rightW);
            case GENERATOR -> initGenerator(rightX, y + 70, rightW);
        }

        Button save = addRenderableWidget(Button.builder(Component.literal(originalId.isBlank() ? "Create" : "Save"), ignored -> save())
                .bounds(x + w - 166, y + h - 30, 72, 20).build());
        save.active = !data.error();
        Button delete = addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "Confirm" : "Delete"), ignored -> delete())
                .bounds(x + w - 244, y + h - 30, 72, 20).build());
        delete.active = !originalId.isBlank();
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + w - 88, y + h - 30, 74, 20).build());
    }

    private void addTabButton(Tab value, int x, int y, int width) {
        Button button = addRenderableWidget(Button.builder(Component.literal(value.label), ignored -> {
                    collectDraft();
                    tab = value;
                    rebuildWidgets();
                }).bounds(x, y, width, 20).build());
        button.active = tab != value;
    }

    private void initGeneral(int x, int y, int w) {
        labelBox("Dimension ID", x, y, w, originalId.isBlank() ? draft.id : draft.resourceId(), 64,
                box -> idBox = box, originalId.isBlank());
        labelBox("Display name", x, y + 44, w, draft.displayName, 64, box -> nameBox = box, true);

        addRenderableWidget(Button.builder(Component.literal("<"), ignored -> cyclePreset(-1))
                .bounds(x, y + 88, 28, 20).build());
        Button preset = addRenderableWidget(Button.builder(Component.literal("Preset: " + draft.presetValue().label()), ignored -> cyclePreset(1))
                .bounds(x + 32, y + 88, Math.max(110, w - 64), 20).build());
        preset.active = true;
        addRenderableWidget(Button.builder(Component.literal(">"), ignored -> cyclePreset(1))
                .bounds(x + w - 28, y + 88, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Enabled: " + onOff(draft.enabled)), ignored -> {
                    draft.enabled = !draft.enabled;
                    rebuildWidgets();
                }).bounds(x, y + 120, Math.min(180, w), 20).build());
    }

    private void initEnvironment(int x, int y, int w) {
        int half = (w - 6) / 2;
        addToggle(x, y, half, "Skylight", () -> draft.hasSkylight, value -> draft.hasSkylight = value);
        addToggle(x + half + 6, y, half, "Ceiling", () -> draft.hasCeiling, value -> draft.hasCeiling = value);
        addToggle(x, y + 26, half, "Natural", () -> draft.natural, value -> draft.natural = value);
        addToggle(x + half + 6, y + 26, half, "Ultrawarm", () -> draft.ultrawarm, value -> draft.ultrawarm = value);
        addToggle(x, y + 52, half, "Beds work", () -> draft.bedWorks, value -> draft.bedWorks = value);
        addToggle(x + half + 6, y + 52, half, "Respawn anchors", () -> draft.respawnAnchorWorks, value -> draft.respawnAnchorWorks = value);
        addToggle(x, y + 78, half, "Raids", () -> draft.hasRaids, value -> draft.hasRaids = value);
        addToggle(x + half + 6, y + 78, half, "Piglin safe", () -> draft.piglinSafe, value -> draft.piglinSafe = value);

        int fieldW = Math.max(70, (w - 12) / 3);
        numericBox("Min Y", x, y + 112, fieldW, Integer.toString(draft.minY), value -> draft.minY = intValue(value, draft.minY));
        numericBox("Height", x + fieldW + 6, y + 112, fieldW, Integer.toString(draft.height), value -> draft.height = intValue(value, draft.height));
        numericBox("Logical", x + (fieldW + 6) * 2, y + 112, fieldW, Integer.toString(draft.logicalHeight), value -> draft.logicalHeight = intValue(value, draft.logicalHeight));
        numericBox("Coordinate scale", x, y + 156, fieldW, Double.toString(draft.coordinateScale), value -> draft.coordinateScale = doubleValue(value, draft.coordinateScale));
        numericBox("Ambient 0-1", x + fieldW + 6, y + 156, fieldW, Float.toString(draft.ambientLight), value -> draft.ambientLight = (float) doubleValue(value, draft.ambientLight));
        numericBox("Fixed time (-1 off)", x + (fieldW + 6) * 2, y + 156, fieldW, Long.toString(draft.fixedTime), value -> draft.fixedTime = longValue(value, draft.fixedTime));
    }

    private void initGenerator(int x, int y, int w) {
        DimensionPreset preset = draft.presetValue();
        if (preset != DimensionPreset.FLAT && preset != DimensionPreset.EMPTY) return;
        labelBox("Biome", x, y, w, draft.biome, 128, ignored -> {}, true);
        EditBox biome = draftFields.get(draftFields.size() - 1);
        biome.setResponder(value -> draft.biome = value);
        addToggle(x, y + 44, Math.min(150, w / 2), "Features", () -> draft.generateFeatures, value -> draft.generateFeatures = value);
        addToggle(x + Math.min(156, w / 2 + 6), y + 44, Math.min(150, w / 2), "Lakes", () -> draft.generateLakes, value -> draft.generateLakes = value);

        if (preset == DimensionPreset.FLAT) {
            int blockW = Math.max(110, w - 84);
            layerRow("Bottom", x, y + 78, blockW, draft.bottomBlock, draft.bottomLayers,
                    value -> draft.bottomBlock = value, value -> draft.bottomLayers = value);
            layerRow("Middle", x, y + 122, blockW, draft.middleBlock, draft.middleLayers,
                    value -> draft.middleBlock = value, value -> draft.middleLayers = value);
            layerRow("Top", x, y + 166, blockW, draft.topBlock, draft.topLayers,
                    value -> draft.topBlock = value, value -> draft.topLayers = value);
        } else {
            labelBox("Platform block", x, y + 78, w, draft.platformBlock, 128, ignored -> {}, true);
            EditBox platform = draftFields.get(draftFields.size() - 1);
            platform.setResponder(value -> draft.platformBlock = value);
            int half = (w - 6) / 2;
            numericBox("Platform size (odd)", x, y + 122, half, Integer.toString(draft.platformSize), value -> draft.platformSize = intValue(value, draft.platformSize));
            numericBox("Platform Y", x + half + 6, y + 122, half, Integer.toString(draft.platformY), value -> draft.platformY = intValue(value, draft.platformY));
        }
    }

    private void layerRow(String label, int x, int y, int blockW, String block, int count,
                          java.util.function.Consumer<String> blockSetter, java.util.function.IntConsumer countSetter) {
        labelBox(label + " block", x, y, blockW, block, 128, ignored -> {}, true);
        EditBox blockBox = draftFields.get(draftFields.size() - 1);
        blockBox.setResponder(blockSetter);
        numericBox("Layers", x + blockW + 6, y, 72, Integer.toString(count), value -> countSetter.accept(intValue(value, count)));
    }

    private void addToggle(int x, int y, int w, String label, BoolGetter getter, BoolSetter setter) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + onOff(getter.get())), ignored -> {
                    setter.set(!getter.get());
                    rebuildWidgets();
                }).bounds(x, y, w, 20).build());
    }

    private void labelBox(String label, int x, int y, int w, String value, int max,
                          java.util.function.Consumer<EditBox> receiver, boolean editable) {
        EditBox box = new EditBox(font, x, y + 14, w, 20, Component.literal(label));
        box.setValue(value == null ? "" : value);
        box.setMaxLength(max);
        box.setEditable(editable);
        addRenderableWidget(box);
        draftFields.add(box);
        draftLabels.add(new FieldLabel(label, x, y));
        receiver.accept(box);
    }

    private void numericBox(String label, int x, int y, int w, String value, java.util.function.Consumer<String> responder) {
        labelBox(label, x, y, w, value, 24, ignored -> {}, true);
        draftFields.get(draftFields.size() - 1).setResponder(responder);
    }

    private void cyclePreset(int delta) {
        collectDraft();
        DimensionPreset[] values = DimensionPreset.values();
        int next = Math.floorMod(draft.presetValue().ordinal() + delta, values.length);
        String id = draft.id;
        String name = draft.displayName;
        boolean enabled = draft.enabled;
        draft.applyPreset(values[next]);
        draft.id = id;
        draft.displayName = name;
        draft.enabled = enabled;
        rebuildWidgets();
    }

    private void collectDraft() {
        if (draft == null) return;
        if (idBox != null && originalId.isBlank()) draft.id = idBox.getValue();
        if (nameBox != null) draft.displayName = nameBox.getValue();
        draft.normalize();
    }

    private void save() {
        collectDraft();
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuDimensionManagerSubmitPayload(
                originalId.isBlank() ? "create" : "save", originalId, GSON.toJson(draft), id));
    }

    private void delete() {
        if (originalId.isBlank()) return;
        if (!confirmDelete) {
            confirmDelete = true;
            rebuildWidgets();
            return;
        }
        ClientPacketDistributor.sendToServer(new SsuDimensionManagerSubmitPayload(
                "delete", originalId, "", nextRequestId++));
    }

    private void request(String selectedId) {
        ClientPacketDistributor.sendToServer(new SsuDimensionManagerRequestPayload(selectedId, nextRequestId++));
    }

    private void newDraft() {
        draft = ManagedDimensionDefinition.preset("new_dimension", "New Dimension", DimensionPreset.OVERWORLD);
        originalId = "";
    }

    private void changeListPage(int delta) {
        int pages = Math.max(1, (data.dimensions().size() + LIST_ROWS - 1) / LIST_ROWS);
        listPage = Math.max(0, Math.min(pages - 1, listPage + delta));
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        int h = panelHeight();
        int listW = Math.min(205, Math.max(165, w / 3));
        int rightX = x + listW + 18;
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + w, y + h, PANEL);
        g.outline(x, y, w, h, BORDER);
        g.fill(x + 8, y + 64, x + listW + 4, y + h - 36, CARD);
        g.fill(rightX - 6, y + 64, x + w - 8, y + h - 36, CARD);
        g.text(font, "Dimensions", x + 12, y + 14, TEXT, true);
        g.text(font, "Create and configure world-local dimensions. Registry changes require a full restart.",
                x + 96, y + 15, MUTED, false);

        if (draft == null) {
            var selected = data.dimensions().stream().filter(entry -> entry.id().equals(data.selectedId())).findFirst().orElse(null);
            if (selected != null) {
                g.text(font, selected.displayName(), rightX, y + 82, TEXT, true);
                g.text(font, selected.id(), rightX, y + 101, MUTED, false);
                g.text(font, selected.vanilla() ? "Vanilla dimension" : "External datapack/mod dimension",
                        rightX, y + 126, WARNING, false);
                g.text(font, "It is visible for permissions but cannot be edited or deleted by SSU.",
                        rightX, y + 144, MUTED, false);
            }
        } else {
            for (FieldLabel label : draftLabels) {
                g.text(font, label.text(), label.x(), label.y(), MUTED, false);
            }
            drawLabels(g, rightX, y + 70, w - (rightX - x) - 14);
        }

        if (data.restartRequired()) {
            g.text(font, "Restart required to apply dimension registry changes.", x + 86, y + h - 25, WARNING, false);
        }
        if (!data.notice().isBlank()) {
            g.text(font, trim(data.notice(), 76), rightX, y + h - 48, data.error() ? ERROR : GOOD, false);
        }
        int pages = Math.max(1, (data.dimensions().size() + LIST_ROWS - 1) / LIST_ROWS);
        g.text(font, "Page " + (listPage + 1) + " / " + pages, x + 78, y + h - 25, MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawLabels(GuiGraphicsExtractor g, int x, int y, int w) {
        switch (tab) {
            case GENERAL -> {
                g.text(font, "Preset applies safe defaults; all environment values remain editable.", x, y + 146, MUTED, false);
                g.text(font, originalId.isBlank() ? "IDs use the simpleserverutilities namespace."
                        : "Dimension IDs are permanent after creation.", x, y + 166, MUTED, false);
            }
            case ENVIRONMENT -> {
                g.text(font, "World behavior", x, y - 12, MUTED, false);
                g.text(font, "Height values are normalized to valid 16-block boundaries when saved.", x, y + 202, MUTED, false);
            }
            case GENERATOR -> {
                DimensionPreset preset = draft.presetValue();
                if (preset == DimensionPreset.FLAT || preset == DimensionPreset.EMPTY) {
                    if (preset == DimensionPreset.EMPTY) {
                        g.text(font, "The 9×9 platform is generated once after the restart loads this dimension.", x, y + 172, MUTED, false);
                    }
                } else {
                    g.text(font, preset.label() + " uses Minecraft's vanilla noise generator preset.", x, y + 18, TEXT, false);
                    g.text(font, "Choose Flat or Empty for editable layer/platform generation.", x, y + 40, MUTED, false);
                }
            }
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) minecraft.setScreenAndShow(parent);
        else super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    private int panelWidth() { return Math.max(520, Math.min(690, width - 8)); }
    private int panelHeight() { return Math.max(320, Math.min(390, height - 8)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static String trim(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, Math.max(0, max - 1)) + "…";
    }
    private static int intValue(String value, int fallback) { try { return Integer.parseInt(value.trim()); } catch (Exception ignored) { return fallback; } }
    private static long longValue(String value, long fallback) { try { return Long.parseLong(value.trim()); } catch (Exception ignored) { return fallback; } }
    private static double doubleValue(String value, double fallback) { try { return Double.parseDouble(value.trim()); } catch (Exception ignored) { return fallback; } }

    private enum Tab {
        GENERAL("General"), ENVIRONMENT("Environment"), GENERATOR("Generator");
        private final String label;
        Tab(String label) { this.label = label; }
    }
    private record FieldLabel(String text, int x, int y) { }
    @FunctionalInterface private interface BoolGetter { boolean get(); }
    @FunctionalInterface private interface BoolSetter { void set(boolean value); }
}
