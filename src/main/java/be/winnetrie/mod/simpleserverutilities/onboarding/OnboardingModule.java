package be.winnetrie.mod.simpleserverutilities.onboarding;

import be.winnetrie.mod.simpleserverutilities.Config;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class OnboardingModule implements SsuModule {
    private final OnboardingManager manager;
    public OnboardingModule(OnboardingManager manager) { this.manager = manager; }
    @Override public String id() { return "onboarding"; }
    @Override public Set<String> requiredDependencies() { return Set.of("storage", "spawn"); }
    @Override public boolean isEnabled() { return Config.ENABLE_ONBOARDING.get(); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions", "moderation", "server_operations"); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(OnboardingManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void onServerStopping(MinecraftServer server) { manager.save(); manager.clearRuntime(); }
}
