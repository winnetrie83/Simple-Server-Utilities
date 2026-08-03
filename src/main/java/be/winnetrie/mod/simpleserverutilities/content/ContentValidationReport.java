package be.winnetrie.mod.simpleserverutilities.content;

import java.util.List;

public record ContentValidationReport(List<ContentValidationIssue> issues) {
    public ContentValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ContentValidationIssue.Severity.ERROR);
    }

    public long errorCount() {
        return issues.stream().filter(issue -> issue.severity() == ContentValidationIssue.Severity.ERROR).count();
    }

    public long warningCount() {
        return issues.stream().filter(issue -> issue.severity() == ContentValidationIssue.Severity.WARNING).count();
    }
}
