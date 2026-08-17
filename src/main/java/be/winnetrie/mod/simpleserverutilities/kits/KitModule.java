package be.winnetrie.mod.simpleserverutilities.kits;

import be.winnetrie.mod.simpleserverutilities.Config;import java.util.Set;import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;import net.minecraft.server.MinecraftServer;
public final class KitModule implements SsuModule{private final KitManager manager;public KitModule(KitManager manager){this.manager=manager;}@Override public String id(){return "kits";}@Override public Set<String> requiredDependencies() { return Set.of("storage"); }
    @Override public boolean isEnabled() { return Config.ENABLE_KITS.get(); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions", "economy", "server_operations"); }@Override public void initialize(SsuServiceRegistry services){services.register(KitManager.class,manager);}@Override public void onServerStarting(MinecraftServer server){manager.load(server);}@Override public void onServerStopping(MinecraftServer server){manager.save();manager.clearRuntime();}}
