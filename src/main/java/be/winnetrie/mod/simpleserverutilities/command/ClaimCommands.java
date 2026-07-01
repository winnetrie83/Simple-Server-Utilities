package be.winnetrie.mod.simpleserverutilities.command;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> create(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> delete(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))

                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> info(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("claimchunk")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> claimChunk(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("unclaim")
                        .executes(context -> unclaim(context.getSource())))

                .then(Commands.literal("trust")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> trust(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "name")
                                        )))))

                .then(Commands.literal("untrust")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> untrust(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "name")
                                        )))))

                .then(Commands.literal("flag")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("flag", StringArgumentType.word())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(context -> setFlag(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "flag"),
                                                        BoolArgumentType.getBool(context, "value")
                                                ))))))

                .then(Commands.literal("flags")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> flagInfo(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("msg")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> setMessage(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "message")
                                        )))))

                .then(Commands.literal("chunks")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && PermissionService.has(player, PermissionService.CLAIM_BYPASS))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.literal("set")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(0))
                                                .executes(context -> setMaxChunks(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "number")
                                                ))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(0))
                                                .executes(context -> addMaxChunks(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "number")
                                                ))))))

                .then(Commands.literal("groups")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && PermissionService.has(player, PermissionService.CLAIM_BYPASS))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.literal("set")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(0))
                                                .executes(context -> setMaxGroups(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "number")
                                                ))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(0))
                                                .executes(context -> addMaxGroups(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "number")
                                                ))))));
    }

    private static int create(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            player.sendSystemMessage(Component.literal("Player claims are disabled."));
            return 0;
        }

        if (!PermissionService.has(player, PermissionService.CLAIM_CREATE)) {
            player.sendSystemMessage(Component.literal("You do not have permission to create claim groups."));
            return 0;
        }

        boolean success = SimpleServerUtilities.PLAYER_CLAIMS.createClaimGroup(
                player.level(),
                name,
                player.getUUID()
        );

        if (!success) {
            player.sendSystemMessage(Component.literal("Could not create claim group. The name may already exist or you reached your group limit."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Claim group '" + name + "' created."));
        return 1;
    }

    private static int delete(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        boolean adminBypass = PermissionService.has(player, PermissionService.CLAIM_BYPASS);

        boolean success = SimpleServerUtilities.PLAYER_CLAIMS.deleteClaimGroup(
                player.getUUID(),
                name,
                adminBypass
        );

        if (!success) {
            player.sendSystemMessage(Component.literal("Could not delete claim group '" + name + "'."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Claim group '" + name + "' deleted."));
        return 1;
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        int count = SimpleServerUtilities.PLAYER_CLAIMS.countClaimGroups(player.getUUID());
        int max = SimpleServerUtilities.PLAYER_CLAIMS.getMaxClaimGroups(player.getUUID());

        player.sendSystemMessage(Component.literal("Claim groups: " + count + " / " + max));

        for (PlayerClaim claim : SimpleServerUtilities.PLAYER_CLAIMS.getClaims()) {
            if (!claim.isOwner(player.getUUID())) {
                continue;
            }

            player.sendSystemMessage(Component.literal(
                    " - " + claim.getDisplayName()
                            + " | chunks: " + claim.getChunkCount()
                            + " | dimension: " + claim.getDimension()
            ));
        }

        return 1;
    }

    private static int info(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), name);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim group not found: " + name));
            return 0;
        }

        sendClaimInfo(player, claim);
        return 1;
    }

    private static int claimChunk(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            player.sendSystemMessage(Component.literal("Player claims are disabled."));
            return 0;
        }

        if (!PermissionService.has(player, PermissionService.CLAIM_CREATE)) {
            player.sendSystemMessage(Component.literal("You do not have permission to claim chunks."));
            return 0;
        }

        ChunkPos chunkPos = player.chunkPosition();

        boolean success = SimpleServerUtilities.PLAYER_CLAIMS.claimChunk(
                player.level(),
                chunkPos,
                player.getUUID(),
                name
        );

        if (!success) {
            player.sendSystemMessage(Component.literal("Could not claim this chunk. It may already be claimed, overlap a region, or you reached your chunk limit."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Chunk " + chunkPos.x() + ", " + chunkPos.z() + " added to claim group '" + name + "'."));
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

        player.sendSystemMessage(Component.literal("Chunk " + chunkPos.x() + ", " + chunkPos.z() + " unclaimed."));
        return 1;
    }

    private static int trust(CommandSourceStack source, String playerName, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim group not found: " + claimName));
            return 0;
        }

        if (!canEditClaim(player, claim)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can trust players."));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        if (claim.isOwner(targetUuid.get())) {
            player.sendSystemMessage(Component.literal("The owner is already trusted."));
            return 0;
        }

        claim.trust(targetUuid.get());
        SimpleServerUtilities.PLAYER_CLAIMS.save();

        player.sendSystemMessage(Component.literal(playerName + " is now trusted in claim group '" + claimName + "'."));
        return 1;
    }

    private static int untrust(CommandSourceStack source, String playerName, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim group not found: " + claimName));
            return 0;
        }

        if (!canEditClaim(player, claim)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can untrust players."));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        claim.untrust(targetUuid.get());
        SimpleServerUtilities.PLAYER_CLAIMS.save();

        player.sendSystemMessage(Component.literal(playerName + " is no longer trusted in claim group '" + claimName + "'."));
        return 1;
    }

    private static int setFlag(CommandSourceStack source, String claimName, String flag, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim group not found: " + claimName));
            return 0;
        }

        if (!canEditClaim(player, claim)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can change claim flags."));
            return 0;
        }

        String normalizedFlag = flag.toLowerCase();

        switch (normalizedFlag) {
            case "pistons", "allowpistons" -> claim.getSettings().setAllowPistons(value);
            case "explosions", "allowexplosions" -> claim.getSettings().setAllowExplosions(value);
            case "water", "allowwaterflow" -> claim.getSettings().setAllowWaterFlow(value);
            case "lava", "allowlavaflow" -> claim.getSettings().setAllowLavaFlow(value);
            case "otherfluids", "allowotherfluidflow" -> claim.getSettings().setAllowOtherFluidFlow(value);
            case "redstone", "allowredstone" -> claim.getSettings().setAllowRedstone(value);
            case "hoppers", "allowhoppers" -> claim.getSettings().setAllowHoppers(value);
            case "ownerlessprojectiles", "allowownerlessprojectiles" -> claim.getSettings().setAllowOwnerlessProjectiles(value);
            case "fire", "firespread", "allowfirespread" -> claim.getSettings().setAllowFireSpread(value);
            default -> {
                player.sendSystemMessage(Component.literal("Unknown claim flag: " + flag));
                return 0;
            }
        }

        SimpleServerUtilities.PLAYER_CLAIMS.save();
        player.sendSystemMessage(Component.literal("Claim group '" + claimName + "' flag '" + flag + "' set to " + value + "."));
        return 1;
    }

    private static int flagInfo(CommandSourceStack source, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim group not found: " + claimName));
            return 0;
        }

        sendFlagInfo(player, claim);
        return 1;
    }

    private static int setMessage(CommandSourceStack source, String claimName, String message) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim group not found: " + claimName));
            return 0;
        }

        if (!canEditClaim(player, claim)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can change the claim message."));
            return 0;
        }

        claim.setWelcomeMessage(message);
        SimpleServerUtilities.PLAYER_CLAIMS.save();

        player.sendSystemMessage(Component.literal("Welcome message updated for claim group '" + claimName + "'."));
        return 1;
    }

    private static int setMaxChunks(CommandSourceStack source, String playerName, int amount) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        SimpleServerUtilities.PLAYER_CLAIMS.setMaxChunks(targetUuid.get(), amount);
        executor.sendSystemMessage(Component.literal("Max claim chunks for " + playerName + " set to " + amount + "."));
        return 1;
    }

    private static int addMaxChunks(CommandSourceStack source, String playerName, int amount) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        SimpleServerUtilities.PLAYER_CLAIMS.addMaxChunks(targetUuid.get(), amount);
        int newMax = SimpleServerUtilities.PLAYER_CLAIMS.getMaxChunks(targetUuid.get());

        executor.sendSystemMessage(Component.literal("Added " + amount + " claim chunks to " + playerName + ". New max: " + newMax + "."));
        return 1;
    }

    private static int setMaxGroups(CommandSourceStack source, String playerName, int amount) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        SimpleServerUtilities.PLAYER_CLAIMS.setMaxClaimGroups(targetUuid.get(), amount);
        executor.sendSystemMessage(Component.literal("Max claim groups for " + playerName + " set to " + amount + "."));
        return 1;
    }

    private static int addMaxGroups(CommandSourceStack source, String playerName, int amount) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        SimpleServerUtilities.PLAYER_CLAIMS.addMaxClaimGroups(targetUuid.get(), amount);
        int newMax = SimpleServerUtilities.PLAYER_CLAIMS.getMaxClaimGroups(targetUuid.get());

        executor.sendSystemMessage(Component.literal("Added " + amount + " claim groups to " + playerName + ". New max: " + newMax + "."));
        return 1;
    }

    private static void sendClaimInfo(ServerPlayer player, PlayerClaim claim) {
        int usedChunks = SimpleServerUtilities.PLAYER_CLAIMS.countClaimChunks(claim.getOwner());
        int maxChunks = SimpleServerUtilities.PLAYER_CLAIMS.getMaxChunks(claim.getOwner());

        player.sendSystemMessage(Component.literal("Claim group: " + claim.getDisplayName()));
        player.sendSystemMessage(Component.literal("Owner UUID: " + claim.getOwner()));
        player.sendSystemMessage(Component.literal("Dimension: " + claim.getDimension()));
        player.sendSystemMessage(Component.literal("Chunks in this group: " + claim.getChunkCount()));
        player.sendSystemMessage(Component.literal("Owner total chunks: " + usedChunks + " / " + maxChunks));
        player.sendSystemMessage(Component.literal("Trusted players: " + claim.getTrustedPlayers().size()));
        player.sendSystemMessage(Component.literal("Created at: " + formatTimestamp(claim.getCreatedAt())));
        player.sendSystemMessage(Component.literal("Last chunk change: " + formatTimestamp(claim.getLastChunkChangeAt())));

        if (!claim.getWelcomeMessage().isBlank()) {
            player.sendSystemMessage(Component.literal("Message: " + claim.getWelcomeMessage()));
        }

        sendFlagInfo(player, claim);
    }

    private static void sendFlagInfo(ServerPlayer player, PlayerClaim claim) {
        player.sendSystemMessage(Component.literal("Flags for '" + claim.getDisplayName() + "':"));
        player.sendSystemMessage(Component.literal(" - Pistons: " + claim.getSettings().isAllowPistons()));
        player.sendSystemMessage(Component.literal(" - Explosions: " + claim.getSettings().isAllowExplosions()));
        player.sendSystemMessage(Component.literal(" - Water: " + claim.getSettings().isAllowWaterFlow()));
        player.sendSystemMessage(Component.literal(" - Lava: " + claim.getSettings().isAllowLavaFlow()));
        player.sendSystemMessage(Component.literal(" - Other fluids: " + claim.getSettings().isAllowOtherFluidFlow()));
        player.sendSystemMessage(Component.literal(" - Redstone: " + claim.getSettings().isAllowRedstone()));
        player.sendSystemMessage(Component.literal(" - Hoppers: " + claim.getSettings().isAllowHoppers()));
        player.sendSystemMessage(Component.literal(" - Ownerless projectiles: " + claim.getSettings().isAllowOwnerlessProjectiles()));
        player.sendSystemMessage(Component.literal(" - Fire spread: " + claim.getSettings().isAllowFireSpread()));
    }

    private static boolean canEditClaim(ServerPlayer player, PlayerClaim claim) {
        return claim.isOwner(player.getUUID()) || PermissionService.has(player, PermissionService.CLAIM_BYPASS);
    }

    private static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "unknown";
        }

        return Instant.ofEpochMilli(timestamp).toString();
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
        source.sendSystemMessage(Component.literal(" - /claims create <name>"));
        source.sendSystemMessage(Component.literal(" - /claims delete <name>"));
        source.sendSystemMessage(Component.literal(" - /claims list"));
        source.sendSystemMessage(Component.literal(" - /claims info <name>"));
        source.sendSystemMessage(Component.literal(" - /claims claimchunk <name>"));
        source.sendSystemMessage(Component.literal(" - /claims unclaim"));
        source.sendSystemMessage(Component.literal(" - /claims trust <player> <name>"));
        source.sendSystemMessage(Component.literal(" - /claims untrust <player> <name>"));
        source.sendSystemMessage(Component.literal(" - /claims flag <name> <flag> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /claims flags <name>"));
        source.sendSystemMessage(Component.literal(" - /claims msg <name> <message>"));
        source.sendSystemMessage(Component.literal("Admin commands:"));
        source.sendSystemMessage(Component.literal(" - /claims chunks <player> set <number>"));
        source.sendSystemMessage(Component.literal(" - /claims chunks <player> add <number>"));
        source.sendSystemMessage(Component.literal(" - /claims groups <player> set <number>"));
        source.sendSystemMessage(Component.literal(" - /claims groups <player> add <number>"));
        return 1;
    }
}