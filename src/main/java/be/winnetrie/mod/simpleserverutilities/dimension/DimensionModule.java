package be.winnetrie.mod.simpleserverutilities.dimension;

import be.winnetrie.mod.simpleserverutilities.Config;
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

    @Override public Set<String> requiredDependencies() { return Set.of("storage"); }
    @Override public boolean isEnabled() { return Config.ENABLE_DIMENSIONS.get(); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions", "server_operations"); }

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
