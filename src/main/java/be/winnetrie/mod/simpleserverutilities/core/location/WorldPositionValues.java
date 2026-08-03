package be.winnetrie.mod.simpleserverutilities.core.location;

/**
 * Shared normalization for persisted, dimension-aware positions. Storage
 * models keep their existing JSON shape while using one validation policy.
 */
public record WorldPositionValues(
        String dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public static WorldPositionValues normalize(
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        String safeDimension = dimension == null || dimension.isBlank()
                ? "minecraft:overworld" : dimension.trim();
        if (safeDimension.length() > 128) safeDimension = safeDimension.substring(0, 128);
        double safeX = finiteClamp(x, -30_000_000.0D, 30_000_000.0D, 0.0D);
        double safeY = finiteClamp(y, -20_000_000.0D, 20_000_000.0D, 64.0D);
        double safeZ = finiteClamp(z, -30_000_000.0D, 30_000_000.0D, 0.0D);
        float safeYaw = Float.isFinite(yaw) ? yaw : 0.0F;
        float safePitch = Float.isFinite(pitch)
                ? Math.max(-90.0F, Math.min(90.0F, pitch)) : 0.0F;
        return new WorldPositionValues(safeDimension, safeX, safeY, safeZ, safeYaw, safePitch);
    }

    private static double finiteClamp(double value, double minimum, double maximum, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
