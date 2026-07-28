package be.winnetrie.mod.simpleserverutilities.claim.map;

import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimOperationResult;

public record ClaimMapBatchResult(ClaimOperationResult result, int affectedChunks) {

    public static ClaimMapBatchResult success(int affectedChunks) {
        return new ClaimMapBatchResult(ClaimOperationResult.success(), Math.max(0, affectedChunks));
    }

    public static ClaimMapBatchResult failure(ClaimOperationResult result) {
        return new ClaimMapBatchResult(result, 0);
    }

    public boolean isSuccess() {
        return result != null && result.isSuccess();
    }
}
