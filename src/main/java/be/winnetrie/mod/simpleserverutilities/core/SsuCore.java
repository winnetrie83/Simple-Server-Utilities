package be.winnetrie.mod.simpleserverutilities.core;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleRegistry;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/**
 * Shared foundation for the gradual Core 2.0 migration.
 */
public final class SsuCore {

    private final SsuServiceRegistry services = new SsuServiceRegistry();
    private final SsuModuleRegistry modules = new SsuModuleRegistry();

    public SsuServiceRegistry services() {
        return services;
    }

    public SsuModuleRegistry modules() {
        return modules;
    }

    public void initialize() {
        modules.initialize(services);
    }

    public void onServerStarting(MinecraftServer server) {
        modules.onServerStarting(server);
    }

    public void onServerStopping(MinecraftServer server) {
        modules.onServerStopping(server);
    }
}
