package be.winnetrie.mod.simpleserverutilities.client.minigame;

import be.winnetrie.mod.simpleserverutilities.network.MinigameDominationVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

/** Renders large, centered Domination node names above the physical banner. */
public final class DominationRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final float LABEL_SCALE = 0.25F;
    private final Minecraft minecraft;

    public DominationRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.level == null) return;
        String dimension = minecraft.level.dimension().identifier().toString();
        for (MinigameDominationVisualPayload.Entry entry : DominationClientState.snapshot()) {
            if (!dimension.equals(entry.dimension())) continue;
            renderLabel(entry);
        }
    }

    private static void renderLabel(MinigameDominationVisualPayload.Entry entry) {
        // Standing banners are centered in their block. Keep the billboard exactly on that
        // centerline and high enough that the enlarged text never intersects the cloth.
        double centerX = Math.floor(entry.x()) + 0.5D;
        double centerZ = Math.floor(entry.z()) + 0.5D;
        Vec3 center = new Vec3(centerX, entry.y() + 2.85D, centerZ);
        int color = 0xFF000000 | (entry.topColor() & 0x00FFFFFF);
        Gizmos.billboardText(entry.label(), center,
                TextGizmo.Style.forColorAndCentered(color).withScale(LABEL_SCALE)).setAlwaysOnTop();
    }
}
