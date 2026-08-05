package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.claim.tax.PlayerClaimTaxManager;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle-owned claim subsystem for the incremental Core 2.0 migration. */
public final class ClaimModule implements SsuModule {

    private final PlayerClaimManager manager;
    private final PlayerClaimTaxManager taxManager;

    public ClaimModule(PlayerClaimManager manager, PlayerClaimTaxManager taxManager) {
        this.manager = manager;
        this.taxManager = taxManager;
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
        services.register(PlayerClaimTaxManager.class, taxManager);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        ClaimPresenceEvents.clearRuntimeState();
        manager.load(server);
        taxManager.load(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        taxManager.save();
        manager.save();
        taxManager.clear();
        manager.clear();
        ClaimPresenceEvents.clearRuntimeState();
    }
}
