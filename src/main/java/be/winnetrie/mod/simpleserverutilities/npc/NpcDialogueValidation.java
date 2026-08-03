package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Side-effect-free graph/editor validation shared by the client preview and authoritative save path. */
public final class NpcDialogueValidation {
    private NpcDialogueValidation() {
    }

    public static Report validate(NpcDialogueDefinition dialogue,
                                  Collection<String> registeredConditions,
                                  Collection<String> registeredActions,
                                  Collection<String> registeredServices,
                                  Map<String, ? extends Collection<String>> serviceTargets) {
        ArrayList<Issue> issues = new ArrayList<>();
        if (dialogue == null) {
            issues.add(error("dialogue", "Dialogue data is missing."));
            return new Report(issues);
        }

        Set<String> conditionTypes = normalizedSet(registeredConditions);
        Set<String> actionTypes = normalizedSet(registeredActions);
        Set<String> services = normalizedSet(registeredServices);
        Map<String, Set<String>> targets = normalizeTargets(serviceTargets);

        String dialogueId = safe(dialogue.id);
        if (dialogueId.isBlank()) issues.add(error("dialogue", "Dialogue ID cannot be blank."));
        else if (!dialogueId.equals(ContentId.normalize(dialogueId))) {
            issues.add(warning("dialogue", "Dialogue ID will normalize to '" + ContentId.normalize(dialogueId) + "'."));
        }
        if (safe(dialogue.displayName).isBlank()) issues.add(warning("dialogue", "Dialogue display name is blank."));

        List<NpcDialogueNode> nodes = dialogue.nodes == null ? List.of() : dialogue.nodes;
        if (nodes.isEmpty()) {
            issues.add(error("dialogue", "A dialogue needs at least one node."));
            return new Report(issues);
        }
        if (nodes.size() > NpcDialogueDefinition.MAX_NODES) {
            issues.add(error("dialogue", "Dialogue exceeds " + NpcDialogueDefinition.MAX_NODES + " nodes."));
        }

        LinkedHashMap<String, NpcDialogueNode> byId = new LinkedHashMap<>();
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            NpcDialogueNode node = nodes.get(nodeIndex);
            String location = "node " + (nodeIndex + 1);
            if (node == null) {
                issues.add(error(location, "Node is missing."));
                continue;
            }
            String id = safe(node.id);
            if (id.isBlank()) {
                issues.add(error(location, "Node ID cannot be blank."));
                continue;
            }
            if (!id.equals(ContentId.normalize(id))) {
                issues.add(warning(location, "Node ID will normalize to '" + ContentId.normalize(id) + "'."));
            }
            if (byId.putIfAbsent(id, node) != null) issues.add(error(location, "Duplicate node ID '" + id + "'."));
        }

        String start = safe(dialogue.startNode);
        if (start.isBlank()) issues.add(error("dialogue", "Start node cannot be blank."));
        else if (!byId.containsKey(start)) issues.add(error("dialogue", "Start node '" + start + "' does not exist."));

