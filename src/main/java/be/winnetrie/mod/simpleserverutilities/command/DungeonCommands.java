package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.dungeon.DungeonEditorService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player and administrator commands for the independent Dungeon Framework. */
public final class DungeonCommands {
    private DungeonCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dungeon")
                .then(Commands.literal("open").executes(c -> open(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("join").then(Commands.argument("dungeon", StringArgumentType.word()).executes(c -> join(c.getSource(), StringArgumentType.getString(c,"dungeon")))))
                .then(Commands.literal("leave").executes(c -> leave(c.getSource())))
                .then(Commands.literal("edit").executes(c -> edit(c.getSource(),"")).then(Commands.argument("dungeon",StringArgumentType.word()).executes(c -> edit(c.getSource(),StringArgumentType.getString(c,"dungeon")))))
                .then(Commands.literal("force-start").then(Commands.argument("dungeon",StringArgumentType.word()).executes(c -> forceStart(c.getSource(),StringArgumentType.getString(c,"dungeon")))))
                .then(Commands.literal("complete").then(Commands.argument("dungeon",StringArgumentType.word()).executes(c -> complete(c.getSource(),StringArgumentType.getString(c,"dungeon"),true))))
                .then(Commands.literal("fail").then(Commands.argument("dungeon",StringArgumentType.word()).executes(c -> complete(c.getSource(),StringArgumentType.getString(c,"dungeon"),false))))
                .then(Commands.literal("advance-stage").executes(c -> advance(c.getSource())))
                .then(Commands.literal("release-arena").then(Commands.argument("dungeon_or_arena",StringArgumentType.word()).executes(c -> release(c.getSource(),StringArgumentType.getString(c,"dungeon_or_arena")))));
    }

    private static int open(CommandSourceStack source){ServerPlayer player=player(source);if(player==null)return 0;SimpleServerUtilities.DUNGEONS.open(player);return 1;}
    private static int join(CommandSourceStack source,String id){ServerPlayer player=player(source);if(player==null)return 0;try{player.sendSystemMessage(Component.literal(SimpleServerUtilities.DUNGEONS.joinQueue(player,id)));return 1;}catch(RuntimeException e){source.sendFailure(Component.literal(message(e)));return 0;}}
    private static int leave(CommandSourceStack source){ServerPlayer player=player(source);if(player==null)return 0;player.sendSystemMessage(Component.literal(SimpleServerUtilities.DUNGEONS.leave(player,true)));return 1;}
    private static int status(CommandSourceStack source){if(!canAdmin(source)){source.sendFailure(Component.literal("Dungeon administrator permission is required."));return 0;}var s=SimpleServerUtilities.DUNGEONS.snapshot();source.sendSystemMessage(Component.literal("SSU Dungeons: definitions="+s.definitions()+", queued="+s.queuedPlayers()+", runs="+s.runs()+", reserved="+s.reservedArenas()+", resetting="+s.resettingArenas()+", blocked="+s.blockedArenas()+", recoveries="+s.pendingRecoveries()));return 1;}
    private static int edit(CommandSourceStack source,String id){ServerPlayer player=player(source);if(player==null)return 0;if(!PermissionService.getBoolean(player,PermissionKeys.DUNGEONS_ADMIN,false)){source.sendFailure(Component.literal("Dungeon administrator permission is required."));return 0;}DungeonEditorService.open(player,id);return 1;}
    private static int forceStart(CommandSourceStack source,String id){if(!canAdmin(source)){source.sendFailure(Component.literal("Dungeon administrator permission is required."));return 0;}try{source.sendSystemMessage(Component.literal(SimpleServerUtilities.DUNGEONS.forceStart(id)));return 1;}catch(RuntimeException e){source.sendFailure(Component.literal(message(e)));return 0;}}
    private static int complete(CommandSourceStack source,String id,boolean success){if(!canAdmin(source)){source.sendFailure(Component.literal("Dungeon administrator permission is required."));return 0;}try{source.sendSystemMessage(Component.literal(success?SimpleServerUtilities.DUNGEONS.completeFirstRun(id,"Completed by an administrator."):SimpleServerUtilities.DUNGEONS.failFirstRun(id,"Failed by an administrator.")));return 1;}catch(RuntimeException e){source.sendFailure(Component.literal(message(e)));return 0;}}
    private static int advance(CommandSourceStack source){ServerPlayer player=player(source);if(player==null)return 0;if(!PermissionService.getBoolean(player,PermissionKeys.DUNGEONS_ADMIN,false)){source.sendFailure(Component.literal("Dungeon administrator permission is required."));return 0;}try{source.sendSystemMessage(Component.literal(SimpleServerUtilities.DUNGEONS.advancePlayerRun(player,"Advanced by an administrator.")));return 1;}catch(RuntimeException e){source.sendFailure(Component.literal(message(e)));return 0;}}
    private static int release(CommandSourceStack source,String target){if(!canAdmin(source)){source.sendFailure(Component.literal("Dungeon administrator permission is required."));return 0;}source.sendSystemMessage(Component.literal(SimpleServerUtilities.DUNGEONS.releaseBlockedArena(target)));return 1;}
    private static ServerPlayer player(CommandSourceStack source){if(source.getEntity() instanceof ServerPlayer player)return player;source.sendFailure(Component.literal("This command can only be used by a player."));return null;}
    private static boolean canAdmin(CommandSourceStack source){return !(source.getEntity() instanceof ServerPlayer player)||PermissionService.getBoolean(player,PermissionKeys.DUNGEONS_ADMIN,false);}
    private static String message(RuntimeException exception){return exception.getMessage()==null?"The dungeon operation failed safely.":exception.getMessage();}
}
