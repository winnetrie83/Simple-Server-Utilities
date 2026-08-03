package be.winnetrie.mod.simpleserverutilities.core.module;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/**
 * Lifecycle and dependency adapter for one SSU subsystem.
 *
 * <p>The canonical subsystem state is owned by the manager instance registered
 * in {@code SimpleServerUtilities}. Module objects do not duplicate that state;
 * they order startup/shutdown and expose the same manager through the service
 * registry. This is the settled Core 2.0 ownership model.</p>
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

    /** Called before any module performs its final persistence. */
    default void beforeServerStopping(MinecraftServer server) {
    }

    default void onServerStopping(MinecraftServer server) {
    }
}
