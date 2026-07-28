package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapOperation;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ClaimMapScreen extends Screen {

    private static final int CONTROL_WIDTH = 210;

    private ClaimMapDataPayload payload;
    private ClaimMapOperation operation;
    private final Set<Long> selectedChunks = new LinkedHashSet<>();
    private ClaimMapWidget mapWidget;
    private final ClaimTerrainMap terrainMap = new ClaimTerrainMap();
    private EditBox claimNameBox;
    private Button applyButton;
    private Button clearButton;
    private String draftClaimName = "";

    public ClaimMapScreen(ClaimMapDataPayload payload) {
        super(Component.literal("Interactive Claim Map"));
        this.payload = payload;
        this.operation = payload.selectedClaimGroup().isBlank() ? ClaimMapOperation.CREATE : ClaimMapOperation.ADD;
    }

    public void acceptSnapshot(ClaimMapDataPayload updated) {
        boolean completedCreate = operation == ClaimMapOperation.CREATE
                && !updated.error()
                && !updated.notice().isBlank()
                && updated.ownedClaimGroups().stream().anyMatch(name -> name.equalsIgnoreCase(updated.selectedClaimGroup()));
        this.payload = updated;
        this.selectedChunks.clear();
        if (completedCreate) {
            operation = ClaimMapOperation.ADD;
            draftClaimName = "";
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int margin = 14;
        int panelWidth = Math.min(760, width - margin * 2);
        int panelLeft = (width - panelWidth) / 2;
        int top = 36;
        int bottom = height - 38;
        int mapSize = Math.max(180, Math.min(bottom - top, panelWidth - CONTROL_WIDTH - 18));
        int mapLeft = panelLeft;
        int controlLeft = mapLeft + mapSize + 12;

        mapWidget = addRenderableWidget(new ClaimMapWidget(
                mapLeft,
                top,
                mapSize,
                mapSize,
                payload,
                terrainMap,
                operation,
                selectedChunks,
                this::pan,
                this::zoom,
                this::selectionChanged
        ));

        addRenderableWidget(Button.builder(Component.literal("↖"), ignored -> pan(-1, -1))
                .bounds(controlLeft, top, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), ignored -> pan(0, -1))
                .bounds(controlLeft + 30, top, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↗"), ignored -> pan(1, -1))
                .bounds(controlLeft + 60, top, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("←"), ignored -> pan(-1, 0))
                .bounds(controlLeft, top + 22, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Center"), ignored -> recenter())
                .bounds(controlLeft + 30, top + 22, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), ignored -> pan(1, 0))
                .bounds(controlLeft + 90, top + 22, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↙"), ignored -> pan(-1, 1))
                .bounds(controlLeft, top + 44, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), ignored -> pan(0, 1))
                .bounds(controlLeft + 30, top + 44, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↘"), ignored -> pan(1, 1))
                .bounds(controlLeft + 60, top + 44, 28, 20).build());

        Button zoomIn = Button.builder(Component.literal("Zoom +"), ignored -> zoom(-1))
                .bounds(controlLeft + 122, top, 82, 20).build();
        zoomIn.active = payload.radius() > 2;
        addRenderableWidget(zoomIn);
        Button zoomOut = Button.builder(Component.literal("Zoom -"), ignored -> zoom(1))
                .bounds(controlLeft + 122, top + 22, 82, 20).build();
        zoomOut.active = payload.radius() < 12;
        addRenderableWidget(zoomOut);

        int y = top + 82;
        Button previousClaim = Button.builder(Component.literal("<"), ignored -> cycleClaim(-1))
                .bounds(controlLeft, y, 26, 20).build();
        previousClaim.active = !payload.ownedClaimGroups().isEmpty();
        addRenderableWidget(previousClaim);
        Button nextClaim = Button.builder(Component.literal(">"), ignored -> cycleClaim(1))
                .bounds(controlLeft + 178, y, 26, 20).build();
        nextClaim.active = !payload.ownedClaimGroups().isEmpty();
        addRenderableWidget(nextClaim);

        y += 34;
        addModeButton(ClaimMapOperation.ADD, "Expand", controlLeft, y, 64);
        addModeButton(ClaimMapOperation.REMOVE, "Remove", controlLeft + 70, y, 64);
        Button create = addModeButton(ClaimMapOperation.CREATE, "New", controlLeft + 140, y, 64);
        create.active = payload.canCreateClaims() && payload.usedClaimGroups() < payload.maxClaimGroups();

        y += 30;
        if (operation == ClaimMapOperation.CREATE) {
            claimNameBox = new EditBox(font, controlLeft, y, 204, 20, Component.literal("New claim name"));
            claimNameBox.setMaxLength(32);
            claimNameBox.setValue(draftClaimName);
            claimNameBox.setResponder(value -> {
                draftClaimName = value;
                updateActionButtons();
            });
            addRenderableWidget(claimNameBox);
            y += 28;
        } else {
            claimNameBox = null;
        }

        applyButton = Button.builder(Component.literal(applyLabel()), ignored -> applySelection())
                .bounds(controlLeft, y, 132, 20).build();
        addRenderableWidget(applyButton);
        clearButton = Button.builder(Component.literal("Clear"), ignored -> clearSelection())
                .bounds(controlLeft + 138, y, 66, 20).build();
        addRenderableWidget(clearButton);
        updateActionButtons();

        addRenderableWidget(Button.builder(Component.literal("Back to SSU menu"), ignored -> backToMenu())
                .bounds(panelLeft, height - 28, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(panelLeft + panelWidth - 70, height - 28, 70, 20).build());
    }

    private Button addModeButton(ClaimMapOperation mode, String label, int x, int y, int width) {
        Button button = Button.builder(Component.literal(label), ignored -> setOperation(mode))
                .bounds(x, y, width, 20)
                .build();
        button.active = operation != mode && (mode == ClaimMapOperation.CREATE || !payload.selectedClaimGroup().isBlank());
        addRenderableWidget(button);
        return button;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(760, width - 28);
        int panelLeft = (width - panelWidth) / 2;
        int top = 36;
        int mapSize = Math.max(180, Math.min(height - 74, panelWidth - CONTROL_WIDTH - 18));
        int controlLeft = panelLeft + mapSize + 12;

        graphics.text(font, "Claim map", panelLeft, 15, 0xFFFFFFFF);
        graphics.text(font,
                "Center: " + payload.centerChunkX() + ", " + payload.centerChunkZ()
                        + "  |  radius: " + payload.radius(),
                panelLeft + 80, 15, 0xFFBEBEBE);

        String selectedClaim = operation == ClaimMapOperation.CREATE
                ? (draftClaimName.isBlank() ? "<new claim>" : draftClaimName)
                : (payload.selectedClaimGroup().isBlank() ? "<none>" : payload.selectedClaimGroup());
        graphics.text(font, selectedClaim, controlLeft + 32, top + 88, 0xFFFFFFFF);

        int infoY = top + (operation == ClaimMapOperation.CREATE ? 202 : 174);
        graphics.text(font, "Selected: " + selectedChunks.size() + " / " + ClaimMapWidget.MAX_SELECTION_SIZE + " chunk(s)", controlLeft, infoY, 0xFFFFFFFF);
        graphics.text(font,
                "Total chunks: " + payload.usedChunks() + " / " + payload.maxChunks(),
                controlLeft, infoY + 16, 0xFFCCCCCC);
        graphics.text(font,
                "Claims: " + payload.usedClaimGroups() + " / " + payload.maxClaimGroups(),
                controlLeft, infoY + 32, 0xFFCCCCCC);
        if (!payload.selectedClaimGroup().isBlank()) {
            String perClaimLimit = payload.maxChunksPerClaim() <= 0 ? "unlimited" : Integer.toString(payload.maxChunksPerClaim());
            graphics.text(font,
                    "This claim: " + payload.selectedClaimChunks() + " / " + perClaimLimit,
                    controlLeft, infoY + 48, 0xFFCCCCCC);
        }

        int legendY = infoY + 78;
        drawLegend(graphics, controlLeft, legendY, payload.ownClaimColor(), "Your claim");
        drawLegend(graphics, controlLeft, legendY + 14, payload.otherClaimColor(), "Other claim");
        drawLegend(graphics, controlLeft, legendY + 28, payload.regionColor(), "Server region");
        drawLegend(graphics, controlLeft, legendY + 42, 0xFFB8C0C8, "Wilderness / terrain");
        drawLegend(graphics, controlLeft, legendY + 56, payload.selectionColor(), "Selected");

        if (!payload.notice().isBlank()) {
            graphics.text(font, payload.notice(), panelLeft, height - 44,
                    payload.error() ? 0xFFFF6B6B : 0xFF6BFF88);
        } else {
            graphics.text(font, "Left-click chunks to select. Right-drag to pan. Scroll to zoom.",
                    panelLeft, height - 44, 0xFFBEBEBE);
        }

        ClaimMapDataPayload.Entry hovered = mapWidget == null ? null : mapWidget.entryAt(mouseX, mouseY);
        if (hovered != null) {
            String text = "Chunk " + hovered.chunkX() + ", " + hovered.chunkZ();
            if (!hovered.claimName().isBlank()) {
                text += " — " + hovered.claimName();
            }
            graphics.text(font, text, panelLeft, top + mapSize + 4, 0xFFFFFFFF);
        }
    }

    private void drawLegend(GuiGraphicsExtractor graphics, int x, int y, int color, String label) {
        graphics.fill(x, y + 2, x + 10, y + 12, withAlpha(color, 0x58));
        graphics.outline(x, y + 2, 10, 10, color);
        graphics.text(font, label, x + 15, y + 2, 0xFFCCCCCC);
    }

    private static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    private void setOperation(ClaimMapOperation newOperation) {
        operation = newOperation;
        selectedChunks.clear();
        rebuildWidgets();
    }

    private void cycleClaim(int direction) {
        List<String> claims = payload.ownedClaimGroups();
        if (claims.isEmpty()) {
            return;
        }
        int current = indexOfIgnoreCase(claims, payload.selectedClaimGroup());
        int next = Math.floorMod((current < 0 ? 0 : current) + direction, claims.size());
        operation = ClaimMapOperation.ADD;
        requestMap(payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), claims.get(next));
    }

    private void pan(int dx, int dz) {
        requestMap(payload.centerChunkX() + dx, payload.centerChunkZ() + dz,
                payload.radius(), payload.selectedClaimGroup());
    }

    private void recenter() {
        if (minecraft.player == null) {
            return;
        }
        requestMap(minecraft.player.chunkPosition().x(), minecraft.player.chunkPosition().z(),
                payload.radius(), payload.selectedClaimGroup());
    }

    private void zoom(int delta) {
        int radius = Math.max(2, Math.min(12, payload.radius() + delta));
        if (radius != payload.radius()) {
            requestMap(payload.centerChunkX(), payload.centerChunkZ(), radius, payload.selectedClaimGroup());
        }
    }

    private void requestMap(int centerX, int centerZ, int radius, String selectedClaim) {
        selectedChunks.clear();
        ClientPacketDistributor.sendToServer(new ClaimMapRequestPayload(centerX, centerZ, radius, selectedClaim));
    }

    private void applySelection() {
        if (!canApply()) {
            return;
        }
        String claimName = operation == ClaimMapOperation.CREATE ? draftClaimName.trim() : payload.selectedClaimGroup();
        List<ClaimMapActionPayload.ChunkCoordinate> chunks = new ArrayList<>(selectedChunks.size());
        for (long key : selectedChunks) {
            chunks.add(new ClaimMapActionPayload.ChunkCoordinate(ClaimMapWidget.keyX(key), ClaimMapWidget.keyZ(key)));
        }
        ClientPacketDistributor.sendToServer(new ClaimMapActionPayload(
                operation,
                claimName,
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius(),
                chunks
        ));
    }

    private boolean canApply() {
        if (selectedChunks.isEmpty()) {
            return false;
        }
        if (operation == ClaimMapOperation.CREATE) {
            return payload.canCreateClaims() && draftClaimName.matches("[A-Za-z0-9_-]{1,32}");
        }
        return !payload.selectedClaimGroup().isBlank();
    }

    private String applyLabel() {
        return switch (operation) {
            case CREATE -> "Create selected claim";
            case ADD -> "Add selected chunks";
            case REMOVE -> "Remove selected chunks";
        };
    }

    private void clearSelection() {
        selectedChunks.clear();
        updateActionButtons();
    }

    private void selectionChanged() {
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (applyButton != null) {
            applyButton.active = canApply();
        }
        if (clearButton != null) {
            clearButton.active = !selectedChunks.isEmpty();
        }
    }

    private void backToMenu() {
        if (minecraft.player != null) {
            minecraft.player.connection.sendUnattendedCommand("ssu menu", null);
        }
    }

    private static int indexOfIgnoreCase(List<String> values, String value) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void tick() {
        super.tick();
        terrainMap.tick(payload);
    }

    @Override
    public void removed() {
        terrainMap.close();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
