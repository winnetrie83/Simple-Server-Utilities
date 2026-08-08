package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.CommandDispatcher;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.maintenance.SsuReloadService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SSUCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ssu")
                .then(Commands.literal("menu")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> openMenu(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(BorderCommands.build())
                .then(CoreCommands.build())
                .then(ContentCommands.build())
                .then(EconomyCommands.buildAdmin())
                .then(UnifiedPermissionCommands.buildRank())
                .then(UnifiedPermissionCommands.buildPermission())
                .then(PlayerSettingsCommands.build())
                .then(UtilityMiningCommands.build())
                .then(HologramCommands.build())
                .then(NpcCommands.build())
                .then(QuestCommands.build())
                .then(AchievementCommands.build())
                .then(MinigameCommands.build())
                .then(DungeonCommands.build()));

        dispatcher.register(ClaimCommands.build());
        dispatcher.register(RegionCommands.build());
        dispatcher.register(HomeCommands.build());
        dispatcher.register(WarpCommands.build());
        dispatcher.register(SpawnCommands.build());
        dispatcher.register(SpawnCommands.buildSetAlias());
        dispatcher.register(SpawnCommands.buildClearAlias());
        dispatcher.register(MailCommands.build());
        // dispatcher.register(KitCommands.build());
        dispatcher.register(EconomyCommands.buildPlayerRoot());
        dispatcher.register(EconomyCommands.buildBalanceAlias());
        dispatcher.register(EconomyCommands.buildPayAlias());
    }


    private static int openMenu(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        SimpleServerUtilities.MENUS.open(player);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player
                && !PermissionService.getBoolean(player, PermissionKeys.SSU_RELOAD, false)) {
            player.sendSystemMessage(Component.literal("You do not have permission to reload Simple Server Utilities."));
            return 0;
        }
        try {
            var result = SsuReloadService.reloadAll(source.getServer());
            if (!result.successful()) {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
            source.sendSystemMessage(Component.literal(result.message()));
            return 1;
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to reload Simple Server Utilities", exception);
            source.sendFailure(Component.literal("SSU reload failed. Check the server log for details."));
            return 0;
        }
    }

}
