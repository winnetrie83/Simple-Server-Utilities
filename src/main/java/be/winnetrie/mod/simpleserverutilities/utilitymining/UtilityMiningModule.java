package be.winnetrie.mod.simpleserverutilities.utilitymining;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class UtilityMiningModule implements SsuModule {
    private final UtilityMiningManager manager;
    private final PlacedTreeBlockTracker placements;

    public UtilityMiningModule(UtilityMiningManager manager, PlacedTreeBlockTracker placements) {
        this.manager = manager;
        this.placements = placements;
    }

    @Override public String id() { return "utility_mining"; }

    @Override public boolean isEnabled() { return (Config.ENABLE_TREECAPITATOR.get() || Config.ENABLE_VEINMINER.get()); }

    @Override public Set<String> requiredDependencies() { return Set.of("storage", "ui_preferences"); }
    @Override public Set<String> optionalDependencies() { return Set.of("claims", "regions", "permissions"); }

    @Override public void initialize(SsuServiceRegistry services) {
        services.register(UtilityMiningManager.class, manager);
        services.register(PlacedTreeBlockTracker.class, placements);
    }

    @Override public void onServerStarting(MinecraftServer server) {
        if (Config.ENABLE_TREECAPITATOR.get()) placements.load(server);
        else placements.clear();
    }

    @Override public void onServerStopping(MinecraftServer server) {
        manager.clearClients(server);
        manager.clear();
        placements.save();
        placements.clear();
    }
}
