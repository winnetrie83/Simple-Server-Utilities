package be.winnetrie.mod.simpleserverutilities.client.utilitymining;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import be.winnetrie.mod.simpleserverutilities.client.render.SsuDebugGizmos;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;

/** Renders the merged outer silhouette twice to create a readable glow-like holographic outline. */
public final class UtilityMiningOutlineRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final double EPSILON = 0.003D;

    private final Minecraft minecraft;
    private List<UtilityMiningOutlineMath.Line> cachedLines = List.of();
    private List<net.minecraft.core.BlockPos> cachedBlocks = List.of();

    public UtilityMiningOutlineRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       double camX, double camY, double camZ) {
        SsuDebugGizmos.begin(minecraft, poseStack, bufferSource, camX, camY, camZ);
        Frustum frustum = null; // 1.21.1 DebugRenderer does not pass its render frustum to child renderers.
        float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        if (minecraft.level == null) {
            return;
        }

        UtilityMiningClientState.Preview preview = UtilityMiningClientState.snapshot();
        if (!preview.isVisible()
                || !minecraft.level.dimension().location().toString().equals(preview.dimension())) {
            return;
        }

        if (!preview.blocks().equals(cachedBlocks)) {
            cachedBlocks = preview.blocks();
            cachedLines = UtilityMiningOutlineMath.outerOutline(cachedBlocks);
        }

        float strength = preview.brightness() / 100.0F;
        int glowColor = withAlpha(preview.color(), Math.round(35 + 100 * strength));
        int coreColor = withAlpha(preview.color(), Math.round(150 + 105 * strength));
        float glowWidth = 3.0F + 2.5F * strength;
        float coreWidth = 1.0F + 1.25F * strength;

        for (UtilityMiningOutlineMath.Line line : cachedLines) {
            Vec3 start = offset(line.start());
            Vec3 end = offset(line.end());
            SsuDebugGizmos.line(start, end, glowColor, glowWidth).setAlwaysOnTop();
            SsuDebugGizmos.line(start, end, coreColor, coreWidth).setAlwaysOnTop();
        }
    }

    private static Vec3 offset(Vec3 value) {
        return new Vec3(value.x + EPSILON, value.y + EPSILON, value.z + EPSILON);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
