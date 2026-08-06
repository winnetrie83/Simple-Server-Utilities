package be.winnetrie.mod.simpleserverutilities.client.identity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Renders the selected global title as one full-colour line above the normal player nametag. */
public final class PlayerTitleRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    public PlayerTitleRenderer(Minecraft minecraft) { this.minecraft = minecraft; }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
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
            Gizmos.billboardText(entry.title(), center,
                    TextGizmo.Style.forColorAndCentered(0xFF000000 | entry.titleColor()).withScale(0.22F));
        }
    }
}
