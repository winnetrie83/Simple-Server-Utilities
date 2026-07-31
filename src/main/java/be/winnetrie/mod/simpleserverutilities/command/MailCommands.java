package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MailCommands {
    private MailCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mail")
                .requires(source -> Config.ENABLE_MAIL.get() && source.getEntity() instanceof ServerPlayer)
                .executes(context -> open(context.getSource()));
    }

    private static int open(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        SimpleServerUtilities.MAIL.openMailbox(player);
        return 1;
    }
}
