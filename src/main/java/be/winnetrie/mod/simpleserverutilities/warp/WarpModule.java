package be.winnetrie.mod.simpleserverutilities.warp;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle and service boundary for persistent server warps. */
public final class WarpModule implements SsuModule {

    private final WarpManager manager;

    public WarpModule(WarpManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "warps";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("storage");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(WarpManager.class, manager);
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
