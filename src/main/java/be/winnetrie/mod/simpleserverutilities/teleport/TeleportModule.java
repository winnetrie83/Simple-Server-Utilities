package be.winnetrie.mod.simpleserverutilities.teleport;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Explicit service ownership for teleport requests and cooldown state. */
public final class TeleportModule implements SsuModule {

    private final TeleportManager manager;

    public TeleportModule(TeleportManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "teleport";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("claims", "regions", "permissions");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(TeleportManager.class, manager);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.clear();
    }
}
