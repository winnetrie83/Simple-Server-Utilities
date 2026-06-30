package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Optional;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.ChunkPos;

public class ClaimCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("claims")
                .requires(source -> source.getEntity() instanceof ServerPlayer)

                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())))

                .then(Commands.literal("claim")
                        .executes(context -> claim(context.getSource())))

                .then(Commands.literal("unclaim")
                        .executes(context -> unclaim(context.getSource())))

                .then(Commands.literal("info")
                        .executes(context -> claimInfo(context.getSource())))

                .then(Commands.literal("count")
                        .executes(context -> claimCount(context.getSource())))

                .then(Commands.literal("trust")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> trust(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")
                                ))))

                .then(Commands.literal("untrust")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> untrust(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")
                                ))))

                .then(Commands.literal("flag")
                        .then(Commands.argument("flag", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> setFlag(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "flag"),
                                                BoolArgumentType.getBool(context, "value")
                                        )))))

                .then(Commands.literal("flags")
                        .executes(context -> flagInfo(context.getSource())));
    }

    private static int claim(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            player.sendSystemMessage(Component.literal("Player claims are disabled."));
            return 0;
        }

        if (!PermissionService.has(player, PermissionService.CLAIM_CREATE)) {
            player.sendSystemMessage(Component.literal("You do not have permission to claim chunks."));
            return 0;
        }

        int claimCount = SimpleServerUtilities.PLAYER_CLAIMS.countClaims(player.getUUID());

        if (claimCount >= Config.MAX_PLAYER_CLAIMS.get()) {
            player.sendSystemMessage(Component.literal("You reached the maximum amount of claims."));
            return 0;
        }

        ChunkPos chunkPos = player.chunkPosition();
        boolean success = SimpleServerUtilities.PLAYER_CLAIMS.claim(player.level(), chunkPos, player.getUUID());

        if (!success) {
            player.sendSystemMessage(Component.literal("This chunk is already claimed."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Chunk claimed."));
        return 1;
    }

    private static int unclaim(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        ChunkPos chunkPos = player.chunkPosition();

        boolean adminBypass = PermissionService.has(player, PermissionService.CLAIM_BYPASS);

        boolean success = SimpleServerUtilities.PLAYER_CLAIMS.unclaim(
                player.level(),
                chunkPos,
                player.getUUID(),
                adminBypass
        );

        if (!success) {
            player.sendSystemMessage(Component.literal("You cannot unclaim this chunk."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Chunk unclaimed."));
        return 1;
    }

    private static int claimInfo(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        ChunkPos chunkPos = player.chunkPosition();

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), chunkPos);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("This chunk is not claimed."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Claimed chunk: " + claim.getChunkX() + ", " + claim.getChunkZ()));
        player.sendSystemMessage(Component.literal("Owner UUID: " + claim.getOwner()));
        player.sendSystemMessage(Component.literal("Trusted players: " + claim.getTrustedPlayers().size()));

        sendFlagInfo(player, claim);

        return 1;
    }

    private static int claimCount(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        int count = SimpleServerUtilities.PLAYER_CLAIMS.countClaims(player.getUUID());
        int max = Config.MAX_PLAYER_CLAIMS.get();

        player.sendSystemMessage(Component.literal("Claims: " + count + " / " + max));
        return 1;
    }

    private static int trust(CommandSourceStack source, String playerName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), player.chunkPosition());

        if (claim == null) {
            player.sendSystemMessage(Component.literal("This chunk is not claimed."));
            return 0;
        }

        if (!claim.isOwner(player.getUUID()) && !PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can trust players."));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found: " + playerName));
            return 0;
        }

        if (claim.isOwner(targetUuid.get())) {
            player.sendSystemMessage(Component.literal("The owner is already trusted."));
            return 0;
        }

        claim.trust(targetUuid.get());
        SimpleServerUtilities.PLAYER_CLAIMS.save();

        player.sendSystemMessage(Component.literal(playerName + " is now trusted in this claim."));
        return 1;
    }

    private static int untrust(CommandSourceStack source, String playerName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), player.chunkPosition());

        if (claim == null) {
            player.sendSystemMessage(Component.literal("This chunk is not claimed."));
            return 0;
        }

        if (!claim.isOwner(player.getUUID()) && !PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can untrust players."));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found: " + playerName));
            return 0;
        }

        claim.untrust(targetUuid.get());
        SimpleServerUtilities.PLAYER_CLAIMS.save();

        player.sendSystemMessage(Component.literal(playerName + " is no longer trusted in this claim."));
        return 1;
    }

    private static int setFlag(CommandSourceStack source, String flag, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), player.chunkPosition());

        if (claim == null) {
            player.sendSystemMessage(Component.literal("This chunk is not claimed."));
            return 0;
        }

        if (!claim.isOwner(player.getUUID()) && !PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can change claim flags."));
            return 0;
        }

        switch (flag) {
            case "pistons" -> claim.getSettings().setAllowPistons(value);
            case "explosions" -> claim.getSettings().setAllowExplosions(value);
            case "water" -> claim.getSettings().setAllowWaterFlow(value);
            case "lava" -> claim.getSettings().setAllowLavaFlow(value);
            case "otherfluids" -> claim.getSettings().setAllowOtherFluidFlow(value);
            case "redstone" -> claim.getSettings().setAllowRedstone(value);
            case "hoppers" -> claim.getSettings().setAllowHoppers(value);
            case "ownerlessprojectiles" -> claim.getSettings().setAllowOwnerlessProjectiles(value);
            default -> {
                player.sendSystemMessage(Component.literal("Unknown claim flag."));
                return 0;
            }
        }

        SimpleServerUtilities.PLAYER_CLAIMS.save();
        player.sendSystemMessage(Component.literal("Claim flag '" + flag + "' set to " + value + "."));
        return 1;
    }

    private static int flagInfo(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), player.chunkPosition());

        if (claim == null) {
            player.sendSystemMessage(Component.literal("This chunk is not claimed."));
            return 0;
        }

        sendFlagInfo(player, claim);
        return 1;
    }

    private static void sendFlagInfo(ServerPlayer player, PlayerClaim claim) {
        player.sendSystemMessage(Component.literal("Flags:"));
        player.sendSystemMessage(Component.literal(" - Pistons: " + claim.getSettings().isAllowPistons()));
        player.sendSystemMessage(Component.literal(" - Explosions: " + claim.getSettings().isAllowExplosions()));
        player.sendSystemMessage(Component.literal(" - Water: " + claim.getSettings().isAllowWaterFlow()));
        player.sendSystemMessage(Component.literal(" - Lava: " + claim.getSettings().isAllowLavaFlow()));
        player.sendSystemMessage(Component.literal(" - Other fluids: " + claim.getSettings().isAllowOtherFluidFlow()));
        player.sendSystemMessage(Component.literal(" - Redstone: " + claim.getSettings().isAllowRedstone()));
        player.sendSystemMessage(Component.literal(" - Hoppers: " + claim.getSettings().isAllowHoppers()));
        player.sendSystemMessage(Component.literal(" - Ownerless Projectiles: " + claim.getSettings().isAllowOwnerlessProjectiles()));
    }

    private static Optional<UUID> findPlayerUuid(ServerPlayer player, String name) {
        PlayerList playerList = player.level().getServer().getPlayerList();

        ServerPlayer onlinePlayer = playerList.getPlayerByName(name);
        if (onlinePlayer != null) {
            return Optional.of(onlinePlayer.getUUID());
        }

        return Optional.empty();
    }

    private static int help(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("Claim commands:"));
        source.sendSystemMessage(Component.literal(" - /claims claim"));
        source.sendSystemMessage(Component.literal(" - /claims unclaim"));
        source.sendSystemMessage(Component.literal(" - /claims info"));
        source.sendSystemMessage(Component.literal(" - /claims count"));
        source.sendSystemMessage(Component.literal(" - /claims trust <player>"));
        source.sendSystemMessage(Component.literal(" - /claims untrust <player>"));
        source.sendSystemMessage(Component.literal(" - /claims flag <flag> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /claims flags"));
        return 1;
    }
}