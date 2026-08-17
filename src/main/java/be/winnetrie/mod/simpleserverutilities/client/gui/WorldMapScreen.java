package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.client.mapmarker.MapMarkerClientState;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.PlayerUiSettingUpdatePayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Full-screen explored world map with compact professional controls and marker context actions. */
public final class WorldMapScreen extends Screen {

    private static final int[] ZOOM_RADII = {3, 5, 8, 12, 18, 24, 32};
    private static final int MARGIN = 8;
    private static final int TOP_BAR = 26;
    private static final int BOTTOM_BAR = 26;
    private static final int TOOLBAR_WIDTH = 34;
    private static final int INFO_WIDTH = 170;
    private static final int PANEL = 0xE8121720;
    private static final int PANEL_ALT = 0xE318202B;
    private static final int FRAME = 0xFF64778D;
    private static final int MUTED = 0xFF9FB0C3;
    private static final int ACCENT = 0xFFFFD66B;

    private WorldMapDataPayload payload;
    private final WorldMapTerrainMap terrainMap = new WorldMapTerrainMap();
    private WorldMapWidget mapWidget;
    private boolean showClaims;
    private boolean showRegions;
    private boolean showMarkers;
    private int mapSize = 384;
    private int mapLeft;
    private int mapTop;
    private int infoLeft;
    private WorldMapWidget.ContextClick context;
    private UUID pendingDelete;
    private String status = "";
    private boolean statusError;

    public WorldMapScreen(WorldMapDataPayload payload) {
        super(Component.literal("World Map"));
        this.payload = payload;
        this.showClaims = payload.showClaims();
        this.showRegions = payload.showRegions();
        this.showMarkers = MapMarkerClientState.showOnWorldMap();
    }

    public void acceptSnapshot(WorldMapDataPayload updated) {
        if (updated.centerChunkX() != payload.centerChunkX()
                || updated.centerChunkZ() != payload.centerChunkZ()
                || updated.radius() != payload.radius()) {
            return;
        }
        payload = updated;
        if (mapWidget != null) {
            mapWidget.update(updated);
        }
    }

