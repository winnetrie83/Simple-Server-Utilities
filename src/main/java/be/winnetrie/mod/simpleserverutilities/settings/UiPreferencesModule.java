package be.winnetrie.mod.simpleserverutilities.settings;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for persistent per-player dashboard and minimap preferences. */
public final class UiPreferencesModule implements SsuModule {

    private final PlayerUiPreferencesManager manager;

    public UiPreferencesModule(PlayerUiPreferencesManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "ui_preferences";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("storage");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(PlayerUiPreferencesManager.class, manager);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        manager.load(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
    }
}
