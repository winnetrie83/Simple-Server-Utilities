package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Collection;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.policy.HomePolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportOptions;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportType;
import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.home.PlayerHome;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class HomeCommands {

    private HomeCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("homes")
                .requires(source -> Config.ENABLE_HOMES.get() && source.getEntity() instanceof ServerPlayer)

                // /homes
                .executes(context -> listHomes(context.getSource()))

                // /homes list
                .then(Commands.literal("list")
                        .executes(context -> listHomes(context.getSource())))

                // /homes sethome
                // /homes sethome <name>
                .then(Commands.literal("sethome")
                        .executes(context -> setHome(
                                context.getSource(),
                                SimpleServerUtilities.HOMES.getDefaultHomeName()
                        ))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> setHome(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /homes tp
                // /homes tp <name>
                .then(Commands.literal("tp")
                        .executes(context -> teleportHome(
                                context.getSource(),
                                SimpleServerUtilities.HOMES.getDefaultHomeName()
                        ))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> teleportHome(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /homes delhome
                // /homes delhome <name>
                .then(Commands.literal("delhome")
                        .executes(context -> deleteHome(
                                context.getSource(),
                                SimpleServerUtilities.HOMES.getDefaultHomeName()
                        ))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> deleteHome(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))
                // /homes cancel
                .then(Commands.literal("cancel")
                        .executes(context -> cancelTeleport(context.getSource())))

                // /homes help
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())));
    }

    private static int setHome(CommandSourceStack source, String homeName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!HomePolicy.canSetHomeAt(player, player.blockPosition())) {
            player.sendSystemMessage(Component.literal("You do not have permission to set homes here."));
            return 0;
        }

        boolean success = SimpleServerUtilities.HOMES.setHome(player, homeName);

        if (!success) {
            int max = HomePolicy.getMaxHomes(player);
            player.sendSystemMessage(Component.literal("You reached the maximum amount of homes: " + max));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Home '" + homeName + "' set."));
        return 1;
    }

    private static int teleportHome(CommandSourceStack source, String homeName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!HomePolicy.canTeleportHome(player, context)) {
            player.sendSystemMessage(Component.literal(TeleportPolicy.denialMessage(TeleportType.HOME, context)));
            return 0;
        }

        PlayerHome home = SimpleServerUtilities.HOMES.getHome(player.getUUID(), homeName);

        if (home == null) {
            player.sendSystemMessage(Component.literal("Home not found: " + homeName));
            return 0;
        }

        ServerLevel level = getHomeLevel(player, home);

        if (level == null) {
            player.sendSystemMessage(Component.literal("Home dimension is not loaded: " + home.getDimension()));
            return 0;
        }

        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.HOME, context);

        return SimpleServerUtilities.TELEPORTS.requestTeleport(
                player,
                "homes",
                "home '" + home.getDisplayName() + "'",
                options,
                level,
                home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch(),
                candidate -> HomePolicy.canTeleportHome(candidate,
                        PermissionContext.at(candidate, candidate.blockPosition())),
                candidate -> TeleportPolicy.denialMessage(TeleportType.HOME,
                        PermissionContext.at(candidate, candidate.blockPosition()))
        );
    }

    private static int listHomes(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!HomePolicy.canUseHomes(player, context)) {
            player.sendSystemMessage(Component.literal("You do not have permission to use homes here."));
            return 0;
        }

        Collection<PlayerHome> homes = SimpleServerUtilities.HOMES.getHomes(player.getUUID());
        int count = SimpleServerUtilities.HOMES.countHomes(player.getUUID());
        int max = HomePolicy.getMaxHomes(player);

        player.sendSystemMessage(Component.literal("Homes: " + count + " / " + max));

        if (homes.isEmpty()) {
            player.sendSystemMessage(Component.literal("You do not have any homes yet."));
            return 1;
        }

        for (PlayerHome home : homes) {
            player.sendSystemMessage(Component.literal(
                    " - " + home.getDisplayName()
                            + " | " + home.getDimension()
                            + " | x: " + formatCoordinate(home.getX())
                            + " y: " + formatCoordinate(home.getY())
                            + " z: " + formatCoordinate(home.getZ())
            ));
        }

        return 1;
    }

    private static int deleteHome(CommandSourceStack source, String homeName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!HomePolicy.canDeleteHome(player, context)) {
            player.sendSystemMessage(Component.literal("You do not have permission to delete homes here."));
            return 0;
        }

        boolean success = SimpleServerUtilities.HOMES.deleteHome(player.getUUID(), homeName);

        if (!success) {
            player.sendSystemMessage(Component.literal("Home not found: " + homeName));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Home '" + homeName + "' deleted."));
        return 1;
    }

    private static int help(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        player.sendSystemMessage(Component.literal("Homes commands:"));
        player.sendSystemMessage(Component.literal(" - /homes"));
        player.sendSystemMessage(Component.literal(" - /homes list"));
        player.sendSystemMessage(Component.literal(" - /homes sethome"));
        player.sendSystemMessage(Component.literal(" - /homes sethome <name>"));
        player.sendSystemMessage(Component.literal(" - /homes tp"));
        player.sendSystemMessage(Component.literal(" - /homes tp <name>"));
        player.sendSystemMessage(Component.literal(" - /homes delhome"));
        player.sendSystemMessage(Component.literal(" - /homes delhome <name>"));
        player.sendSystemMessage(Component.literal(" - /homes cancel"));

        return 1;
    }

    private static ServerLevel getHomeLevel(ServerPlayer player, PlayerHome home) {
        try {
            Identifier dimensionId = Identifier.parse(home.getDimension());
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

            return player.level().getServer().getLevel(dimension);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatCoordinate(double coordinate) {
        return String.format("%.1f", coordinate);
    }

    private static int cancelTeleport(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        boolean cancelled = SimpleServerUtilities.TELEPORTS.cancel(player);

        if (!cancelled) {
            player.sendSystemMessage(Component.literal("You do not have a pending teleport."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Pending teleport cancelled."));
        return 1;
    }
}