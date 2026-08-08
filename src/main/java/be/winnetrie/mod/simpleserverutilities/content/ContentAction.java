package be.winnetrie.mod.simpleserverutilities.content;

import java.util.LinkedHashMap;
import java.util.Map;

/** Flexible data-driven action or reward node. */
public final class ContentAction {
    private String type = "";
    private Map<String, String> parameters = new LinkedHashMap<>();

    public ContentAction() {
    }

    public ContentAction(String type, Map<String, String> parameters) {
        this.type = type;
        this.parameters = parameters;
        normalize();
    }

    public ContentAction normalize() {
        type = ContentId.require(type, "Action type");
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (normalized.size() >= 64 || entry.getKey() == null) break;
                String key = ContentId.normalize(entry.getKey());
                if (key.isBlank()) continue;
                String value = entry.getValue() == null ? "" : entry.getValue().trim();
                int maximum = "stack_json".equals(key) ? 8192 : 512;
                if (value.length() > maximum) value = value.substring(0, maximum);
                normalized.put(key, value);
            }
        }
        parameters = normalized;
        return this;
    }

    public String type() { return type; }
    public Map<String, String> parameters() { return Map.copyOf(parameters); }
    public String parameter(String key) { return parameters.getOrDefault(ContentId.normalize(key), ""); }
}
