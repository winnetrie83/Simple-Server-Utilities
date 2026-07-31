package be.winnetrie.mod.simpleserverutilities.statistics;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for custom player statistics. */
public final class StatisticsModule implements SsuModule {
    private final PlayerStatisticsManager manager;

    public StatisticsModule(PlayerStatisticsManager manager) {
        this.manager = manager;
    }

    @Override public String id() { return "statistics"; }
    @Override public Set<String> dependencies() { return Set.of("storage"); }
    @Override public boolean isEnabled() { return Config.ENABLE_CUSTOM_STATISTICS.get(); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(PlayerStatisticsManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { manager.saveAll(); }
    @Override public void onServerStopping(MinecraftServer server) { manager.clear(); }
}
