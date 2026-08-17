package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.util.List;
import java.util.Random;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.NpcArcaneVfxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Hand-built Arcane Missiles VFX. The missile body, trail, runes and impact are custom textured
 * geometry submitted directly into the 26.2 feature renderer; vanilla particles are not used as
 * the main visual.
 */
public final class NpcArcaneVfxRenderer {
    private static final Identifier MISSILE_TEXTURE = texture("arcane_missile_core.png");
    private static final Identifier TRAIL_TEXTURE = texture("arcane_missile_trail.png");
    private static final Identifier RUNE_TEXTURE = texture("arcane_rune.png");
    private static final Identifier IMPACT_TEXTURE = texture("arcane_impact.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int NO_OVERLAY = 0;
    private static final int MISSILES = 5;

    private NpcArcaneVfxRenderer() {
    }

    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        List<NpcArcaneVfxClientState.Effect> effects = NpcArcaneVfxClientState.activeEffects();
        if (effects.isEmpty()) return;

        String dimension = minecraft.level.dimension().identifier().toString();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        long now = System.nanoTime();
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(-camera.x, -camera.y, -camera.z);

        event.getSubmitNodeCollector().submitCustomGeometry(
                stack,
                RenderTypes.entityTranslucentEmissive(TRAIL_TEXTURE),
                (pose, consumer) -> {
                    for (NpcArcaneVfxClientState.Effect effect : effects) {
                        if (!dimension.equals(effect.dimension()) || effect.mode() != NpcArcaneVfxPayload.MODE_VOLLEY) continue;
                        renderVolleyTrails(minecraft, effect, now, camera, pose, consumer);
                    }
                }
        );

        event.getSubmitNodeCollector().submitCustomGeometry(
                stack,
                RenderTypes.entityTranslucentEmissive(MISSILE_TEXTURE),
                (pose, consumer) -> {
                    for (NpcArcaneVfxClientState.Effect effect : effects) {
                        if (!dimension.equals(effect.dimension())) continue;
                        if (effect.mode() == NpcArcaneVfxPayload.MODE_VOLLEY) {
                            renderVolleyHeads(minecraft, effect, now, camera, pose, consumer);
                        } else if (effect.mode() == NpcArcaneVfxPayload.MODE_CHARGE) {
                            renderChargeHands(minecraft, effect, now, camera, pose, consumer);
                        }
                    }
                }
        );

        event.getSubmitNodeCollector().submitCustomGeometry(
                stack,
                RenderTypes.entityTranslucentEmissive(RUNE_TEXTURE),
                (pose, consumer) -> {
                    for (NpcArcaneVfxClientState.Effect effect : effects) {
                        if (!dimension.equals(effect.dimension()) || effect.mode() != NpcArcaneVfxPayload.MODE_CHARGE) continue;
                        renderChargeRunes(minecraft, effect, now, camera, pose, consumer);
                    }
                }
        );

        event.getSubmitNodeCollector().submitCustomGeometry(
                stack,
                RenderTypes.entityTranslucentEmissive(IMPACT_TEXTURE),
                (pose, consumer) -> {
                    for (NpcArcaneVfxClientState.Effect effect : effects) {
                        if (!dimension.equals(effect.dimension()) || effect.mode() != NpcArcaneVfxPayload.MODE_VOLLEY) continue;
                        renderVolleyImpacts(minecraft, effect, now, camera, pose, consumer);
                    }
                }
        );

        stack.popPose();
    }

    private static void renderVolleyTrails(Minecraft minecraft, NpcArcaneVfxClientState.Effect effect,
            long now, Vec3 camera, PoseStack.Pose pose, VertexConsumer consumer) {
        double age = effect.ageSeconds(now);
        double total = Math.max(0.18D, effect.durationSeconds());
        VolleyFrame frame = volleyFrame(minecraft, effect);
        for (int missile = 0; missile < MISSILES; missile++) {
            double delay = missile * 0.028D;
            double p = (age - delay) / Math.max(0.12D, total - delay);
            if (p <= 0.0D || p > 1.10D) continue;
            double t = easeOutCubic(Math.min(1.0D, p));
            Path path = missilePath(frame, effect.seed(), missile);
            double tailStart = Math.max(0.0D, t - 0.34D);
            int segments = 14;
            for (int i = 0; i < segments; i++) {
                double a = tailStart + (t - tailStart) * (i / (double) segments);
                double b = tailStart + (t - tailStart) * ((i + 1) / (double) segments);
                if (b <= a + 1.0E-5D) continue;
                Vec3 p0 = curvedPoint(path, a, effect.seed(), missile);
                Vec3 p1 = curvedPoint(path, b, effect.seed(), missile);
                double fadeA = i / (double) segments;
                double fadeB = (i + 1) / (double) segments;
                double widthA = 0.025D + 0.075D * fadeA;
                double widthB = 0.025D + 0.085D * fadeB;
                ribbonSegment(consumer, pose, p0, p1, camera, widthA, widthB,
                        (float) fadeA, (float) fadeB,
                        alphaColor((int) (210.0D * fadeA)), alphaColor((int) (245.0D * fadeB)));
            }
        }
    }

