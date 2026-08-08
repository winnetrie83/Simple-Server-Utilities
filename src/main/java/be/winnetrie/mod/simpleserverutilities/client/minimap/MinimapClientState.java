package be.winnetrie.mod.simpleserverutilities.client.minimap;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.client.map.AerialMapAtlas;
import be.winnetrie.mod.simpleserverutilities.client.mapmarker.MapMarkerClientState;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client lifecycle, polling and HUD rendering for the SSU minimap. */
public final class MinimapClientState {

    private static final int ENABLED_REFRESH_TICKS = 40;
    private static final int DISABLED_REFRESH_TICKS = 100;
    private static final int MARGIN = 8;
    private static final int PANEL = 3;
    private static final int CIRCLE_SCISSOR_BANDS = 32;
    private static final int FRAME_TEXTURE_SIZE = 256;
    private static final int RECT_FRAME_INSET_LEFT = 32;
    private static final int RECT_FRAME_INSET_TOP = 32;
    private static final int RECT_FRAME_INSET_RIGHT = 33;
    private static final int RECT_FRAME_INSET_BOTTOM = 34;
    private static final Identifier FRAME_RECTANGLE = Identifier.fromNamespaceAndPath(
            SimpleServerUtilities.MODID, "textures/gui/minimap/frame_rectangle.png");
    private static final Identifier FRAME_CIRCLE = Identifier.fromNamespaceAndPath(
            SimpleServerUtilities.MODID, "textures/gui/minimap/frame_circle.png");
    private static final Identifier PLAYER_ARROW = Identifier.fromNamespaceAndPath(
            SimpleServerUtilities.MODID, "textures/gui/minimap/arrow.png");

    private static final MinimapTerrainMap TERRAIN = new MinimapTerrainMap();

    private static MinimapDataPayload data = defaults();
    private static int requestCountdown;
    private static String lastDimension = "";
    private static boolean showCalendar;

    private MinimapClientState() {
    }

    public static void apply(MinimapDataPayload payload) {
        // Terrain.tick compares the effective overlay hash itself. Repeated,
        // identical server snapshots must not invalidate the visible texture.
        data = payload;
        showCalendar = payload.showCalendar();
        AerialMapAtlas.setLiveUpdateRadiusChunks(payload.liveUpdateRadiusChunks());
        requestCountdown = payload.enabled() ? ENABLED_REFRESH_TICKS : DISABLED_REFRESH_TICKS;
    }

    public static void applySettings(SsuMenuSnapshotPayload.UiSettingsSummary settings) {
        data = new MinimapDataPayload(
                data.allowed(),
                data.allowed() && settings.minimapEnabled(),
                settings.minimapSize(),
                settings.minimapShape(),
                settings.minimapTexturedFrame(),
                settings.minimapPosition(),
                settings.minimapNorthUp(),
                settings.minimapShowClaims(),
                settings.minimapShowRegions(),
                settings.minimapShowCalendar(),
                settings.mapLiveUpdateRadiusChunks(),
                data.dimension(),
                data.centerChunkX(),
                data.centerChunkZ(),
                data.ownClaimColor(),
                data.otherClaimColor(),
                data.regionColor(),
                data.claims(),
                data.regions()
        );
        showCalendar = settings.minimapShowCalendar();
        // Size, position and rotation are presentation-only. Shape and overlay
        // changes are detected without clearing the currently visible map.
        requestCountdown = 0;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        String dimension = minecraft.level.dimension().identifier().toString();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            requestCountdown = 0;
            TERRAIN.invalidate();
        }

        int playerChunkX = ((int) Math.floor(minecraft.player.getX())) >> 4;
        int playerChunkZ = ((int) Math.floor(minecraft.player.getZ())) >> 4;
        if (dimension.equals(data.dimension())
                && (Math.abs(playerChunkX - data.centerChunkX()) >= 2
                        || Math.abs(playerChunkZ - data.centerChunkZ()) >= 2)) {
            requestCountdown = 0;
        }

        if (requestCountdown-- <= 0) {
            ClientPacketDistributor.sendToServer(new MinimapRequestPayload());
            requestCountdown = data.enabled() ? ENABLED_REFRESH_TICKS : DISABLED_REFRESH_TICKS;
        }

