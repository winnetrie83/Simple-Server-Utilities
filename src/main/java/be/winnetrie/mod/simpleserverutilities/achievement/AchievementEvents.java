package be.winnetrie.mod.simpleserverutilities.achievement;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class AchievementEvents { private AchievementEvents(){} @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(SimpleServerUtilities.CORE.modules().isActive("achievements"))SimpleServerUtilities.ACHIEVEMENTS.tick(event.getServer());} }
