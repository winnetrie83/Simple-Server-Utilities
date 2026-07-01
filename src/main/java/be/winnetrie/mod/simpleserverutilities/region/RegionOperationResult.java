package be.winnetrie.mod.simpleserverutilities.region;

public class RegionOperationResult {

    public enum Type {
        SUCCESS,
        NAME_ALREADY_EXISTS,
        REGION_NOT_FOUND,
        OVERLAPS_PLAYER_CLAIM,
        INVALID_REGION_OVERLAP
    }

    private final Type type;
    private final String details;

    private RegionOperationResult(Type type, String details) {
        this.type = type;
        this.details = details;
    }

    public static RegionOperationResult success() {
        return new RegionOperationResult(Type.SUCCESS, "");
    }

    public static RegionOperationResult fail(Type type, String details) {
        return new RegionOperationResult(type, details == null ? "" : details);
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