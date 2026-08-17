package be.winnetrie.mod.simpleserverutilities.achievement;

import java.util.Set;
import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class AchievementModule implements SsuModule {
    private final AchievementManager manager;
    public AchievementModule(AchievementManager manager){this.manager=manager;}
    @Override public String id(){return "achievements";}
    @Override public Set<String> requiredDependencies() { return Set.of("storage", "content_core"); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions"); }
    @Override public Set<String> integrationDependencies() { return Set.of("economy", "mail"); }
    @Override public boolean isEnabled() { return Config.ENABLE_ACHIEVEMENTS.get(); }
    @Override public void initialize(SsuServiceRegistry services){services.register(AchievementManager.class,manager);}
    @Override public void onServerStarting(MinecraftServer server){manager.load(server);}
    @Override public void beforeServerStopping(MinecraftServer server){manager.saveAll();}
    @Override public void onServerStopping(MinecraftServer server){manager.clear();}
}
