package be.winnetrie.mod.simpleserverutilities.dungeon;

import java.util.Set;
import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionResult;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Independent region-based Dungeon Framework. */
public final class DungeonModule implements SsuModule {
    private final DungeonManager manager;
    public DungeonModule(DungeonManager manager) { this.manager = manager; }

    @Override public String id() { return "dungeons"; }
    @Override public boolean isEnabled() { return Config.ENABLE_DUNGEONS.get(); }
    @Override public Set<String> dependencies() { return Set.of("content_core", "storage", "permissions", "regions"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(DungeonManager.class, manager);
        registerConditions();
        DungeonNpcBridge.register(manager, SimpleServerUtilities.NPC_SERVICES);
    }

    private void registerConditions() {
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("dungeon_queued")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("dungeon_queued", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String id = condition.parameter("dungeon");
                return manager.isQueued(player.getUUID(), id)
                        ? ContentConditionResult.allow("Player is queued for a dungeon.")
                        : ContentConditionResult.deny("Player is not queued for that dungeon.");
            });
        }
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("dungeon_active")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("dungeon_active", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String id = condition.parameter("dungeon");
                return manager.isInRun(player.getUUID(), id)
                        ? ContentConditionResult.allow("Player is in a dungeon run.")
                        : ContentConditionResult.deny("Player is not in that dungeon.");
            });
        }
    }

    private static ServerPlayer requirePlayer(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("This dungeon condition requires a player.");
        return player;
    }

    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { manager.shutdownRuntime(true); manager.saveAll(); }
    @Override public void onServerStopping(MinecraftServer server) { manager.clear(); }
}
