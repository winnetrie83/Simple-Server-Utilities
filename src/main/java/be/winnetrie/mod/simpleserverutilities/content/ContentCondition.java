package be.winnetrie.mod.simpleserverutilities.content;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Flexible data-driven condition node used by every future content module. */
public final class ContentCondition {
    public static final int MAX_DEPTH = 32;
    public static final int MAX_NODES = 2_048;

    private String type = "always";
    private Map<String, String> parameters = new LinkedHashMap<>();
    private List<ContentCondition> children = new ArrayList<>();

    public ContentCondition() {
    }

    public ContentCondition(String type, Map<String, String> parameters, List<ContentCondition> children) {
        this.type = type;
        this.parameters = parameters;
        this.children = children;
        normalize();
    }

    public ContentCondition normalize() {
        return normalize(0, new int[] {0});
    }

    private ContentCondition normalize(int depth, int[] nodeCount) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Condition tree exceeds maximum depth " + MAX_DEPTH + ".");
        }
        if (++nodeCount[0] > MAX_NODES) {
            throw new IllegalArgumentException("Condition tree exceeds maximum size " + MAX_NODES + ".");
        }
        type = ContentId.require(type, "Condition type");
        parameters = ContentDataMap.normalize(parameters, 64, 512);
        ArrayList<ContentCondition> normalizedChildren = new ArrayList<>();
        if (children != null) {
            for (ContentCondition child : children) {
                if (child == null) continue;
                normalizedChildren.add(child.normalize(depth + 1, nodeCount));
                if (normalizedChildren.size() >= 128) break;
            }
        }
        children = normalizedChildren;
        return this;
    }

    public String type() { return type; }
    public Map<String, String> parameters() { return Map.copyOf(parameters); }
    public List<ContentCondition> children() { return List.copyOf(children); }

    public String parameter(String key) {
        return parameters.getOrDefault(ContentId.normalize(key), "");
    }
}