    public void acceptMarkerResult(MapMarkerActionResultPayload result) {
        status = result.message();
        statusError = !result.success();
        if (result.success()) {
            context = null;
            pendingDelete = null;
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int usableHeight = Math.max(190, height - MARGIN * 2 - TOP_BAR - BOTTOM_BAR);
        int availableWidth = Math.max(220, width - MARGIN * 2 - TOOLBAR_WIDTH - INFO_WIDTH - 12);
        mapSize = Math.max(180, Math.min(usableHeight, availableWidth));
        int wholeWidth = TOOLBAR_WIDTH + 6 + mapSize + 6 + INFO_WIDTH;
        int shellLeft = Math.max(MARGIN, (width - wholeWidth) / 2);
        mapLeft = shellLeft + TOOLBAR_WIDTH + 6;
        mapTop = MARGIN + TOP_BAR;
        infoLeft = mapLeft + mapSize + 6;

        mapWidget = addRenderableWidget(new WorldMapWidget(
                mapLeft, mapTop, mapSize, mapSize, payload, terrainMap,
                this::pan, this::zoom, this::openContext));
        mapWidget.setShowClaims(showClaims);
        mapWidget.setShowRegions(showRegions);
        mapWidget.setShowMarkers(showMarkers);

        int toolbarX = shellLeft + 6;
        int y = mapTop + 3;
        addIconButton(toolbarX, y, "+", "Zoom in", ignored -> zoom(-1), payload.radius() > ZOOM_RADII[0]); y += 25;
        addIconButton(toolbarX, y, "−", "Zoom out", ignored -> zoom(1), payload.radius() < ZOOM_RADII[ZOOM_RADII.length - 1]); y += 25;
        addIconButton(toolbarX, y, "C", "Center on player", ignored -> recenter(), minecraft.player != null); y += 31;
        addIconButton(toolbarX, y, showClaims ? "C✓" : "C", "Toggle claim layer", ignored -> toggleClaims(), true); y += 25;
        addIconButton(toolbarX, y, showRegions ? "R✓" : "R", "Toggle server-region layer", ignored -> toggleRegions(), true); y += 25;
        addIconButton(toolbarX, y, showMarkers ? "M✓" : "M", "Toggle marker layer", ignored -> toggleMarkers(), true); y += 31;
        addIconButton(toolbarX, y, "◆", "Manage markers", ignored -> openMarkerManager(), true); y += 25;
        addIconButton(toolbarX, y, "#", "Open claim map", ignored -> openClaimMap(), true); y += 25;
        addIconButton(toolbarX, y, "↻", "refresh", ignored -> terrainMap.invalidate(), true);

        int toolbarBottomY = mapTop + mapSize - 23;
        addCompactButton(toolbarX, toolbarBottomY, 28, "←", "Back to SSU menu", ignored -> backToMenu());
        int shellRight = infoLeft + INFO_WIDTH;
        addCompactButton(shellRight - 31, MARGIN + 3, 28, "×", "Close world map", ignored -> onClose());

        buildContextButtons();
    }

    private void buildContextButtons() {
        if (context == null) return;
        int left = contextMenuLeft();
        int top = contextMenuTop();
        MapMarkerSyncPayload.Entry marker = context.marker();
        if (marker == null) {
            addCompactButton(left, top, 126, "+ Add marker", "Create a marker at this location", ignored -> createMarker());
        } else {
            addCompactButton(left, top, 60, "Edit", "Edit " + marker.name(), ignored -> editMarker(marker));
            String deleteLabel = marker.id().equals(pendingDelete) ? "Confirm" : "Delete";
            addCompactButton(left + 64, top, 62, deleteLabel, "Delete " + marker.name(), ignored -> deleteMarker(marker));
        }
        addCompactButton(left, top + 24, 126, "Close", "Close marker menu", ignored -> closeContext());
    }

    private Button addIconButton(int x, int y, String label, String tooltip, Button.OnPress action, boolean active) {
        Button button = Button.builder(Component.literal(label), action).bounds(x, y, 28, 20).build();
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        button.active = active;
        return addRenderableWidget(button);
    }

    private Button addCompactButton(int x, int y, int buttonWidth, String label, String tooltip, Button.OnPress action) {
        Button button = Button.builder(Component.literal(label), action).bounds(x, y, buttonWidth, 20).build();
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return addRenderableWidget(button);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        SsuGuiScale.fullscreenDimWhenScaled(graphics, this, 0xA5000000);
        int shellLeft = mapLeft - TOOLBAR_WIDTH - 6;
        graphics.fill(shellLeft, MARGIN, infoLeft + INFO_WIDTH, height - MARGIN, PANEL);
        graphics.outline(shellLeft, MARGIN, infoLeft + INFO_WIDTH - shellLeft, height - MARGIN * 2, FRAME);
        graphics.fill(shellLeft, MARGIN, infoLeft + INFO_WIDTH, MARGIN + TOP_BAR - 2, PANEL_ALT);
        graphics.fill(shellLeft + 3, mapTop, mapLeft - 3, mapTop + mapSize, PANEL_ALT);
        graphics.fill(infoLeft, mapTop, infoLeft + INFO_WIDTH, mapTop + mapSize, PANEL_ALT);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        renderContextFrame(graphics);

        graphics.text(font, "WORLD MAP", shellLeft + 8, MARGIN + 8, ACCENT);
        graphics.text(font, payload.dimension(), shellLeft + 78, MARGIN + 8, MUTED);
        String scale = (payload.radius() * 2 + 1) * 16 + " × " + (payload.radius() * 2 + 1) * 16 + " blocks";
        graphics.text(font, scale, infoLeft + 8, MARGIN + 8, MUTED);

        renderInfoPanel(graphics, mouseX, mouseY);
        renderBottomStatus(graphics, mouseX, mouseY);

        if (!payload.allowed()) {
            graphics.centeredText(font, "World map permission denied", mapLeft + mapSize / 2, mapTop + mapSize / 2, 0xFFFF6B6B);
        }
    }

    private void renderContextFrame(GuiGraphicsExtractor graphics) {
        if (context == null) {
            return;
        }
        int buttonLeft = contextMenuLeft();
        int buttonTop = contextMenuTop();
        int frameLeft = buttonLeft - 7;
        int frameTop = buttonTop - 7;
        int frameWidth = 140;
        int frameHeight = 58;
        int buttonRight = buttonLeft + 126;
        int buttonBottom = buttonTop + 44;
        int frameRight = frameLeft + frameWidth;
        int frameBottom = frameTop + frameHeight;

        // Render the surrounding gutter and the deliberate gaps between buttons
        // after the map widget. This keeps the map from showing through the panel
        // without covering the actual child button surfaces.
        int panelColor = 0xF2161C25;
        graphics.fill(frameLeft, frameTop, frameRight, buttonTop, panelColor);
        graphics.fill(frameLeft, buttonBottom, frameRight, frameBottom, panelColor);
        graphics.fill(frameLeft, buttonTop, buttonLeft, buttonBottom, panelColor);
        graphics.fill(buttonRight, buttonTop, frameRight, buttonBottom, panelColor);
        graphics.fill(buttonLeft, buttonTop + 20, buttonRight, buttonTop + 24, panelColor);
        if (context.marker() != null) {
            graphics.fill(buttonLeft + 60, buttonTop, buttonLeft + 64, buttonTop + 20, panelColor);
        }
        graphics.outline(frameLeft, frameTop, frameWidth, frameHeight, 0xFF9FB0C3);
        graphics.outline(frameLeft + 2, frameTop + 2, frameWidth - 4, frameHeight - 4, 0xFF34475A);
    }

    private void renderInfoPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = infoLeft + 9;
        int y = mapTop + 10;
        graphics.text(font, "LAYERS", x, y, ACCENT); y += 17;
        drawLayer(graphics, x, y, showClaims, payload.ownClaimColor(), "Claims"); y += 15;
        drawLayer(graphics, x, y, showRegions, payload.regionColor(), "Server regions"); y += 15;
        drawLayer(graphics, x, y, true, 0xFFFFD54F, "Player"); y += 25;

        graphics.text(font, "LOCATION", x, y, ACCENT); y += 17;
        WorldMapWidget.LocationInfo location = mapWidget == null ? null : mapWidget.locationAt(mouseX, mouseY);
        if (context != null && mapWidget != null) location = mapWidget.locationFor(context.coordinate());
        WorldMapWidget.WorldCoordinate coordinate = location == null ? null : location.coordinate();
        if (coordinate == null) {
            graphics.text(font, "Move over the map", x, y, MUTED);
        } else {
            graphics.text(font, "X  " + coordinate.x(), x, y, 0xFFE6EAF0); y += 13;
            graphics.text(font, "Y  " + coordinate.y(), x, y, 0xFFE6EAF0); y += 13;
            graphics.text(font, "Z  " + coordinate.z(), x, y, 0xFFE6EAF0); y += 13;
            graphics.text(font, fittedLocationLine("Biome  ", location.biomeId()), x, y, 0xFFE6EAF0); y += 13;
            graphics.text(font, fittedLocationLine("Block  ", location.blockId()), x, y, 0xFFE6EAF0);
        }
        y += 25;

        MapMarkerSyncPayload.Entry selected = context == null ? null : context.marker();
        graphics.text(font, selected == null ? "MARKERS" : "SELECTED MARKER", x, y, ACCENT); y += 17;
        if (selected == null) {
            graphics.text(font, MapMarkerClientState.markers().size() + " personal markers", x, y, MUTED); y += 14;
            graphics.text(font, "Right-click to create", x, y, MUTED);
        } else {
            graphics.text(font, selected.name(), x, y, 0xFFFFFFFF); y += 14;
            graphics.fill(x, y, x + 12, y + 12, selected.colorArgb());
            graphics.outline(x, y, 12, 12, 0xFFFFFFFF); y += 18;
            graphics.text(font, selected.x() + ", " + selected.y() + ", " + selected.z(), x, y, MUTED);
        }
        y += 30;
        int progress = terrainMap.progressPercent();
        if (progress < 100) {
            graphics.text(font, "Terrain " + progress + "%", x, y, ACCENT);
        }
        if (!status.isBlank()) {
            graphics.text(font, status, x, mapTop + mapSize - 18, statusError ? 0xFFFF6B6B : 0xFF6BFF88);
        }
    }

