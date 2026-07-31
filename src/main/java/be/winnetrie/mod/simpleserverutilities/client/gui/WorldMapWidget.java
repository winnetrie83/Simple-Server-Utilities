package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.client.map.AerialMapAtlas;
import be.winnetrie.mod.simpleserverutilities.client.mapmarker.MapMarkerClientState;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Large interactive world-map canvas with overlays and marker context clicks. */
final class WorldMapWidget extends AbstractWidget {

    private static final int BACKGROUND = 0xE610141A;
    private static final int FRAME = 0xFF64778D;
    private static final int PLAYER_OUTLINE = 0xFF161616;
    private static final int PLAYER_COLOR = 0xFFFFD54F;

    private WorldMapDataPayload payload;
    private final WorldMapTerrainMap terrainMap;
    private final BiConsumer<Integer, Integer> onPan;
    private final IntConsumer onZoom;
    private final java.util.function.Consumer<ContextClick> onContext;
    private boolean showClaims = true;
    private boolean showRegions = true;
    private boolean showMarkers = true;
    private boolean middleDragging;
    private double dragStartX;
    private double dragStartY;
    private double dragCurrentX;
    private double dragCurrentY;

    WorldMapWidget(
            int x,
            int y,
            int width,
            int height,
            WorldMapDataPayload payload,
            WorldMapTerrainMap terrainMap,
            BiConsumer<Integer, Integer> onPan,
            IntConsumer onZoom,
            java.util.function.Consumer<ContextClick> onContext
    ) {
        super(x, y, width, height, Component.literal("World map"));
        this.payload = payload;
        this.terrainMap = terrainMap;
        this.onPan = onPan;
        this.onZoom = onZoom;
        this.onContext = onContext;
        terrainMap.ensureView(payload, Math.max(width, height));
    }

    void update(WorldMapDataPayload payload) {
        this.payload = payload;
        terrainMap.ensureView(payload, Math.max(getWidth(), getHeight()));
    }

    void setShowClaims(boolean value) { showClaims = value; }
    void setShowRegions(boolean value) { showRegions = value; }
    void setShowMarkers(boolean value) { showMarkers = value; }

    WorldCoordinate coordinateAt(double mouseX, double mouseY) {
        LocationInfo location = locationAt(mouseX, mouseY);
        return location == null ? null : location.coordinate();
    }

