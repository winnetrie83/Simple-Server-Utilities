package be.winnetrie.mod.simpleserverutilities.content;

import java.util.LinkedHashSet;
import java.util.Set;

/** Minimal cross-module descriptor used before definitions are accepted or reloaded. */
public record ContentReferenceNode(
        String id,
        String module,
        Set<String> dependencies
) {
    public ContentReferenceNode {
        id = ContentId.require(id, "Content reference ID");
        module = ContentId.require(module, "Content module");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (dependencies != null) {
            for (String dependency : dependencies) {
                String value = ContentId.normalize(dependency);
                if (!value.isBlank()) normalized.add(value);
            }
        }
        dependencies = Set.copyOf(normalized);
    }
}
