package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
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
                .requires(source -> SsuModuleAccess.active("mail") && source.getEntity() instanceof ServerPlayer)
                .executes(context -> open(context.getSource()));
    }

    private static int open(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        SimpleServerUtilities.MAIL.openMailbox(player);
        return 1;
    }

    private static boolean requireModule(CommandSourceStack source) {
        if (be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("mail")) return true;
        source.sendFailure(Component.literal("Mail is disabled or blocked by a required dependency."));
        return false;
    }
}
