package be.winnetrie.mod.simpleserverutilities.moderation;

import java.util.Set;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class ModerationModule implements SsuModule {
    private final ModerationManager manager;
    public ModerationModule(ModerationManager manager){this.manager=manager;}
    @Override public String id(){return "moderation";}
    @Override public Set<String> dependencies(){return Set.of("storage","permissions","spawn","economy","mail","regions","jails");}
    @Override public void initialize(SsuServiceRegistry services){services.register(ModerationManager.class,manager);}
    @Override public void onServerStarting(MinecraftServer server){manager.load(server);}
    @Override public void onServerStopping(MinecraftServer server){manager.save();manager.clearRuntime();}
}
