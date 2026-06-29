package be.winnetrie.mod.simpleserverutilities.permission;

import net.minecraft.server.level.ServerPlayer;

public class PermissionService {

    public static final String CLAIM_CREATE = "ssu.claim.create";
    public static final String CLAIM_DELETE = "ssu.claim.delete";
    public static final String CLAIM_BYPASS = "ssu.claim.bypass";

    public static final String REGION_CREATE = "ssu.region.create";
    public static final String REGION_DELETE = "ssu.region.delete";
    public static final String REGION_EDIT = "ssu.region.edit";

    private PermissionService() {
    }

    public static boolean has(ServerPlayer player, String permission) {
        if (permission.equals(CLAIM_CREATE)) {
            return true;
        }

        if (permission.equals(CLAIM_DELETE)) {
            return true;
        }

        return isAdmin(player);
    }

    public static boolean isAdmin(ServerPlayer player) {
        if (player.level().getServer() == null) {
            return false;
        }
/* 
        return player.level()
                .getServer()
                .getPlayerList()
                .isOp(new NameAndId(player.getGameProfile()));
*/
    return false;
    }
}