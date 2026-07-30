package be.winnetrie.mod.simpleserverutilities.client.minimap;

import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
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

    private MinimapClientState() {
    }

    public static void apply(MinimapDataPayload payload) {
        // Terrain.tick compares the effective overlay hash itself. Repeated,
        // identical server snapshots must not invalidate the visible texture.
        data = payload;
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
                data.dimension(),
                data.centerChunkX(),
                data.centerChunkZ(),
                data.ownClaimColor(),
                data.otherClaimColor(),
                data.regionColor(),
                data.claims(),
                data.regions()
        );
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

        int maximumWidth = graphics.guiWidth() - MARGIN * 2;
        int maximumHeight = graphics.guiHeight() - 36;
        int maximum = Math.max(48, Math.min(maximumWidth, maximumHeight));
        int size = Math.max(48, Math.min(data.size(), maximum));
        Position position = position(graphics.guiWidth(), graphics.guiHeight(), size, data.position());
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
        graphics.disableScissor();

        drawPlayerMarker(graphics, x + size / 2, y + size / 2,
                data.northUp() ? minecraft.player.getYRot() : 0.0F);
        if (data.northUp()) {
            graphics.centeredText(minecraft.font, "N", x + size / 2, y + 3, 0xFFFFFFFF);
        }

        String coordinates = (int) Math.floor(minecraft.player.getX())
                + ", " + (int) Math.floor(minecraft.player.getZ());
        int labelY = position.bottom() ? y - 11 : y + size + 4;
        int textWidth = minecraft.font.width(coordinates);
        graphics.fill(x + size / 2 - textWidth / 2 - 3, labelY - 2,
                x + size / 2 + textWidth / 2 + 3, labelY + 10, 0xA0000000);
        graphics.centeredText(minecraft.font, coordinates, x + size / 2, labelY, 0xFFF2F2F2);
    }

    public static void clear() {
        data = defaults();
        requestCountdown = 0;
        lastDimension = "";
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

    private static Position position(int guiWidth, int guiHeight, int size, String rawPosition) {
        String normalized = rawPosition == null ? "TOP_RIGHT" : rawPosition.toUpperCase(java.util.Locale.ROOT);
        boolean right = normalized.endsWith("RIGHT");
        boolean bottom = normalized.startsWith("BOTTOM");
        int x = right ? guiWidth - size - MARGIN : MARGIN;
        int y = bottom ? guiHeight - size - MARGIN - 12 : MARGIN;
        return new Position(x, y, bottom);
    }

    private static MinimapDataPayload defaults() {
        return new MinimapDataPayload(
                true, false, 96, "CIRCLE", "TOP_RIGHT", true, true, true,
                "", 0, 0, 0xFF4BCB63, 0xFFE05B5B, 0xFFFFB347,
                java.util.List.of(), java.util.List.of()
        );
    }

    private record Position(int x, int y, boolean bottom) {
    }
}
