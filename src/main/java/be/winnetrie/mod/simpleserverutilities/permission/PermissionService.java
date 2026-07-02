package be.winnetrie.mod.simpleserverutilities.permission;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class PermissionService {

    public static final String CLAIM_CREATE = "ssu.claim.create";
    public static final String CLAIM_DELETE = "ssu.claim.delete";
    public static final String CLAIM_BYPASS = "ssu.claim.bypass";

    public static final String REGION_CREATE = "ssu.region.create";
    public static final String REGION_DELETE = "ssu.region.delete";
    public static final String REGION_EDIT = "ssu.region.edit";

    public static final String WARP_ADMIN = "ssu.warp.admin";
    public static final String WARP_USE = "ssu.warp.use";

    private PermissionService() {
    }

    public static boolean has(ServerPlayer player, String permission) {
        if (permission.equals(CLAIM_CREATE)) {
            return true;
        }

        if (permission.equals(CLAIM_DELETE)) {
            return true;
        }

        if (permission.equals(WARP_USE)) {
            return true;
        }

        return isAdmin(player);
    }

    public static boolean isAdmin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();

        if (server == null) {
            return false;
        }

        return server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }
}