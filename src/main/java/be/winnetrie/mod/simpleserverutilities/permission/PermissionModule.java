package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lifecycle-owned permission subsystem. Save formats and resolver semantics remain unchanged. */
public final class PermissionModule implements SsuModule {

    private final PermissionManager manager;

    public PermissionModule(PermissionManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "permissions";
    }

    @Override
    public boolean isEnabled() {
        return Config.ENABLE_PERMISSION_SYSTEM.get();
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("claims", "storage");
    }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(PermissionManager.class, manager);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        manager.load(server);
        manager.migrateLegacyClaimLimitOverrides();
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
        manager.clear();
    }
}
