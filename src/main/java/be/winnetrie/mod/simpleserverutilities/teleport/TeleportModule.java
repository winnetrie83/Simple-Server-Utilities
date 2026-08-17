package be.winnetrie.mod.simpleserverutilities.teleport;

import be.winnetrie.mod.simpleserverutilities.Config;
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

    @Override public Set<String> requiredDependencies() { return Set.of(); }
    @Override public boolean isEnabled() { return Config.ENABLE_TELEPORT.get(); }
    @Override public Set<String> optionalDependencies() { return Set.of("claims", "regions", "permissions", "moderation"); }
    @Override public Set<String> integrationDependencies() { return Set.of("minigames"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(TeleportManager.class, manager);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.clear();
    }
}
