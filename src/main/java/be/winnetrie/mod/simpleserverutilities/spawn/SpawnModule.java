package be.winnetrie.mod.simpleserverutilities.spawn;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle and service boundary for the persistent server spawn. */
public final class SpawnModule implements SsuModule {

    private final ServerSpawnManager manager;

    public SpawnModule(ServerSpawnManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "spawn";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("storage", "permissions", "teleport");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(ServerSpawnManager.class, manager);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        manager.load(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
    }
}
