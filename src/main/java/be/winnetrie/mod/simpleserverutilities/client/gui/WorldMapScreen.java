package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.ClaimMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.PlayerUiSettingUpdatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Full-screen explored world map opened by the M key. */
public final class WorldMapScreen extends Screen {

    private static final int CONTROL_WIDTH = 190;
    private static final int[] ZOOM_RADII = {3, 5, 8, 12, 18, 24, 32};

    private WorldMapDataPayload payload;
    private final WorldMapTerrainMap terrainMap = new WorldMapTerrainMap();
    private WorldMapWidget mapWidget;
    private boolean showClaims = true;
    private boolean showRegions = true;
    private int mapSize = 384;

    public WorldMapScreen(WorldMapDataPayload payload) {
        super(Component.literal("World Map"));
        this.payload = payload;
        this.showClaims = payload.showClaims();
        this.showRegions = payload.showRegions();
    }

    public void acceptSnapshot(WorldMapDataPayload updated) {
        if (updated.centerChunkX() != payload.centerChunkX()
                || updated.centerChunkZ() != payload.centerChunkZ()
                || updated.radius() != payload.radius()) {
            return;
        }
        this.payload = updated;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int margin = 14;
        int panelWidth = Math.min(980, width - margin * 2);
        int panelLeft = (width - panelWidth) / 2;
        int top = 36;
        int bottom = height - 38;
        mapSize = Math.max(180, Math.min(bottom - top, panelWidth - CONTROL_WIDTH - 18));
        int mapLeft = panelLeft;
        int controlLeft = mapLeft + mapSize + 12;

        mapWidget = addRenderableWidget(new WorldMapWidget(
                mapLeft,
                top,
                mapSize,
                mapSize,
                payload,
                terrainMap,
                this::pan,
                this::zoom
        ));
        mapWidget.setShowClaims(showClaims);
        mapWidget.setShowRegions(showRegions);

        int step = panStep();
        addRenderableWidget(Button.builder(Component.literal("↖"), ignored -> pan(-step, -step))
                .bounds(controlLeft, top, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), ignored -> pan(0, -step))
                .bounds(controlLeft + 30, top, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↗"), ignored -> pan(step, -step))
                .bounds(controlLeft + 60, top, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("←"), ignored -> pan(-step, 0))
                .bounds(controlLeft, top + 22, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Center"), ignored -> recenter())
                .bounds(controlLeft + 30, top + 22, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), ignored -> pan(step, 0))
                .bounds(controlLeft + 90, top + 22, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↙"), ignored -> pan(-step, step))
                .bounds(controlLeft, top + 44, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), ignored -> pan(0, step))
                .bounds(controlLeft + 30, top + 44, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↘"), ignored -> pan(step, step))
                .bounds(controlLeft + 60, top + 44, 28, 20).build());

        Button zoomIn = Button.builder(Component.literal("Zoom +"), ignored -> zoom(-1))
                .bounds(controlLeft + 102, top, 82, 20).build();
        zoomIn.active = payload.radius() > ZOOM_RADII[0];
        addRenderableWidget(zoomIn);
        Button zoomOut = Button.builder(Component.literal("Zoom -"), ignored -> zoom(1))
                .bounds(controlLeft + 102, top + 22, 82, 20).build();
        zoomOut.active = payload.radius() < ZOOM_RADII[ZOOM_RADII.length - 1];
        addRenderableWidget(zoomOut);

        int y = top + 84;
        addRenderableWidget(Button.builder(
                        Component.literal("Claims: " + (showClaims ? "On" : "Off")),
                        ignored -> toggleClaims())
                .bounds(controlLeft, y, 184, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(
                        Component.literal("Regions: " + (showRegions ? "On" : "Off")),
                        ignored -> toggleRegions())
                .bounds(controlLeft, y, 184, 20).build());
        y += 38;
        addRenderableWidget(Button.builder(Component.literal("Open claim map"), ignored -> openClaimMap())
                .bounds(controlLeft, y, 184, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Refresh terrain"), ignored -> terrainMap.invalidate())
                .bounds(controlLeft, y, 184, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back to SSU menu"), ignored -> backToMenu())
                .bounds(panelLeft, height - 28, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Claim map"), ignored -> openClaimMap())
                .bounds(panelLeft + 136, height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(panelLeft + panelWidth - 70, height - 28, 70, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(980, width - 28);
        int panelLeft = (width - panelWidth) / 2;
        int top = 36;
        int controlLeft = panelLeft + mapSize + 12;

        graphics.text(font, "World map", panelLeft, 15, 0xFFFFFFFF);
        graphics.text(font,
                "Center: " + payload.centerChunkX() + ", " + payload.centerChunkZ()
                        + "  |  radius: " + payload.radius() + " chunks",
                panelLeft + 82,
                15,
                0xFFBEBEBE
        );

        if (!payload.allowed()) {
            graphics.text(font, "You do not have permission to use the world map.",
                    controlLeft, top + 180, 0xFFFF6B6B);
        } else {
            graphics.text(font, "Explored terrain", controlLeft, top + 178, 0xFFFFFFFF);
            graphics.text(font, "The map remembers chunks", controlLeft, top + 194, 0xFFCCCCCC);
            graphics.text(font, "seen during this session.", controlLeft, top + 208, 0xFFCCCCCC);
            int progress = terrainMap.progressPercent();
            if (progress < 100) {
                graphics.text(font, "Rendering: " + progress + "%", controlLeft, top + 232, 0xFFFFD66B);
            }
        }

        drawLegend(graphics, controlLeft, top + 262, payload.ownClaimColor(), "Your claim");
        drawLegend(graphics, controlLeft, top + 278, payload.otherClaimColor(), "Other claim");
        drawLegend(graphics, controlLeft, top + 294, payload.regionColor(), "Server region");

        WorldMapWidget.WorldCoordinate coordinate = mapWidget == null ? null : mapWidget.coordinateAt(mouseX, mouseY);
        if (coordinate != null) {
            graphics.text(font,
                    "X " + coordinate.x() + "  Z " + coordinate.z(),
                    panelLeft,
                    top + mapSize + 4,
                    0xFFFFFFFF
            );
        }
        graphics.text(font,
                "Hold right mouse and drag the map. Scroll to zoom. M opens this map.",
                panelLeft,
                height - 44,
                0xFFBEBEBE
        );
    }

    private void drawLegend(GuiGraphicsExtractor graphics, int x, int y, int color, String label) {
        graphics.fill(x, y + 2, x + 10, y + 12, withAlpha(color, 0x48));
        graphics.outline(x, y + 2, 10, 10, color);
        graphics.text(font, label, x + 15, y + 2, 0xFFCCCCCC);
    }

    private void pan(int dx, int dz) {
        requestMap(payload.centerChunkX() + dx, payload.centerChunkZ() + dz, payload.radius());
    }

    private void recenter() {
        if (minecraft.player == null) {
            return;
        }
        requestMap(minecraft.player.chunkPosition().x(), minecraft.player.chunkPosition().z(), payload.radius());
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
            if (candidate < distance) {
                best = i;
                distance = candidate;
            }
        }
        return best;
    }

    private int panStep() {
        return Math.max(1, payload.radius() / 3);
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

    private void saveMapSetting(String key, boolean value) {
        ClientPacketDistributor.sendToServer(new PlayerUiSettingUpdatePayload(key, Boolean.toString(value)));
    }

    private void requestMap(int centerChunkX, int centerChunkZ, int radius) {
        /*
         * Update the viewport immediately so drag and zoom feedback are local.
         * Existing overlays remain anchored to their world coordinates until
         * the authoritative server response supplies the complete new set.
         */
        payload = new WorldMapDataPayload(
                payload.allowed(),
                payload.dimension(),
                centerChunkX,
                centerChunkZ,
                radius,
                payload.ownClaimColor(),
                payload.otherClaimColor(),
                payload.regionColor(),
                showClaims,
                showRegions,
                payload.claims(),
                payload.regions()
        );
        rebuildWidgets();
        ClientPacketDistributor.sendToServer(new WorldMapRequestPayload(centerChunkX, centerChunkZ, radius));
    }

    private void openClaimMap() {
        int claimRadius = Math.max(2, Math.min(12, payload.radius()));
        ClientPacketDistributor.sendToServer(new ClaimMapRequestPayload(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                claimRadius,
                ""
        ));
    }

    private void backToMenu() {
        if (minecraft.player != null) {
            minecraft.player.connection.sendUnattendedCommand("ssu menu", null);
        }
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
        if (event.buttonInfo().button() == 1
                && mapWidget != null
                && mapWidget.beginRightDrag(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (mapWidget != null && mapWidget.isRightDragging()) {
            return mapWidget.updateRightDrag(event.x(), event.y());
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (mapWidget != null && mapWidget.isRightDragging()) {
            return mapWidget.finishRightDrag();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }
}
