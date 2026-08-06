package be.winnetrie.mod.simpleserverutilities.client.region;

import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Translucent, non-destructive world preview shown before a selection snapshot is placed. */
public final class RegionSnapshotPreviewRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;

    public RegionSnapshotPreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.level == null) return;
        RegionSnapshotPreviewPayload payload = RegionSnapshotPreviewClientState.snapshot();
        if (!payload.active() || !minecraft.level.dimension().identifier().toString().equals(payload.dimension())) return;
        BlockPos origin = BlockPos.of(payload.origin());
        int sy = Math.max(1, payload.sizeY()), sz = Math.max(1, payload.sizeZ());
        for (RegionSnapshotPreviewPayload.PreviewBlock block : payload.blocks()) {
            int x = block.relativeIndex() / (sy * sz);
            int remainder = block.relativeIndex() % (sy * sz);
            int y = remainder / sz;
            int z = remainder % sz;
            AABB box = new AABB(origin.getX() + x + 0.04, origin.getY() + y + 0.04, origin.getZ() + z + 0.04,
                    origin.getX() + x + 0.96, origin.getY() + y + 0.96, origin.getZ() + z + 0.96);
            if (frustum.isVisible(box)) Gizmos.cuboid(box, GizmoStyle.fill(block.color())).setAlwaysOnTop();
        }
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + payload.sizeX(), origin.getY() + payload.sizeY(), origin.getZ() + payload.sizeZ());
        Gizmos.cuboid(bounds, GizmoStyle.fill(0x1600E5FF)).setAlwaysOnTop();
        String label = "Preview: " + payload.snapshotName() + " · " + payload.sizeX() + "×" + payload.sizeY() + "×" + payload.sizeZ()
                + (payload.sampled() ? " · sampled" : "");
        Vec3 center = new Vec3((bounds.minX + bounds.maxX) * 0.5, bounds.maxY + 0.7,
                (bounds.minZ + bounds.maxZ) * 0.5);
        Gizmos.billboardText(label, center, TextGizmo.Style.forColorAndCentered(0xFF6FE7FF).withScale(0.28F))
                .setAlwaysOnTop();
    }
}