    private static void renderVolleyHeads(Minecraft minecraft, NpcArcaneVfxClientState.Effect effect,
            long now, Vec3 camera, PoseStack.Pose pose, VertexConsumer consumer) {
        double age = effect.ageSeconds(now);
        double total = Math.max(0.18D, effect.durationSeconds());
        VolleyFrame frame = volleyFrame(minecraft, effect);
        for (int missile = 0; missile < MISSILES; missile++) {
            double delay = missile * 0.028D;
            double p = (age - delay) / Math.max(0.12D, total - delay);
            if (p <= 0.0D || p >= 1.0D) continue;
            double t = easeOutCubic(p);
            Path path = missilePath(frame, effect.seed(), missile);
            Vec3 center = curvedPoint(path, t, effect.seed(), missile);
            double pulse = 1.0D + Math.sin((age * 24.0D) + missile * 1.7D) * 0.08D;
            billboard(consumer, pose, center, camera, 0.20D * pulse, 0xFFFFFFFF, 0.0D);
            billboard(consumer, pose, center, camera, 0.31D * pulse, 0x72FFFFFF,
                    age * 2.4D + missile * 0.35D);
        }
    }

    private static void renderVolleyImpacts(Minecraft minecraft, NpcArcaneVfxClientState.Effect effect,
            long now, Vec3 camera, PoseStack.Pose pose, VertexConsumer consumer) {
        double age = effect.ageSeconds(now);
        double total = Math.max(0.18D, effect.durationSeconds());
        VolleyFrame frame = volleyFrame(minecraft, effect);
        for (int missile = 0; missile < MISSILES; missile++) {
            double delay = missile * 0.028D;
            double p = (age - delay) / Math.max(0.12D, total - delay);
            if (p < 0.86D || p > 1.16D) continue;
            double local = Math.max(0.0D, Math.min(1.0D, (p - 0.86D) / 0.30D));
            Path path = missilePath(frame, effect.seed(), missile);
            Vec3 impact = path.end();
            double size = 0.16D + local * 0.46D;
            int alpha = (int) (230.0D * (1.0D - local));
            billboard(consumer, pose, impact, camera, size, alphaColor(alpha), local * 1.6D + missile * 0.4D);
        }
    }

