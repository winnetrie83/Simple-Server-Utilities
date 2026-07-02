package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Collection;
import java.util.Set;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

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
                .requires(source -> source.getEntity() instanceof ServerPlayer)

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

                // /homes help
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())));
    }

    private static int setHome(CommandSourceStack source, String homeName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_HOMES.get()) {
            player.sendSystemMessage(Component.literal("Homes are disabled on this server."));
            return 0;
        }

        boolean success = SimpleServerUtilities.HOMES.setHome(player, homeName);

        if (!success) {
            int max = SimpleServerUtilities.HOMES.getMaxHomes(player.getUUID());
            player.sendSystemMessage(Component.literal("You reached the maximum amount of homes: " + max));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Home '" + homeName + "' set."));
        return 1;
    }

    private static int teleportHome(CommandSourceStack source, String homeName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_HOMES.get()) {
            player.sendSystemMessage(Component.literal("Homes are disabled on this server."));
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

        player.teleportTo(
                level,
                home.getX(),
                home.getY(),
                home.getZ(),
                Set.of(),
                home.getYaw(),
                home.getPitch(),
                true
        );

        player.sendSystemMessage(Component.literal("Teleported to home '" + home.getDisplayName() + "'."));
        return 1;
    }

    private static int listHomes(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_HOMES.get()) {
            player.sendSystemMessage(Component.literal("Homes are disabled on this server."));
            return 0;
        }

        Collection<PlayerHome> homes = SimpleServerUtilities.HOMES.getHomes(player.getUUID());
        int count = SimpleServerUtilities.HOMES.countHomes(player.getUUID());
        int max = SimpleServerUtilities.HOMES.getMaxHomes(player.getUUID());

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

        if (!Config.ENABLE_HOMES.get()) {
            player.sendSystemMessage(Component.literal("Homes are disabled on this server."));
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
}