package be.winnetrie.mod.simpleserverutilities.client.minimap;

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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client lifecycle, polling and HUD rendering for the SSU minimap. */
public final class MinimapClientState {

    private static final int ENABLED_REFRESH_TICKS = 40;
    private static final int DISABLED_REFRESH_TICKS = 100;
    private static final int MARGIN = 8;
    private static final int PANEL = 3;

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

        if (!circle) {
            graphics.fill(x - PANEL, y - PANEL, x + size + PANEL, y + size + PANEL, 0xB5101318);
            graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFFE2C675);
        }

        graphics.enableScissor(x, y, x + size, y + size);
        if (data.northUp()) {
            TERRAIN.render(graphics, x, y, size);
        } else {
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
        drawMarkers(graphics, minecraft, x, y, size, circle);
        graphics.disableScissor();

        drawPlayerMarker(graphics, x + size / 2, y + size / 2,
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

    private static void drawPlayerMarker(GuiGraphicsExtractor graphics, int centerX, int centerY, float yaw) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(180.0F + yaw));
        graphics.pose().translate(-centerX, -centerY);
        graphics.fill(centerX - 2, centerY - 4, centerX + 3, centerY + 4, 0xFF171717);
        graphics.fill(centerX - 1, centerY - 5, centerX + 2, centerY + 3, 0xFFFFD54F);
        graphics.fill(centerX - 3, centerY + 1, centerX + 4, centerY + 4, 0xFF171717);
        graphics.fill(centerX - 2, centerY + 1, centerX + 3, centerY + 3, 0xFFFFFFFF);
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
                true, false, 96, "CIRCLE", "TOP_RIGHT", true, true, true, false, 8,
                "", 0, 0, 0xFF4BCB63, 0xFFE05B5B, 0xFFFFB347,
                java.util.List.of(), java.util.List.of()
        );
    }

    private record Position(int x, int y, boolean bottom) {
    }
}
