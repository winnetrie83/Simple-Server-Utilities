package be.winnetrie.mod.simpleserverutilities.content;

public record ContentValidationIssue(Severity severity, String code, String subject, String message) {
    public ContentValidationIssue {
        severity = severity == null ? Severity.ERROR : severity;
        code = ContentId.normalize(code);
        subject = ContentId.normalize(subject);
        message = message == null ? "" : message;
    }

    public enum Severity {
        WARNING,
        ERROR
    }
}
