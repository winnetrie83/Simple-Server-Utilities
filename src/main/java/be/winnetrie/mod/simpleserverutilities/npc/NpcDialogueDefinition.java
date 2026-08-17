package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Reusable graph-based NPC dialogue stored independently from NPC placements. */
public final class NpcDialogueDefinition {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_NODES = 64;
    public static final int MAX_ID_LENGTH = 64;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "dialogue";
    public String displayName = "Dialogue";
    public String startNode = "start";
    public boolean enabled = true;
    public List<NpcDialogueNode> nodes = new ArrayList<>();

    public NpcDialogueDefinition normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = requireId(id, "Dialogue ID");
        displayName = limit(displayName == null || displayName.isBlank() ? id : displayName.trim(), 96);
        startNode = requireId(startNode, "Dialogue start node");
        if (nodes != null && nodes.size() > MAX_NODES) {
            throw new IllegalArgumentException("Dialogue exceeds " + MAX_NODES + " nodes.");
        }
        LinkedHashMap<String, NpcDialogueNode> unique = new LinkedHashMap<>();
        if (nodes != null) {
            for (NpcDialogueNode node : nodes) {
                if (node == null) continue;
                node.normalize();
                if (unique.putIfAbsent(node.id, node) != null) {
                    throw new IllegalArgumentException("Duplicate dialogue node ID: " + node.id);
                }
            }
        }
        if (unique.isEmpty()) {
            NpcDialogueNode node = new NpcDialogueNode();
            node.id = "start";
            unique.put(node.id, node.normalize());
        }
        if (!unique.containsKey(startNode)) {
            throw new IllegalArgumentException("Dialogue start node does not exist: " + startNode);
        }
        for (NpcDialogueNode node : unique.values()) {
            if (!node.fallbackNode.isBlank()) {
                if (!unique.containsKey(node.fallbackNode)) {
                    throw new IllegalArgumentException("Node '" + node.id + "' references missing fallback node '" + node.fallbackNode + "'.");
                }
                if (node.id.equals(node.fallbackNode)) {
                    throw new IllegalArgumentException("Node '" + node.id + "' cannot fall back to itself.");
                }
            }
            for (NpcDialogueChoice choice : node.choices) {
                if (!choice.nextNode.isBlank() && !unique.containsKey(choice.nextNode)) {
                    throw new IllegalArgumentException("Choice '" + choice.id + "' references missing node '" + choice.nextNode + "'.");
                }
            }
        }
        validateFallbackCycles(unique);
        nodes = new ArrayList<>(unique.values());
        return this;
    }

    public NpcDialogueNode node(String rawId) {
        String wanted = ContentId.normalize(rawId);
        for (NpcDialogueNode node : nodes) if (node.id.equals(wanted)) return node;
        return null;
    }

    public NpcDialogueDefinition copy() {
        NpcDialogueDefinition copy = new NpcDialogueDefinition();
        copy.schemaVersion = schemaVersion;
        copy.id = id;
        copy.displayName = displayName;
        copy.startNode = startNode;
        copy.enabled = enabled;
        copy.nodes = nodes.stream().map(NpcDialogueNode::copy).toList();
        return copy;
    }

    public static NpcDialogueDefinition simple(String rawId, String npcName, String text) {
        NpcDialogueDefinition value = new NpcDialogueDefinition();
        value.id = requireId(rawId, "Dialogue ID");
        value.displayName = (npcName == null || npcName.isBlank() ? value.id : npcName) + " dialogue";
        NpcDialogueNode start = new NpcDialogueNode();
        start.id = "start";
        start.speaker = npcName == null ? "" : npcName;
        start.text = text == null || text.isBlank() ? "Hello." : text;
        NpcDialogueChoice close = new NpcDialogueChoice();
        close.id = "goodbye";
        close.text = "Goodbye";
        close.closeDialogue = true;
        start.choices.add(close);
        value.nodes.add(start);
        return value.normalize();
    }

    public Map<String, NpcDialogueNode> nodeMap() {
        LinkedHashMap<String, NpcDialogueNode> result = new LinkedHashMap<>();
        for (NpcDialogueNode node : nodes) result.put(node.id, node);
        return Map.copyOf(result);
    }


    private static void validateFallbackCycles(Map<String, NpcDialogueNode> nodes) {
        for (NpcDialogueNode start : nodes.values()) {
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            NpcDialogueNode current = start;
            while (current != null && !current.fallbackNode.isBlank()) {
                if (!seen.add(current.id)) {
                    throw new IllegalArgumentException("Dialogue fallback cycle detected at node '" + current.id + "'.");
                }
                current = nodes.get(current.fallbackNode);
            }
        }
    }

    static String requireId(String raw, String label) {
        String value = ContentId.require(raw, label);
        if (value.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(label + " exceeds " + MAX_ID_LENGTH + " characters.");
        }
        return value;
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
