package be.winnetrie.mod.simpleserverutilities.teleport;

/** Pure movement comparison used by delayed teleports and validation harnesses. */
public final class TeleportMovementGuard {

    private static final double MOVEMENT_EPSILON_SQUARED = 0.0001D;

    private TeleportMovementGuard() {
    }

    public static boolean hasMoved(
            String startDimension,
            double startX,
            double startY,
            double startZ,
            String currentDimension,
            double currentX,
            double currentY,
            double currentZ
    ) {
        if (startDimension == null || currentDimension == null
                || !startDimension.equals(currentDimension)) {
            return true;
        }
        double dx = currentX - startX;
        double dy = currentY - startY;
        double dz = currentZ - startZ;
        return dx * dx + dy * dy + dz * dz > MOVEMENT_EPSILON_SQUARED;
    }
}
