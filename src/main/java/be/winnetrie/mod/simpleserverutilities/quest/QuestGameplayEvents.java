package be.winnetrie.mod.simpleserverutilities.quest;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Quest-only lifecycle events. Generic gameplay publication lives in ContentGameplayEvents. */
public final class QuestGameplayEvents {
    private QuestGameplayEvents() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active()) {
            SimpleServerUtilities.QUESTS.savePlayer(player.getUUID());
        }
    }

    private static boolean active() {
        return Config.ENABLE_QUESTS.get() && SimpleServerUtilities.CORE.modules().isActive("quests");
    }
}