    LocationInfo locationAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return null;
        int totalBlocks = totalBlocks();
        int worldX = minimumBlockX() + Math.min(totalBlocks - 1, Math.max(0,
                (int) ((mouseX - getX() - dragOffsetX()) * totalBlocks / getWidth())));
        int worldZ = minimumBlockZ() + Math.min(totalBlocks - 1, Math.max(0,
                (int) ((mouseY - getY() - dragOffsetY()) * totalBlocks / getHeight())));
        Minecraft minecraft = Minecraft.getInstance();
        int fallback = minecraft.player == null ? 64 : (int) Math.floor(minecraft.player.getY()) - 1;
        if (minecraft.level == null
                || !payload.dimension().equals(minecraft.level.dimension().identifier().toString())) {
            return new LocationInfo(new WorldCoordinate(worldX, fallback + 1, worldZ), "", "");
        }
        AerialMapAtlas.SurfaceInfo info =
                AerialMapAtlas.surfaceInfoAvailable(minecraft.level, worldX, worldZ, fallback);
        return new LocationInfo(
                new WorldCoordinate(worldX, info.surfaceY() + 1, worldZ),
                info.blockId(),
                info.biomeId()
        );
    }

    LocationInfo locationFor(WorldCoordinate coordinate) {
        if (coordinate == null) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !payload.dimension().equals(minecraft.level.dimension().identifier().toString())) {
            return new LocationInfo(coordinate, "", "");
        }
        AerialMapAtlas.SurfaceInfo info = AerialMapAtlas.surfaceInfoAvailable(
                minecraft.level, coordinate.x(), coordinate.z(), coordinate.y() - 1);
        return new LocationInfo(
                new WorldCoordinate(coordinate.x(), info.surfaceY() + 1, coordinate.z()),
                info.blockId(),
                info.biomeId()
        );
    }

    MapMarkerSyncPayload.Entry markerAt(double mouseX, double mouseY) {
        if (!showMarkers || !isMouseOver(mouseX, mouseY)) return null;
        MapMarkerSyncPayload.Entry best = null;
        double bestDistance = 9.0D * 9.0D;
        for (MapMarkerSyncPayload.Entry marker : MapMarkerClientState.markers()) {
            if (!payload.dimension().equals(marker.dimension()) || !inside(marker.x(), marker.z())) continue;
            int screenX = worldToScreenX(marker.x());
            int screenY = worldToScreenZ(marker.z());
            double dx = mouseX - screenX;
            double dy = mouseY - screenY;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = marker;
            }
        }
        return best;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BACKGROUND);
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        terrainMap.render(graphics, getX() + dragOffsetX(), getY() + dragOffsetY(),
                Math.min(getWidth(), getHeight()), payload);
        if (showClaims) drawClaims(graphics);
        if (showRegions) drawRegions(graphics);
        if (showMarkers) drawMarkers(graphics);
        drawPlayer(graphics);
        graphics.disableScissor();
        graphics.outline(getX(), getY(), getWidth(), getHeight(), FRAME);
        graphics.centeredText(Minecraft.getInstance().font, "N", getX() + getWidth() / 2, getY() + 4, 0xFFFFFFFF);
    }

    private void drawClaims(GuiGraphicsExtractor graphics) {
        Map<ClaimChunkStatus, Set<Long>> groups = new LinkedHashMap<>();
        for (WorldMapDataPayload.ClaimOverlay claim : payload.claims()) {
            groups.computeIfAbsent(claim.status(), ignored -> new LinkedHashSet<>())
                    .add(key(claim.chunkX(), claim.chunkZ()));
        }
        for (Map.Entry<ClaimChunkStatus, Set<Long>> group : groups.entrySet()) {
            int color = claimColor(group.getKey());
            for (long cell : group.getValue()) {
                Bounds bounds = chunkBounds(keyX(cell), keyZ(cell));
                if (bounds != null) graphics.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, withAlpha(color, 0x28));
            }
        }
        for (Map.Entry<ClaimChunkStatus, Set<Long>> group : groups.entrySet()) {
            int color = claimColor(group.getKey());
            Set<Long> cells = group.getValue();
            for (long cell : cells) {
                int chunkX = keyX(cell);
                int chunkZ = keyZ(cell);
                Bounds bounds = chunkBounds(chunkX, chunkZ);
                if (bounds == null) continue;
                if (!cells.contains(key(chunkX, chunkZ - 1))) graphics.fill(bounds.left, bounds.top, bounds.right, bounds.top + 1, color);
                if (!cells.contains(key(chunkX, chunkZ + 1))) graphics.fill(bounds.left, bounds.bottom - 1, bounds.right, bounds.bottom, color);
                if (!cells.contains(key(chunkX - 1, chunkZ))) graphics.fill(bounds.left, bounds.top, bounds.left + 1, bounds.bottom, color);
                if (!cells.contains(key(chunkX + 1, chunkZ))) graphics.fill(bounds.right - 1, bounds.top, bounds.right, bounds.bottom, color);
            }
        }
    }

    private void drawRegions(GuiGraphicsExtractor graphics) {
        for (WorldMapDataPayload.RegionOverlay region : payload.regions()) {
            int left = worldToScreenX(region.minX());
            int top = worldToScreenZ(region.minZ());
            int right = worldToScreenX(region.maxX() + 1);
            int bottom = worldToScreenZ(region.maxZ() + 1);
            if (right <= getX() || bottom <= getY() || left >= getX() + getWidth() || top >= getY() + getHeight()) continue;
            left = Math.max(getX(), left);
            top = Math.max(getY(), top);
            right = Math.min(getX() + getWidth(), right);
            bottom = Math.min(getY() + getHeight(), bottom);
            graphics.fill(left, top, right, bottom, withAlpha(payload.regionColor(), 0x20));
            outline(graphics, left, top, right, bottom, payload.regionColor());
        }
    }

    private void drawMarkers(GuiGraphicsExtractor graphics) {
        for (MapMarkerSyncPayload.Entry marker : MapMarkerClientState.markers()) {
            if (!payload.dimension().equals(marker.dimension()) || !inside(marker.x(), marker.z())) continue;
            int x = worldToScreenX(marker.x());
            int y = worldToScreenZ(marker.z());
            int color = marker.colorArgb();
            drawMarkerCircle(graphics, x, y, color);
        }
    }


    private static void drawMarkerCircle(GuiGraphicsExtractor graphics, int x, int y, int color) {
        // Pixel-rounded circle: dark outline, selected colour and a small white centre.
        graphics.fill(x - 1, y - 4, x + 2, y + 5, 0xE6111111);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xE6111111);
        graphics.fill(x - 4, y - 1, x + 5, y + 2, 0xE6111111);
        graphics.fill(x - 1, y - 3, x + 2, y + 4, color);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        graphics.fill(x - 3, y - 1, x + 4, y + 2, color);
        graphics.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
    }

    private void drawPlayer(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().identifier().toString().equals(payload.dimension())) return;
        int playerX = (int) Math.floor(minecraft.player.getX());
        int playerZ = (int) Math.floor(minecraft.player.getZ());
        if (!inside(playerX, playerZ)) return;
        int x = worldToScreenX(playerX);
        int z = worldToScreenZ(playerZ);
        graphics.fill(x - 3, z - 3, x + 4, z + 4, PLAYER_OUTLINE);
        graphics.fill(x - 2, z - 2, x + 3, z + 3, PLAYER_COLOR);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;
        int button = event.buttonInfo().button();
        if (button == 1) return openContextAt(event.x(), event.y());
        return button == 2 && beginMiddleDrag(event.x(), event.y());
    }

    boolean openContextAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        WorldCoordinate coordinate = coordinateAt(mouseX, mouseY);
        if (coordinate != null) {
            onContext.accept(new ContextClick(coordinate, markerAt(mouseX, mouseY),
                    (int) Math.round(mouseX), (int) Math.round(mouseY)));
        }
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return event.buttonInfo().button() == 2 && updateMiddleDrag(event.x(), event.y());
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return event.buttonInfo().button() == 2 && finishMiddleDrag();
    }

    boolean beginMiddleDrag(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        setFocused(true);
        middleDragging = true;
        dragStartX = dragCurrentX = mouseX;
        dragStartY = dragCurrentY = mouseY;
        return true;
    }

    boolean updateMiddleDrag(double mouseX, double mouseY) {
        if (!middleDragging) return false;
        dragCurrentX = mouseX;
        dragCurrentY = mouseY;
        return true;
    }

    boolean finishMiddleDrag() {
        if (!middleDragging) return false;
        int previewX = dragOffsetX();
        int previewY = dragOffsetY();
        middleDragging = false;
        double pixelsPerChunkX = getWidth() / (double) (payload.radius() * 2 + 1);
        double pixelsPerChunkZ = getHeight() / (double) (payload.radius() * 2 + 1);
        int chunkDeltaX = MapPanMath.chunkDelta(previewX, pixelsPerChunkX);
        int chunkDeltaZ = MapPanMath.chunkDelta(previewY, pixelsPerChunkZ);
        if (chunkDeltaX != 0 || chunkDeltaZ != 0) onPan.accept(chunkDeltaX, chunkDeltaZ);
        return true;
    }

    boolean isMiddleDragging() { return middleDragging; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0.0D) return false;
        onZoom.accept(scrollY > 0.0D ? -1 : 1);
        return true;
    }

    private Bounds chunkBounds(int chunkX, int chunkZ) {
        int left = worldToScreenX(chunkX << 4);
        int top = worldToScreenZ(chunkZ << 4);
        int right = worldToScreenX((chunkX + 1) << 4);
        int bottom = worldToScreenZ((chunkZ + 1) << 4);
        if (right <= getX() || bottom <= getY() || left >= getX() + getWidth() || top >= getY() + getHeight()) return null;
        return new Bounds(Math.max(getX(), left), Math.max(getY(), top),
                Math.min(getX() + getWidth(), Math.max(left + 1, right)),
                Math.min(getY() + getHeight(), Math.max(top + 1, bottom)));
    }

    private int worldToScreenX(int worldX) {
        return getX() + dragOffsetX() + (int) Math.round((worldX - minimumBlockX()) * getWidth() / (double) totalBlocks());
    }

    private int worldToScreenZ(int worldZ) {
        return getY() + dragOffsetY() + (int) Math.round((worldZ - minimumBlockZ()) * getHeight() / (double) totalBlocks());
    }

    private int dragOffsetX() { return middleDragging ? (int) Math.round(dragCurrentX - dragStartX) : 0; }
    private int dragOffsetY() { return middleDragging ? (int) Math.round(dragCurrentY - dragStartY) : 0; }
    private int minimumBlockX() { return (payload.centerChunkX() - payload.radius()) << 4; }
    private int minimumBlockZ() { return (payload.centerChunkZ() - payload.radius()) << 4; }
    private int totalBlocks() { return (payload.radius() * 2 + 1) * 16; }
    private boolean inside(int x, int z) {
        return x >= minimumBlockX() && z >= minimumBlockZ()
                && x < minimumBlockX() + totalBlocks() && z < minimumBlockZ() + totalBlocks();
    }

    private int claimColor(ClaimChunkStatus status) {
        return switch (status) {
            case OWNED_BY_SELF -> payload.ownClaimColor();
            case OWNED_BY_TRUSTED -> lighten(payload.ownClaimColor(), 36);
            case OWNED_BY_OTHER -> payload.otherClaimColor();
            case REGION -> payload.regionColor();
            case WILDERNESS -> 0x00000000;
        };
    }

    private static int lighten(int argb, int amount) {
        int red = Math.min(255, ((argb >>> 16) & 0xFF) + amount);
        int green = Math.min(255, ((argb >>> 8) & 0xFF) + amount);
        int blue = Math.min(255, (argb & 0xFF) + amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int withAlpha(int argb, int alpha) { return (alpha << 24) | (argb & 0x00FFFFFF); }
    private static void outline(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
    private static int keyX(long key) { return (int) (key >> 32); }
    private static int keyZ(long key) { return (int) key; }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }

    record WorldCoordinate(int x, int y, int z) {
    }

    record LocationInfo(WorldCoordinate coordinate, String blockId, String biomeId) {
    }

    record ContextClick(WorldCoordinate coordinate, MapMarkerSyncPayload.Entry marker, int screenX, int screenY) {
    }

    private record Bounds(int left, int top, int right, int bottom) {
    }
}
