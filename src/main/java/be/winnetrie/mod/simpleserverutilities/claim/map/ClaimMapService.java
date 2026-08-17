package be.winnetrie.mod.simpleserverutilities.claim.map;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimOperationResult;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimTaxDeleteActionPayload;
import be.winnetrie.mod.simpleserverutilities.permission.policy.ClaimPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClaimMapService {

    private static final int MAX_CENTER_DISTANCE = 128;

    private ClaimMapService() {
    }

    public static void handleRequest(ClaimMapRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("claims")) return;
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!ClaimPolicy.canUseMap(player)) {
            return;
        }
        int centerChunkX = payload.centerChunkX();
        int centerChunkZ = payload.centerChunkZ();
        if (payload.centerOnSelectedClaim() && !payload.selectedClaimGroup().isBlank()) {
            PlayerClaim selected = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(
                    player.getUUID(), payload.selectedClaimGroup());
            ChunkPos center = claimCenter(selected);
            if (center != null) {
                centerChunkX = center.x;
                centerChunkZ = center.z;
            }
        }
        sendMap(
                player,
                centerChunkX,
                centerChunkZ,
                payload.radius(),
                payload.selectedClaimGroup(),
                "",
                false
        );
    }

    public static void handleAction(ClaimMapActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("claims")) return;
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!ClaimPolicy.canUseMap(player)) {
            return;
        }

        if (!isSafeViewport(player, payload.centerChunkX(), payload.centerChunkZ(),
                payload.claimName(), payload.radius())) {
            sendMap(player, player.chunkPosition().x, player.chunkPosition().z, payload.radius(),
                    payload.claimName(), "Map operation rejected: view is too far from your position.", true);
            return;
        }

        if (payload.operation() == ClaimMapOperation.DELETE) {
            if (!ClaimPolicy.canDeleteClaim(player)) {
                sendMap(player, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), payload.claimName(),
                        "You do not have permission to delete claims.", true);
                return;
            }
            PlayerClaim deletedClaim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(
                    player.getUUID(), payload.claimName());
            if (deletedClaim != null && !ClaimPolicy.hasAdminBypass(player)
                    && SimpleServerUtilities.CLAIM_TAX.requiresDeleteSettlement(deletedClaim)) {
                sendMap(player, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), payload.claimName(),
                        "Choose Pay & delete or Forfeit capacity in the tax confirmation window.", true);
                return;
            }
            boolean deleted = SimpleServerUtilities.PLAYER_CLAIMS.deleteClaimGroup(
                    player.getUUID(), payload.claimName(), ClaimPolicy.hasAdminBypass(player));
            if (deleted) {
                if (deletedClaim != null && SsuModuleAccess.active("visualization")) {
                    SimpleServerUtilities.BORDER_VISUALIZATIONS.hideClaim(player, deletedClaim);
                }
                sendMap(player, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), "",
                        "Deleted claim '" + payload.claimName() + "'.", false);
            } else {
                sendMap(player, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), payload.claimName(),
                        "Claim could not be deleted.", true);
            }
            return;
        }

        List<ChunkPos> chunks = new ArrayList<>(payload.chunks().size());
        for (ClaimMapActionPayload.ChunkCoordinate chunk : payload.chunks()) {
            if (Math.abs(chunk.x() - payload.centerChunkX()) > payload.radius()
                    || Math.abs(chunk.z() - payload.centerChunkZ()) > payload.radius()) {
                sendMap(player, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(),
                        payload.claimName(), "Map operation rejected: selection is outside the visible map.", true);
                return;
            }
            chunks.add(new ChunkPos(chunk.x(), chunk.z()));
        }

        ClaimMapBatchResult result = SimpleServerUtilities.PLAYER_CLAIMS.applyMapOperation(
                player,
                payload.operation(),
                payload.claimName(),
                chunks
        );

        String selectedClaim = payload.claimName();
        String notice = formatResult(payload.operation(), selectedClaim, result);
        boolean error = !result.isSuccess();

        if (result.isSuccess() && SsuModuleAccess.active("visualization")) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshShownClaim(player);
        }

        sendMap(
                player,
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius(),
                selectedClaim,
                notice,
                error
        );
    }

    public static void handleTaxDelete(ClaimTaxDeleteActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("claims")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ClaimPolicy.canUseMap(player) || !ClaimPolicy.canDeleteClaim(player)) return;
        if (!isSafeViewport(player, payload.centerChunkX(), payload.centerChunkZ(), payload.claimName(), payload.radius())) {
            sendMap(player, player.chunkPosition().x, player.chunkPosition().z, payload.radius(), payload.claimName(),
                    "Tax settlement rejected: view is too far from your claim.", true);
            return;
        }
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), payload.claimName());
        if (claim == null) {
            sendMap(player, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(), "",
                    "Claim no longer exists.", true);
            return;
        }
        var mode = payload.mode() == ClaimTaxDeleteActionPayload.Mode.FORFEIT_AND_DELETE
                ? be.winnetrie.mod.simpleserverutilities.claim.tax.PlayerClaimTaxManager.VoluntaryDeleteMode.FORFEIT_AND_DELETE
                : be.winnetrie.mod.simpleserverutilities.claim.tax.PlayerClaimTaxManager.VoluntaryDeleteMode.PAY_AND_DELETE;
        var result = SimpleServerUtilities.CLAIM_TAX.settleVoluntaryDeletion(player, payload.claimName(), mode);
        PlayerClaim remaining = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), payload.claimName());
        if (result.successful() && remaining == null && SsuModuleAccess.active("visualization")) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.hideClaim(player, claim);
        }
        sendMap(player, payload.centerChunkX(), payload.centerChunkZ(), payload.radius(),
                remaining == null ? "" : payload.claimName(), result.message(), !result.successful());
    }

    public static void open(ServerPlayer player, String selectedClaimGroup) {
        sendMap(
                player,
                player.chunkPosition().x,
                player.chunkPosition().z,
                5,
                selectedClaimGroup,
                "",
                false
        );
    }

    private static void sendMap(
            ServerPlayer player,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            String selectedClaimGroup,
            String notice,
            boolean error
    ) {
        boolean ownedClaimViewport = isOwnedClaimViewport(
                player, centerChunkX, centerChunkZ, selectedClaimGroup, radius);
        ChunkPos playerChunk = player.chunkPosition();
        int safeCenterX = ownedClaimViewport ? centerChunkX : Math.max(playerChunk.x - MAX_CENTER_DISTANCE,
                Math.min(playerChunk.x + MAX_CENTER_DISTANCE, centerChunkX));
        int safeCenterZ = ownedClaimViewport ? centerChunkZ : Math.max(playerChunk.z - MAX_CENTER_DISTANCE,
                Math.min(playerChunk.z + MAX_CENTER_DISTANCE, centerChunkZ));

        ClaimMapData data = SimpleServerUtilities.PLAYER_CLAIMS.getMapData(
                player,
                safeCenterX,
                safeCenterZ,
                radius,
                selectedClaimGroup,
                notice,
                error,
                !ownedClaimViewport
        );
        PacketDistributor.sendToPlayer(player, ClaimMapDataPayload.from(data));
    }

    private static boolean isSafeViewport(ServerPlayer player, int centerChunkX, int centerChunkZ,
            String selectedClaimGroup, int radius) {
        ChunkPos playerChunk = player.chunkPosition();
        return (Math.abs(centerChunkX - playerChunk.x) <= MAX_CENTER_DISTANCE
                && Math.abs(centerChunkZ - playerChunk.z) <= MAX_CENTER_DISTANCE)
                || isOwnedClaimViewport(player, centerChunkX, centerChunkZ, selectedClaimGroup, radius);
    }

    private static boolean isOwnedClaimViewport(ServerPlayer player, int centerChunkX, int centerChunkZ,
            String selectedClaimGroup, int radius) {
        if (selectedClaimGroup == null || selectedClaimGroup.isBlank()) {
            return false;
        }
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(
                player.getUUID(), selectedClaimGroup);
        if (claim == null || claim.getChunks().isEmpty()
                || !claim.getDimension().equals(player.level().dimension().location().toString())) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (var chunk : claim.getChunks()) {
            minX = Math.min(minX, chunk.getX());
            maxX = Math.max(maxX, chunk.getX());
            minZ = Math.min(minZ, chunk.getZ());
            maxZ = Math.max(maxZ, chunk.getZ());
        }
        int margin = Math.max(2, Math.min(12, radius));
        return centerChunkX >= minX - margin && centerChunkX <= maxX + margin
                && centerChunkZ >= minZ - margin && centerChunkZ <= maxZ + margin;
    }

    private static ChunkPos claimCenter(PlayerClaim claim) {
        if (claim == null || claim.getChunks().isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (var chunk : claim.getChunks()) {
            minX = Math.min(minX, chunk.getX());
            maxX = Math.max(maxX, chunk.getX());
            minZ = Math.min(minZ, chunk.getZ());
            maxZ = Math.max(maxZ, chunk.getZ());
        }
        return new ChunkPos(minX + Math.floorDiv(maxX - minX, 2),
                minZ + Math.floorDiv(maxZ - minZ, 2));
    }

    private static String formatResult(
            ClaimMapOperation operation,
            String claimName,
            ClaimMapBatchResult batchResult
    ) {
        if (batchResult.isSuccess()) {
            return switch (operation) {
                case CREATE -> "Created claim '" + claimName + "' with " + batchResult.affectedChunks() + " chunk(s).";
                case ADD -> "Added " + batchResult.affectedChunks() + " chunk(s) to '" + claimName + "'.";
                case REMOVE -> "Removed " + batchResult.affectedChunks() + " chunk(s) from '" + claimName + "'.";
                case DELETE -> "Deleted claim '" + claimName + "'.";
            };
        }

        ClaimOperationResult result = batchResult.result();
        String details = result == null || result.getDetails().isBlank() ? "" : " " + result.getDetails();
        return switch (result == null ? ClaimOperationResult.Type.INVALID_SELECTION : result.getType()) {
            case PLAYER_CLAIMS_DISABLED -> "Player claims are disabled.";
            case CLAIM_GROUP_NOT_FOUND -> "Claim not found." + details;
            case CLAIM_GROUP_ALREADY_EXISTS -> "A claim with that name already exists." + details;
            case CLAIM_GROUP_LIMIT_REACHED -> "You reached the maximum number of claims." + details;
            case CLAIM_GROUP_CHUNK_LIMIT_REACHED -> "This claim reached its maximum size." + details;
            case WRONG_DIMENSION -> "This claim belongs to another dimension." + details;
            case CHUNK_ALREADY_CLAIMED -> "One of the selected chunks is already claimed." + details;
            case CHUNK_NOT_CLAIMED -> "One of the selected chunks is not part of this claim." + details;
            case CHUNK_LIMIT_REACHED -> "You reached the maximum number of claim chunks." + details;
            case CHUNK_NOT_ADJACENT -> "The final claim must be one connected area.";
            case CHUNK_REMOVAL_DISCONNECTS_CLAIM -> "That removal would split the claim into separate areas.";
            case CHUNK_OVERLAPS_REGION -> "A selected chunk overlaps a server region." + details;
            case EMPTY_SELECTION -> "Select at least one chunk.";
            case INVALID_CLAIM_NAME -> "Invalid claim name." + details;
            case CLAIM_DELETE_SETTLEMENT_REQUIRED -> "This claim must be settled before it can be deleted." + details;
            case TAX_SETTLEMENT_IN_PROGRESS -> "A claim-tax settlement is currently in progress." + details;
            case INVALID_SELECTION -> "The selected map operation is not allowed." + details;
            case NOT_OWNER -> "You are not the owner of this claim." + details;
            case SUCCESS -> "Operation completed.";
        };
    }
}
