package be.winnetrie.mod.simpleserverutilities.blockinfo;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Lightweight lifecycle gate for the client-side block information overlay. */
public final class BlockInformationModule implements SsuModule {
    @Override public String id() { return "block_information"; }
    @Override public Set<String> dependencies() { return Set.of("permissions", "ui_preferences"); }
    @Override public boolean isEnabled() { return Config.ENABLE_BLOCK_INFORMATION.get(); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(BlockInformationModule.class, this); }
    @Override public void onServerStarting(MinecraftServer server) { BlockInformationService.syncAll(server); }
    @Override public void onServerStopping(MinecraftServer server) { BlockInformationService.clearAll(server); }
}
