package be.winnetrie.mod.simpleserverutilities.client.minigame;

import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Labels and outlines minigame setup objects while the administrator holds the Setup Tool. */
public final class MinigameSetupVisualRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final String TOOL_NAME = "SSU Minigame Setup Tool";
    /** Roughly 3.6 times the former setup-label size. */
    private static final float LABEL_SCALE = 0.40F;
    private static final double LABEL_HALF_WIDTH_PER_FONT_PIXEL = 0.01350D;
    private static final double LABEL_HORIZONTAL_PADDING = 0.14D;
    private static final double LABEL_HALF_HEIGHT = 0.29D;
    private static final double BACKGROUND_DEPTH_OFFSET = 0.008D;
    private static final int BACKGROUND_COLOR = 0xB0000000;
    private static final double BOUNDS_EPSILON = 0.003D;
    private final Minecraft minecraft;

    public MinigameSetupVisualRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.level == null || minecraft.player == null) return;
        if (!TOOL_NAME.equals(minecraft.player.getMainHandItem().getHoverName().getString())) return;
        String dimension = minecraft.level.dimension().identifier().toString();
        var camera = minecraft.gameRenderer.mainCamera();
        var forwardVector = camera.forwardVector();
        var leftVector = camera.leftVector();
        var upVector = camera.upVector();
        Vec3 cameraForward = new Vec3(forwardVector.x(), forwardVector.y(), forwardVector.z()).normalize();
        Vec3 cameraHorizontal = new Vec3(leftVector.x(), leftVector.y(), leftVector.z()).normalize();
        Vec3 cameraUp = new Vec3(upVector.x(), upVector.y(), upVector.z()).normalize();

        for (MinigameSetupVisualPayload.Bounds entry : MinigameSetupVisualClientState.boundsSnapshot()) {
            if (!dimension.equals(entry.dimension())) continue;
            renderBounds(entry, frustum, cameraForward, cameraHorizontal, cameraUp);
        }
        for (MinigameSetupVisualPayload.Entry entry : MinigameSetupVisualClientState.markerSnapshot()) {
            if (!dimension.equals(entry.dimension())) continue;
            int color = 0xFF000000 | (entry.color() & 0x00FFFFFF);
            Vec3 labelPos = new Vec3(entry.x(), entry.y() + 3.00D, entry.z());
            renderLabel(entry.label(), labelPos, color, cameraForward, cameraHorizontal, cameraUp);
        }
    }

    private void renderBounds(MinigameSetupVisualPayload.Bounds entry, Frustum frustum,
                              Vec3 cameraForward, Vec3 cameraHorizontal, Vec3 cameraUp) {
        AABB box = new AABB(entry.minX() - BOUNDS_EPSILON, entry.minY() - BOUNDS_EPSILON,
                entry.minZ() - BOUNDS_EPSILON, entry.maxX() + 1.0D + BOUNDS_EPSILON,
                entry.maxY() + 1.0D + BOUNDS_EPSILON, entry.maxZ() + 1.0D + BOUNDS_EPSILON);
        if (!frustum.isVisible(box)) return;

        int rgb = entry.color() & 0x00FFFFFF;
        int strokeColor = 0xF0000000 | rgb;
        int fillAlpha = switch (entry.kind()) {
            case MinigameSetupVisualPayload.Bounds.SPECTATOR -> 0x18;
            case MinigameSetupVisualPayload.Bounds.SPLEEF_FLOOR -> 0x2A;
            default -> 0x00;
        };
        int fillColor = (fillAlpha << 24) | rgb;
        float width = entry.kind() == MinigameSetupVisualPayload.Bounds.GAME ? 0.075F : 0.060F;

        if (fillAlpha > 0) Gizmos.cuboid(box, GizmoStyle.fill(fillColor)).setAlwaysOnTop();
        Vec3 p000 = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 p001 = new Vec3(box.minX, box.minY, box.maxZ);
        Vec3 p010 = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 p011 = new Vec3(box.minX, box.maxY, box.maxZ);
        Vec3 p100 = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 p101 = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 p110 = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 p111 = new Vec3(box.maxX, box.maxY, box.maxZ);
        line(p000, p001, strokeColor, width); line(p000, p010, strokeColor, width);
        line(p000, p100, strokeColor, width); line(p001, p011, strokeColor, width);
        line(p001, p101, strokeColor, width); line(p010, p011, strokeColor, width);
        line(p010, p110, strokeColor, width); line(p100, p101, strokeColor, width);
        line(p100, p110, strokeColor, width); line(p011, p111, strokeColor, width);
        line(p101, p111, strokeColor, width); line(p110, p111, strokeColor, width);

        Vec3 labelCenter = new Vec3((box.minX + box.maxX) * 0.5D, box.maxY + 0.65D,
                (box.minZ + box.maxZ) * 0.5D);
        renderLabel(entry.label(), labelCenter, 0xFF000000 | rgb,
                cameraForward, cameraHorizontal, cameraUp);
    }

    private static void line(Vec3 start, Vec3 end, int color, float width) {
        Gizmos.line(start, end, color, width).setAlwaysOnTop();
    }

    private void renderLabel(String label, Vec3 textCenter, int color, Vec3 cameraForward,
                             Vec3 cameraHorizontal, Vec3 cameraUp) {
        double halfWidth = minecraft.font.width(label) * LABEL_HALF_WIDTH_PER_FONT_PIXEL
                + LABEL_HORIZONTAL_PADDING;
        Vec3 backgroundCenter = textCenter.add(cameraForward.scale(BACKGROUND_DEPTH_OFFSET));
        Vec3 horizontal = cameraHorizontal.scale(halfWidth);
        Vec3 vertical = cameraUp.scale(LABEL_HALF_HEIGHT);

        Gizmos.rect(
                backgroundCenter.subtract(horizontal).add(vertical),
                backgroundCenter.add(horizontal).add(vertical),
                backgroundCenter.add(horizontal).subtract(vertical),
                backgroundCenter.subtract(horizontal).subtract(vertical),
                GizmoStyle.fill(BACKGROUND_COLOR))
                .setAlwaysOnTop();
        Gizmos.billboardText(label, textCenter,
                TextGizmo.Style.forColorAndCentered(color).withScale(LABEL_SCALE))
                .setAlwaysOnTop();
    }
}
