package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.CommandDispatcher;
import java.time.Duration;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.blockinfo.BlockInformationService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
                .then(EconomyCommands.buildAdmin())
                .then(UnifiedPermissionCommands.buildRank())
                .then(UnifiedPermissionCommands.buildPermission())
                .then(PlayerSettingsCommands.build())
                .then(UtilityMiningCommands.build())
                .then(HologramCommands.build()));

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
        if (SimpleServerUtilities.JOBS.size() > 0) {
            source.sendFailure(Component.literal(
                    "Cannot reload SSU while " + SimpleServerUtilities.JOBS.size()
                            + " long-running job(s) are active. Finish or cancel them first."
            ));
            return 0;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            reloadAll(source.getServer());
            source.sendSystemMessage(Component.literal("Simple Server Utilities reloaded."));
            return 1;
        }

        if (!PermissionService.getBoolean(player, PermissionKeys.SSU_RELOAD, false)) {
            player.sendSystemMessage(Component.literal("You do not have permission to reload Simple Server Utilities."));
            return 0;
        }

        reloadAll(source.getServer());
        player.sendSystemMessage(Component.literal("Simple Server Utilities reloaded."));
        return 1;
    }

    private static void reloadAll(MinecraftServer server) {
        boolean statisticsWasActive = SimpleServerUtilities.CORE.modules().isActive("statistics");
        if (statisticsWasActive) SimpleServerUtilities.STATISTICS.saveAll();
        SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));

        // Match the Core 2.0 dependency order while keeping the storage worker alive.
        // Apply changed module switches before touching module-owned data. This
        // guarantees that /ssu reload cannot resurrect a disabled subsystem.
        SimpleServerUtilities.CORE.modules().refreshEnabledState(server);

        SimpleServerUtilities.TRANSACTIONS.clear();
        SimpleServerUtilities.ECONOMY.load(server);
        if (Config.ENABLE_PLAYER_CLAIMS.get()) {
            SimpleServerUtilities.PLAYER_CLAIMS.load(server);
        }
        if (Config.ENABLE_PERMISSION_SYSTEM.get()) {
            SimpleServerUtilities.PERMISSIONS.load(server);
            SimpleServerUtilities.PERMISSIONS.migrateLegacyClaimLimitOverrides();
        }
        if (Config.ENABLE_MAIL.get()) {
            SimpleServerUtilities.MAIL.load(server);
        }
        if (Config.ENABLE_HOMES.get()) {
            SimpleServerUtilities.HOMES.load(server);
        }
        if (Config.ENABLE_WARPS.get()) {
            SimpleServerUtilities.WARPS.load(server);
        }
        SimpleServerUtilities.SERVER_SPAWN.load(server);
        SimpleServerUtilities.UI_PREFERENCES.load(server);
        if (statisticsWasActive && SimpleServerUtilities.CORE.modules().isActive("statistics")) {
            SimpleServerUtilities.STATISTICS.load(server);
        }
        BlockInformationService.syncAll(server);
        if (Config.ENABLE_TREECAPITATOR.get()) {
            SimpleServerUtilities.TREE_PLACEMENTS.load(server);
        } else {
            SimpleServerUtilities.TREE_PLACEMENTS.save();
            SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));
            SimpleServerUtilities.TREE_PLACEMENTS.clear();
        }
        SimpleServerUtilities.UTILITY_MINING.clearClients(server);
        SimpleServerUtilities.UTILITY_MINING.clear();
        if (Config.ENABLE_ADMIN_REGIONS.get()) {
            SimpleServerUtilities.REGIONS.load(server);
            SimpleServerUtilities.REGION_SNAPSHOTS.load(server);
            SimpleServerUtilities.REGION_RENT_JOURNAL.loadAndRecover(server);
        }
        SimpleServerUtilities.BORDER_SETTINGS.load(server);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(server);
        if (Config.ENABLE_HOLOGRAMS.get()) {
            SimpleServerUtilities.HOLOGRAMS.load(server);
            SimpleServerUtilities.HOLOGRAMS.syncAll();
        } else {
            SimpleServerUtilities.HOLOGRAMS.clearClients(server);
        }
    }
}
