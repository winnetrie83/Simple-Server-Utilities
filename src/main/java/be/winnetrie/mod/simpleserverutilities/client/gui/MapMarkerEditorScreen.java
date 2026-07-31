package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact create/edit form for one personal map marker. */
public final class MapMarkerEditorScreen extends Screen {
    private static final ColorPreset[] COLORS = {
            new ColorPreset("Black", 0xFF000000),
            new ColorPreset("Dark Blue", 0xFF0000AA),
            new ColorPreset("Dark Green", 0xFF00AA00),
            new ColorPreset("Dark Aqua", 0xFF00AAAA),
            new ColorPreset("Dark Red", 0xFFAA0000),
            new ColorPreset("Dark Purple", 0xFFAA00AA),
            new ColorPreset("Gold", 0xFFFFAA00),
            new ColorPreset("Gray", 0xFFAAAAAA),
            new ColorPreset("Dark Gray", 0xFF555555),
            new ColorPreset("Blue", 0xFF5555FF),
            new ColorPreset("Green", 0xFF55FF55),
            new ColorPreset("Aqua", 0xFF55FFFF),
            new ColorPreset("Red", 0xFFFF5555),
            new ColorPreset("Light Purple", 0xFFFF55FF),
            new ColorPreset("Yellow", 0xFFFFFF55),
            new ColorPreset("White", 0xFFFFFFFF)
    };

    private final Screen parent;
    private final UUID markerId;
    private final String dimension;
    private boolean resolveSurfaceHeight;
    private int selectedColor;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Button saveButton;
    private String status = "";
    private boolean statusError;

    public MapMarkerEditorScreen(
            Screen parent,
            MapMarkerSyncPayload.Entry marker,
            String dimension,
            int x,
            int y,
            int z,
            boolean resolveSurfaceHeight
    ) {
        super(Component.literal(marker == null ? "Create map marker" : "Edit map marker"));
        this.parent = parent;
        this.markerId = marker == null ? new UUID(0L, 0L) : marker.id();
        this.dimension = marker == null ? dimension : marker.dimension();
        this.resolveSurfaceHeight = marker == null && resolveSurfaceHeight;
        this.selectedColor = marker == null ? 0xFFFFFF55 : marker.colorArgb();
        this.initialName = marker == null ? "Marker" : marker.name();
        this.initialX = marker == null ? x : marker.x();
        this.initialY = marker == null ? y : marker.y();
        this.initialZ = marker == null ? z : marker.z();
    }

    private final String initialName;
    private final int initialX;
    private final int initialY;
    private final int initialZ;

