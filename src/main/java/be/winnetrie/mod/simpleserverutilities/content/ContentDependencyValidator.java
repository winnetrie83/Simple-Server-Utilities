package be.winnetrie.mod.simpleserverutilities.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Detects duplicate IDs, missing references and dependency cycles across independent modules. */
public final class ContentDependencyValidator {

    public ContentValidationReport validate(Collection<ContentReferenceNode> rawNodes) {
        ArrayList<ContentValidationIssue> issues = new ArrayList<>();
        LinkedHashMap<String, ContentReferenceNode> nodes = new LinkedHashMap<>();
        if (rawNodes != null) {
            for (ContentReferenceNode node : rawNodes) {
                if (node == null) continue;
                ContentReferenceNode previous = nodes.putIfAbsent(node.id(), node);
                if (previous != null) {
                    issues.add(error("duplicate_id", node.id(),
                            "Content ID is already registered by module '" + previous.module() + "'."));
                }
            }
        }

        for (ContentReferenceNode node : nodes.values()) {
            for (String dependency : node.dependencies()) {
                if (!nodes.containsKey(dependency)) {
                    issues.add(error("missing_dependency", node.id(),
                            "Missing dependency '" + dependency + "'."));
                }
            }
        }

        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String id : nodes.keySet()) detectCycle(id, nodes, visiting, visited, new ArrayList<>(), issues);
        return new ContentValidationReport(issues);
    }

    private static void detectCycle(
            String id,
            Map<String, ContentReferenceNode> nodes,
            Set<String> visiting,
            Set<String> visited,
            ArrayList<String> path,
            ArrayList<ContentValidationIssue> issues
    ) {
        if (visited.contains(id) || !nodes.containsKey(id)) return;
        if (!visiting.add(id)) {
            int start = path.indexOf(id);
            String cycle = start >= 0 ? String.join(" -> ", path.subList(start, path.size())) + " -> " + id : id;
            issues.add(error("dependency_cycle", id, "Dependency cycle detected: " + cycle));
            return;
        }
        path.add(id);
        for (String dependency : nodes.get(id).dependencies()) {
            detectCycle(dependency, nodes, visiting, visited, path, issues);
        }
        path.remove(path.size() - 1);
        visiting.remove(id);
        visited.add(id);
    }

    private static ContentValidationIssue error(String code, String subject, String message) {
        return new ContentValidationIssue(ContentValidationIssue.Severity.ERROR, code, subject, message);
    }
}
