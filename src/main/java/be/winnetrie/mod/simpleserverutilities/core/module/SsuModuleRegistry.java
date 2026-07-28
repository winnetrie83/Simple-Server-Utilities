package be.winnetrie.mod.simpleserverutilities.core.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/**
 * Deterministic module registry with dependency validation and lifecycle
 * ordering. It is intentionally independent of the current legacy managers so
 * modules can be migrated one at a time without changing save formats.
 */
public final class SsuModuleRegistry {

    private final Map<String, SsuModule> modules = new LinkedHashMap<>();
    private final List<SsuModule> startOrder = new ArrayList<>();
    private boolean initialized;

    public synchronized void register(SsuModule module) {
        Objects.requireNonNull(module, "module");
        String id = normalizeId(module.id());

        if (initialized) {
            throw new IllegalStateException("Cannot register SSU module after initialization: " + id);
        }

        SsuModule previous = modules.putIfAbsent(id, module);
        if (previous != null) {
            throw new IllegalStateException("Duplicate SSU module id: " + id);
        }
    }

    public synchronized void initialize(SsuServiceRegistry services) {
        if (initialized) {
            return;
        }

        startOrder.clear();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();

        for (String moduleId : modules.keySet()) {
            visit(moduleId, visiting, visited);
        }

        for (SsuModule module : startOrder) {
            if (module.isEnabled()) {
                module.initialize(services);
            }
        }

        initialized = true;
    }

    public synchronized void onServerStarting(MinecraftServer server) {
        ensureInitialized();
        for (SsuModule module : startOrder) {
            if (module.isEnabled()) {
                module.onServerStarting(server);
            }
        }
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        ensureInitialized();
        for (int i = startOrder.size() - 1; i >= 0; i--) {
            SsuModule module = startOrder.get(i);
            if (module.isEnabled()) {
                module.onServerStopping(server);
            }
        }
    }

    public synchronized Collection<SsuModule> modules() {
        return Collections.unmodifiableCollection(new ArrayList<>(modules.values()));
    }

    private void visit(String moduleId, Set<String> visiting, Set<String> visited) {
        if (visited.contains(moduleId)) {
            return;
        }
        if (!visiting.add(moduleId)) {
            throw new IllegalStateException("Cyclic SSU module dependency involving: " + moduleId);
        }

        SsuModule module = modules.get(moduleId);
        if (module == null) {
            throw new IllegalStateException("Unknown SSU module dependency: " + moduleId);
        }

        for (String rawDependency : module.dependencies()) {
            String dependency = normalizeId(rawDependency);
            if (!modules.containsKey(dependency)) {
                throw new IllegalStateException(
                        "SSU module '" + moduleId + "' depends on missing module '" + dependency + "'"
                );
            }
            visit(dependency, visiting, visited);
        }

        visiting.remove(moduleId);
        visited.add(moduleId);
        startOrder.add(module);
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("SSU module registry has not been initialized");
        }
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("SSU module id cannot be blank");
        }
        return id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
