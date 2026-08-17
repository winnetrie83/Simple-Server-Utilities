package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionResult;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Independent Quest Core. NPC integration is an optional service bridge only. */
public final class QuestModule implements SsuModule {
    private final QuestManager manager;

    public QuestModule(QuestManager manager) { this.manager = manager; }

    @Override public String id() { return "quests"; }
    @Override public boolean isEnabled() { return Config.ENABLE_QUESTS.get(); }
    @Override public Set<String> dependencies() { return Set.of("content_core", "storage", "permissions"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(QuestManager.class, manager);
        QuestRewardHandlers.register(SimpleServerUtilities.CONTENT_ACTIONS);
        registerConditions();
        QuestNpcBridge.register(manager, SimpleServerUtilities.NPC_SERVICES);
    }

    private void registerConditions() {
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("quest_available")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("quest_available", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String quest = requireQuest(condition.parameter("quest"));
                String problem = manager.validateStart(player, quest, "npc");
                return problem.isBlank()
                        ? ContentConditionResult.allow("Quest available: " + quest)
                        : ContentConditionResult.deny(problem);
            });
        }
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("quest_completed")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("quest_completed", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String quest = requireQuest(condition.parameter("quest"));
                return manager.hasCompleted(player.getUUID(), quest)
                        ? ContentConditionResult.allow("Quest completed: " + quest)
                        : ContentConditionResult.deny("Quest not completed: " + quest);
            });
        }
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("quest_active")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("quest_active", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String quest = requireQuest(condition.parameter("quest"));
                return manager.isActive(player.getUUID(), quest)
                        ? ContentConditionResult.allow("Quest active: " + quest)
                        : ContentConditionResult.deny("Quest not active: " + quest);
            });
        }
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered("quest_ready")) {
            SimpleServerUtilities.CONTENT_CONDITIONS.register("quest_ready", (condition, context, engine, progression) -> {
                ServerPlayer player = requirePlayer(context == null ? null : context.player());
                String quest = requireQuest(condition.parameter("quest"));
                return manager.isReady(player.getUUID(), quest)
                        ? ContentConditionResult.allow("Quest ready: " + quest)
                        : ContentConditionResult.deny("Quest not ready: " + quest);
            });
        }
    }

    private static ServerPlayer requirePlayer(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("This quest condition requires a player.");
        return player;
    }
    private static String requireQuest(String value) {
        return be.winnetrie.mod.simpleserverutilities.content.ContentId.require(value, "Quest condition quest");
    }

    @Override public void onServerStarting(MinecraftServer server) { manager.load(server); }
    @Override public void beforeServerStopping(MinecraftServer server) { manager.saveAll(); }
    @Override public void onServerStopping(MinecraftServer server) { manager.clear(); }
}
