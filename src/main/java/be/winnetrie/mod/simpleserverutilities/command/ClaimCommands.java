package be.winnetrie.mod.simpleserverutilities.command;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapChunk;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapData;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimOperationResult;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;


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

                .then(Commands.literal("map")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> map(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("gui")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> gui(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> teleportToOwnClaim(
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

                .then(Commands.literal("admin")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && PermissionService.has(player, PermissionService.CLAIM_BYPASS))

                        .then(Commands.literal("list")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> adminList(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player")
                                        ))))

                        .then(Commands.literal("info")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> adminInfo(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "name")
                                                )))))

                        .then(Commands.literal("tp")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> adminTeleport(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "name")
                                                )))))

                        .then(Commands.literal("setspawn")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> setClaimSpawn(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))

                        .then(Commands.literal("delete")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> adminDelete(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "name")
                                                ))))))

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
            player.sendSystemMessage(Component.literal("You do not have permission to create claims."));
            return 0;
        }

        ClaimOperationResult result = SimpleServerUtilities.PLAYER_CLAIMS.createClaimGroupResult(
                player.level(),
                name,
                player.getUUID()
        );

        if (!result.isSuccess()) {
            sendClaimOperationFailure(player, result);
            return 0;
        }

        player.sendSystemMessage(Component.literal("Claim '" + name + "' created."));
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
            player.sendSystemMessage(Component.literal("Could not delete claim '" + name + "'."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Claim '" + name + "' deleted."));
        return 1;
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        int count = SimpleServerUtilities.PLAYER_CLAIMS.countClaimGroups(player.getUUID());
        int max = SimpleServerUtilities.PLAYER_CLAIMS.getMaxClaimGroups(player.getUUID());

        player.sendSystemMessage(Component.literal("Claims: " + count + " / " + max));

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
            player.sendSystemMessage(Component.literal("Claim not found: " + name));
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

        ClaimOperationResult result = SimpleServerUtilities.PLAYER_CLAIMS.claimChunkResult(
                player.level(),
                chunkPos,
                player.getUUID(),
                name
        );

        if (!result.isSuccess()) {
            sendClaimOperationFailure(player, result);
            return 0;
        }

        player.sendSystemMessage(Component.literal("Chunk " + chunkPos.x() + ", " + chunkPos.z() + " added to claim '" + name + "'."));
        return 1;
    }

    private static int unclaim(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        ChunkPos chunkPos = player.chunkPosition();

        boolean adminBypass = PermissionService.has(player, PermissionService.CLAIM_BYPASS);

        ClaimOperationResult result = SimpleServerUtilities.PLAYER_CLAIMS.unclaimResult(
                player.level(),
                chunkPos,
                player.getUUID(),
                adminBypass
        );

        if (!result.isSuccess()) {
            sendClaimOperationFailure(player, result);
            return 0;
        }

        player.sendSystemMessage(Component.literal("Chunk " + chunkPos.x() + ", " + chunkPos.z() + " unclaimed."));
        return 1;
    }

    private static int trust(CommandSourceStack source, String playerName, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
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

        player.sendSystemMessage(Component.literal(playerName + " is now trusted in claim '" + claimName + "'."));
        return 1;
    }

    private static int untrust(CommandSourceStack source, String playerName, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
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

        player.sendSystemMessage(Component.literal(playerName + " is no longer trusted in claim '" + claimName + "'."));
        return 1;
    }

    private static int setFlag(CommandSourceStack source, String claimName, String flag, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        if (!canEditClaim(player, claim)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can change claim flags."));
            return 0;
        }

        String normalizedFlag = flag.toLowerCase();

        switch (normalizedFlag) {
            case "pvp", "allowpvp" -> claim.getSettings().setAllowPvp(value);
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
                player.sendSystemMessage(Component.literal("Flags: pvp, pistons, explosions, water, lava, otherfluids, redstone, hoppers, ownerlessprojectiles, fire"));
                return 0;
            }
        }

        SimpleServerUtilities.PLAYER_CLAIMS.save();
        player.sendSystemMessage(Component.literal("Claim '" + claimName + "' flag '" + flag + "' set to " + value + "."));
        return 1;
    }

    private static int flagInfo(CommandSourceStack source, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        sendFlagInfo(player, claim);
        return 1;
    }

    private static int setMessage(CommandSourceStack source, String claimName, String message) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        if (!canEditClaim(player, claim)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can change the claim message."));
            return 0;
        }

        claim.setWelcomeMessage(message);
        SimpleServerUtilities.PLAYER_CLAIMS.save();

        player.sendSystemMessage(Component.literal("Welcome message updated for claim '" + claimName + "'."));
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
        executor.sendSystemMessage(Component.literal("Max claims for " + playerName + " set to " + amount + "."));
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

        executor.sendSystemMessage(Component.literal("Added " + amount + " claims to " + playerName + ". New max: " + newMax + "."));
        return 1;
    }

    private static int setClaimSpawn(CommandSourceStack source, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        if (!canEditClaim(player, claim)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can set the claim spawn."));
            return 0;
        }

        String currentDimension = player.level().dimension().identifier().toString();

        if (!claim.getDimension().equals(currentDimension)) {
            player.sendSystemMessage(Component.literal("You must be in the same dimension as this claim."));
            return 0;
        }

        ChunkPos chunkPos = player.chunkPosition();

        if (!claim.hasChunk(chunkPos.x(), chunkPos.z())) {
            player.sendSystemMessage(Component.literal("You must stand inside the claim to set its spawn."));
            return 0;
        }

        claim.setSpawn(player.blockPosition(), player.getYRot(), player.getXRot());
        SimpleServerUtilities.PLAYER_CLAIMS.save();

        player.sendSystemMessage(Component.literal("Spawn set for claim '" + claim.getDisplayName() + "'."));
        return 1;
    }

    private static int teleportToOwnClaim(CommandSourceStack source, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        return teleportToClaim(
                player,
                claim,
                "Teleported to claim '" + claim.getDisplayName() + "'."
        );
    }

    private static int adminList(CommandSourceStack source, String playerName) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        int count = SimpleServerUtilities.PLAYER_CLAIMS.countClaimGroups(targetUuid.get());
        int max = SimpleServerUtilities.PLAYER_CLAIMS.getMaxClaimGroups(targetUuid.get());

        executor.sendSystemMessage(Component.literal("Claims for " + playerName + ": " + count + " / " + max));

        for (PlayerClaim claim : SimpleServerUtilities.PLAYER_CLAIMS.getClaims()) {
            if (!claim.isOwner(targetUuid.get())) {
                continue;
            }

            executor.sendSystemMessage(Component.literal(
                    " - " + claim.getDisplayName()
                            + " | chunks: " + claim.getChunkCount()
                            + " | dimension: " + claim.getDimension()
            ));
        }

        return 1;
    }

    private static int adminInfo(CommandSourceStack source, String playerName, String claimName) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(targetUuid.get(), claimName);

        if (claim == null) {
            executor.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        sendClaimInfo(executor, claim);
        return 1;
    }

    private static int adminTeleport(CommandSourceStack source, String playerName, String claimName) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(targetUuid.get(), claimName);

        if (claim == null) {
            executor.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        return teleportToClaim(
                executor,
                claim,
                "Teleported to " + playerName + "'s claim '" + claim.getDisplayName() + "'."
        );
    }

    private static int adminDelete(CommandSourceStack source, String playerName, String claimName) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        boolean success = SimpleServerUtilities.PLAYER_CLAIMS.deleteClaimGroup(
                targetUuid.get(),
                claimName,
                true
        );

        if (!success) {
            executor.sendSystemMessage(Component.literal("Could not delete claim '" + claimName + "'."));
            return 0;
        }

        executor.sendSystemMessage(Component.literal("Deleted claim '" + claimName + "' from " + playerName + "."));
        return 1;
    }

    private static int teleportToClaim(ServerPlayer player, PlayerClaim claim, String successMessage) {
        ServerLevel level = getClaimLevel(player, claim);

        if (level == null) {
            player.sendSystemMessage(Component.literal("Claim dimension is not loaded: " + claim.getDimension()));
            return 0;
        }

        BlockPos spawnPos = claim.getSpawnPos();

        if (spawnPos != null) {
            player.teleportTo(
                    level,
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    Set.of(),
                    claim.getSpawnYaw(),
                    claim.getSpawnPitch(),
                    true
            );

            player.sendSystemMessage(Component.literal(successMessage));
            return 1;
        }

        BlockPos teleportPos = findClaimTeleportPos(level, claim);

        if (teleportPos == null) {
            player.sendSystemMessage(Component.literal("This claim has no chunks to teleport to."));
            return 0;
        }

        player.teleportTo(
                level,
                teleportPos.getX() + 0.5,
                teleportPos.getY(),
                teleportPos.getZ() + 0.5,
                Set.of(),
                player.getYRot(),
                player.getXRot(),
                true
        );

        player.sendSystemMessage(Component.literal(successMessage));
        return 1;
    }

    private static ServerLevel getClaimLevel(ServerPlayer player, PlayerClaim claim) {
        try {
            Identifier dimensionId = Identifier.parse(claim.getDimension());
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

            return player.level().getServer().getLevel(dimension);
        } catch (Exception e) {
            return null;
        }
    }

    private static BlockPos findClaimTeleportPos(ServerLevel level, PlayerClaim claim) {
        if (claim.getChunks().isEmpty()) {
            return null;
        }

        ClaimChunk firstChunk = claim.getChunks().iterator().next();

        int blockX = firstChunk.getX() * 16 + 8;
        int blockZ = firstChunk.getZ() * 16 + 8;

        return level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(blockX, 0, blockZ)
        );
    }

    private static void sendClaimInfo(ServerPlayer player, PlayerClaim claim) {
        int usedChunks = SimpleServerUtilities.PLAYER_CLAIMS.countClaimChunks(claim.getOwner());
        int maxChunks = SimpleServerUtilities.PLAYER_CLAIMS.getMaxChunks(claim.getOwner());

        player.sendSystemMessage(Component.literal("Claim group: " + claim.getDisplayName()));
        player.sendSystemMessage(Component.literal("Owner UUID: " + claim.getOwner()));
        player.sendSystemMessage(Component.literal("Dimension: " + claim.getDimension()));
        player.sendSystemMessage(Component.literal("Chunks in this claim: " + claim.getChunkCount()));

        if (claim.hasSpawn()) {
            BlockPos spawn = claim.getSpawnPos();
            player.sendSystemMessage(Component.literal("Spawn: " + spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ()));
        } 
        else {
            player.sendSystemMessage(Component.literal("Spawn: not set"));
        }

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
        player.sendSystemMessage(Component.literal(" - PvP: " + claim.getSettings().isAllowPvp()));
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
        //source.sendSystemMessage(Component.literal(" - /claims flag <name> <pvp|pistons|explosions|water|lava|otherfluids|redstone|hoppers|ownerlessprojectiles|fire> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /claims flags <name>"));
        source.sendSystemMessage(Component.literal(" - /claims msg <name> <message>"));
        source.sendSystemMessage(Component.literal(" - /claims map <name>"));
        source.sendSystemMessage(Component.literal(" - /claims tp <name>"));
        source.sendSystemMessage(Component.literal(" - /claims setspawn <name>"));

        source.sendSystemMessage(Component.literal("Admin commands:"));
        source.sendSystemMessage(Component.literal(" - /claims chunks <player> set <number>"));
        source.sendSystemMessage(Component.literal(" - /claims chunks <player> add <number>"));
        source.sendSystemMessage(Component.literal(" - /claims groups <player> set <number>"));
        source.sendSystemMessage(Component.literal(" - /claims groups <player> add <number>"));
        source.sendSystemMessage(Component.literal(" - /claims admin list <player>"));
        source.sendSystemMessage(Component.literal(" - /claims admin info <player> <name>"));
        source.sendSystemMessage(Component.literal(" - /claims admin tp <player> <name>"));
        source.sendSystemMessage(Component.literal(" - /claims admin delete <player> <name>"));

        return 1;
    }

    private static void sendClaimOperationFailure(ServerPlayer player, ClaimOperationResult result) {
        switch (result.getType()) {
            case PLAYER_CLAIMS_DISABLED ->
                    player.sendSystemMessage(Component.literal("Player claims are disabled."));

            case CLAIM_GROUP_NOT_FOUND ->
                    player.sendSystemMessage(Component.literal("Claim not found: " + result.getDetails()));

            case CLAIM_GROUP_ALREADY_EXISTS ->
                    player.sendSystemMessage(Component.literal("A claim with that name already exists: " + result.getDetails()));

            case CLAIM_GROUP_LIMIT_REACHED ->
                    player.sendSystemMessage(Component.literal("You reached the maximum amount of claims. " + result.getDetails()));

            case CLAIM_GROUP_CHUNK_LIMIT_REACHED ->
                player.sendSystemMessage(Component.literal("This claim reached its maximum size. " + result.getDetails()));

            case WRONG_DIMENSION ->
                    player.sendSystemMessage(Component.literal("This claim belongs to another dimension. " + result.getDetails()));

            case CHUNK_ALREADY_CLAIMED ->
                    player.sendSystemMessage(Component.literal("This chunk is already claimed. " + result.getDetails()));

            case CHUNK_NOT_CLAIMED ->
                    player.sendSystemMessage(Component.literal("This chunk is not claimed. " + result.getDetails()));

            case CHUNK_LIMIT_REACHED ->
                    player.sendSystemMessage(Component.literal("You reached the maximum amount of claim chunks. " + result.getDetails()));

            case CHUNK_NOT_ADJACENT ->
                    player.sendSystemMessage(Component.literal("This chunk must be adjacent to the claim. " + result.getDetails()));

            case CHUNK_REMOVAL_DISCONNECTS_CLAIM ->
                    player.sendSystemMessage(Component.literal("You cannot unclaim this chunk because it would split the claim. Remove outer chunks first or delete the claim. " + result.getDetails()));

            case CHUNK_OVERLAPS_REGION ->
                    player.sendSystemMessage(Component.literal("This chunk overlaps a region and cannot be claimed. " + result.getDetails()));

            case NOT_OWNER ->
                    player.sendSystemMessage(Component.literal("You are not the owner of this claim: " + result.getDetails()));

            case SUCCESS ->
                    player.sendSystemMessage(Component.literal("Operation completed successfully."));
        }
    }

    private static int map(CommandSourceStack source, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        ClaimMapData data = SimpleServerUtilities.PLAYER_CLAIMS.getMapData(
                player,
                4,
                claimName
        );

        player.sendSystemMessage(Component.literal("Claim map for '" + claimName + "':"));
        player.sendSystemMessage(Component.literal("Legend: P=current, .=free, M=mine, T=trusted, O=other, R=region"));

        //int size = data.getRadius() * 2 + 1;

        for (int dz = -data.getRadius(); dz <= data.getRadius(); dz++) {
            StringBuilder line = new StringBuilder();

            for (int dx = -data.getRadius(); dx <= data.getRadius(); dx++) {
                int chunkX = data.getCenterChunkX() + dx;
                int chunkZ = data.getCenterChunkZ() + dz;

                ClaimMapChunk chunk = findMapChunk(data, chunkX, chunkZ);

                if (chunk == null) {
                    line.append("? ");
                    continue;
                }

                line.append(getMapSymbol(chunk)).append(" ");
            }

            player.sendSystemMessage(Component.literal(line.toString()));
        }

        return 1;
    }

    private static ClaimMapChunk findMapChunk(ClaimMapData data, int chunkX, int chunkZ) {
        for (ClaimMapChunk chunk : data.getChunks()) {
            if (chunk.getChunkX() == chunkX && chunk.getChunkZ() == chunkZ) {
                return chunk;
            }
        }

        return null;
    }

    private static String getMapSymbol(ClaimMapChunk chunk) {
        if (chunk.isCurrentChunk()) {
            return "P";
        }

        return switch (chunk.getStatus()) {
            case WILDERNESS -> ".";
            case OWNED_BY_SELF -> "M";
            case OWNED_BY_TRUSTED -> "T";
            case OWNED_BY_OTHER -> "O";
            case REGION -> "R";
        };
    }

    private static int gui(CommandSourceStack source, String claimName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);

        if (claim == null) {
            player.sendSystemMessage(Component.literal("Claim not found: " + claimName));
            return 0;
        }

        ClaimMapData data = SimpleServerUtilities.PLAYER_CLAIMS.getMapData(
                player,
                4,
                claimName
        );

        PacketDistributor.sendToPlayer(
                player,
                ClaimMapDataPayload.from(data)
        );

        return 1;
    }
}