    private static void renderChargeRunes(Minecraft minecraft, NpcArcaneVfxClientState.Effect effect,
            long now, Vec3 camera, PoseStack.Pose pose, VertexConsumer consumer) {
        Entity source = minecraft.level == null ? null : minecraft.level.getEntity(effect.sourceEntityId());
        Vec3 center = source == null ? effect.start() : source.position();
        double progress = effect.progress(now);
        double age = effect.ageSeconds(now);
        double pulse = 0.82D + 0.10D * Math.sin(age * 12.0D);
        int alpha = (int) (150.0D + 75.0D * Math.sin(Math.min(1.0D, progress) * Math.PI));

        // Horizontal spinning caster sigil.
        double angle = age * 1.85D;
        Vec3 xAxis = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)).scale(pulse);
        Vec3 zAxis = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)).scale(pulse);
        orientedQuad(consumer, pose, center.add(0.0D, 0.045D, 0.0D), xAxis, zAxis, alphaColor(alpha));

        // A second counter-rotating ring in a vertical plane behind the hands.
        Vec3 target = currentEnd(minecraft, effect);
        Vec3 look = target.subtract(center);
        if (look.lengthSqr() < 1.0E-6D) look = new Vec3(0.0D, 0.0D, 1.0D);
        look = look.normalize();
        Vec3 right = horizontalRight(look);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double spin = -age * 2.25D;
        Vec3 verticalX = right.scale(Math.cos(spin)).add(up.scale(Math.sin(spin))).scale(0.48D);
        Vec3 verticalY = up.scale(Math.cos(spin)).subtract(right.scale(Math.sin(spin))).scale(0.48D);
        orientedQuad(consumer, pose, center.add(0.0D, source == null ? 1.15D : source.getBbHeight() * 0.62D, 0.0D),
                verticalX, verticalY, alphaColor(Math.min(210, alpha + 25)));
    }

    private static void renderChargeHands(Minecraft minecraft, NpcArcaneVfxClientState.Effect effect,
            long now, Vec3 camera, PoseStack.Pose pose, VertexConsumer consumer) {
        Entity source = minecraft.level == null ? null : minecraft.level.getEntity(effect.sourceEntityId());
        Vec3 base = source == null ? effect.start() : source.position();
        Vec3 target = currentEnd(minecraft, effect);
        Vec3 forward = target.subtract(base);
        forward = new Vec3(forward.x, 0.0D, forward.z);
        if (forward.lengthSqr() < 1.0E-6D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 right = horizontalRight(forward);
        double bodyHeight = source == null ? 1.8D : Math.max(1.0D, source.getBbHeight());
        double age = effect.ageSeconds(now);
        double size = 0.13D + 0.035D * Math.sin(age * 18.0D);
        for (int hand = -1; hand <= 1; hand += 2) {
            Vec3 handCenter = base.add(0.0D, bodyHeight * 0.60D, 0.0D)
                    .add(right.scale(hand * 0.34D))
                    .add(forward.scale(0.16D));
            billboard(consumer, pose, handCenter, camera, size, 0xE8FFFFFF, age * 3.0D * hand);
            billboard(consumer, pose, handCenter, camera, size * 1.55D, 0x55FFFFFF, -age * 2.0D * hand);
        }
    }

    private static VolleyFrame volleyFrame(Minecraft minecraft, NpcArcaneVfxClientState.Effect effect) {
        Entity source = minecraft.level == null ? null : minecraft.level.getEntity(effect.sourceEntityId());
        Entity target = minecraft.level == null ? null : minecraft.level.getEntity(effect.targetEntityId());
        Vec3 start = source == null ? effect.start() : source.position().add(0.0D, source.getBbHeight() * 0.82D, 0.0D);
        Vec3 end = target == null ? effect.end() : target.getBoundingBox().getCenter().add(0.0D, target.getBbHeight() * 0.05D, 0.0D);
        Vec3 direct = end.subtract(start);
        if (direct.lengthSqr() < 1.0E-6D) direct = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 forward = direct.normalize();
        Vec3 right = horizontalRight(forward);
        Vec3 up = right.cross(forward);
        if (up.lengthSqr() < 1.0E-6D) up = new Vec3(0.0D, 1.0D, 0.0D);
        up = up.normalize();
        return new VolleyFrame(start, end, forward, right, up, direct.length());
    }

    private static Path missilePath(VolleyFrame frame, int seed, int missile) {
        double fan = (missile - (MISSILES - 1) * 0.5D) / ((MISSILES - 1) * 0.5D);
        Random random = new Random(seed * 31L + missile * 0x9E3779B97F4A7C15L);
        double jitter = (random.nextDouble() - 0.5D) * 0.18D;
        double sideStart = fan * 0.32D;
        Vec3 start = frame.start().add(frame.right().scale(sideStart)).add(frame.up().scale(0.04D + Math.abs(fan) * 0.07D));
        Vec3 end = frame.end().add(frame.right().scale(fan * Math.min(0.72D, 0.16D + frame.length() * 0.022D)))
                .add(frame.up().scale((2 - Math.abs(missile - 2)) * 0.035D));
        Vec3 c1 = start.add(frame.forward().scale(frame.length() * 0.25D))
                .add(frame.right().scale(fan * (0.58D + jitter)))
                .add(frame.up().scale(0.28D + Math.abs(fan) * 0.18D));
        Vec3 c2 = start.add(frame.forward().scale(frame.length() * 0.70D))
                .add(frame.right().scale(-fan * (0.26D - jitter * 0.5D)))
                .add(frame.up().scale(0.36D + (2 - Math.abs(missile - 2)) * 0.06D));
        return new Path(start, c1, c2, end);
    }

    private static Vec3 curvedPoint(Path path, double t, int seed, int missile) {
        Vec3 base = cubicBezier(path.start(), path.c1(), path.c2(), path.end(), t);
        Vec3 ahead = cubicBezier(path.start(), path.c1(), path.c2(), path.end(), Math.min(1.0D, t + 0.015D));
        Vec3 tangent = ahead.subtract(base);
        if (tangent.lengthSqr() < 1.0E-6D) tangent = path.end().subtract(path.start());
        tangent = tangent.normalize();
        Vec3 axisSeed = Math.abs(tangent.y) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = tangent.cross(axisSeed);
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();
        Vec3 normal = side.cross(tangent).normalize();
        double phase = missile * 0.83D + (seed & 31) * 0.11D;
        double envelope = Math.sin(Math.PI * Math.max(0.0D, Math.min(1.0D, t)));
        double wave = t * Math.PI * 5.2D + phase;
        return base.add(side.scale(Math.sin(wave) * 0.075D * envelope))
                .add(normal.scale(Math.cos(wave * 0.92D) * 0.042D * envelope));
    }

    private static Vec3 currentEnd(Minecraft minecraft, NpcArcaneVfxClientState.Effect effect) {
        if (minecraft.level != null) {
            Entity target = minecraft.level.getEntity(effect.targetEntityId());
            if (target != null) return target.getBoundingBox().getCenter();
        }
        return effect.end();
    }

    private static void ribbonSegment(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 camera,
            double width0, double width1, float u0, float u1, int color0, int color1) {
        Vec3 tangent = p1.subtract(p0);
        if (tangent.lengthSqr() < 1.0E-8D) return;
        tangent = tangent.normalize();
        Vec3 midpoint = p0.add(p1).scale(0.5D);
        Vec3 view = camera.subtract(midpoint);
        if (view.lengthSqr() < 1.0E-8D) view = new Vec3(0.0D, 1.0D, 0.0D);
        view = view.normalize();
        Vec3 width = tangent.cross(view);
        if (width.lengthSqr() < 1.0E-8D) width = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (width.lengthSqr() < 1.0E-8D) width = new Vec3(1.0D, 0.0D, 0.0D);
        width = width.normalize();

        Vec3 a = p0.add(width.scale(width0));
        Vec3 b = p0.subtract(width.scale(width0));
        Vec3 c = p1.subtract(width.scale(width1));
        Vec3 d = p1.add(width.scale(width1));
        quad(consumer, pose, a, b, c, d, u0, u1, color0, color1);
    }

    private static void billboard(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 camera,
            double halfSize, int color, double rotation) {
        Vec3 view = camera.subtract(center);
        if (view.lengthSqr() < 1.0E-8D) view = new Vec3(0.0D, 0.0D, 1.0D);
        view = view.normalize();
        Vec3 right = view.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-8D) right = new Vec3(1.0D, 0.0D, 0.0D);
        right = right.normalize();
        Vec3 up = right.cross(view).normalize();
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        Vec3 rx = right.scale(cos).add(up.scale(sin)).scale(halfSize);
        Vec3 uy = up.scale(cos).subtract(right.scale(sin)).scale(halfSize);
        orientedQuad(consumer, pose, center, rx, uy, color);
    }

    private static void orientedQuad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center,
            Vec3 halfX, Vec3 halfY, int color) {
        Vec3 a = center.subtract(halfX).subtract(halfY);
        Vec3 b = center.add(halfX).subtract(halfY);
        Vec3 c = center.add(halfX).add(halfY);
        Vec3 d = center.subtract(halfX).add(halfY);
        texturedQuad(consumer, pose, a, b, c, d, color);
    }

    private static void texturedQuad(VertexConsumer consumer, PoseStack.Pose pose,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(consumer, pose, a, color, 0.0F, 1.0F);
        vertex(consumer, pose, b, color, 1.0F, 1.0F);
        vertex(consumer, pose, c, color, 1.0F, 0.0F);
        vertex(consumer, pose, d, color, 0.0F, 0.0F);
        // Back face as well so the sigils and sprites remain visible from every angle.
        vertex(consumer, pose, d, color, 0.0F, 0.0F);
        vertex(consumer, pose, c, color, 1.0F, 0.0F);
        vertex(consumer, pose, b, color, 1.0F, 1.0F);
        vertex(consumer, pose, a, color, 0.0F, 1.0F);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float u0, float u1, int color0, int color1) {
        vertex(consumer, pose, a, color0, u0, 0.0F);
        vertex(consumer, pose, b, color0, u0, 1.0F);
        vertex(consumer, pose, c, color1, u1, 1.0F);
        vertex(consumer, pose, d, color1, u1, 0.0F);
        vertex(consumer, pose, d, color1, u1, 0.0F);
        vertex(consumer, pose, c, color1, u1, 1.0F);
        vertex(consumer, pose, b, color0, u0, 1.0F);
        vertex(consumer, pose, a, color0, u0, 0.0F);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, int color, float u, float v) {
        consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static Vec3 cubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0D - t;
        double tt = t * t;
        double uu = u * u;
        return p0.scale(uu * u)
                .add(p1.scale(3.0D * uu * t))
                .add(p2.scale(3.0D * u * tt))
                .add(p3.scale(tt * t));
    }

    private static Vec3 horizontalRight(Vec3 forward) {
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        if (right.lengthSqr() < 1.0E-8D) right = new Vec3(1.0D, 0.0D, 0.0D);
        return right.normalize();
    }

    private static double easeOutCubic(double value) {
        double t = Math.max(0.0D, Math.min(1.0D, value));
        double inv = 1.0D - t;
        return 1.0D - inv * inv * inv;
    }

    private static int alphaColor(int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | 0x00FFFFFF;
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "textures/vfx/" + name);
    }

    private record VolleyFrame(Vec3 start, Vec3 end, Vec3 forward, Vec3 right, Vec3 up, double length) {
    }

    private record Path(Vec3 start, Vec3 c1, Vec3 c2, Vec3 end) {
    }
}
