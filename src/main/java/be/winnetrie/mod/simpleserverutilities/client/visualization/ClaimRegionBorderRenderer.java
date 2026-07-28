package be.winnetrie.mod.simpleserverutilities.client.visualization;

import be.winnetrie.mod.simpleserverutilities.client.visualization.BorderVisualizationClientState.LayerState;
import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ClaimRegionBorderRenderer implements DebugRenderer.SimpleDebugRenderer {

    private static final double REGION_RENDER_DISTANCE_SQUARED = 512.0 * 512.0;
    private static final double CLAIM_RENDER_DISTANCE_SQUARED = 192.0 * 192.0;
    private static final double EPSILON = 0.002;
    private static final double CLAIM_RIBBON_HALF_THICKNESS = 0.055;
    private static final double CLAIM_RIBBON_LOW_OFFSET = -8.75;
    private static final double CLAIM_RIBBON_HIGH_OFFSET = 1.75;

    private final Minecraft minecraft;

    public ClaimRegionBorderRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(
            double camX,
            double camY,
            double camZ,
            DebugValueAccess debugValues,
            Frustum frustum,
            float partialTicks
    ) {
        if (minecraft.level == null) {
            return;
        }

        String dimension = minecraft.level.dimension().identifier().toString();

        for (LayerState layer : BorderVisualizationClientState.snapshot()) {
            if (!dimension.equals(layer.dimension())) {
                continue;
            }

            boolean claimLayer = layer.layer() == BorderLayer.CLAIM
                    || layer.layer() == BorderLayer.CLAIM_FOCUS;

            for (BorderVisualizationPayload.Entry entry : layer.entries()) {
                if (claimLayer) {
                    renderClaimRibbon(entry, camX, camY, camZ, frustum);
                } else {
                    renderRegionBoxes(entry, camX, camY, camZ, frustum);
                    renderStaticEdges(entry, camX, camY, camZ);
                }
            }
        }
    }

    private static void renderClaimRibbon(
            BorderVisualizationPayload.Entry entry,
            double camX,
            double camY,
            double camZ,
            Frustum frustum
    ) {
        double lowY = Math.floor(camY) + CLAIM_RIBBON_LOW_OFFSET;
        double highY = Math.floor(camY) + CLAIM_RIBBON_HIGH_OFFSET;

        for (BorderVisualizationPayload.Edge edge : entry.edges()) {
            if (distanceSquaredToSegment2D(
                    camX,
                    camZ,
                    edge.x1(),
                    edge.z1(),
                    edge.x2(),
                    edge.z2()
            ) > CLAIM_RENDER_DISTANCE_SQUARED) {
                continue;
            }

            double minX = Math.min(edge.x1(), edge.x2());
            double maxX = Math.max(edge.x1(), edge.x2());
            double minZ = Math.min(edge.z1(), edge.z2());
            double maxZ = Math.max(edge.z1(), edge.z2());

            if (edge.x1() == edge.x2()) {
                minX -= CLAIM_RIBBON_HALF_THICKNESS;
                maxX += CLAIM_RIBBON_HALF_THICKNESS;
            } else {
                minZ -= CLAIM_RIBBON_HALF_THICKNESS;
                maxZ += CLAIM_RIBBON_HALF_THICKNESS;
            }

            AABB ribbon = new AABB(minX, lowY, minZ, maxX, highY, maxZ);
            if (!frustum.isVisible(ribbon)) {
                continue;
            }

            if (entry.fillColor() != 0) {
                Gizmos.cuboid(ribbon, GizmoStyle.fill(entry.fillColor()));
            }

            Vec3 lowStart = new Vec3(edge.x1(), lowY, edge.z1());
            Vec3 lowEnd = new Vec3(edge.x2(), lowY, edge.z2());
            Vec3 highStart = new Vec3(edge.x1(), highY, edge.z1());
            Vec3 highEnd = new Vec3(edge.x2(), highY, edge.z2());

            line(lowStart, lowEnd, entry, false);
            line(highStart, highEnd, entry, false);
            line(lowStart, highStart, entry, false);
            line(lowEnd, highEnd, entry, false);
        }
    }

    private static void renderRegionBoxes(
            BorderVisualizationPayload.Entry entry,
            double camX,
            double camY,
            double camZ,
            Frustum frustum
    ) {
        for (BorderVisualizationPayload.Box box : entry.boxes()) {
            AABB bounds = new AABB(
                    box.minX() - EPSILON,
                    box.minY() - EPSILON,
                    box.minZ() - EPSILON,
                    box.maxX() + 1.0 + EPSILON,
                    box.maxY() + 1.0 + EPSILON,
                    box.maxZ() + 1.0 + EPSILON
            );

            if (distanceSquaredToAabb(bounds, camX, camY, camZ) > REGION_RENDER_DISTANCE_SQUARED
                    || !frustum.isVisible(bounds)) {
                continue;
            }

            if (entry.fillColor() != 0) {
                Gizmos.cuboid(bounds, GizmoStyle.fill(entry.fillColor()));
            }

            if (entry.strokeBoxes()) {
                Gizmos.cuboid(bounds, GizmoStyle.stroke(entry.strokeColor(), entry.strokeWidth()))
                        .setAlwaysOnTop();
            }
        }
    }

    private static void renderStaticEdges(
            BorderVisualizationPayload.Entry entry,
            double camX,
            double camY,
            double camZ
    ) {
        if (entry.edges().isEmpty()) {
            return;
        }

        double lowY = Math.floor(camY) - 1.0 + EPSILON;
        double highY = lowY + 4.0;

        for (BorderVisualizationPayload.Edge edge : entry.edges()) {
            if (distanceSquaredToSegment2D(
                    camX,
                    camZ,
                    edge.x1(),
                    edge.z1(),
                    edge.x2(),
                    edge.z2()
            ) > REGION_RENDER_DISTANCE_SQUARED) {
                continue;
            }

            Vec3 lowStart = new Vec3(edge.x1(), lowY, edge.z1());
            Vec3 lowEnd = new Vec3(edge.x2(), lowY, edge.z2());
            Vec3 highStart = new Vec3(edge.x1(), highY, edge.z1());
            Vec3 highEnd = new Vec3(edge.x2(), highY, edge.z2());

            line(lowStart, lowEnd, entry, true);
            line(highStart, highEnd, entry, true);
            line(lowStart, highStart, entry, true);
            line(lowEnd, highEnd, entry, true);
        }
    }

    private static void line(
            Vec3 start,
            Vec3 end,
            BorderVisualizationPayload.Entry entry,
            boolean alwaysOnTop
    ) {
        var gizmo = Gizmos.line(start, end, entry.strokeColor(), entry.strokeWidth());
        if (alwaysOnTop) {
            gizmo.setAlwaysOnTop();
        }
    }

    private static double distanceSquaredToAabb(AABB bounds, double x, double y, double z) {
        double dx = axisDistance(x, bounds.minX, bounds.maxX);
        double dy = axisDistance(y, bounds.minY, bounds.maxY);
        double dz = axisDistance(z, bounds.minZ, bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0;
    }

    private static double distanceSquaredToSegment2D(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        double segmentX = endX - startX;
        double segmentZ = endZ - startZ;
        double segmentLengthSquared = segmentX * segmentX + segmentZ * segmentZ;

        if (segmentLengthSquared == 0.0) {
            double dx = pointX - startX;
            double dz = pointZ - startZ;
            return dx * dx + dz * dz;
        }

        double t = ((pointX - startX) * segmentX + (pointZ - startZ) * segmentZ)
                / segmentLengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));

        double closestX = startX + t * segmentX;
        double closestZ = startZ + t * segmentZ;
        double dx = pointX - closestX;
        double dz = pointZ - closestZ;
        return dx * dx + dz * dz;
    }
}
