package be.winnetrie.mod.simpleserverutilities.auction;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class AuctionHouseModule implements SsuModule {
    private final AuctionHouseManager manager;
    public AuctionHouseModule(AuctionHouseManager manager) { this.manager = manager; }
    @Override public String id() { return "auction_house"; }
    @Override public boolean isEnabled() { return Config.ENABLE_AUCTION_HOUSE.get(); }
    @Override public Set<String> requiredDependencies() { return Set.of("storage", "transactions", "economy", "mail"); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions"); }
    @Override public void initialize(SsuServiceRegistry services) { services.register(AuctionHouseManager.class, manager); }
    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void onServerStopping(MinecraftServer server) { manager.saveAllSync(); manager.clear(); }
}