    @Override
    protected void init() {
        int panelWidth = Math.min(420, width - 28);
        int left = (width - panelWidth) / 2;
        int top = Math.max(28, (height - 250) / 2);
        int fieldLeft = left + 110;
        int fieldWidth = panelWidth - 126;

        nameBox = addRenderableWidget(new EditBox(font, fieldLeft, top + 38, fieldWidth, 20, Component.literal("Marker name")));
        nameBox.setMaxLength(40);
        nameBox.setValue(initialName);
        nameBox.setResponder(value -> updateSaveButton());

        int coordinateWidth = Math.max(54, (fieldWidth - 8) / 3);
        xBox = coordinateBox(fieldLeft, top + 74, coordinateWidth, initialX, false);
        yBox = coordinateBox(fieldLeft + coordinateWidth + 4, top + 74, coordinateWidth, initialY, true);
        zBox = coordinateBox(fieldLeft + (coordinateWidth + 4) * 2, top + 74, coordinateWidth, initialZ, false);

        int colorLeft = fieldLeft;
        int colorTop = top + 112;
        for (int index = 0; index < COLORS.length; index++) {
            ColorPreset preset = COLORS[index];
            int buttonX = colorLeft + (index % 8) * 28;
            int buttonY = colorTop + (index / 8) * 24;
            ColorButton button = new ColorButton(
                    buttonX,
                    buttonY,
                    24,
                    20,
                    preset.name(),
                    preset.argb(),
                    () -> selectedColor == preset.argb(),
                    ignored -> {
                        selectedColor = preset.argb();
                        rebuildWidgets();
                    });
            button.setTooltip(Tooltip.create(Component.literal(
                    preset.name() + " • " + String.format("#%06X", preset.argb() & 0xFFFFFF))));
            addRenderableWidget(button);
        }

        saveButton = Button.builder(Component.literal(markerId.getMostSignificantBits() == 0L && markerId.getLeastSignificantBits() == 0L
                        ? "Create marker" : "Save marker"), ignored -> submit())
                .bounds(left + panelWidth - 198, top + 196, 116, 20).build();
        addRenderableWidget(saveButton);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> closeToParent())
                .bounds(left + panelWidth - 76, top + 196, 62, 20).build());
        updateSaveButton();
    }

    private EditBox coordinateBox(int x, int y, int width, int value, boolean height) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal("Coordinate"));
        box.setMaxLength(12);
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d{0,10}"));
        box.setResponder(text -> {
            if (height) resolveSurfaceHeight = false;
            updateSaveButton();
        });
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA5000000);
        int panelWidth = Math.min(420, width - 28);
        int left = (width - panelWidth) / 2;
        int top = Math.max(28, (height - 250) / 2);
        graphics.fill(left, top, left + panelWidth, top + 230, 0xE5121720);
        graphics.outline(left, top, panelWidth, 230, 0xFF64778D);
        graphics.centeredText(font, title, left + panelWidth / 2, top + 12, 0xFFFFD66B);
        graphics.text(font, "Name", left + 18, top + 44, 0xFFE6EAF0);
        graphics.text(font, "Coordinates", left + 18, top + 80, 0xFFE6EAF0);
        graphics.text(font, "X", xBox.getX() + 3, top + 65, 0xFF9FB0C3);
        graphics.text(font, "Y", yBox.getX() + 3, top + 65, 0xFF9FB0C3);
        graphics.text(font, "Z", zBox.getX() + 3, top + 65, 0xFF9FB0C3);
        graphics.text(font, "Color", left + 18, top + 118, 0xFFE6EAF0);
        graphics.fill(left + 78, top + 115, left + 96, top + 133, selectedColor);
        graphics.outline(left + 78, top + 115, 18, 18, 0xFFFFFFFF);
        graphics.text(font, "Dimension: " + dimension, left + 18, top + 170, 0xFF9FB0C3);
        if (resolveSurfaceHeight) {
            graphics.text(font, "Y will be verified as one block above the clicked surface.",
                    left + 18, top + 184, 0xFF7EDB9B);
        }
        if (!status.isBlank()) {
            graphics.text(font, status, left + 18, top + 216, statusError ? 0xFFFF6B6B : 0xFF6BFF88);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    public void acceptResult(MapMarkerActionResultPayload result) {
        status = result.message();
        statusError = !result.success();
        if (result.success()) closeToParent();
    }

    private void submit() {
        Integer x = parse(xBox.getValue());
        Integer y = parse(yBox.getValue());
        Integer z = parse(zBox.getValue());
        if (x == null || y == null || z == null || nameBox.getValue().trim().isBlank()) return;
        boolean create = markerId.getMostSignificantBits() == 0L && markerId.getLeastSignificantBits() == 0L;
        ClientPacketDistributor.sendToServer(new MapMarkerActionPayload(
                create ? "create" : "update", markerId, nameBox.getValue().trim(), dimension,
                x, y, z, selectedColor, resolveSurfaceHeight));
        status = "Saving…";
        statusError = false;
        saveButton.active = false;
    }

    private void updateSaveButton() {
        if (saveButton != null) {
            saveButton.active = nameBox != null && !nameBox.getValue().trim().isBlank()
                    && parse(xBox == null ? "" : xBox.getValue()) != null
                    && parse(yBox == null ? "" : yBox.getValue()) != null
                    && parse(zBox == null ? "" : zBox.getValue()) != null;
        }
    }

    private static Integer parse(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return null; }
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }


    private record ColorPreset(String name, int argb) {}

    private static final class ColorButton extends Button {
        private final int color;
        private final java.util.function.BooleanSupplier selected;

        private ColorButton(
                int x,
                int y,
                int width,
                int height,
                String name,
                int color,
                java.util.function.BooleanSupplier selected,
                OnPress onPress
        ) {
            super(x, y, width, height, Component.literal(name), onPress, DEFAULT_NARRATION);
            this.color = color;
            this.selected = selected;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);
            graphics.fill(getX() + 3, getY() + 3, getRight() - 3, getBottom() - 3, color);
            int outline = selected.getAsBoolean()
                    ? 0xFFFFFFFF
                    : (isHoveredOrFocused() ? 0xFFBFD7F0 : 0xFF20242B);
            graphics.outline(getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, outline);
        }
    }

    @Override public void onClose() { closeToParent(); }
    @Override public boolean isPauseScreen() { return false; }
}
