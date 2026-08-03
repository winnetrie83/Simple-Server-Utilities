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
        parameters = ContentDataMap.normalize(parameters, 64, 512);
        return this;
    }

    public String type() { return type; }
    public Map<String, String> parameters() { return Map.copyOf(parameters); }
    public String parameter(String key) { return parameters.getOrDefault(ContentId.normalize(key), ""); }
}
