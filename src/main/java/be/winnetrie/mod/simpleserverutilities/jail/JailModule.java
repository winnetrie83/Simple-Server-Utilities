package be.winnetrie.mod.simpleserverutilities.jail;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class JailModule implements SsuModule {
    private final JailManager manager;
    private final JailSetupToolManager tools;

    public JailModule(JailManager manager, JailSetupToolManager tools) {
        this.manager = manager;
        this.tools = tools;
    }

    @Override public String id() { return "jails"; }
    @Override public Set<String> requiredDependencies() { return Set.of("storage", "regions", "moderation"); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions", "economy", "server_operations"); }
    @Override public boolean isEnabled() { return Config.ENABLE_JAILS.get(); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(JailManager.class, manager);
        services.register(JailSetupToolManager.class, tools);
    }

    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
        manager.clearRuntime();
        tools.clear();
    }
}
