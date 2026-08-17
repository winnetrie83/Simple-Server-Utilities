package be.winnetrie.mod.simpleserverutilities.core.module;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/**
 * Lifecycle and dependency adapter for one SSU subsystem.
 *
 * <p>Dependencies deliberately distinguish between hard lifecycle requirements
 * and optional/integration links. A feature must only use a required dependency
 * when it genuinely cannot function without it. Optional and integration
 * dependencies never switch the module off; consumers are expected to degrade
 * gracefully while the linked module is unavailable.</p>
 */
public interface SsuModule {

    String id();

    /**
     * Legacy hard-dependency declaration kept as a compatibility bridge. New
     * modules should override {@link #requiredDependencies()} instead.
     */
    @Deprecated
    default Set<String> dependencies() {
        return Set.of();
    }

    /** Modules that must be active before this module may be active. */
    default Set<String> requiredDependencies() {
        return dependencies();
    }

    /**
     * Modules whose services improve this module, but whose absence must not
     * disable the module itself.
     */
    default Set<String> optionalDependencies() {
        return Set.of();
    }

    /**
     * Pure cross-feature bridges. These are diagnostic metadata and never take
     * part in lifecycle ordering or activation.
     */
    default Set<String> integrationDependencies() {
        return Set.of();
    }

    /**
     * Core infrastructure cannot be switched off by ordinary feature settings.
     * It may still declare hard dependencies on other core infrastructure for
     * deterministic startup/shutdown ordering.
     */
    default boolean isCoreInfrastructure() {
        return false;
    }

    /** Administrator-requested state. Required dependencies decide effective state. */
    default boolean isEnabled() {
        return true;
    }

    default void initialize(SsuServiceRegistry services) {
    }

    default void onServerStarting(MinecraftServer server) {
    }

    /**
     * Called after a runtime activation refresh whenever the effective active
     * module set changed. Useful for optional bridges such as Regions <-> Economy.
     */
    default void onDependencyStateChanged(MinecraftServer server) {
    }

    /** Called before any module performs its final persistence. */
    default void beforeServerStopping(MinecraftServer server) {
    }

    default void onServerStopping(MinecraftServer server) {
    }
}
