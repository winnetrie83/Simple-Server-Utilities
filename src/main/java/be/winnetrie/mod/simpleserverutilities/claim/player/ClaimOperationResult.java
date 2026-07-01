package be.winnetrie.mod.simpleserverutilities.claim.player;

public class ClaimOperationResult {

    public enum Type {
        SUCCESS,

        PLAYER_CLAIMS_DISABLED,

        CLAIM_GROUP_NOT_FOUND,
        CLAIM_GROUP_ALREADY_EXISTS,
        CLAIM_GROUP_LIMIT_REACHED,

        WRONG_DIMENSION,

        CHUNK_ALREADY_CLAIMED,
        CHUNK_NOT_CLAIMED,
        CHUNK_LIMIT_REACHED,
        CHUNK_NOT_ADJACENT,
        CHUNK_OVERLAPS_REGION,

        NOT_OWNER
    }

    private final Type type;
    private final String details;

    private ClaimOperationResult(Type type, String details) {
        this.type = type;
        this.details = details == null ? "" : details;
    }

    public static ClaimOperationResult success() {
        return new ClaimOperationResult(Type.SUCCESS, "");
    }

    public static ClaimOperationResult fail(Type type, String details) {
        return new ClaimOperationResult(type, details);
    }

    public Type getType() {
        return type;
    }

    public String getDetails() {
        return details;
    }

    public boolean isSuccess() {
        return type == Type.SUCCESS;
    }
}