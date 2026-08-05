package be.winnetrie.mod.simpleserverutilities.client.minigame;

import be.winnetrie.mod.simpleserverutilities.mixin.LevelRendererAccessor;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCtfVisualPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renders the carried CTF flag on the player's back and a matching upward beam. */
public final class CaptureTheFlagRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;

    public CaptureTheFlagRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.level == null) return;
        for (MinigameCtfVisualPayload.Entry entry : CaptureTheFlagClientState.snapshot()) {
            Entity carrier = minecraft.level.getEntity(entry.entityId());
            if (carrier == null || carrier.isRemoved()) continue;
            renderBackFlag(carrier, entry.color(), frustum);
            renderBeam(carrier, entry.color(), camX, camY, camZ, partialTicks);
        }
    }

    private static void renderBackFlag(Entity carrier, int rgb, Frustum frustum) {
        double yaw = Math.toRadians(carrier.getYRot());
        double backX = Math.sin(yaw) * 0.34D;
        double backZ = -Math.cos(yaw) * 0.34D;
        double x = carrier.getX() + backX;
        double y = carrier.getY() + 0.45D;
        double z = carrier.getZ() + backZ;
        int opaque = 0xFF000000 | (rgb & 0x00FFFFFF);
        int translucent = 0xD0000000 | (rgb & 0x00FFFFFF);

        Vec3 poleBottom = new Vec3(x, y, z);
        Vec3 poleTop = new Vec3(x, y + 1.8D, z);
        Gizmos.line(poleBottom, poleTop, 0xFF5C4632, 0.035F).setAlwaysOnTop();

        boolean northSouth = Math.abs(Math.cos(yaw)) >= Math.abs(Math.sin(yaw));
        AABB cloth = northSouth
                ? new AABB(x - 0.38D, y + 0.92D, z - 0.035D, x + 0.38D, y + 1.62D, z + 0.035D)
                : new AABB(x - 0.035D, y + 0.92D, z - 0.38D, x + 0.035D, y + 1.62D, z + 0.38D);
        if (frustum.isVisible(cloth)) {
            Gizmos.cuboid(cloth, GizmoStyle.fill(translucent));
            Vec3 lowA = northSouth ? new Vec3(cloth.minX, cloth.minY, z) : new Vec3(x, cloth.minY, cloth.minZ);
            Vec3 lowB = northSouth ? new Vec3(cloth.maxX, cloth.minY, z) : new Vec3(x, cloth.minY, cloth.maxZ);
            Vec3 highA = northSouth ? new Vec3(cloth.minX, cloth.maxY, z) : new Vec3(x, cloth.maxY, cloth.minZ);
            Vec3 highB = northSouth ? new Vec3(cloth.maxX, cloth.maxY, z) : new Vec3(x, cloth.maxY, cloth.maxZ);
            Gizmos.line(lowA, lowB, opaque, 0.025F).setAlwaysOnTop();
            Gizmos.line(highA, highB, opaque, 0.025F).setAlwaysOnTop();
            Gizmos.line(lowA, highA, opaque, 0.025F).setAlwaysOnTop();
            Gizmos.line(lowB, highB, opaque, 0.025F).setAlwaysOnTop();
        }
    }

    private void renderBeam(Entity carrier, int rgb, double camX, double camY, double camZ, float partialTicks) {
        if (minecraft.level == null || minecraft.levelRenderer == null) return;
        int startY = (int) Math.floor(carrier.getY() + 2.0D);
        int height = Math.max(8, minecraft.level.getMaxY() + 1 - startY);
        PoseStack poseStack = new PoseStack();
        poseStack.translate(carrier.getX() - camX, startY - camY, carrier.getZ() - camZ);
        float animationTime = (float) (minecraft.level.getGameTime() + partialTicks);
        int opaque = 0xFF000000 | (rgb & 0x00FFFFFF);
        BeaconRenderer.submitBeaconBeam(
                poseStack,
                ((LevelRendererAccessor) minecraft.levelRenderer).ssu$getSubmitNodeStorage(),
                BeaconRenderer.BEAM_LOCATION,
                1.0F,
                animationTime,
                0,
                height,
                opaque,
                BeaconRenderer.SOLID_BEAM_RADIUS,
                BeaconRenderer.BEAM_GLOW_RADIUS);
    }
}
