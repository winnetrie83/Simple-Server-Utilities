package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.spawn.ServerSpawn;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnPolicy;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player and administrative command surface for the persistent server spawn. */
public final class SpawnCommands {

    private SpawnCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("spawn")
                .requires(source -> SsuModuleAccess.active("spawn") && source.getEntity() instanceof ServerPlayer)
                .executes(context -> teleport(context.getSource()))
                .then(Commands.literal("set")
                        .executes(context -> set(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource())))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context.getSource())));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildSetAlias() {
        return Commands.literal("setspawn")
                .requires(source -> SsuModuleAccess.active("spawn") && source.getEntity() instanceof ServerPlayer)
                .executes(context -> set(context.getSource()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildClearAlias() {
        return Commands.literal("delspawn")
                .requires(source -> SsuModuleAccess.active("spawn") && source.getEntity() instanceof ServerPlayer)
                .executes(context -> clear(context.getSource()));
    }

    private static int teleport(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        return SpawnService.requestTeleport((ServerPlayer) source.getEntity());
    }

    private static int set(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (!SpawnPolicy.canAdmin(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to set the server spawn."));
            return 0;
        }
        SimpleServerUtilities.SERVER_SPAWN.set(player);
        player.sendSystemMessage(Component.literal("Server spawn set to your current position."));
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (!SpawnPolicy.canAdmin(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to clear the server spawn."));
            return 0;
        }
        boolean removed = SimpleServerUtilities.SERVER_SPAWN.clear();
        player.sendSystemMessage(Component.literal(removed
                ? "Server spawn cleared."
                : "The server spawn was not set."));
        return removed ? 1 : 0;
    }

    private static int info(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (!SpawnPolicy.canAdmin(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to inspect the server spawn."));
            return 0;
        }
        ServerSpawn spawn = SimpleServerUtilities.SERVER_SPAWN.get();
        if (spawn == null) {
            player.sendSystemMessage(Component.literal("The server spawn has not been set."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Server spawn:"));
        player.sendSystemMessage(Component.literal(" - Dimension: " + spawn.getDimension()));
        player.sendSystemMessage(Component.literal(" - Position: "
                + floor(spawn.getX()) + ", " + floor(spawn.getY()) + ", " + floor(spawn.getZ())));
        if (!spawn.getUpdatedByName().isBlank()) {
            player.sendSystemMessage(Component.literal(" - Last set by: " + spawn.getUpdatedByName()));
        }
        return 1;
    }

    private static int cancel(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (!SimpleServerUtilities.TELEPORTS.cancel(player)) {
            player.sendSystemMessage(Component.literal("You do not have a pending teleport."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Pending teleport cancelled."));
        return 1;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static boolean requireModule(CommandSourceStack source) {
        if (be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("spawn")) return true;
        source.sendFailure(Component.literal("Server Spawn is disabled or blocked by a required dependency."));
        return false;
    }
}