    private void renderBottomStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        WorldMapWidget.LocationInfo location = mapWidget == null ? null : mapWidget.locationAt(mouseX, mouseY);
        String statusLine;
        if (location == null) {
            statusLine = "Middle-drag: pan   •   Wheel: zoom   •   Right-click: marker";
        } else {
            WorldMapWidget.WorldCoordinate coordinate = location.coordinate();
            statusLine = "X " + coordinate.x() + "   Y " + coordinate.y() + "   Z " + coordinate.z()
                    + "   •   " + displayRegistryName(location.biomeId())
                    + "   •   " + displayRegistryName(location.blockId());
            statusLine = font.plainSubstrByWidth(statusLine, Math.max(80, mapSize - 70));
        }
        graphics.centeredText(font, statusLine, mapLeft + mapSize / 2, height - MARGIN - 17, 0xFFCED7E2);
    }

    private String fittedLocationLine(String prefix, String registryId) {
        String value = displayRegistryName(registryId);
        int available = Math.max(30, INFO_WIDTH - 18 - font.width(prefix));
        return prefix + font.plainSubstrByWidth(value, available);
    }

    private static String displayRegistryName(String registryId) {
        if (registryId == null || registryId.isBlank()) return "Unknown";
        int separator = registryId.indexOf(':');
        String path = separator >= 0 ? registryId.substring(separator + 1) : registryId;
        String[] words = path.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder(path.length());
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) result.append(word.substring(1));
        }
        return result.isEmpty() ? "Unknown" : result.toString();
    }

    private void drawLayer(GuiGraphicsExtractor graphics, int x, int y, boolean enabled, int color, String label) {
        graphics.fill(x, y + 1, x + 10, y + 11, enabled ? color : 0xFF3C4652);
        graphics.outline(x, y + 1, 10, 10, enabled ? 0xFFFFFFFF : 0xFF6E7883);
        graphics.text(font, label, x + 16, y + 2, enabled ? 0xFFE6EAF0 : MUTED);
    }

    private void openContext(WorldMapWidget.ContextClick clicked) {
        context = clicked;
        pendingDelete = null;
        status = "";
        rebuildWidgets();
    }

    private void closeContext() {
        context = null;
        pendingDelete = null;
        rebuildWidgets();
    }

    private void createMarker() {
        if (context == null || minecraft == null) return;
        WorldMapWidget.WorldCoordinate coordinate = context.coordinate();
        minecraft.setScreenAndShow(new MapMarkerEditorScreen(
                this, null, payload.dimension(), coordinate.x(), coordinate.y(), coordinate.z(), true));
    }

    private void editMarker(MapMarkerSyncPayload.Entry marker) {
        if (minecraft != null) {
            minecraft.setScreenAndShow(new MapMarkerEditorScreen(
                    this, marker, marker.dimension(), marker.x(), marker.y(), marker.z(), false));
        }
    }

    private void deleteMarker(MapMarkerSyncPayload.Entry marker) {
        if (!marker.id().equals(pendingDelete)) {
            pendingDelete = marker.id();
            status = "Click Delete again to confirm.";
            statusError = false;
            rebuildWidgets();
            return;
        }
        ClientPacketDistributor.sendToServer(new MapMarkerActionPayload(
                "delete", marker.id(), marker.name(), marker.dimension(), marker.x(), marker.y(), marker.z(),
                marker.colorArgb(), false));
        status = "Deleting marker…";
    }

    private void openMarkerManager() {
        if (minecraft != null) minecraft.setScreenAndShow(new MapMarkerManagementScreen(this));
    }

    private void pan(int dx, int dz) {
        requestMap(payload.centerChunkX() + dx, payload.centerChunkZ() + dz, payload.radius());
    }

    private void recenter() {
        if (minecraft.player != null) {
            requestMap(minecraft.player.chunkPosition().x(), minecraft.player.chunkPosition().z(), payload.radius());
        }
    }

    private void zoom(int direction) {
        int current = nearestZoomIndex(payload.radius());
        int next = Math.max(0, Math.min(ZOOM_RADII.length - 1, current + direction));
        if (ZOOM_RADII[next] != payload.radius()) {
            requestMap(payload.centerChunkX(), payload.centerChunkZ(), ZOOM_RADII[next]);
        }
    }

    private int nearestZoomIndex(int radius) {
        int best = 0;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < ZOOM_RADII.length; i++) {
            int candidate = Math.abs(ZOOM_RADII[i] - radius);
            if (candidate < distance) { best = i; distance = candidate; }
        }
        return best;
    }

    private void toggleClaims() {
        showClaims = !showClaims;
        saveMapSetting("worldmap_claims", showClaims);
        rebuildWidgets();
    }

    private void toggleRegions() {
        showRegions = !showRegions;
        saveMapSetting("worldmap_regions", showRegions);
        rebuildWidgets();
    }

    private void toggleMarkers() {
        showMarkers = !showMarkers;
        saveMapSetting("worldmap_markers", showMarkers);
        rebuildWidgets();
    }

    private void saveMapSetting(String key, boolean value) {
        ClientPacketDistributor.sendToServer(new PlayerUiSettingUpdatePayload(key, Boolean.toString(value)));
    }

    private void requestMap(int centerChunkX, int centerChunkZ, int radius) {
        payload = new WorldMapDataPayload(
                payload.allowed(), payload.dimension(), centerChunkX, centerChunkZ, radius,
                payload.ownClaimColor(), payload.otherClaimColor(), payload.regionColor(),
                showClaims, showRegions, payload.claims(), payload.regions());
        context = null;
        rebuildWidgets();
        ClientPacketDistributor.sendToServer(new WorldMapRequestPayload(centerChunkX, centerChunkZ, radius));
    }

    private void openClaimMap() {
        int claimRadius = Math.max(2, Math.min(12, payload.radius()));
        ClientPacketDistributor.sendToServer(new ClaimMapRequestPayload(
                payload.centerChunkX(), payload.centerChunkZ(), claimRadius, "", false));
    }

    private void backToMenu() {
        if (minecraft.player != null) minecraft.player.connection.sendUnattendedCommand("ssu menu", null);
    }

    @Override
    public void tick() {
        super.tick();
        terrainMap.tick(payload, Math.max(384, mapSize));
    }

    @Override
    public void removed() {
        terrainMap.close();
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.buttonInfo().button();

        // Treat the marker context menu as a modal overlay. Its buttons are drawn over
        // the map widget, so resolve their hitboxes here before the normal child-widget
        // dispatch can give the underlying map focus or consume the click.
        if (button == 0 && context != null) {
            return handleContextMenuClick(event.x(), event.y());
        }
        if (button == 1 && mapWidget != null && mapWidget.openContextAt(event.x(), event.y())) {
            return true;
        }
        if (button == 2 && mapWidget != null && mapWidget.beginMiddleDrag(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY) {
        if (context == null) return false;

        int left = contextMenuLeft();
        int top = contextMenuTop();
        MapMarkerSyncPayload.Entry marker = context.marker();

        if (marker == null) {
            if (SsuGuiGeometry.inside(mouseX, mouseY, left, top, 126, 20)) {
                createMarker();
                return true;
            }
        } else {
            if (SsuGuiGeometry.inside(mouseX, mouseY, left, top, 60, 20)) {
                editMarker(marker);
                return true;
            }
            if (SsuGuiGeometry.inside(mouseX, mouseY, left + 64, top, 62, 20)) {
                deleteMarker(marker);
                return true;
            }
        }
        if (SsuGuiGeometry.inside(mouseX, mouseY, left, top + 24, 126, 20)) {
            closeContext();
            return true;
        }

        // A click outside closes the overlay, but is still consumed so it cannot click
        // through to map controls or begin an unrelated map interaction.
        closeContext();
        return true;
    }

    private int contextMenuLeft() {
        return Math.max(mapLeft + 8, Math.min(mapLeft + mapSize - 134, context.screenX() + 5));
    }

    private int contextMenuTop() {
        return Math.max(mapTop + 8, Math.min(mapTop + mapSize - 52, context.screenY() + 5));
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
    public boolean isPauseScreen() { return false; }
}
