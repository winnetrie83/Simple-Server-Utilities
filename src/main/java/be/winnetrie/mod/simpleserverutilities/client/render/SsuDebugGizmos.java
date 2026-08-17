package be.winnetrie.mod.simpleserverutilities.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small 1.21.1 compatibility renderer for the world-space primitives SSU used
 * through Minecraft's newer Gizmo API on 26.2.
 *
 * <p>NeoForge 1.21.1 debug renderers still receive a PoseStack and
 * MultiBufferSource directly. This class keeps the feature renderers readable
 * while drawing their text, filled quads/boxes and lines through that older
 * pipeline.</p>
 */
public final class SsuDebugGizmos {
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private SsuDebugGizmos() {
    }

    public static void begin(Minecraft minecraft, PoseStack poseStack, MultiBufferSource buffers,
                             double camX, double camY, double camZ) {
        CONTEXT.set(new Context(minecraft, poseStack, buffers, camX, camY, camZ));
    }

    public static void end() {
        CONTEXT.remove();
    }

    public static Handle billboardText(String text, Vec3 position, TextStyle style) {
        Context context = CONTEXT.get();
        if (context == null || text == null || position == null || style == null) return Handle.INSTANCE;
        DebugRenderer.renderFloatingText(
                context.poseStack(), context.buffers(), text,
                position.x - context.camX(), position.y - context.camY(), position.z - context.camZ(),
                style.color(), style.scale(), style.centered(), 0.0F, true);
        return Handle.INSTANCE;
    }

    public static Handle cuboid(AABB bounds, FillStyle style) {
        Context context = CONTEXT.get();
        if (context == null || bounds == null || style == null) return Handle.INSTANCE;
        int color = style.argb();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        AABB relative = bounds.move(-context.camX(), -context.camY(), -context.camZ());
        DebugRenderer.renderFilledBox(context.poseStack(), context.buffers(), relative, r, g, b, a);
        return Handle.INSTANCE;
    }

    public static Handle rect(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, FillStyle style) {
        Context context = CONTEXT.get();
        if (context == null || p0 == null || p1 == null || p2 == null || p3 == null || style == null) {
            return Handle.INSTANCE;
        }
        var consumer = context.buffers().getBuffer(RenderType.debugQuads());
        var matrix = context.poseStack().last().pose();
        addQuadVertex(consumer, matrix, p0, context, style.argb());
        addQuadVertex(consumer, matrix, p1, context, style.argb());
        addQuadVertex(consumer, matrix, p2, context, style.argb());
        addQuadVertex(consumer, matrix, p3, context, style.argb());
        return Handle.INSTANCE;
    }

    public static Handle line(Vec3 start, Vec3 end, int argb, float width) {
        Context context = CONTEXT.get();
        if (context == null || start == null || end == null) return Handle.INSTANCE;
        var consumer = context.buffers().getBuffer(RenderType.lines());
        var pose = context.poseStack().last();
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = length > 1.0E-7D ? (float) (dx / length) : 0.0F;
        float ny = length > 1.0E-7D ? (float) (dy / length) : 1.0F;
        float nz = length > 1.0E-7D ? (float) (dz / length) : 0.0F;
        consumer.addVertex(pose, (float) (start.x - context.camX()), (float) (start.y - context.camY()),
                        (float) (start.z - context.camZ()))
                .setColor(argb).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) (end.x - context.camX()), (float) (end.y - context.camY()),
                        (float) (end.z - context.camZ()))
                .setColor(argb).setNormal(pose, nx, ny, nz);
        return Handle.INSTANCE;
    }

    private static void addQuadVertex(com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                      org.joml.Matrix4f matrix, Vec3 point, Context context, int argb) {
        consumer.addVertex(matrix,
                        (float) (point.x - context.camX()),
                        (float) (point.y - context.camY()),
                        (float) (point.z - context.camZ()))
                .setColor(argb);
    }

    public static final class FillStyle {
        private final int argb;

        private FillStyle(int argb) {
            this.argb = argb;
        }

        public static FillStyle fill(int argb) {
            return new FillStyle(argb);
        }

        int argb() {
            return argb;
        }
    }

    public static final class TextStyle {
        private final int color;
        private final boolean centered;
        private final float scale;

        private TextStyle(int color, boolean centered, float scale) {
            this.color = color;
            this.centered = centered;
            this.scale = scale;
        }

        public static TextStyle forColorAndCentered(int color) {
            return new TextStyle(color, true, 0.20F);
        }

        public TextStyle withScale(float scale) {
            return new TextStyle(color, centered, scale);
        }

        int color() { return color; }
        boolean centered() { return centered; }
        float scale() { return scale; }
    }

    /** Chain-compatible placeholder for newer Gizmo#setAlwaysOnTop calls. */
    public enum Handle {
        INSTANCE;
        public Handle setAlwaysOnTop() { return this; }
    }

    private record Context(Minecraft minecraft, PoseStack poseStack, MultiBufferSource buffers,
                           double camX, double camY, double camZ) {
    }
}
