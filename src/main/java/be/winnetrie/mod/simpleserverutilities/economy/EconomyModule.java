package be.winnetrie.mod.simpleserverutilities.economy;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class EconomyModule implements SsuModule {

    private final EconomyManager manager;

    public EconomyModule(EconomyManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("storage", "transactions");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(EconomyManager.class, manager);
        services.register(EconomyService.class, manager);
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
