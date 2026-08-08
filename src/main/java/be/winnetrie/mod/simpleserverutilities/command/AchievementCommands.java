package be.winnetrie.mod.simpleserverutilities.command;

import java.util.UUID;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorOpenPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Commands primarily used as a safe transport target for clickable achievement chat components. */
public final class AchievementCommands {
    private AchievementCommands(){}
    public static LiteralArgumentBuilder<CommandSourceStack> build(){return Commands.literal("achievement")
            .then(Commands.literal("open").executes(c->open(c.getSource())))
            .then(Commands.literal("view").then(Commands.argument("player",StringArgumentType.word()).then(Commands.argument("achievement",StringArgumentType.word()).executes(c->view(c.getSource(),StringArgumentType.getString(c,"player"),StringArgumentType.getString(c,"achievement"))))))
            .then(Commands.literal("edit").executes(c->edit(c.getSource(),"")).then(Commands.argument("achievement",StringArgumentType.word()).executes(c->edit(c.getSource(),StringArgumentType.getString(c,"achievement")))));
    }
    private static int open(CommandSourceStack source){if(!(source.getEntity() instanceof ServerPlayer p)){source.sendFailure(Component.literal("This command can only be used by a player."));return 0;}if(!SimpleServerUtilities.ACHIEVEMENTS.canUse(p)&&!SimpleServerUtilities.ACHIEVEMENTS.canAdmin(p)){source.sendFailure(Component.literal("Achievements are disabled or you do not have permission to use them."));return 0;}SimpleServerUtilities.ACHIEVEMENTS.openComparison(p,p.getUUID(),"");return 1;}
    private static int view(CommandSourceStack source,String player,String achievement){if(!(source.getEntity() instanceof ServerPlayer p)){source.sendFailure(Component.literal("This command can only be used by a player."));return 0;}if(!SimpleServerUtilities.ACHIEVEMENTS.canUse(p)&&!SimpleServerUtilities.ACHIEVEMENTS.canAdmin(p)){source.sendFailure(Component.literal("Achievements are disabled or you do not have permission to use them."));return 0;}try{SimpleServerUtilities.ACHIEVEMENTS.openComparison(p,UUID.fromString(player),achievement);return 1;}catch(IllegalArgumentException e){source.sendFailure(Component.literal("Invalid achievement link."));return 0;}}
    private static int edit(CommandSourceStack source,String id){if(!(source.getEntity() instanceof ServerPlayer p)||!SimpleServerUtilities.ACHIEVEMENTS.canAdmin(p)){source.sendFailure(Component.literal("Achievement administrator permission is required."));return 0;}var d=id==null||id.isBlank()?new be.winnetrie.mod.simpleserverutilities.achievement.AchievementDefinition().normalize():SimpleServerUtilities.ACHIEVEMENTS.definition(id);if(d==null){source.sendFailure(Component.literal("Achievement not found."));return 0;}PacketDistributor.sendToPlayer(p,new AchievementEditorOpenPayload(id==null?"":id,SimpleServerUtilities.ACHIEVEMENTS.toJson(d),SimpleServerUtilities.ECONOMY.settings().getCurrencySymbol(),SimpleServerUtilities.ECONOMY.settings().getDecimalPlaces(),0L));return 1;}
}
