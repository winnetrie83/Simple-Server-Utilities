package be.winnetrie.mod.simpleserverutilities.home;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle and service boundary for persistent player homes. */
public final class HomeModule implements SsuModule {

    private final PlayerHomeManager manager;

    public HomeModule(PlayerHomeManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "homes";
    }

    @Override public boolean isEnabled() { return Config.ENABLE_HOMES.get(); }

    @Override public Set<String> requiredDependencies() { return Set.of("storage"); }
    @Override public Set<String> optionalDependencies() { return Set.of("claims", "permissions", "teleport"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(PlayerHomeManager.class, manager);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        manager.load(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
        manager.clear();
    }
}
