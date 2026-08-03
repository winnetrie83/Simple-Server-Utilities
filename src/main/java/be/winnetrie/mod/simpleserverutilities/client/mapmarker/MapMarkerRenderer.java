package be.winnetrie.mod.simpleserverutilities.client.mapmarker;

import be.winnetrie.mod.simpleserverutilities.mixin.LevelRendererAccessor;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

/** In-world camera-facing marker icons and distance-limited beacon beams. */
public final class MapMarkerRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int CIRCLE_SEGMENTS = 32;
    private static final double CIRCLE_RADIUS = 0.145D;
    private static final double CIRCLE_FILL_RADIUS = 0.1325D;
    private static final double CIRCLE_FRONT_OFFSET = 0.004D;
    private static final double MAX_ICON_DISTANCE = 512.0D;
    private static final double ICON_REFERENCE_DISTANCE = 16.0D;
    private static final double MAX_VISUAL_SCALE = MAX_ICON_DISTANCE / ICON_REFERENCE_DISTANCE;
    private static final double LOOK_ANGLE_RADIANS = Math.toRadians(2.25D);
    private static final double LOOK_ANGLE_SINE_SQUARED = square(Math.sin(LOOK_ANGLE_RADIANS));
    private static final float LABEL_SCALE = 0.72F;
    private static final double LABEL_BACKGROUND_TOP = 0.30D;
    private static final double LABEL_BACKGROUND_BOTTOM = 0.48D;
    private static final double LABEL_HALF_WIDTH_PER_FONT_PIXEL = 0.02430D;
    private static final double LABEL_HORIZONTAL_PADDING = 0.10D;

    private final Minecraft minecraft;

    public MapMarkerRenderer(Minecraft minecraft) {
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
        if (minecraft.level == null || minecraft.player == null) return;
        String dimension = minecraft.level.dimension().identifier().toString();
        double beamDistanceSquared = square(MapMarkerClientState.beamDistance());
        double iconDistanceSquared = square(MAX_ICON_DISTANCE);
        var camera = minecraft.gameRenderer.mainCamera();
        var forwardVector = camera.forwardVector();
        var leftVector = camera.leftVector();
        var upVector = camera.upVector();
        Vec3 cameraForward = new Vec3(forwardVector.x(), forwardVector.y(), forwardVector.z()).normalize();
        Vec3 cameraHorizontal = new Vec3(leftVector.x(), leftVector.y(), leftVector.z()).normalize();
        Vec3 cameraUp = new Vec3(upVector.x(), upVector.y(), upVector.z()).normalize();
        MapMarkerSyncPayload.Entry lookedAt = null;
        double lookedAtDistance = 0.0D;
        double bestAngularError = Double.POSITIVE_INFINITY;

        for (MapMarkerSyncPayload.Entry marker : MapMarkerClientState.markers()) {
            if (!dimension.equals(marker.dimension())) continue;
            double centerX = marker.x() + 0.5D;
            double centerY = marker.y() + 0.5D;
            double centerZ = marker.z() + 0.5D;
            double dx = centerX - camX;
            double dy = centerY - camY;
            double dz = centerZ - camZ;
            double horizontalDistanceSquared = dx * dx + dz * dz;

            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (MapMarkerClientState.showInWorld() && distanceSquared <= iconDistanceSquared) {
                double distance = Math.sqrt(distanceSquared);
                double visualScale = visualScale(distance);
                drawBillboardCircle(
                        centerX, centerY, centerZ,
                        camX, camY, camZ,
                        marker.colorArgb(), visualScale);

                double projected = dx * cameraForward.x + dy * cameraForward.y + dz * cameraForward.z;
                if (projected > 0.0D && distanceSquared > 1.0E-6D) {
                    double perpendicularSquared = Math.max(0.0D, distanceSquared - projected * projected);
                    double angularError = perpendicularSquared / distanceSquared;
                    if (angularError <= LOOK_ANGLE_SINE_SQUARED && angularError < bestAngularError) {
                        bestAngularError = angularError;
                        lookedAt = marker;
                        lookedAtDistance = distance;
                    }
                }
            }
            if (MapMarkerClientState.showBeams() && horizontalDistanceSquared <= beamDistanceSquared) {
                drawBeam(marker, camX, camY, camZ, partialTicks, marker.colorArgb());
            }
        }

        if (lookedAt != null) {
            drawLookLabel(lookedAt, lookedAtDistance, cameraForward, cameraHorizontal, cameraUp);
        }
    }

    private void drawBillboardCircle(
            double x,
            double y,
            double z,
            double camX,
            double camY,
            double camZ,
            int color,
            double visualScale
    ) {
        Vec3 center = new Vec3(x, y, z);
        Vec3 towardCamera = new Vec3(camX - x, camY - y, camZ - z);
        if (towardCamera.lengthSqr() < 1.0E-6D) {
            towardCamera = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            towardCamera = towardCamera.normalize();
        }

        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(towardCamera);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = towardCamera.cross(right).normalize();

        drawFilledBillboardDisk(center, right, up, CIRCLE_RADIUS * visualScale, 0xDD101010);
        drawFilledBillboardDisk(
                center.add(towardCamera.scale(CIRCLE_FRONT_OFFSET * visualScale)),
                right,
                up,
                CIRCLE_FILL_RADIUS * visualScale,
                0xF0000000 | (color & 0x00FFFFFF));
    }

    private static void drawFilledBillboardDisk(
            Vec3 center,
            Vec3 right,
            Vec3 up,
            double radius,
            int color
    ) {
        for (int index = 0; index < CIRCLE_SEGMENTS; index++) {
            double lowerY = -radius + (radius * 2.0D * index / CIRCLE_SEGMENTS);
            double upperY = -radius + (radius * 2.0D * (index + 1) / CIRCLE_SEGMENTS);
            double lowerHalfWidth = Math.sqrt(Math.max(0.0D, radius * radius - lowerY * lowerY));
            double upperHalfWidth = Math.sqrt(Math.max(0.0D, radius * radius - upperY * upperY));

            Vec3 upperLeft = center.add(up.scale(upperY)).subtract(right.scale(upperHalfWidth));
            Vec3 upperRight = center.add(up.scale(upperY)).add(right.scale(upperHalfWidth));
            Vec3 lowerRight = center.add(up.scale(lowerY)).add(right.scale(lowerHalfWidth));
            Vec3 lowerLeft = center.add(up.scale(lowerY)).subtract(right.scale(lowerHalfWidth));
            Gizmos.rect(upperLeft, upperRight, lowerRight, lowerLeft, GizmoStyle.fill(color))
                    .setAlwaysOnTop();
        }
    }

    private void drawLookLabel(
            MapMarkerSyncPayload.Entry marker,
            double distance,
            Vec3 cameraForward,
            Vec3 cameraHorizontal,
            Vec3 cameraUp
    ) {
        String label = marker.name() + "  •  " + formatDistance(distance);
        double visualScale = visualScale(distance);
        Vec3 markerCenter = new Vec3(
                marker.x() + 0.5D,
                marker.y() + 0.5D,
                marker.z() + 0.5D);
        Vec3 textCenter = markerCenter.add(
                cameraUp.scale((CIRCLE_RADIUS + 0.42D) * visualScale));
        double halfWidth = (minecraft.font.width(label) * LABEL_HALF_WIDTH_PER_FONT_PIXEL
                + LABEL_HORIZONTAL_PADDING) * visualScale;
        Vec3 backgroundCenter = textCenter.add(cameraForward.scale(0.012D * visualScale));
        Vec3 horizontal = cameraHorizontal.scale(halfWidth);
        Vec3 top = cameraUp.scale(LABEL_BACKGROUND_TOP * visualScale);
        Vec3 bottom = cameraUp.scale(LABEL_BACKGROUND_BOTTOM * visualScale);
        Gizmos.rect(
                backgroundCenter.subtract(horizontal).add(top),
                backgroundCenter.add(horizontal).add(top),
                backgroundCenter.add(horizontal).subtract(bottom),
                backgroundCenter.subtract(horizontal).subtract(bottom),
                GizmoStyle.fill(0xC010141A))
                .setAlwaysOnTop();
        Gizmos.billboardText(label, textCenter,
                TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale((float) (LABEL_SCALE * visualScale)))
                .setAlwaysOnTop();
    }

    private static String formatDistance(double distance) {
        if (distance < 10.0D) {
            return String.format(java.util.Locale.ROOT, "%.1f m", distance);
        }
        return Math.round(distance) + " m";
    }

    private void drawBeam(
            MapMarkerSyncPayload.Entry marker,
            double camX,
            double camY,
            double camZ,
            float partialTicks,
            int color
    ) {
        if (minecraft.level == null || minecraft.levelRenderer == null) return;

        int minimumY = minecraft.level.getMinY();
        int maximumYExclusive = minecraft.level.getMaxY() + 1;
        int height = maximumYExclusive - minimumY;
        if (height <= 0) return;

        PoseStack poseStack = new PoseStack();
        poseStack.translate(
                marker.x() - camX,
                minimumY - camY,
                marker.z() - camZ);

        float animationTime = (float) (minecraft.level.getGameTime() + partialTicks);
        int opaqueColor = 0xFF000000 | (color & 0x00FFFFFF);
        BeaconRenderer.submitBeaconBeam(
                poseStack,
                ((LevelRendererAccessor) minecraft.levelRenderer).ssu$getSubmitNodeStorage(),
                BeaconRenderer.BEAM_LOCATION,
                1.0F,
                animationTime,
                0,
                height,
                opaqueColor,
                BeaconRenderer.SOLID_BEAM_RADIUS,
                BeaconRenderer.BEAM_GLOW_RADIUS);
    }

    private static double visualScale(double distance) {
        if (!Double.isFinite(distance)) return 1.0D;
        return Math.max(1.0D, Math.min(MAX_VISUAL_SCALE, distance / ICON_REFERENCE_DISTANCE));
    }

    private static double square(double value) {
        return value * value;
    }
}
