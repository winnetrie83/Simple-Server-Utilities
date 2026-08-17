package be.winnetrie.mod.simpleserverutilities.hologram;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class HologramModule implements SsuModule {
    private final HologramManager manager;
    private final HologramToolManager tools;

    public HologramModule(HologramManager manager, HologramToolManager tools) {
        this.manager = manager;
        this.tools = tools;
    }

    @Override public String id() { return "holograms"; }
    @Override public boolean isEnabled() { return Config.ENABLE_HOLOGRAMS.get(); }

    @Override public Set<String> requiredDependencies() { return Set.of("storage"); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions", "statistics", "mines", "regions"); }

    @Override public void initialize(SsuServiceRegistry services) {
        services.register(HologramManager.class, manager);
        services.register(HologramToolManager.class, tools);
    }

    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }

    @Override public void onServerStopping(MinecraftServer server) {
        manager.save();
        manager.clearClients(server);
        manager.clear();
        tools.clear();
    }
}
