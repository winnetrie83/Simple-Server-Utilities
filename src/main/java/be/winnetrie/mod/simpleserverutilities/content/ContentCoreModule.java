package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.permission.TemporaryPermissionManager;
import net.minecraft.server.MinecraftServer;

/** Lifecycle owner for the module-independent Content & Progression Core. */
public final class ContentCoreModule implements SsuModule {
    private final ContentProgressionManager progression;
    private final ContentConditionEngine conditions;
    private final ContentActionEngine actions;
    private final ContentEventBus events;
    private final ContentDependencyValidator dependencies;
    private final ContentRewardLedger rewardLedger;
    private final TemporaryPermissionManager temporaryPermissions;

    public ContentCoreModule(
            ContentProgressionManager progression,
            ContentConditionEngine conditions,
            ContentActionEngine actions,
            ContentEventBus events,
            ContentDependencyValidator dependencies,
            ContentRewardLedger rewardLedger,
            TemporaryPermissionManager temporaryPermissions
    ) {
        this.progression = progression;
        this.conditions = conditions;
        this.actions = actions;
        this.events = events;
        this.dependencies = dependencies;
        this.rewardLedger = rewardLedger;
        this.temporaryPermissions = temporaryPermissions;
    }

    @Override public String id() { return "content_core"; }
    @Override public Set<String> dependencies() { return Set.of("storage", "transactions"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(ContentProgressionManager.class, progression);
        services.register(ContentConditionEngine.class, conditions);
        services.register(ContentActionEngine.class, actions);
        services.register(ContentEventBus.class, events);
        services.register(ContentDependencyValidator.class, dependencies);
        services.register(ContentRewardLedger.class, rewardLedger);
        services.register(TemporaryPermissionManager.class, temporaryPermissions);
        ContentRewardHandlers.register(actions);
    }

    @Override public void onServerStarting(MinecraftServer server) { progression.load(server); rewardLedger.load(server); temporaryPermissions.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { progression.saveAll(); rewardLedger.saveAll(); temporaryPermissions.saveAll(); }

    @Override
    public void onServerStopping(MinecraftServer server) {
        progression.clear();
        rewardLedger.clear();
        temporaryPermissions.clear();
        events.resetRuntimeCounters();
    }
}
