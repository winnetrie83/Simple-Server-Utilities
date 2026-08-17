package be.winnetrie.mod.simpleserverutilities.statistics.community;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for curated website/community activity statistics. */
public final class CommunityStatisticsModule implements SsuModule {
    private final CommunityStatisticsManager manager;

    public CommunityStatisticsModule(CommunityStatisticsManager manager) { this.manager = manager; }

    @Override public String id() { return "community_statistics"; }
    @Override public Set<String> requiredDependencies() { return Set.of("storage", "content_core"); }
    @Override public boolean isEnabled() { return Config.ENABLE_COMMUNITY_STATISTICS.get(); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(CommunityStatisticsManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { manager.saveAll(); }
    @Override public void onServerStopping(MinecraftServer server) { manager.clear(); }
}
