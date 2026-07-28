package be.winnetrie.mod.simpleserverutilities.region;

/**
 * Global economy policy for server-region rentals.
 *
 * <p>Percentages are stored in permille to avoid floating point arithmetic.</p>
 */
public final class RegionRentEconomySettings {

    private int ownerSharePermille = 0;
    private int playerCancelRefundPermille = 0;
    private int adminCancelRefundPermille = 1_000;

    public void normalize() {
        ownerSharePermille = clamp(ownerSharePermille);
        playerCancelRefundPermille = clamp(playerCancelRefundPermille);
        adminCancelRefundPermille = clamp(adminCancelRefundPermille);
    }

    public int getOwnerSharePermille() {
        return ownerSharePermille;
    }

    public void setOwnerSharePermille(int value) {
        ownerSharePermille = clamp(value);
    }

    public int getPlayerCancelRefundPermille() {
        return playerCancelRefundPermille;
    }

    public void setPlayerCancelRefundPermille(int value) {
        playerCancelRefundPermille = clamp(value);
    }

    public int getAdminCancelRefundPermille() {
        return adminCancelRefundPermille;
    }

    public void setAdminCancelRefundPermille(int value) {
        adminCancelRefundPermille = clamp(value);
    }

    public int getOwnerSharePercent() {
        return ownerSharePermille / 10;
    }

    public int getPlayerCancelRefundPercent() {
        return playerCancelRefundPermille / 10;
    }

    public int getAdminCancelRefundPercent() {
        return adminCancelRefundPermille / 10;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(1_000, value));
    }
}
