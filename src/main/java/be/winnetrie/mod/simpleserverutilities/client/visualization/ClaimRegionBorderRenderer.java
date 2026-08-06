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

            double renderDistance = layer.renderDistance();
            double renderDistanceSquared = renderDistance * renderDistance;
            for (BorderVisualizationPayload.Entry entry : layer.entries()) {
                if (claimLayer) {
                    renderClaimRibbon(entry, camX, camY, camZ, frustum, renderDistance);
                } else {
                    renderRegionStyleEntry(entry, camX, camY, camZ, frustum, renderDistance);
                }
            }
        }
    }

    private static void renderClaimRibbon(
            BorderVisualizationPayload.Entry entry,
            double camX,
            double camY,
            double camZ,
            Frustum frustum,
            double renderDistance
    ) {
        double lowY = Math.floor(camY) + CLAIM_RIBBON_LOW_OFFSET;
        double highY = Math.floor(camY) + CLAIM_RIBBON_HIGH_OFFSET;

        for (BorderVisualizationPayload.Edge edge : entry.edges()) {
            HorizontalSegment visible = clipHorizontalSegment(
                    edge.x1(), edge.z1(), edge.x2(), edge.z2(), camX, camZ, renderDistance
            );
            if (visible == null) continue;

            double minX = Math.min(visible.x1(), visible.x2());
            double maxX = Math.max(visible.x1(), visible.x2());
            double minZ = Math.min(visible.z1(), visible.z2());
            double maxZ = Math.max(visible.z1(), visible.z2());

            if (visible.x1() == visible.x2()) {
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

            Vec3 lowStart = new Vec3(visible.x1(), lowY, visible.z1());
            Vec3 lowEnd = new Vec3(visible.x2(), lowY, visible.z2());
            Vec3 highStart = new Vec3(visible.x1(), highY, visible.z1());
            Vec3 highEnd = new Vec3(visible.x2(), highY, visible.z2());

            line(lowStart, lowEnd, entry, false);
            line(highStart, highEnd, entry, false);
            line(lowStart, highStart, entry, false);
            line(lowEnd, highEnd, entry, false);
        }
    }

    /** Renders one box/edge entry with the exact depth, clipping and fill rules used by region borders. */
    public static void renderRegionStyleEntry(
            BorderVisualizationPayload.Entry entry,
            double camX,
            double camY,
            double camZ,
            Frustum frustum,
            double renderDistance
    ) {
        double safeDistance = Math.max(8.0D, renderDistance);
        renderRegionBoxes(entry, camX, camY, camZ, frustum, safeDistance, safeDistance * safeDistance);
        renderStaticEdges(entry, camX, camY, camZ, safeDistance);
    }

    private static void renderRegionBoxes(
            BorderVisualizationPayload.Entry entry,
            double camX,
            double camY,
            double camZ,
            Frustum frustum,
            double renderDistance,
            double renderDistanceSquared
    ) {
        Vec3 camera = new Vec3(camX, camY, camZ);
        for (BorderVisualizationPayload.Box box : entry.boxes()) {
            AABB bounds = new AABB(
                    box.minX() - EPSILON,
                    box.minY() - EPSILON,
                    box.minZ() - EPSILON,
                    box.maxX() + 1.0 + EPSILON,
                    box.maxY() + 1.0 + EPSILON,
                    box.maxZ() + 1.0 + EPSILON
            );

            if (distanceSquaredToAabb(bounds, camX, camY, camZ) > renderDistanceSquared
                    || !frustum.isVisible(bounds)) {
                continue;
            }

            // A whole translucent cuboid can reveal faces hundreds of blocks away when the
            // player stands inside a large region. Keep the fill only when the complete box
            // fits inside the configured radius; individual border edges are clipped below.
            if (entry.fillColor() != 0
                    && farthestDistanceSquaredToAabb(bounds, camX, camY, camZ) <= renderDistanceSquared) {
                Gizmos.cuboid(bounds, GizmoStyle.fill(entry.fillColor()));
            }

            if (entry.strokeBoxes()) {
                renderClippedBoxEdges(bounds, camera, renderDistance, entry, frustum);
            }
        }
    }

    private static void renderStaticEdges(
            BorderVisualizationPayload.Entry entry,
            double camX,
            double camY,
            double camZ,
            double renderDistance
    ) {
        if (entry.edges().isEmpty()) {
            return;
        }

        double lowY = Math.floor(camY) - 1.0 + EPSILON;
        double highY = lowY + 4.0;

        for (BorderVisualizationPayload.Edge edge : entry.edges()) {
            HorizontalSegment visible = clipHorizontalSegment(
                    edge.x1(), edge.z1(), edge.x2(), edge.z2(), camX, camZ, renderDistance
            );
            if (visible == null) continue;

            Vec3 lowStart = new Vec3(visible.x1(), lowY, visible.z1());
            Vec3 lowEnd = new Vec3(visible.x2(), lowY, visible.z2());
            Vec3 highStart = new Vec3(visible.x1(), highY, visible.z1());
            Vec3 highEnd = new Vec3(visible.x2(), highY, visible.z2());

            line(lowStart, lowEnd, entry, true);
            line(highStart, highEnd, entry, true);
            line(lowStart, highStart, entry, true);
            line(lowEnd, highEnd, entry, true);
        }
    }

    private static void renderClippedBoxEdges(
            AABB bounds,
            Vec3 camera,
            double renderDistance,
            BorderVisualizationPayload.Entry entry,
            Frustum frustum
    ) {
        Vec3 p000 = new Vec3(bounds.minX, bounds.minY, bounds.minZ);
        Vec3 p001 = new Vec3(bounds.minX, bounds.minY, bounds.maxZ);
        Vec3 p010 = new Vec3(bounds.minX, bounds.maxY, bounds.minZ);
        Vec3 p011 = new Vec3(bounds.minX, bounds.maxY, bounds.maxZ);
        Vec3 p100 = new Vec3(bounds.maxX, bounds.minY, bounds.minZ);
        Vec3 p101 = new Vec3(bounds.maxX, bounds.minY, bounds.maxZ);
        Vec3 p110 = new Vec3(bounds.maxX, bounds.maxY, bounds.minZ);
        Vec3 p111 = new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ);

        renderClippedLine(p000, p001, camera, renderDistance, entry, frustum);
        renderClippedLine(p000, p010, camera, renderDistance, entry, frustum);
        renderClippedLine(p000, p100, camera, renderDistance, entry, frustum);
        renderClippedLine(p001, p011, camera, renderDistance, entry, frustum);
        renderClippedLine(p001, p101, camera, renderDistance, entry, frustum);
        renderClippedLine(p010, p011, camera, renderDistance, entry, frustum);
        renderClippedLine(p010, p110, camera, renderDistance, entry, frustum);
        renderClippedLine(p100, p101, camera, renderDistance, entry, frustum);
        renderClippedLine(p100, p110, camera, renderDistance, entry, frustum);
        renderClippedLine(p011, p111, camera, renderDistance, entry, frustum);
        renderClippedLine(p101, p111, camera, renderDistance, entry, frustum);
        renderClippedLine(p110, p111, camera, renderDistance, entry, frustum);
    }

    private static void renderClippedLine(
            Vec3 start,
            Vec3 end,
            Vec3 camera,
            double radius,
            BorderVisualizationPayload.Entry entry,
            Frustum frustum
    ) {
        LineSegment visible = clipSegmentToSphere(start, end, camera, radius);
        if (visible == null || !frustum.isVisible(segmentBounds(visible.start(), visible.end()))) return;
        line(visible.start(), visible.end(), entry, true);
    }

    private static AABB segmentBounds(Vec3 start, Vec3 end) {
        double padding = 0.05;
        return new AABB(
                Math.min(start.x, end.x) - padding,
                Math.min(start.y, end.y) - padding,
                Math.min(start.z, end.z) - padding,
                Math.max(start.x, end.x) + padding,
                Math.max(start.y, end.y) + padding,
                Math.max(start.z, end.z) + padding
        );
    }

    private static LineSegment clipSegmentToSphere(Vec3 start, Vec3 end, Vec3 center, double radius) {
        Vec3 direction = end.subtract(start);
        Vec3 offset = start.subtract(center);
        double a = direction.dot(direction);
        if (a <= 1.0E-12) return offset.lengthSqr() <= radius * radius ? new LineSegment(start, end) : null;
        double b = 2.0 * offset.dot(direction);
        double c = offset.dot(offset) - radius * radius;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) return c <= 0.0 ? new LineSegment(start, end) : null;
        double root = Math.sqrt(discriminant);
        double t1 = (-b - root) / (2.0 * a);
        double t2 = (-b + root) / (2.0 * a);
        double from = Math.max(0.0, Math.min(t1, t2));
        double to = Math.min(1.0, Math.max(t1, t2));
        if (c <= 0.0) from = 0.0;
        if (end.subtract(center).lengthSqr() <= radius * radius) to = 1.0;
        if (from > to || to < 0.0 || from > 1.0) return null;
        return new LineSegment(start.add(direction.scale(from)), start.add(direction.scale(to)));
    }

    private static HorizontalSegment clipHorizontalSegment(
            double startX,
            double startZ,
            double endX,
            double endZ,
            double centerX,
            double centerZ,
            double radius
    ) {
        double directionX = endX - startX;
        double directionZ = endZ - startZ;
        double offsetX = startX - centerX;
        double offsetZ = startZ - centerZ;
        double a = directionX * directionX + directionZ * directionZ;
        double radiusSquared = radius * radius;
        if (a <= 1.0E-12) {
            return offsetX * offsetX + offsetZ * offsetZ <= radiusSquared
                    ? new HorizontalSegment(startX, startZ, endX, endZ)
                    : null;
        }
        double b = 2.0 * (offsetX * directionX + offsetZ * directionZ);
        double c = offsetX * offsetX + offsetZ * offsetZ - radiusSquared;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) return c <= 0.0 ? new HorizontalSegment(startX, startZ, endX, endZ) : null;
        double root = Math.sqrt(discriminant);
        double t1 = (-b - root) / (2.0 * a);
        double t2 = (-b + root) / (2.0 * a);
        double from = Math.max(0.0, Math.min(t1, t2));
        double to = Math.min(1.0, Math.max(t1, t2));
        if (c <= 0.0) from = 0.0;
        double endOffsetX = endX - centerX;
        double endOffsetZ = endZ - centerZ;
        if (endOffsetX * endOffsetX + endOffsetZ * endOffsetZ <= radiusSquared) to = 1.0;
        if (from > to || to < 0.0 || from > 1.0) return null;
        return new HorizontalSegment(
                startX + directionX * from,
                startZ + directionZ * from,
                startX + directionX * to,
                startZ + directionZ * to
        );
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

    private static double farthestDistanceSquaredToAabb(AABB bounds, double x, double y, double z) {
        double dx = Math.max(Math.abs(x - bounds.minX), Math.abs(x - bounds.maxX));
        double dy = Math.max(Math.abs(y - bounds.minY), Math.abs(y - bounds.maxY));
        double dz = Math.max(Math.abs(z - bounds.minZ), Math.abs(z - bounds.maxZ));
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

    private record HorizontalSegment(double x1, double z1, double x2, double z2) {
    }

    private record LineSegment(Vec3 start, Vec3 end) {
    }
}
