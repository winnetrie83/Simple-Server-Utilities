package be.winnetrie.mod.simpleserverutilities.identity;

import be.winnetrie.mod.simpleserverutilities.Config;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for global titles and per-player identity selections. */
public final class IdentityModule implements SsuModule {
    private final PlayerIdentityManager manager;

    public IdentityModule(PlayerIdentityManager manager) {
        this.manager = manager;
    }

    @Override public String id() { return "identity"; }
    @Override public Set<String> requiredDependencies() { return Set.of("storage", "ui_preferences"); }
    @Override public boolean isEnabled() { return Config.ENABLE_IDENTITY.get(); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions", "minigames"); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(PlayerIdentityManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void onServerStopping(MinecraftServer server) { manager.saveAll(); manager.clear(); }
}
