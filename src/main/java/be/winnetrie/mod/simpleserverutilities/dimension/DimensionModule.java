package be.winnetrie.mod.simpleserverutilities.dimension;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class DimensionModule implements SsuModule {
    private final ManagedDimensionManager manager;

    public DimensionModule(ManagedDimensionManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "dimensions";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("storage", "permissions");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(ManagedDimensionManager.class, manager);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        manager.load(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.clear();
    }
}
