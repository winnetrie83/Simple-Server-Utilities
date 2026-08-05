package be.winnetrie.mod.simpleserverutilities.quest;

import be.winnetrie.mod.simpleserverutilities.content.ContentActionEngine;
import be.winnetrie.mod.simpleserverutilities.content.ContentRewardHandlers;

/** Compatibility bridge: generic rewards are owned by Content Core. */
public final class QuestRewardHandlers {
    private QuestRewardHandlers() {
    }

    public static void register(ContentActionEngine engine) {
        ContentRewardHandlers.register(engine);
    }
}
