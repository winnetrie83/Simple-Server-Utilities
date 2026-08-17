package be.winnetrie.mod.simpleserverutilities.core.storage;

import java.time.Duration;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Owns the shared asynchronous storage worker for the full server lifecycle. */
public final class StorageModule implements SsuModule {

    private final BatchedStorageService storage;

    public StorageModule(BatchedStorageService storage) {
        this.storage = storage;
    }

    @Override
    public String id() {
        return "storage";
    }
    @Override public Set<String> requiredDependencies() { return Set.of(); }
    @Override public boolean isCoreInfrastructure() { return true; }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(BatchedStorageService.class, storage);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        storage.start();
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        storage.stop(Duration.ofSeconds(10));
    }
}
