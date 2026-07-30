package be.winnetrie.mod.simpleserverutilities.core.performance;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for the shared in-memory performance counters. */
public final class PerformanceModule implements SsuModule {

    private final SsuPerformanceMonitor monitor;

    public PerformanceModule(SsuPerformanceMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public String id() {
        return "performance";
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(SsuPerformanceMonitor.class, monitor);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        monitor.reset();
    }
}
