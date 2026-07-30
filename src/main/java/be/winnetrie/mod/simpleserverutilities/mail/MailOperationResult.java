package be.winnetrie.mod.simpleserverutilities.mail;

public record MailOperationResult(boolean successful, String code, String message) {
    public static MailOperationResult success(String message) {
        return new MailOperationResult(true, "success", message == null ? "" : message);
    }

    public static MailOperationResult failure(String code, String message) {
        return new MailOperationResult(false, code == null ? "failed" : code, message == null ? "Mail operation failed." : message);
    }
}