        for (Map.Entry<String, NpcDialogueNode> entry : byId.entrySet()) {
            String nodeId = entry.getKey();
            NpcDialogueNode node = entry.getValue();
            String nodeLocation = "node '" + nodeId + "'";
            if (safe(node.text).isBlank()) issues.add(warning(nodeLocation, "Dialogue text is blank."));
            validateActions(node.enterActions, nodeLocation + " entry actions", actionTypes, issues);

            List<NpcDialogueChoice> choices = node.choices == null ? List.of() : node.choices;
            if (choices.isEmpty()) issues.add(warning(nodeLocation, "Node has no choices and will close without an explicit exit."));
            if (choices.size() > NpcDialogueNode.MAX_CHOICES) {
                issues.add(error(nodeLocation, "Node exceeds " + NpcDialogueNode.MAX_CHOICES + " choices."));
            }
            LinkedHashSet<String> choiceIds = new LinkedHashSet<>();
            boolean hasExit = false;
            for (int choiceIndex = 0; choiceIndex < choices.size(); choiceIndex++) {
                NpcDialogueChoice choice = choices.get(choiceIndex);
                String choiceLocation = nodeLocation + ", choice " + (choiceIndex + 1);
                if (choice == null) {
                    issues.add(error(choiceLocation, "Choice is missing."));
                    continue;
                }
                String choiceId = safe(choice.id);
                if (choiceId.isBlank()) issues.add(error(choiceLocation, "Choice ID cannot be blank."));
                else if (!choiceIds.add(choiceId)) issues.add(error(choiceLocation, "Duplicate choice ID '" + choiceId + "'."));
                if (safe(choice.text).isBlank()) issues.add(warning(choiceLocation, "Player-facing choice text is blank."));

                validateCondition(choice.condition, choiceLocation + " condition", conditionTypes, issues, 0, new int[] {0});
                validateActions(choice.actions, choiceLocation + " actions", actionTypes, issues);

                String next = safe(choice.nextNode);
                if (!next.isBlank() && !byId.containsKey(next)) {
                    issues.add(error(choiceLocation, "Next node '" + next + "' does not exist."));
                }
                String service = ContentId.normalize(choice.service);
                if (!service.isBlank() && !services.contains(service)) {
                    issues.add(error(choiceLocation, "Service '" + service + "' is not registered."));
                }
                String target = safe(choice.serviceTarget);
                Set<String> knownTargets = targets.getOrDefault(service, Set.of());
                if (requiresTarget(service) && target.isBlank()) {
                    issues.add(error(choiceLocation, "Service '" + service + "' requires a target."));
                } else if (!target.isBlank() && !knownTargets.isEmpty() && !knownTargets.contains(target)) {
                    issues.add(warning(choiceLocation, "Target '" + target + "' is not in the current " + service + " catalogue."));
                }
                if (next.isBlank() && service.isBlank() && !choice.closeDialogue) {
                    issues.add(warning(choiceLocation, "Choice has no next node, service or explicit close action."));
                }
                hasExit |= choice.closeDialogue || !service.isBlank();
            }
            if (!choices.isEmpty() && !hasExit && choices.stream().allMatch(choice -> choice != null && nodeId.equals(safe(choice.nextNode)))) {
                issues.add(warning(nodeLocation, "Every choice loops back to this node; the dialogue has no obvious exit."));
            }
        }

