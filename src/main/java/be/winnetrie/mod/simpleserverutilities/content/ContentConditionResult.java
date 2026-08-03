package be.winnetrie.mod.simpleserverutilities.content;

/** Evaluation result with an admin/debug-friendly explanation. */
public record ContentConditionResult(boolean matched, String reason) {
    public ContentConditionResult {
        reason = reason == null ? "" : reason;
    }

    public static ContentConditionResult allow(String reason) {
        return new ContentConditionResult(true, reason);
    }

    public static ContentConditionResult deny(String reason) {
        return new ContentConditionResult(false, reason);
    }
}
