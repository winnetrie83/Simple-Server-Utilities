package be.winnetrie.mod.simpleserverutilities.client.hologram;

import java.net.URI;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import be.winnetrie.mod.simpleserverutilities.network.HologramSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class HologramClientState {
    private static volatile List<HologramSyncPayload.Entry> entries = List.of();

    private HologramClientState() {
    }

    public static void apply(HologramSyncPayload payload) {
        entries = payload == null ? List.of() : payload.entries();
        HologramImageCache.synchronize(entries);
    }

    public static List<HologramSyncPayload.Entry> entries() {
        return entries;
    }

    public static void clear() {
        entries = List.of();
        HologramImageCache.clear();
    }

    public static String targetedHologramId(Minecraft minecraft) {
        HologramSyncPayload.Entry entry = targetedEntry(minecraft, false);
        return entry == null ? null : entry.id();
    }

    public static URI targetedLink(Minecraft minecraft) {
        HologramSyncPayload.Entry best = targetedEntry(minecraft, true);
        if (best == null) return null;
        try {
            URI uri = URI.create(best.url());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                return null;
            }
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HologramSyncPayload.Entry targetedEntry(Minecraft minecraft, boolean linksOnly) {
        if (minecraft.player == null || minecraft.level == null) return null;
        String dimension = minecraft.level.dimension().identifier().toString();
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getViewVector(1.0F).normalize();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-8D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();

        HologramSyncPayload.Entry best = null;
        double bestAlong = Double.MAX_VALUE;
        double bestNormalizedDistance = Double.MAX_VALUE;

        for (HologramSyncPayload.Entry entry : entries) {
            if (!dimension.equals(entry.dimension())) continue;
            if (linksOnly && (entry.type() != HologramType.LINK || entry.url().isBlank())) continue;

            Vec3 firstLine = new Vec3(entry.x(), entry.y(), entry.z());
            if (entry.type() == HologramType.IMAGE) {
                Vec3 relative = firstLine.subtract(eye);
                double along = relative.dot(look);
                double maximum = Math.max(8.0D, entry.viewDistance());
                if (along < 0.0D || along > maximum) continue;

                Vec3 planeHit = eye.add(look.scale(along));
                Vec3 offset = planeHit.subtract(firstLine);
                double horizontal = Math.abs(offset.dot(right));
                double vertical = Math.abs(offset.dot(up));
                double halfWidth = Math.max(0.10D, entry.imageWidth() * entry.scale() * 0.5D);
                double halfHeight = Math.max(0.10D, entry.imageHeight() * entry.scale() * 0.5D);
                double margin = 0.055D;
                if (horizontal > halfWidth + margin || vertical > halfHeight + margin) continue;

                double normalizedDistance = square(horizontal / Math.max(0.01D, halfWidth))
                        + square(vertical / Math.max(0.01D, halfHeight));
                if (along < bestAlong - 1.0E-6D
                        || (Math.abs(along - bestAlong) <= 1.0E-6D
                        && normalizedDistance < bestNormalizedDistance)) {
                    best = entry;
                    bestAlong = along;
                    bestNormalizedDistance = normalizedDistance;
                }
                continue;
            }

            double spacing = HologramRenderer.LINE_SPACING_PER_UNIT * entry.scale();
            int lines = Math.max(1, entry.lines().size());
            for (int index = 0; index < lines; index++) {
                Vec3 point = firstLine.subtract(up.scale(index * spacing));
                Vec3 relative = point.subtract(eye);
                double along = relative.dot(look);
                double maximum = Math.max(8.0D, entry.viewDistance());
                if (along < 0.0D || along > maximum) continue;

                Vec3 planeHit = eye.add(look.scale(along));
                Vec3 offset = planeHit.subtract(point);
                double horizontal = Math.abs(offset.dot(right));
                double vertical = Math.abs(offset.dot(up));

                String visibleLine = entry.lines().isEmpty() ? "" : entry.lines().get(index);
                if (entry.type() == HologramType.LINK && index == lines - 1) {
                    visibleLine += "  §7[Right-click]";
                }

                // TextGizmo's visual world size is substantially smaller than its raw
                // font-pixel width. This narrow rectangular hitbox follows the rendered
                // line instead of selecting a large sphere around nearby holograms.
                double halfWidth = Math.max(0.14D,
                        HologramRenderer.styledWidthPixels(minecraft, visibleLine, entry.color())
                                * HologramRenderer.TEXT_WIDTH_PER_FONT_PIXEL * entry.scale());
                double halfHeight = Math.max(0.10D, HologramRenderer.TEXT_HALF_HEIGHT_PER_UNIT * entry.scale());
                double margin = 0.055D;
                if (horizontal > halfWidth + margin || vertical > halfHeight + margin) continue;

                double normalizedDistance = square(horizontal / Math.max(0.01D, halfWidth))
                        + square(vertical / Math.max(0.01D, halfHeight));
                if (along < bestAlong - 1.0E-6D
                        || (Math.abs(along - bestAlong) <= 1.0E-6D
                        && normalizedDistance < bestNormalizedDistance)) {
                    best = entry;
                    bestAlong = along;
                    bestNormalizedDistance = normalizedDistance;
                }
            }
        }
        return best;
    }

    private static double square(double value) {
        return value * value;
    }
}
