package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapOperation;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ClaimMapScreen extends Screen {

    private static final int CONTROL_WIDTH = 220;
    private static final int TOOLBAR_WIDTH = 34;
    private static final int MARGIN = 8;
    private static final int TOP_BAR = 26;
    private static final int BOTTOM_BAR = 26;
    private static final int PANEL = 0xE8121720;
    private static final int PANEL_ALT = 0xE318202B;
    private static final int FRAME = 0xFF64778D;
    private static final int ACCENT = 0xFFFFD66B;
    private static final int MUTED = 0xFF9FB0C3;

    private ClaimMapDataPayload payload;
    private ClaimMapOperation operation;
    private final Screen parent;
    private final Set<Long> selectedChunks = new LinkedHashSet<>();
    private ClaimMapWidget mapWidget;
    private ClaimTerrainMap terrainMap = new ClaimTerrainMap();
    private boolean terrainMapClosed;
    private EditBox claimNameBox;
    private Button applyButton;
    private Button clearButton;
    private String draftClaimName = "";
    private String pendingDeleteClaim = "";
    private String pendingCenteredClaim = "";
    private long nextSettingsRequestId = 1L;
    private int mapSize = 384;
    private int mapLeft;
    private int mapTop;
    private int controlLeft;
    private int shellLeft;

    public ClaimMapScreen(ClaimMapDataPayload payload, Screen parent) {
        super(Component.literal("Interactive Claim Map"));
        this.payload = payload;
        this.parent = parent;
        this.operation = payload.selectedClaimGroup().isBlank() ? ClaimMapOperation.CREATE : ClaimMapOperation.ADD;
    }

    public boolean openHomesForClaim(String claimName) {
        if (parent instanceof SsuDashboardScreen dashboard) {
            dashboard.openHomesForClaim(claimName);
            minecraft.setScreenAndShow(dashboard);
            return true;
        }
        SsuDashboardScreen.queueHomesForClaim(claimName);
        ClientPacketDistributor.sendToServer(new SsuMenuActionPayload(
                "refresh_shell", "", "", "", nextSettingsRequestId++));
        return true;
    }

    public void acceptSnapshot(ClaimMapDataPayload updated) {
        if (!pendingCenteredClaim.isBlank()) {
            if (!updated.selectedClaimGroup().equalsIgnoreCase(pendingCenteredClaim)) {
                return;
            }
            pendingCenteredClaim = "";
        } else if (updated.centerChunkX() != payload.centerChunkX()
                || updated.centerChunkZ() != payload.centerChunkZ()
                || updated.radius() != payload.radius()) {
            return;
        }

        boolean completedCreate = operation == ClaimMapOperation.CREATE
                && !updated.error()
                && !updated.notice().isBlank()
                && updated.ownedClaimGroups().stream().anyMatch(name -> name.equalsIgnoreCase(updated.selectedClaimGroup()));
        this.payload = updated;
        this.selectedChunks.clear();
        if (updated.selectedClaimGroup().isBlank() && operation != ClaimMapOperation.CREATE) {
            operation = ClaimMapOperation.CREATE;
            pendingDeleteClaim = "";
        }
        if (completedCreate) {
            operation = ClaimMapOperation.ADD;
            draftClaimName = "";
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (terrainMapClosed) {
            terrainMap = new ClaimTerrainMap();
            terrainMapClosed = false;
        }
        int usableHeight = Math.max(190, height - MARGIN * 2 - TOP_BAR - BOTTOM_BAR);
        int availableWidth = Math.max(220, width - MARGIN * 2 - TOOLBAR_WIDTH - CONTROL_WIDTH - 12);
        mapSize = Math.max(180, Math.min(usableHeight, availableWidth));
        int wholeWidth = TOOLBAR_WIDTH + 6 + mapSize + 6 + CONTROL_WIDTH;
        shellLeft = Math.max(MARGIN, (width - wholeWidth) / 2);
        mapLeft = shellLeft + TOOLBAR_WIDTH + 6;
        mapTop = MARGIN + TOP_BAR;
        controlLeft = mapLeft + mapSize + 6;

        mapWidget = addRenderableWidget(new ClaimMapWidget(
                mapLeft, mapTop, mapSize, mapSize, payload, terrainMap, operation, selectedChunks,
                this::pan, this::zoom, this::selectionChanged));

        int toolbarX = shellLeft + 6;
        int y = mapTop + 3;
        addToolButton(toolbarX, y, "+", "Zoom in", ignored -> zoom(-1), payload.radius() > 2); y += 25;
        addToolButton(toolbarX, y, "−", "Zoom out", ignored -> zoom(1), payload.radius() < 12); y += 25;
        addToolButton(toolbarX, y, "C", "Center on player", ignored -> recenter(), minecraft.player != null); y += 31;
        addToolButton(toolbarX, y, "W", "Open world map", ignored -> openWorldMap(), true);

        int panelX = controlLeft + 8;
        int panelWidth = CONTROL_WIDTH - 16;
        int panelY = mapTop + 10;
        Button previousClaim = addButton(panelX, panelY + 20, 28, "‹", "Previous claim", ignored -> cycleClaim(-1));
        previousClaim.active = !payload.ownedClaimGroups().isEmpty();
        Button nextClaim = addButton(panelX + panelWidth - 28, panelY + 20, 28, "›", "Next claim", ignored -> cycleClaim(1));
        nextClaim.active = !payload.ownedClaimGroups().isEmpty();

        panelY += 74;
        addModeButton(ClaimMapOperation.ADD, "Expand", panelX, panelY, 62);
        addModeButton(ClaimMapOperation.REMOVE, "Remove", panelX + 66, panelY, 62);
        Button create = addModeButton(ClaimMapOperation.CREATE, "New", panelX + 132, panelY, 62);
        create.active = payload.canCreateClaims() && payload.usedClaimGroups() < payload.maxClaimGroups();

        panelY += 30;
        if (operation == ClaimMapOperation.CREATE) {
            claimNameBox = new EditBox(font, panelX, panelY, panelWidth, 20, Component.literal("New claim name"));
            claimNameBox.setMaxLength(32);
            claimNameBox.setValue(draftClaimName);
            claimNameBox.setResponder(value -> { draftClaimName = value; updateActionButtons(); });
            addRenderableWidget(claimNameBox);
            panelY += 28;
        } else {
            claimNameBox = null;
        }

        applyButton = addButton(panelX, panelY, panelWidth - 58, applyLabel(), "Apply selected chunks", ignored -> applySelection());
        clearButton = addButton(panelX + panelWidth - 54, panelY, 54, "Clear", "Clear selection", ignored -> clearSelection());
        panelY += 28;
        Button settings = addButton(panelX, panelY, 94, "Settings", "Open selected claim settings", ignored -> openClaimSettings());
        settings.active = !payload.selectedClaimGroup().isBlank();
        String deleteLabel = pendingDeleteClaim.equalsIgnoreCase(payload.selectedClaimGroup()) ? "Confirm" : "Delete";
        Button delete = addButton(panelX + 100, panelY, 94, deleteLabel, "Delete selected claim", ignored -> requestDeleteClaim());
        delete.active = !payload.selectedClaimGroup().isBlank();
        updateActionButtons();

        int toolbarBottomY = mapTop + mapSize - 23;
        addButton(toolbarX, toolbarBottomY, 28, "←", "Back to SSU menu", ignored -> backToMenu());
        int shellRight = controlLeft + CONTROL_WIDTH;
        addButton(shellRight - 31, MARGIN + 3, 28, "×", "Close claim map", ignored -> onClose());
    }

    private Button addToolButton(int x, int y, String label, String tooltip, Button.OnPress press, boolean active) {
        Button button = addButton(x, y, 28, label, tooltip, press);
        button.active = active;
        return button;
    }

    private Button addButton(int x, int y, int width, String label, String tooltip, Button.OnPress press) {
        Button button = Button.builder(Component.literal(label), press).bounds(x, y, width, 20).build();
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return addRenderableWidget(button);
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
        int right = controlLeft + CONTROL_WIDTH;
        graphics.fill(shellLeft, MARGIN, right, height - MARGIN, PANEL);
        graphics.outline(shellLeft, MARGIN, right - shellLeft, height - MARGIN * 2, FRAME);
        graphics.fill(shellLeft, MARGIN, right, MARGIN + TOP_BAR - 2, PANEL_ALT);
        graphics.fill(shellLeft + 3, mapTop, mapLeft - 3, mapTop + mapSize, PANEL_ALT);
        graphics.fill(controlLeft, mapTop, right, mapTop + mapSize, PANEL_ALT);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(font, "CLAIM MAP", shellLeft + 8, MARGIN + 8, ACCENT);
        graphics.text(font, "Chunk selection & land management", shellLeft + 82, MARGIN + 8, MUTED);

        int x = controlLeft + 8;
        graphics.text(font, "ACTIVE CLAIM", x, mapTop + 10, ACCENT);
        String selectedClaim = payload.selectedClaimGroup().isBlank() ? "No claim selected" : payload.selectedClaimGroup();
        graphics.centeredText(font, selectedClaim, controlLeft + CONTROL_WIDTH / 2, mapTop + 44, 0xFFFFFFFF);
        graphics.text(font, "MODE", x, mapTop + 73, ACCENT);
        int y = mapTop + (operation == ClaimMapOperation.CREATE ? 202 : 174);

        graphics.text(font, "SELECTION", x, y, ACCENT); y += 17;
        graphics.text(font, selectedChunks.size() + " / " + ClaimMapWidget.MAX_SELECTION_SIZE + " chunks", x, y, 0xFFFFFFFF); y += 14;
        graphics.text(font, "Total: " + payload.usedChunks() + " / " + payload.maxChunks(), x, y, MUTED); y += 14;
        graphics.text(font, "Claims: " + payload.usedClaimGroups() + " / " + payload.maxClaimGroups(), x, y, MUTED); y += 14;
        if (!payload.selectedClaimGroup().isBlank()) {
            String limit = payload.maxChunksPerClaim() <= 0 ? "unlimited" : Integer.toString(payload.maxChunksPerClaim());
            graphics.text(font, "Current: " + payload.selectedClaimChunks() + " / " + limit, x, y, MUTED); y += 20;
        } else {
            y += 6;
        }

        graphics.text(font, "LEGEND", x, y, ACCENT); y += 16;
        drawLegend(graphics, x, y, payload.ownClaimColor(), "Your claim"); y += 14;
        drawLegend(graphics, x, y, payload.otherClaimColor(), "Other claim"); y += 14;
        drawLegend(graphics, x, y, payload.regionColor(), "Server region"); y += 14;
        drawLegend(graphics, x, y, payload.selectionColor(), "Selected");

        ClaimMapDataPayload.Entry hovered = mapWidget == null ? null : mapWidget.entryAt(mouseX, mouseY);
        String bottom;
        if (!payload.notice().isBlank()) {
            bottom = payload.notice();
        } else if (hovered != null) {
            bottom = "Chunk " + hovered.chunkX() + ", " + hovered.chunkZ()
                    + (hovered.claimName().isBlank() ? "" : "  •  " + hovered.claimName());
        } else {
            bottom = "Left-click: select   •   Middle-drag: pan   •   Wheel: zoom";
        }
        int bottomX = shellLeft + 16;
        int bottomWidth = Math.max(40, controlLeft + CONTROL_WIDTH - bottomX - 10);
        String visibleBottom = font.plainSubstrByWidth(bottom, bottomWidth);
        graphics.text(font, visibleBottom, bottomX, height - MARGIN - 17,
                payload.error() ? 0xFFFF6B6B : 0xFFCED7E2);
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
        pendingDeleteClaim = "";
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
        pendingDeleteClaim = "";
        requestMap(payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), claims.get(next), true);
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
        requestMap(centerX, centerZ, radius, selectedClaim, false);
    }

    private void requestMap(int centerX, int centerZ, int radius, String selectedClaim, boolean centerOnSelectedClaim) {
        selectedChunks.clear();
        pendingCenteredClaim = centerOnSelectedClaim ? selectedClaim : "";

        /*
         * Move ordinary pan/zoom requests immediately. When cycling claims the
         * server determines the claim's center, while the selected claim label
         * is still updated immediately for responsive navigation.
         */
        payload = new ClaimMapDataPayload(
                centerX,
                centerZ,
                radius,
                selectedClaim,
                payload.ownedClaimGroups(),
                payload.usedChunks(),
                payload.maxChunks(),
                payload.usedClaimGroups(),
                payload.maxClaimGroups(),
                payload.selectedClaimChunks(),
                payload.maxChunksPerClaim(),
                payload.canCreateClaims(),
                payload.ownClaimColor(),
                payload.otherClaimColor(),
                payload.regionColor(),
                payload.selectionColor(),
                "",
                false,
                payload.chunks()
        );
        rebuildWidgets();
        ClientPacketDistributor.sendToServer(new ClaimMapRequestPayload(
                centerX, centerZ, radius, selectedClaim, centerOnSelectedClaim));
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
            case DELETE -> "Delete claim";
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

    private void openClaimSettings() {
        if (payload.selectedClaimGroup().isBlank()) return;
        ClientPacketDistributor.sendToServer(new SsuPropertySettingsRequestPayload(
                "claim", payload.selectedClaimGroup(), nextSettingsRequestId++));
    }

    private void requestDeleteClaim() {
        String claim = payload.selectedClaimGroup();
        if (claim.isBlank()) return;
        if (!pendingDeleteClaim.equalsIgnoreCase(claim)) {
            pendingDeleteClaim = claim;
            rebuildWidgets();
            return;
        }
        pendingDeleteClaim = "";
        selectedChunks.clear();
        ClientPacketDistributor.sendToServer(new ClaimMapActionPayload(
                ClaimMapOperation.DELETE, claim, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), List.of()));
    }

    private void openWorldMap() {
        ClientPacketDistributor.sendToServer(new WorldMapRequestPayload(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                Math.max(3, Math.min(32, payload.radius()))
        ));
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
        terrainMapClosed = true;
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.buttonInfo().button() == 2
                && mapWidget != null
                && mapWidget.beginMiddleDrag(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (mapWidget != null && mapWidget.isMiddleDragging()) {
            return mapWidget.updateMiddleDrag(event.x(), event.y());
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.buttonInfo().button() == 2 && mapWidget != null && mapWidget.isMiddleDragging()) {
            return mapWidget.finishMiddleDrag();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
