package be.winnetrie.mod.simpleserverutilities.serverops;

import java.util.Set;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle adapter for the lightweight server-management suite. */
public final class ServerOperationsModule implements SsuModule {
    private final ServerOperationsManager manager;
    public ServerOperationsModule(ServerOperationsManager manager) { this.manager = manager; }
    @Override public String id() { return "server_operations"; }
    @Override public Set<String> dependencies() { return Set.of("storage", "permissions", "economy"); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(ServerOperationsManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { manager.beforeStop(); }
    @Override public void onServerStopping(MinecraftServer server) { manager.clearRuntime(); }
}
