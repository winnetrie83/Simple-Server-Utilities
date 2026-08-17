package be.winnetrie.mod.simpleserverutilities.client.identity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import be.winnetrie.mod.simpleserverutilities.client.render.SsuDebugGizmos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Renders the selected global title as one full-colour line above the normal player nametag. */
public final class PlayerTitleRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    public PlayerTitleRenderer(Minecraft minecraft) { this.minecraft = minecraft; }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       double camX, double camY, double camZ) {
        SsuDebugGizmos.begin(minecraft, poseStack, bufferSource, camX, camY, camZ);
        Frustum frustum = null; // 1.21.1 DebugRenderer does not pass its render frustum to child renderers.
        float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        if (minecraft.level == null) return;
        for (var entry : PlayerIdentityClientState.snapshot()) {
            if (!entry.showTitle() || entry.title().isBlank()) continue;
            Entity entity = minecraft.level.getEntity(entry.entityId());
            // Match vanilla nametag behaviour: never render the local player's own title,
            // including in third-person view.
            if (entity == null || entity.isInvisible()
                    || minecraft.player != null && entity.getId() == minecraft.player.getId()) continue;
            Vec3 interpolated = entity.getPosition(partialTicks);
            double dx = interpolated.x - camX, dy = interpolated.y - camY, dz = interpolated.z - camZ;
            if (dx * dx + dy * dy + dz * dz > 64.0D * 64.0D) continue;
            Vec3 center = interpolated.add(0.0D, entity.getBbHeight() + 0.82D, 0.0D);
            SsuDebugGizmos.billboardText(entry.title(), center,
                    SsuDebugGizmos.TextStyle.forColorAndCentered(0xFF000000 | entry.titleColor()).withScale(0.22F));
        }
    }
}
