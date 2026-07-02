package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

public class SSUCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(ClaimCommands.build());
        dispatcher.register(RegionCommands.build());
        dispatcher.register(HomeCommands.build());
        dispatcher.register(WarpCommands.build());
        // dispatcher.register(KitCommands.build());
        // dispatcher.register(PermissionCommands.build());
    }
}