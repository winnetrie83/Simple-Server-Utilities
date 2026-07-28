package be.winnetrie.mod.simpleserverutilities.core.module;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/**
 * Lifecycle contract for future SSU modules. Existing systems remain active
 * through their legacy managers during the first Core 2.0 migration steps.
 */
public interface SsuModule {

    String id();

    default Set<String> dependencies() {
        return Set.of();
    }

    default boolean isEnabled() {
        return true;
    }

    default void initialize(SsuServiceRegistry services) {
    }

    default void onServerStarting(MinecraftServer server) {
    }

    default void onServerStopping(MinecraftServer server) {
    }
}
