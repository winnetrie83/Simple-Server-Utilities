package be.winnetrie.mod.simpleserverutilities.client.minigame;

import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

/** Labels physical setup banners while the administrator holds the Setup Tool. */
public final class MinigameSetupVisualRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final String TOOL_NAME = "SSU Minigame Setup Tool";
    private static final float LABEL_SCALE = 0.11F;
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
        for (MinigameSetupVisualPayload.Entry entry : MinigameSetupVisualClientState.snapshot()) {
            if (!dimension.equals(entry.dimension())) continue;
            int color = 0xFF000000 | (entry.color() & 0x00FFFFFF);
            Vec3 labelPos = new Vec3(entry.x(), entry.y() + 2.65D, entry.z());
            Gizmos.billboardText(entry.label(), labelPos,
                    TextGizmo.Style.forColorAndCentered(color).withScale(LABEL_SCALE)).setAlwaysOnTop();
        }
    }
}
