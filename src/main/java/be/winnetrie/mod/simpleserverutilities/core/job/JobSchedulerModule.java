package be.winnetrie.mod.simpleserverutilities.core.job;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for bounded multi-tick jobs and their resource locks. */
public final class JobSchedulerModule implements SsuModule {

    private final SsuJobScheduler scheduler;

    public JobSchedulerModule(SsuJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String id() {
        return "jobs";
    }
    @Override public Set<String> requiredDependencies() { return Set.of(); }
    @Override public boolean isCoreInfrastructure() { return true; }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(SsuJobScheduler.class, scheduler);
    }

    @Override
    public void beforeServerStopping(MinecraftServer server) {
        // Stop world mutation before module-owned managers write their final state.
        scheduler.clear();
    }
}
