package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle-owned claim subsystem for the incremental Core 2.0 migration. */
public final class ClaimModule implements SsuModule {

    private final PlayerClaimManager manager;

    public ClaimModule(PlayerClaimManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "claims";
    }

    @Override
    public boolean isEnabled() {
        return Config.ENABLE_PLAYER_CLAIMS.get();
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("storage");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(PlayerClaimManager.class, manager);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        ClaimPresenceEvents.clearRuntimeState();
        manager.load(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
        manager.clear();
        ClaimPresenceEvents.clearRuntimeState();
    }
}
