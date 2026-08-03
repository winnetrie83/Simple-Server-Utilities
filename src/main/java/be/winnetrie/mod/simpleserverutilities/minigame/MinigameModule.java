package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Map;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionResult;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Independent Minigame Framework; NPC and Quest integration are optional bridges. */
public final class MinigameModule implements SsuModule {
    private final MinigameManager manager;

    public MinigameModule(MinigameManager manager) {
        this.manager = manager;
    }

    @Override public String id() { return "minigames"; }
    @Override public boolean isEnabled() { return Config.ENABLE_MINIGAMES.get(); }
    @Override public Set<String> dependencies() { return Set.of("content_core", "storage", "permissions"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(MinigameManager.class, manager);
        registerConditions();
        MinigameNpcBridge.register(manager, SimpleServerUtilities.NPC_SERVICES);
    }

    private void registerConditions() {
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("minigame_queued")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("minigame_queued", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String id = condition.parameter("minigame");
                return manager.isQueued(player.getUUID(), id)
                        ? ContentConditionResult.allow("Player is queued for a minigame.")
                        : ContentConditionResult.deny("Player is not queued for that minigame.");
            });
        }
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("minigame_active")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("minigame_active", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String id = condition.parameter("minigame");
                return manager.isInMatch(player.getUUID(), id)
                        ? ContentConditionResult.allow("Player is in a minigame match.")
                        : ContentConditionResult.deny("Player is not in that minigame.");
            });
        }
    }

    private static ServerPlayer requirePlayer(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("This minigame condition requires a player.");
        return player;
    }

    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }

    @Override
    public void beforeServerStopping(MinecraftServer server) {
        manager.shutdownRuntime(true);
        manager.saveAll();
    }

    @Override public void onServerStopping(MinecraftServer server) { manager.clear(); }
}
