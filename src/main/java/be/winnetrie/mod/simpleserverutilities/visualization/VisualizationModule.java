package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for persistent border settings and active visualization state. */
public final class VisualizationModule implements SsuModule {

    private final BorderVisualizationSettingsManager settings;
    private final BorderVisualizationService service;

    public VisualizationModule(BorderVisualizationSettingsManager settings, BorderVisualizationService service) {
        this.settings = settings;
        this.service = service;
    }

    @Override
    public String id() {
        return "visualization";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("claims", "regions", "permissions", "storage");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(BorderVisualizationSettingsManager.class, settings);
        services.register(BorderVisualizationService.class, service);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        settings.load(server);
        service.refreshAll(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        service.clear();
    }
}
