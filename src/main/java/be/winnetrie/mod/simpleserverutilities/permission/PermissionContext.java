package be.winnetrie.mod.simpleserverutilities.permission;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class PermissionContext {

    private final ServerPlayer player;
    private final String dimension;
    private final BlockPos position;
    private final String action;
    private final Region region;
    private final PlayerClaim playerClaim;
    private final ClaimRole claimRole;

    private PermissionContext(Builder builder) {
        this.player = builder.player;
        this.dimension = builder.dimension;
        this.position = builder.position;
        this.action = builder.action;
        this.region = builder.region;
        this.playerClaim = builder.playerClaim;
        this.claimRole = builder.claimRole;
    }

    public static PermissionContext global(ServerPlayer player) {
        return builder(player).build();
    }

    public static PermissionContext at(ServerPlayer player, BlockPos position) {
        return builder(player).position(position).resolveArea().build();
    }

    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getAction() {
        return action;
    }

    public Region getRegion() {
        return region;
    }

    public PlayerClaim getPlayerClaim() {
        return playerClaim;
    }

    public ClaimRole getClaimRole() {
        return claimRole;
    }

    public boolean hasRegion() {
        return region != null;
    }

    public boolean hasPlayerClaim() {
        return playerClaim != null;
    }

    public enum ClaimRole {
        OWNER,
        CO_OWNER,
        MEMBER,
        VISITOR,
        NONE
    }

    public static class Builder {
        private final ServerPlayer player;
        private String dimension;
        private BlockPos position;
        private String action = "";
        private Region region;
        private PlayerClaim playerClaim;
        private ClaimRole claimRole = ClaimRole.NONE;

        private Builder(ServerPlayer player) {
            this.player = player;

            if (player != null) {
                this.dimension = player.level().dimension().location().toString();
            }
        }

        public Builder dimension(String dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder position(BlockPos position) {
            this.position = position;
            return this;
        }

        public Builder action(String action) {
            this.action = action == null ? "" : action;
            return this;
        }

        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        public Builder playerClaim(PlayerClaim playerClaim) {
            this.playerClaim = playerClaim;
            this.claimRole = resolveClaimRole(player, playerClaim);
            return this;
        }

        public Builder resolveArea() {
            if (player == null || position == null) {
                return this;
            }

            Level level = player.level();
            this.dimension = level.dimension().location().toString();
            this.region = SsuModuleAccess.active("regions")
                    ? SimpleServerUtilities.REGIONS.getAt(level.dimension(), position)
                    : null;

            ChunkPos chunkPos = new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
            this.playerClaim = SsuModuleAccess.active("claims")
                    ? SimpleServerUtilities.PLAYER_CLAIMS.getClaim(level, chunkPos)
                    : null;
            this.claimRole = resolveClaimRole(player, playerClaim);

            return this;
        }

        public PermissionContext build() {
            return new PermissionContext(this);
        }

        private static ClaimRole resolveClaimRole(ServerPlayer player, PlayerClaim claim) {
            if (player == null || claim == null) {
                return ClaimRole.NONE;
            }

            if (claim.isOwner(player.getUUID())) {
                return ClaimRole.OWNER;
            }

            if (claim.isCoOwner(player.getUUID())) return ClaimRole.CO_OWNER;
            if (claim.isTrusted(player.getUUID())) return ClaimRole.MEMBER;
            return ClaimRole.VISITOR;
        }
    }
}
