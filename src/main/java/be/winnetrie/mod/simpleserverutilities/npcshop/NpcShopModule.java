package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** NPC-owned fixed-price shop module backed by Economy Core. */
public final class NpcShopModule implements SsuModule {
    private final NpcShopManager manager;
    public NpcShopModule(NpcShopManager manager) { this.manager = manager; }
    @Override public String id() { return "npc_shops"; }
    @Override public Set<String> dependencies() { return Set.of("storage", "transactions", "economy", "permissions", "npcs"); }
    @Override public boolean isEnabled() { return Config.ENABLE_NPCS.get(); }
    @Override public void initialize(SsuServiceRegistry services) {
        services.register(NpcShopManager.class, manager);
        SimpleServerUtilities.NPC_SERVICES.register("shop", manager::validateService, manager::executeService);
    }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { manager.finalizeBuybacks(); manager.saveAll(); }
    @Override public void onServerStopping(MinecraftServer server) { manager.clear(); }
}
