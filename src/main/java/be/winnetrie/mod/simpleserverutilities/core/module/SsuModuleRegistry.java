package be.winnetrie.mod.simpleserverutilities.core.module;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.core.storage.BatchedStorageService;
import net.minecraft.server.MinecraftServer;

/**
 * Deterministic module registry with dependency-safe runtime activation.
 *
 * <p>Configured state and effective state are deliberately separate. If an
 * administrator leaves Auction House enabled and disables Economy, Auction House
 * becomes effectively disabled with a dependency reason while its configured
 * preference remains enabled. Re-enabling Economy therefore restores Auction
 * House automatically.</p>
 */
public final class SsuModuleRegistry {

    private final Map<String, SsuModule> modules = new LinkedHashMap<>();
    private final List<SsuModule> startOrder = new ArrayList<>();
    private final Set<String> initializedModules = new LinkedHashSet<>();
    private final Set<String> activeModules = new LinkedHashSet<>();
    private final Map<String, String> disabledReasons = new LinkedHashMap<>();
    private final Map<String, Boolean> configuredStates = new LinkedHashMap<>();
    private SsuServiceRegistry services;
    private boolean initialized;

    public synchronized void register(SsuModule module) {
        Objects.requireNonNull(module, "module");
        String id = normalizeId(module.id());
        if (initialized) throw new IllegalStateException("Cannot register SSU module after initialization: " + id);
        SsuModule previous = modules.putIfAbsent(id, module);
        if (previous != null) throw new IllegalStateException("Duplicate SSU module id: " + id);
    }

    /** Resolves hard dependencies only. Actual module initialization happens when effectively enabled. */
    public synchronized void initialize(SsuServiceRegistry services) {
        if (initialized) return;
        this.services = Objects.requireNonNull(services, "services");

        validateDeclaredLinks();
        startOrder.clear();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String moduleId : modules.keySet()) visitRequired(moduleId, visiting, visited);
        initialized = true;

