package be.winnetrie.mod.simpleserverutilities.mapmarker;

import be.winnetrie.mod.simpleserverutilities.Config;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for personal map markers. */
public final class MapMarkerModule implements SsuModule {
    private final MapMarkerManager manager;

    public MapMarkerModule(MapMarkerManager manager) {
        this.manager = manager;
    }

    @Override public String id() { return "map_markers"; }
    @Override public Set<String> requiredDependencies() { return Set.of("storage", "ui_preferences"); }
    @Override public boolean isEnabled() { return Config.ENABLE_MAP_MARKERS.get(); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(MapMarkerManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { manager.save(); }
    @Override public void onServerStopping(MinecraftServer server) { manager.clear(); }
}
