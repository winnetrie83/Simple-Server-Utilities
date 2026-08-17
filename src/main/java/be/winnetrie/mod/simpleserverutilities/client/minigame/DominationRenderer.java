package be.winnetrie.mod.simpleserverutilities.client.minigame;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import be.winnetrie.mod.simpleserverutilities.client.render.SsuDebugGizmos;
import be.winnetrie.mod.simpleserverutilities.network.MinigameDominationVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;

/** Renders large, centered Domination node names above the physical banner. */
public final class DominationRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final float LABEL_SCALE = 0.25F;
    private static final double LABEL_HALF_WIDTH_PER_FONT_PIXEL = 0.00844D;
    private static final double LABEL_HORIZONTAL_PADDING = 0.09D;
    private static final double LABEL_HALF_HEIGHT = 0.19D;
    private static final double BACKGROUND_DEPTH_OFFSET = 0.008D;
    private static final int BACKGROUND_COLOR = 0xB0000000;
    private final Minecraft minecraft;

    public DominationRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       double camX, double camY, double camZ) {
        SsuDebugGizmos.begin(minecraft, poseStack, bufferSource, camX, camY, camZ);
        Frustum frustum = null; // 1.21.1 DebugRenderer does not pass its render frustum to child renderers.
        float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        if (minecraft.level == null) return;
        String dimension = minecraft.level.dimension().location().toString();
        var camera = minecraft.gameRenderer.getMainCamera();
        var forwardVector = camera.getLookVector();
        var leftVector = camera.getLeftVector();
        var upVector = camera.getUpVector();
        Vec3 cameraForward = new Vec3(forwardVector.x(), forwardVector.y(), forwardVector.z()).normalize();
        Vec3 cameraHorizontal = new Vec3(leftVector.x(), leftVector.y(), leftVector.z()).normalize();
        Vec3 cameraUp = new Vec3(upVector.x(), upVector.y(), upVector.z()).normalize();
        for (MinigameDominationVisualPayload.Entry entry : DominationClientState.snapshot()) {
            if (!dimension.equals(entry.dimension())) continue;
            renderLabel(entry, cameraForward, cameraHorizontal, cameraUp);
        }
    }

    private void renderLabel(MinigameDominationVisualPayload.Entry entry, Vec3 cameraForward,
                             Vec3 cameraHorizontal, Vec3 cameraUp) {
        // Standing banners are centered in their block. Keep the billboard exactly on that
        // centerline and high enough that the enlarged text never intersects the cloth.
        double centerX = Math.floor(entry.x()) + 0.5D;
        double centerZ = Math.floor(entry.z()) + 0.5D;
        Vec3 center = new Vec3(centerX, entry.y() + 2.85D, centerZ);
        int color = 0xFF000000 | (entry.topColor() & 0x00FFFFFF);
        double halfWidth = minecraft.font.width(entry.label()) * LABEL_HALF_WIDTH_PER_FONT_PIXEL
                + LABEL_HORIZONTAL_PADDING;
        Vec3 backgroundCenter = center.add(cameraForward.scale(BACKGROUND_DEPTH_OFFSET));
        Vec3 horizontal = cameraHorizontal.scale(halfWidth);
        Vec3 vertical = cameraUp.scale(LABEL_HALF_HEIGHT);

        SsuDebugGizmos.rect(
                backgroundCenter.subtract(horizontal).add(vertical),
                backgroundCenter.add(horizontal).add(vertical),
                backgroundCenter.add(horizontal).subtract(vertical),
                backgroundCenter.subtract(horizontal).subtract(vertical),
                SsuDebugGizmos.FillStyle.fill(BACKGROUND_COLOR))
                .setAlwaysOnTop();
        SsuDebugGizmos.billboardText(entry.label(), center,
                SsuDebugGizmos.TextStyle.forColorAndCentered(color).withScale(LABEL_SCALE)).setAlwaysOnTop();
    }
}