        // Do not evaluate module.isEnabled() here. Core initialization runs from
        // the mod constructor, before NeoForge has loaded the COMMON config.
        // Feature configured state is snapshotted on the first server/runtime
        // refresh, when config values are safe to read.
        configuredStates.clear();
        disabledReasons.clear();
        for (SsuModule module : startOrder) {
            String id = normalizeId(module.id());
            if (module.isCoreInfrastructure()) {
                configuredStates.put(id, true);
            }
            disabledReasons.put(id, "Waiting for server startup.");
        }
    }

    public synchronized void onServerStarting(MinecraftServer server) {
        ensureInitialized();
        refreshEnabledState(server);
    }

    /**
     * Applies current module switches and hard-dependency cascades. Newly blocked
     * modules stop in reverse dependency order; newly available modules start in
     * dependency order. Optional/integration links never block activation.
     */
    public synchronized void refreshEnabledState(MinecraftServer server) {
        ensureInitialized();
        Objects.requireNonNull(server, "server");

        snapshotConfiguredStates();
        LinkedHashSet<String> targetActive = computeTargetActive();
        LinkedHashSet<String> previousActive = new LinkedHashSet<>(activeModules);

        boolean stoppedAny = false;
        for (int i = startOrder.size() - 1; i >= 0; i--) {
            SsuModule module = startOrder.get(i);
            String id = normalizeId(module.id());
            if (activeModules.contains(id) && !targetActive.contains(id)) {
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

        for (SsuModule module : startOrder) {
            String id = normalizeId(module.id());
            if (!targetActive.contains(id) || activeModules.contains(id)) continue;
            if (!initializedModules.contains(id)) {
                module.initialize(services);
                initializedModules.add(id);
            }
            module.onServerStarting(server);
            activeModules.add(id);
        }

        recomputeDisabledReasons(targetActive);

        if (!previousActive.equals(activeModules)) {
            // Optional bridges are refreshed only after the complete effective set
            // is stable, so consumers never observe a half-transitioned graph.
            for (SsuModule module : startOrder) {
                if (activeModules.contains(normalizeId(module.id()))) {
                    module.onDependencyStateChanged(server);
                }
            }
        }
    }

    public synchronized boolean isActive(String rawId) {
        if (rawId == null || rawId.isBlank()) return false;
        return activeModules.contains(normalizeId(rawId));
    }

    public synchronized boolean isConfiguredEnabled(String rawId) {
        if (rawId == null || rawId.isBlank()) return false;
        String id = normalizeId(rawId);
        SsuModule module = modules.get(id);
        if (module == null) return false;
        if (module.isCoreInfrastructure()) return true;
        return configuredStates.getOrDefault(id, false);
    }

    public synchronized String disabledReason(String rawId) {
        if (rawId == null || rawId.isBlank()) return "Unknown module.";
        String id = normalizeId(rawId);
        if (activeModules.contains(id)) return "";
        return disabledReasons.getOrDefault(id, modules.containsKey(id) ? "Module is inactive." : "Unknown module.");
    }

    public synchronized List<ModuleState> states() {
        ArrayList<ModuleState> result = new ArrayList<>(modules.size());
        for (SsuModule module : modules.values()) {
            String id = normalizeId(module.id());
            result.add(new ModuleState(
                    id,
                    module.isCoreInfrastructure(),
                    configuredState(module),
                    activeModules.contains(id),
                    disabledReasons.getOrDefault(id, ""),
                    normalized(module.requiredDependencies()),
                    normalized(module.optionalDependencies()),
                    normalized(module.integrationDependencies())
            ));
        }
        return List.copyOf(result);
    }

    public synchronized Set<String> requiredDependents(String rawId) {
        String id = normalizeId(rawId);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (SsuModule module : modules.values()) {
                String candidate = normalizeId(module.id());
                if (candidate.equals(id) || result.contains(candidate)) continue;
                Set<String> required = normalized(module.requiredDependencies());
                if (required.contains(id) || required.stream().anyMatch(result::contains)) {
                    changed |= result.add(candidate);
                }
            }
        } while (changed);
        return Collections.unmodifiableSet(result);
    }

    public synchronized Collection<SsuModule> modules() {
        return Collections.unmodifiableCollection(new ArrayList<>(modules.values()));
    }

    public synchronized boolean isInitialized() {
        return initialized;
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
        recomputeDisabledReasons(Set.of());
    }

    private LinkedHashSet<String> computeTargetActive() {
        LinkedHashSet<String> target = new LinkedHashSet<>();
        disabledReasons.clear();
        for (SsuModule module : startOrder) {
            String id = normalizeId(module.id());
            if (!configuredState(module)) {
                disabledReasons.put(id, "Disabled by configuration.");
                continue;
            }
            String blocking = firstBlockingDependency(module, target);
            if (blocking != null) {
                disabledReasons.put(id, "Requires active module '" + blocking + "'.");
                continue;
            }
            target.add(id);
        }
        return target;
    }

    private void recomputeDisabledReasons(Set<String> effective) {
        disabledReasons.clear();
        LinkedHashSet<String> available = new LinkedHashSet<>();
        for (SsuModule module : startOrder) {
            String id = normalizeId(module.id());
            if (!configuredState(module)) {
                disabledReasons.put(id, "Disabled by configuration.");
                continue;
            }
            String blocking = firstBlockingDependency(module, available);
            if (blocking != null) {
                disabledReasons.put(id, "Requires active module '" + blocking + "'.");
                continue;
            }
            if (effective.contains(id)) available.add(id);
            else disabledReasons.put(id, "Module is not active yet.");
        }
    }

    private String firstBlockingDependency(SsuModule module, Set<String> available) {
        for (String dependency : normalized(module.requiredDependencies())) {
            if (!available.contains(dependency)) return dependency;
        }
        return null;
    }

    private void snapshotConfiguredStates() {
        configuredStates.clear();
        for (SsuModule module : startOrder) {
            String id = normalizeId(module.id());
            configuredStates.put(id, module.isCoreInfrastructure() || module.isEnabled());
        }
    }

    private boolean configuredState(SsuModule module) {
        String id = normalizeId(module.id());
        if (module.isCoreInfrastructure()) return true;
        return configuredStates.getOrDefault(id, false);
    }

    private void validateDeclaredLinks() {
        for (SsuModule module : modules.values()) {
            String id = normalizeId(module.id());
            validateLinks(id, "required", module.requiredDependencies());
            validateLinks(id, "optional", module.optionalDependencies());
            validateLinks(id, "integration", module.integrationDependencies());
        }
    }

    private void validateLinks(String id, String kind, Set<String> links) {
        for (String dependency : normalized(links)) {
            if (dependency.equals(id)) throw new IllegalStateException("SSU module '" + id + "' declares itself as a " + kind + " dependency");
            if (!modules.containsKey(dependency)) {
                throw new IllegalStateException("SSU module '" + id + "' declares missing " + kind + " module '" + dependency + "'");
            }
        }
    }

    private void visitRequired(String moduleId, Set<String> visiting, Set<String> visited) {
        if (visited.contains(moduleId)) return;
        if (!visiting.add(moduleId)) throw new IllegalStateException("Cyclic SSU required-module dependency involving: " + moduleId);

        SsuModule module = modules.get(moduleId);
        if (module == null) throw new IllegalStateException("Unknown SSU module dependency: " + moduleId);
        for (String dependency : normalized(module.requiredDependencies())) visitRequired(dependency, visiting, visited);

        visiting.remove(moduleId);
        visited.add(moduleId);
        startOrder.add(module);
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("SSU module registry has not been initialized");
    }

    private static Set<String> normalized(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) result.add(normalizeId(value));
        return Collections.unmodifiableSet(result);
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("SSU module id cannot be blank");
        return id.trim().toLowerCase(Locale.ROOT);
    }

    public record ModuleState(
            String id,
            boolean coreInfrastructure,
            boolean configuredEnabled,
            boolean active,
            String disabledReason,
            Set<String> requiredDependencies,
            Set<String> optionalDependencies,
            Set<String> integrationDependencies
    ) {
        public ModuleState {
            disabledReason = disabledReason == null ? "" : disabledReason;
            requiredDependencies = Set.copyOf(requiredDependencies == null ? Set.of() : requiredDependencies);
            optionalDependencies = Set.copyOf(optionalDependencies == null ? Set.of() : optionalDependencies);
            integrationDependencies = Set.copyOf(integrationDependencies == null ? Set.of() : integrationDependencies);
        }
    }
}
