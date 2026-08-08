package be.winnetrie.mod.simpleserverutilities.onboarding;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class OnboardingModule implements SsuModule {
    private final OnboardingManager manager;
    public OnboardingModule(OnboardingManager manager) { this.manager = manager; }
    @Override public String id() { return "onboarding"; }
    @Override public Set<String> dependencies() { return Set.of("storage", "spawn", "permissions"); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(OnboardingManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void onServerStopping(MinecraftServer server) { manager.save(); manager.clearRuntime(); }
}