        if (byId.containsKey(start)) {
            Set<String> reachable = reachableNodes(start, byId);
            for (String nodeId : byId.keySet()) {
                if (!reachable.contains(nodeId)) issues.add(warning("node '" + nodeId + "'", "Node is unreachable from start node '" + start + "'."));
            }
            detectClosedCycles(start, byId, issues);
        }
        return new Report(issues);
    }

    public static boolean requiresTarget(String service) {
        return switch (ContentId.normalize(service)) {
            case "warp", "quest_offer", "quest_turn_in", "minigame_queue", "dungeon_queue" -> true;
            default -> false;
        };
    }

    private static void validateActions(List<ContentAction> actions, String location, Set<String> registered,
                                        List<Issue> issues) {
        List<ContentAction> safeActions = actions == null ? List.of() : actions;
        if (safeActions.size() > NpcDialogueChoice.MAX_ACTIONS) {
            issues.add(error(location, "Action list exceeds " + NpcDialogueChoice.MAX_ACTIONS + " entries."));
        }
        for (int index = 0; index < safeActions.size(); index++) {
            ContentAction action = safeActions.get(index);
            String actionLocation = location + " " + (index + 1);
            if (action == null) {
                issues.add(error(actionLocation, "Action is missing."));
                continue;
            }
            String type = ContentId.normalize(action.type());
            if (type.isBlank()) issues.add(error(actionLocation, "Action type cannot be blank."));
            else if (!registered.isEmpty() && !registered.contains(type)) {
                issues.add(error(actionLocation, "Action '" + type + "' is not registered."));
            }
        }
    }

    private static void validateCondition(ContentCondition condition, String location, Set<String> registered,
                                          List<Issue> issues, int depth, int[] count) {
        if (condition == null) {
            issues.add(error(location, "Condition is missing."));
            return;
        }
        if (depth > ContentCondition.MAX_DEPTH) {
            issues.add(error(location, "Condition tree exceeds maximum depth " + ContentCondition.MAX_DEPTH + "."));
            return;
        }
        if (++count[0] > ContentCondition.MAX_NODES) {
            issues.add(error(location, "Condition tree exceeds maximum size " + ContentCondition.MAX_NODES + "."));
            return;
        }
        String type = ContentId.normalize(condition.type());
        if (type.isBlank()) issues.add(error(location, "Condition type cannot be blank."));
        else if (!registered.isEmpty() && !registered.contains(type)) {
            issues.add(error(location, "Condition '" + type + "' is not registered."));
        }
        List<ContentCondition> children = condition.children();
        if ("not".equals(type) && children.size() != 1) {
            issues.add(error(location, "NOT requires exactly one child."));
        }
        if (!isComposite(type) && !children.isEmpty()) {
            issues.add(warning(location, "Only all/any/not evaluate child conditions; these children are ignored at runtime."));
        }
        for (int index = 0; index < children.size(); index++) {
            validateCondition(children.get(index), location + "." + (index + 1), registered, issues, depth + 1, count);
        }
    }

    private static Set<String> reachableNodes(String start, Map<String, NpcDialogueNode> nodes) {
        LinkedHashSet<String> reachable = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!reachable.add(id)) continue;
            NpcDialogueNode node = nodes.get(id);
            if (node == null || node.choices == null) continue;
            for (NpcDialogueChoice choice : node.choices) {
                if (choice == null) continue;
                String next = safe(choice.nextNode);
                if (!next.isBlank() && nodes.containsKey(next) && !reachable.contains(next)) pending.addLast(next);
            }
        }
        return reachable;
    }

    /** Warns about strongly connected paths that cannot reach a service/close/dead-end exit. */
    private static void detectClosedCycles(String start, Map<String, NpcDialogueNode> nodes, List<Issue> issues) {
        Set<String> canExit = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, NpcDialogueNode> entry : nodes.entrySet()) {
                if (canExit.contains(entry.getKey())) continue;
                NpcDialogueNode node = entry.getValue();
                List<NpcDialogueChoice> choices = node.choices == null ? List.of() : node.choices;
                if (choices.isEmpty()) {
                    canExit.add(entry.getKey());
                    changed = true;
                    continue;
                }
                for (NpcDialogueChoice choice : choices) {
                    if (choice == null) continue;
                    String next = safe(choice.nextNode);
                    if (choice.closeDialogue || !safe(choice.service).isBlank() || (!next.isBlank() && canExit.contains(next))) {
                        canExit.add(entry.getKey());
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        Set<String> reachable = reachableNodes(start, nodes);
        for (String id : reachable) {
            if (!canExit.contains(id)) issues.add(warning("node '" + id + "'", "This reachable branch is trapped in a graph cycle with no exit."));
        }
    }

    private static boolean isComposite(String type) {
        return "all".equals(type) || "any".equals(type) || "not".equals(type);
    }

    private static Set<String> normalizedSet(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) for (String value : values) {
            String normalized = ContentId.normalize(value);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private static Map<String, Set<String>> normalizeTargets(Map<String, ? extends Collection<String>> values) {
        LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        if (values != null) for (Map.Entry<String, ? extends Collection<String>> entry : values.entrySet()) {
            String service = ContentId.normalize(entry.getKey());
            if (service.isBlank()) continue;
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            if (entry.getValue() != null) for (String value : entry.getValue()) if (value != null && !value.isBlank()) ids.add(value.trim());
            result.put(service, Set.copyOf(ids));
        }
        return Map.copyOf(result);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static Issue error(String location, String message) {
        return new Issue(Severity.ERROR, location, message);
    }

    private static Issue warning(String location, String message) {
        return new Issue(Severity.WARNING, location, message);
    }

    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String location, String message) {
        public Issue {
            severity = severity == null ? Severity.ERROR : severity;
            location = location == null ? "" : location;
            message = message == null ? "" : message;
        }
    }

    public record Report(List<Issue> issues) {
        public Report {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public int errorCount() {
            return (int) issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        }

        public int warningCount() {
            return (int) issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count();
        }

        public boolean valid() {
            return errorCount() == 0;
        }

        public String summary() {
            if (issues.isEmpty()) return "No dialogue issues found.";
            return errorCount() + " error(s), " + warningCount() + " warning(s).";
        }
    }
}
