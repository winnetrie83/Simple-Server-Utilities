package be.winnetrie.mod.simpleserverutilities.client.minigame;

import be.winnetrie.mod.simpleserverutilities.network.MinigameKothVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lightweight translucent half-dome showing the physical King of the Hill range. */
public final class KingOfTheHillVisualRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int SEGMENTS = 24;
    private static final int BANDS = 6;
    private final Minecraft minecraft;
    public KingOfTheHillVisualRenderer(Minecraft minecraft) { this.minecraft = minecraft; }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.level == null) return;
        MinigameKothVisualPayload data = KingOfTheHillVisualClientState.snapshot();
        if (data == null || !data.visible()) return;
        if (!minecraft.level.dimension().identifier().toString().equals(data.dimension())) return;
        double r = data.radius();
        AABB bounds = new AABB(data.x() - r, data.y(), data.z() - r, data.x() + r, data.y() + r, data.z() + r);
        if (!frustum.isVisible(bounds)) return;
        double dx = camX - data.x(), dy = camY - (data.y() + r * 0.4D), dz = camZ - data.z();
        if (dx * dx + dy * dy + dz * dz > 128.0D * 128.0D) return;

        int fill = 0x24000000 | data.rgb();
        int line = 0xA8000000 | data.rgb();
        Vec3 center = new Vec3(data.x(), data.y(), data.z());
        for (int band = 0; band < BANDS; band++) {
            double t0 = (Math.PI * 0.5D) * band / BANDS;
            double t1 = (Math.PI * 0.5D) * (band + 1) / BANDS;
            double h0 = r * Math.cos(t0), h1 = r * Math.cos(t1);
            double rr0 = r * Math.sin(t0), rr1 = r * Math.sin(t1);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = Math.PI * 2.0D * segment / SEGMENTS;
                double a1 = Math.PI * 2.0D * (segment + 1) / SEGMENTS;
                Vec3 p00 = center.add(rr0 * Math.cos(a0), h0, rr0 * Math.sin(a0));
                Vec3 p01 = center.add(rr0 * Math.cos(a1), h0, rr0 * Math.sin(a1));
                Vec3 p11 = center.add(rr1 * Math.cos(a1), h1, rr1 * Math.sin(a1));
                Vec3 p10 = center.add(rr1 * Math.cos(a0), h1, rr1 * Math.sin(a0));
                Gizmos.rect(p00, p01, p11, p10, GizmoStyle.fill(fill));
                if (band == BANDS - 1 || segment % 4 == 0) {
                    Gizmos.line(p10, p11, line, 1.25F);
                }
            }
        }
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double a0 = Math.PI * 2.0D * segment / SEGMENTS;
            double a1 = Math.PI * 2.0D * (segment + 1) / SEGMENTS;
            Vec3 p0 = center.add(r * Math.cos(a0), 0.03D, r * Math.sin(a0));
            Vec3 p1 = center.add(r * Math.cos(a1), 0.03D, r * Math.sin(a1));
            Gizmos.line(p0, p1, line, 1.8F);
        }
        if (!data.label().isBlank()) {
            Gizmos.billboardText(data.label(), center.add(0, r + 0.65D, 0),
                    TextGizmo.Style.forColorAndCentered(0xFF000000 | data.rgb()).withScale(0.22F));
        }
    }
}
