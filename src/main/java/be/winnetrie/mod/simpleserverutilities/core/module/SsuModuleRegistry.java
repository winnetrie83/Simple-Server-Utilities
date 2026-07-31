package be.winnetrie.mod.simpleserverutilities.core.module;

import java.time.Duration;
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
import be.winnetrie.mod.simpleserverutilities.core.storage.BatchedStorageService;
import net.minecraft.server.MinecraftServer;

/**
 * Deterministic module registry with dependency validation, lifecycle ordering
 * and runtime activation refreshes. Disabled modules do not load persistent
 * data. They can be activated later from the admin dashboard without requiring
 * a server restart.
 */
public final class SsuModuleRegistry {

    private final Map<String, SsuModule> modules = new LinkedHashMap<>();
    private final List<SsuModule> startOrder = new ArrayList<>();
    private final Set<String> initializedModules = new LinkedHashSet<>();
    private final Set<String> activeModules = new LinkedHashSet<>();
    private SsuServiceRegistry services;
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

    /** Resolves dependencies only. Actual module initialization happens when enabled. */
    public synchronized void initialize(SsuServiceRegistry services) {
        if (initialized) return;
        this.services = Objects.requireNonNull(services, "services");

        startOrder.clear();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String moduleId : modules.keySet()) visit(moduleId, visiting, visited);
        initialized = true;
    }

    public synchronized void onServerStarting(MinecraftServer server) {
        ensureInitialized();
        refreshEnabledState(server);
    }

    /**
     * Applies current module switches. Newly enabled modules initialize and load;
     * newly disabled modules save and release their runtime state in reverse order.
     */
    public synchronized void refreshEnabledState(MinecraftServer server) {
        ensureInitialized();
        Objects.requireNonNull(server, "server");

        boolean stoppedAny = false;
        for (int i = startOrder.size() - 1; i >= 0; i--) {
            SsuModule module = startOrder.get(i);
            String id = normalizeId(module.id());
            if (activeModules.contains(id) && !module.isEnabled()) {
                module.beforeServerStopping(server);
                module.onServerStopping(server);
                activeModules.remove(id);
                stoppedAny = true;
            }
        }

        if (stoppedAny) {
            services.find(BatchedStorageService.class)
                    .ifPresent(storage -> storage.flush(Duration.ofSeconds(5)));
        }

        List<SsuModule> toStart = new ArrayList<>();
        for (SsuModule module : startOrder) {
            String id = normalizeId(module.id());
            if (!module.isEnabled() || activeModules.contains(id)) continue;
            if (!initializedModules.contains(id)) {
                module.initialize(services);
                initializedModules.add(id);
            }
            toStart.add(module);
        }
        for (SsuModule module : toStart) {
            module.onServerStarting(server);
            activeModules.add(normalizeId(module.id()));
        }
    }

    public synchronized boolean isActive(String rawId) {
        return activeModules.contains(normalizeId(rawId));
    }

    public synchronized void beforeServerStopping(MinecraftServer server) {
        ensureInitialized();
        for (SsuModule module : startOrder) {
            if (activeModules.contains(normalizeId(module.id()))) module.beforeServerStopping(server);
        }
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        ensureInitialized();
        for (int i = startOrder.size() - 1; i >= 0; i--) {
            SsuModule module = startOrder.get(i);
            String id = normalizeId(module.id());
            if (activeModules.remove(id)) module.onServerStopping(server);
        }
    }

    public synchronized Collection<SsuModule> modules() {
        return Collections.unmodifiableCollection(new ArrayList<>(modules.values()));
    }

    private void visit(String moduleId, Set<String> visiting, Set<String> visited) {
        if (visited.contains(moduleId)) return;
        if (!visiting.add(moduleId)) throw new IllegalStateException("Cyclic SSU module dependency involving: " + moduleId);

        SsuModule module = modules.get(moduleId);
        if (module == null) throw new IllegalStateException("Unknown SSU module dependency: " + moduleId);

        for (String rawDependency : module.dependencies()) {
            String dependency = normalizeId(rawDependency);
            if (!modules.containsKey(dependency)) {
                throw new IllegalStateException("SSU module '" + moduleId + "' depends on missing module '" + dependency + "'");
            }
            visit(dependency, visiting, visited);
        }

        visiting.remove(moduleId);
        visited.add(moduleId);
        startOrder.add(module);
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("SSU module registry has not been initialized");
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("SSU module id cannot be blank");
        return id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