        if (data.enabled() && dimension.equals(data.dimension())) {
            TERRAIN.tick(data);
        }
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!data.allowed() || !data.enabled() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.gui.screen() != null) {
            return;
        }
        String dimension = minecraft.level.dimension().identifier().toString();
        if (!dimension.equals(data.dimension())) {
            return;
        }

        int calendarFooterHeight = showCalendar ? 26 : 0;
        int maximumWidth = graphics.guiWidth() - MARGIN * 2;
        int maximumHeight = showCalendar
                ? graphics.guiHeight() - MARGIN * 2 - calendarFooterHeight
                : graphics.guiHeight() - 36;
        int maximum = Math.max(48, Math.min(maximumWidth, maximumHeight));
        int size = Math.max(48, Math.min(data.size(), maximum));
        Position position = position(graphics.guiWidth(), graphics.guiHeight(), size, data.position(), calendarFooterHeight);
        int x = position.x();
        int y = position.y();
        boolean circle = "CIRCLE".equalsIgnoreCase(data.shape());
        Rect mapArea = texturedMapArea(x, y, size, circle, data.texturedFrame());

        if (!data.texturedFrame() && !circle) {
            // Preserve the original SSU minimap border when the player chooses Classic.
            graphics.fill(x - PANEL, y - PANEL, x + size + PANEL, y + size + PANEL, 0xB5101318);
            graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFFE2C675);
        }

        if (circle && data.texturedFrame()) {
            renderCircularTerrain(graphics, minecraft, mapArea.x(), mapArea.y(), mapArea.size());
            // Marker placement already rejects markers outside the circular radius.
            graphics.enableScissor(mapArea.x(), mapArea.y(), mapArea.right(), mapArea.bottom());
            drawMarkers(graphics, minecraft, mapArea.x(), mapArea.y(), mapArea.size(), true);
            graphics.disableScissor();
        } else {
            graphics.enableScissor(mapArea.x(), mapArea.y(), mapArea.right(), mapArea.bottom());
            renderTerrain(graphics, minecraft, mapArea.x(), mapArea.y(), mapArea.size(), circle);
            drawMarkers(graphics, minecraft, mapArea.x(), mapArea.y(), mapArea.size(), circle);
            graphics.disableScissor();
        }

        if (data.texturedFrame()) {
            // The decorative frame sits above terrain/edge markers, while the player
            // marker and compass label stay readable above the frame itself.
            drawTexturedFrame(graphics, x, y, size, circle);
        }
        drawPlayerMarker(graphics, mapArea.x() + mapArea.size() / 2, mapArea.y() + mapArea.size() / 2,
                data.northUp() ? minecraft.player.getYRot() : 0.0F);
        if (data.northUp()) {
            graphics.centeredText(minecraft.font, "N", x + size / 2, y + 3, 0xFFFFFFFF);
        }

        String coordinates = (int) Math.floor(minecraft.player.getX())
                + ", " + (int) Math.floor(minecraft.player.getZ());
        int labelY = position.bottom() && !showCalendar ? y - 11 : y + size + 4;
        drawCenteredHudLabel(graphics, minecraft, coordinates, x + size / 2, labelY);
        if (showCalendar) {
            String calendar = GameCalendar.fromClockTime(minecraft.level.getDefaultClockTime()).displayText();
            drawCenteredHudLabel(graphics, minecraft, calendar, x + size / 2, labelY + 12);
        }
    }

    private static void renderTerrain(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            int x,
            int y,
            int size,
            boolean circle
    ) {
        if (data.northUp()) {
            TERRAIN.render(graphics, x, y, size);
            return;
        }
        int drawSize = circle ? size : (int) Math.ceil(size * 1.43D);
        int drawX = x + (size - drawSize) / 2;
        int drawY = y + (size - drawSize) / 2;
        float centerX = x + size / 2.0F;
        float centerY = y + size / 2.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(-(180.0F + minecraft.player.getYRot())));
        graphics.pose().translate(-centerX, -centerY);
        TERRAIN.render(graphics, drawX, drawY, drawSize);
        graphics.pose().popMatrix();
    }

    /**
     * GuiGraphicsExtractor only exposes rectangular scissors. A small bounded set of
     * horizontal strips approximates a circular stencil without introducing a custom
     * render pipeline; 32 terrain blits is trivial at the minimap's maximum 256 px size.
     */
    private static void renderCircularTerrain(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            int x,
            int y,
            int size
    ) {
        double radius = size / 2.0D;
        double centerY = y + radius;
        for (int band = 0; band < CIRCLE_SCISSOR_BANDS; band++) {
            int top = y + (band * size) / CIRCLE_SCISSOR_BANDS;
            int bottom = y + ((band + 1) * size) / CIRCLE_SCISSOR_BANDS;
            if (bottom <= top) continue;
            double sampleY = (top + bottom) * 0.5D;
            double dy = sampleY - centerY;
            double halfWidth = Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
            int left = Math.max(x, (int) Math.floor(x + radius - halfWidth));
            int right = Math.min(x + size, (int) Math.ceil(x + radius + halfWidth));
            if (right <= left) continue;
            graphics.enableScissor(left, top, right, bottom);
            renderTerrain(graphics, minecraft, x, y, size, true);
            graphics.disableScissor();
        }
    }

    private static void drawTexturedFrame(
            GuiGraphicsExtractor graphics, int x, int y, int size, boolean circle
    ) {
        Identifier texture = circle ? FRAME_CIRCLE : FRAME_RECTANGLE;
        float scale = size / (float) FRAME_TEXTURE_SIZE;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                0, 0, 0, 0, FRAME_TEXTURE_SIZE, FRAME_TEXTURE_SIZE,
                FRAME_TEXTURE_SIZE, FRAME_TEXTURE_SIZE);
        graphics.pose().popMatrix();
    }



    private static void drawMarkers(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            int left,
            int top,
            int size,
            boolean circle
    ) {
        if (!MapMarkerClientState.showOnMinimap() || minecraft.player == null || minecraft.level == null) return;
        String dimension = minecraft.level.dimension().identifier().toString();
        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        double scale = size / (double) MinimapTerrainMap.VISIBLE_BLOCKS;
        double angle = data.northUp() ? 0.0D : Math.toRadians(-(180.0F + minecraft.player.getYRot()));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double centerX = left + size / 2.0D;
        double centerY = top + size / 2.0D;
        double radius = size / 2.0D - 4.0D;
        for (MapMarkerSyncPayload.Entry marker : MapMarkerClientState.markers()) {
            if (!dimension.equals(marker.dimension())) continue;
            double dx = (marker.x() + 0.5D - playerX) * scale;
            double dz = (marker.z() + 0.5D - playerZ) * scale;
            double rotatedX = dx * cos - dz * sin;
            double rotatedY = dx * sin + dz * cos;
            if (Math.abs(rotatedX) > size / 2.0D || Math.abs(rotatedY) > size / 2.0D) continue;
            if (circle && rotatedX * rotatedX + rotatedY * rotatedY > radius * radius) continue;
            int x = (int) Math.round(centerX + rotatedX);
            int y = (int) Math.round(centerY + rotatedY);
            drawMarkerIcon(graphics, x, y, marker.colorArgb());
        }
    }

    private static void drawMarkerIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x - 1, y - 3, x + 2, y + 4, 0xE6111111);
        graphics.fill(x - 3, y - 1, x + 4, y + 2, 0xE6111111);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xE6111111);
        graphics.fill(x - 1, y - 2, x + 2, y + 3, color);
        graphics.fill(x - 2, y - 1, x + 3, y + 2, color);
        graphics.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
    }

    private static void drawCenteredHudLabel(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                                 String text, int centerX, int y) {
        int textWidth = minecraft.font.width(text);
        graphics.fill(centerX - textWidth / 2 - 3, y - 2,
                centerX + textWidth / 2 + 3, y + 10, 0xA0000000);
        graphics.centeredText(minecraft.font, text, centerX, y, 0xFFF2F2F2);
    }

    public static void clear() {
        data = defaults();
        requestCountdown = 0;
        lastDimension = "";
        showCalendar = false;
        TERRAIN.close();
    }

    private static Rect texturedMapArea(int x, int y, int size, boolean circle, boolean texturedFrame) {
        if (!texturedFrame) return new Rect(x, y, size);
        if (circle) return new Rect(x, y, size);
        int left = x + Math.round(size * (RECT_FRAME_INSET_LEFT / (float) FRAME_TEXTURE_SIZE));
        int top = y + Math.round(size * (RECT_FRAME_INSET_TOP / (float) FRAME_TEXTURE_SIZE));
        int right = x + size - Math.round(size * (RECT_FRAME_INSET_RIGHT / (float) FRAME_TEXTURE_SIZE));
        int bottom = y + size - Math.round(size * (RECT_FRAME_INSET_BOTTOM / (float) FRAME_TEXTURE_SIZE));
        int inner = Math.max(16, Math.min(right - left, bottom - top));
        int centeredLeft = left + Math.max(0, (right - left - inner) / 2);
        int centeredTop = top + Math.max(0, (bottom - top - inner) / 2);
        return new Rect(centeredLeft, centeredTop, inner);
    }

    private static void drawPlayerMarker(GuiGraphicsExtractor graphics, int centerX, int centerY, float yaw) {
        int markerSize = 16;
        int markerX = centerX - markerSize / 2;
        int markerY = centerY - markerSize / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(180.0F + yaw));
        graphics.pose().translate(-centerX, -centerY);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PLAYER_ARROW, markerX, markerY,
                0, 0, markerSize, markerSize, markerSize, markerSize);
        graphics.pose().popMatrix();
    }

    private static Position position(int guiWidth, int guiHeight, int size, String rawPosition, int footerHeight) {
        String normalized = rawPosition == null ? "TOP_RIGHT" : rawPosition.toUpperCase(java.util.Locale.ROOT);
        boolean right = normalized.endsWith("RIGHT");
        boolean bottom = normalized.startsWith("BOTTOM");
        int x = right ? guiWidth - size - MARGIN : MARGIN;
        int y = bottom
                ? guiHeight - size - MARGIN - (footerHeight > 0 ? footerHeight : 12)
                : MARGIN;
        return new Position(x, y, bottom);
    }

    private static MinimapDataPayload defaults() {
        return new MinimapDataPayload(
                true, false, 96, "CIRCLE", false, "TOP_RIGHT", true, true, true, false, 8,
                "", 0, 0, 0xFF4BCB63, 0xFFE05B5B, 0xFFFFB347,
                java.util.List.of(), java.util.List.of()
        );
    }

    private record Position(int x, int y, boolean bottom) {
    }

    private record Rect(int x, int y, int size) {
        int right() { return x + size; }
        int bottom() { return y + size; }
    }
}
