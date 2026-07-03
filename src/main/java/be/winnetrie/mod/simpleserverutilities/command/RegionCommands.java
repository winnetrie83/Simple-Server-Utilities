package be.winnetrie.mod.simpleserverutilities.command;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelection;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionManager;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.region.RegionOperationResult;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentData;
import net.minecraft.world.level.block.Blocks;


import java.util.Set;
import java.io.IOException;
import java.util.Map;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.players.PlayerList;


public class RegionCommands {

    private static final RegionSelectionManager SELECTIONS = new RegionSelectionManager();
    private static final long MAX_REGION_BLOCK_OPERATION_VOLUME = 1_000_000L;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("regions")
                .requires(source -> source.getEntity() instanceof ServerPlayer)

                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())))

                .then(Commands.literal("point1")
                        .executes(context -> setPoint1ToCurrentPosition(context.getSource()))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> setPoint1(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")
                                ))))

                .then(Commands.literal("point2")
                        .executes(context -> setPoint2ToCurrentPosition(context.getSource()))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> setPoint2(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")
                                ))))

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

                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> info(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))

                .then(Commands.literal("rentals")
                        .executes(context -> listRentals(context.getSource())))

                .then(Commands.literal("addowner")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> addOwner(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "player")
                                        )))))

                .then(Commands.literal("removeowner")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> removeOwner(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "player")
                                        )))))

                .then(Commands.literal("addmember")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> addMember(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "player")
                                        )))))

                .then(Commands.literal("removemember")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> removeMember(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "player")
                                        )))))

                .then(Commands.literal("rent")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> rent(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("unrent")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> unrent(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("setrent")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("days", IntegerArgumentType.integer(-1))
                                                .executes(context -> setRent(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        IntegerArgumentType.getInteger(context, "days")
                                                ))))))

                .then(Commands.literal("setrentable")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> setRentable(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                BoolArgumentType.getBool(context, "value")
                                        )))))

                .then(Commands.literal("setflag")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("flag", StringArgumentType.word())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(context -> setFlag(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "flag"),
                                                        BoolArgumentType.getBool(context, "value")
                                                ))))))



                .then(Commands.literal("perm")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.literal("list")
                                        .executes(context -> listPermissionOverrides(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        )))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(context -> setPermissionOverride(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "name"),
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")
                                                        )))))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .executes(context -> unsetPermissionOverride(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "key")
                                                ))))))

                .then(Commands.literal("setspawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> setSpawn(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> teleport(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("redefine")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> redefine(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> save(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("reset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> reset(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("clear")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> clear(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("renting")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> enableRenting(
                                        context.getSource(),
                                        BoolArgumentType.getBool(context, "value")
                                ))));
    }

    private static int setPoint1ToCurrentPosition(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        return setPoint1(source, player.blockPosition());
    }

    private static int setPoint2ToCurrentPosition(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        return setPoint2(source, player.blockPosition());
    }

    private static int setPoint1(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        SELECTIONS.setPoint1(player, pos);
        player.sendSystemMessage(Component.literal("Region point 1 set to " + formatPos(pos) + "."));
        return 1;
    }

    private static int setPoint2(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        SELECTIONS.setPoint2(player, pos);
        player.sendSystemMessage(Component.literal("Region point 2 set to " + formatPos(pos) + "."));
        return 1;
    }

    private static int create(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canCreateRegions(player)) {
            return 0;
        }

        RegionSelection selection = SELECTIONS.getSelection(player);

        if (!selection.isComplete()) {
            player.sendSystemMessage(Component.literal("You must set point1 and point2 first."));
            return 0;
        }

        RegionOperationResult result = SimpleServerUtilities.REGIONS.create(name, selection.getDimension(), selection.getPoint1(), selection.getPoint2());

        switch (result.getType()) {
            case SUCCESS -> {
                player.sendSystemMessage(Component.literal("Region '" + name + "' created."));
                SELECTIONS.clear(player);
                return 1;
            }

            case NAME_ALREADY_EXISTS -> {
                player.sendSystemMessage(Component.literal("A region with that name already exists: " + result.getDetails()));
                return 0;
            }

            case OVERLAPS_PLAYER_CLAIM -> {
                player.sendSystemMessage(Component.literal("This region overlaps an existing player claim: " + result.getDetails()));
                return 0;
            }

            case INVALID_REGION_OVERLAP -> {
                player.sendSystemMessage(Component.literal("This region overlaps another region incorrectly: " + result.getDetails()));
                return 0;
            }

            case REGION_NOT_FOUND -> {
                player.sendSystemMessage(Component.literal("Region not found: " + result.getDetails()));
                return 0;
            }
        }
        return 0;
    }

    private static int delete(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canDeleteRegions(player)) {
            return 0;
        }

        boolean success = SimpleServerUtilities.REGIONS.delete(name);

        if (!success) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Region '" + name + "' deleted."));
        return 1;
    }

    private static int info(CommandSourceStack source, String name) {
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            source.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        source.sendSystemMessage(Component.literal("Region: " + region.getName()));
        source.sendSystemMessage(Component.literal("Bounds: " + region.getBoundsText()));
        source.sendSystemMessage(Component.literal("Owners: " + region.getOwners().size()));
        source.sendSystemMessage(Component.literal("Members: " + region.getMembers().size()));

        if (region.getSpawnPos() != null) {
            source.sendSystemMessage(Component.literal("Spawn: " + formatPos(region.getSpawnPos()) + " yaw=" + region.getSpawnYaw() + " pitch=" + region.getSpawnPitch()));
        } 
        else {
            source.sendSystemMessage(Component.literal("Spawn: not set"));
        }

        source.sendSystemMessage(Component.literal("Global renting enabled: " + SimpleServerUtilities.REGIONS.isRentingEnabled()));
        source.sendSystemMessage(Component.literal("Rentable: " + region.getRentData().isRentable()));

        RegionRentData rentData = region.getRentData();

        source.sendSystemMessage(Component.literal("Rentable: " + rentData.isRentable()));
        source.sendSystemMessage(Component.literal("Rented: " + rentData.isRented()));
        source.sendSystemMessage(Component.literal("Rent amount: " + rentData.getAmount()));
        source.sendSystemMessage(Component.literal("Rent period days: " + rentData.getPeriodDays()));

        if (rentData.isRented()) {
            source.sendSystemMessage(Component.literal("Rent remaining: " + formatRentRemaining(rentData)));

            if (isOp((ServerPlayer) source.getEntity()) && rentData.getRenter() != null) {
                source.sendSystemMessage(Component.literal("Renter UUID: " + rentData.getRenter()));
            }
        }

        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (SimpleServerUtilities.REGIONS.getAll().isEmpty()) {
            source.sendSystemMessage(Component.literal("No regions exist yet."));
            return 0;
        }

        source.sendSystemMessage(Component.literal("Regions:"));

        for (Region region : SimpleServerUtilities.REGIONS.getAll()) {
            source.sendSystemMessage(Component.literal(" - " + region.getName() + " " + region.getBoundsText()));
        }

        return 1;
    }

    private static int listRentals(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        boolean admin = isOp(player);

        int count = 0;

        source.sendSystemMessage(Component.literal("Region rentals:"));

        for (Region region : SimpleServerUtilities.REGIONS.getAll()) {
            RegionRentData rentData = region.getRentData();

            if (!rentData.isRentable() && !rentData.isRented()) {
                continue;
            }

            count++;

            String status;

            if (rentData.isRented()) {
                status = rentData.isRentable() ? "rented" : "rented, disabled after end";
            } else {
                status = "available";
            }

            String period = rentData.isPermanent()
                    ? "permanent"
                    : rentData.getPeriodDays() + " day(s)";

            String line = " - " + region.getName()
                    + " | " + status
                    + " | " + rentData.getAmount()
                    + " / " + period;

            if (rentData.isRented()) {
                line += " | " + formatRentRemaining(rentData);

                if (admin && rentData.getRenter() != null) {
                    line += " | renter: " + rentData.getRenter();
                }
            }

            source.sendSystemMessage(Component.literal(line));
        }

        if (count == 0) {
            source.sendSystemMessage(Component.literal("No rentable or rented regions found."));
            return 0;
        }

        return 1;
    }

    private static int addOwner(CommandSourceStack source, String name, String playerName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found: " + playerName));
            return 0;
        }

        region.addOwner(targetUuid.get());
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal(playerName + " is now an owner of region '" + name + "'."));
        return 1;
    }

    private static int removeOwner(CommandSourceStack source, String name, String playerName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found: " + playerName));
            return 0;
        }

        region.removeOwner(targetUuid.get());
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal(playerName + " is no longer an owner of region '" + name + "'."));
        return 1;
    }

    private static int addMember(CommandSourceStack source, String name, String playerName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found: " + playerName));
            return 0;
        }

        region.addMember(targetUuid.get());
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal(playerName + " is now a member of region '" + name + "'."));
        return 1;
    }

    private static int removeMember(CommandSourceStack source, String name, String playerName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        Optional<UUID> targetUuid = findPlayerUuid(player, playerName);

        if (targetUuid.isEmpty()) {
            player.sendSystemMessage(Component.literal("Player not found: " + playerName));
            return 0;
        }

        region.removeMember(targetUuid.get());
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal(playerName + " is no longer a member of region '" + name + "'."));
        return 1;
    }

    private static int rent(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        if (!SimpleServerUtilities.REGIONS.isRentingEnabled()) {
            player.sendSystemMessage(Component.literal("Region renting is currently disabled."));
            return 0;
        }

        if (!region.getRentData().isRentable()) {
            player.sendSystemMessage(Component.literal("This region is not rentable."));
            return 0;
        }

        if (region.getRentData().isRented()) {
            player.sendSystemMessage(Component.literal("This region is already rented."));
            return 0;
        }

        region.getRentData().setRenter(player.getUUID());

        if (region.getRentData().isPermanent()) {
            region.getRentData().setRentEndTime(-1L);
        } else {
            long durationMillis = region.getRentData().getPeriodDays() * 24L * 60L * 60L * 1000L;
            region.getRentData().setRentEndTime(System.currentTimeMillis() + durationMillis);
        }

        region.addMember(player.getUUID());
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal("You rented region '" + name + "'."));
        return 1;
    }

    private static int unrent(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        boolean isRenter = player.getUUID().equals(region.getRentData().getRenter());

        if (!isRenter && !canEditRegions(player)) {
            player.sendSystemMessage(Component.literal("You cannot unrent this region."));
            return 0;
        }

        if (!region.getRentData().isRented()) {
            player.sendSystemMessage(Component.literal("This region is not currently rented."));
            return 0;
        }

        if (!resetRegionIfSnapshotExists(player, region)) {
            return 0;
        }

        UUID renter = region.getRentData().getRenter();

        if (renter != null) {
            region.removeMember(renter);
        }

        region.getRentData().setRenter(null);
        region.getRentData().setRentEndTime(-1L);

        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal("Region '" + name + "' is no longer rented."));
        return 1;
    }

    private static boolean resetRegionIfSnapshotExists(ServerPlayer player, Region region) {
        if (!SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
            player.sendSystemMessage(Component.literal(
                    "No saved snapshot exists for region '" + region.getName() + "'. Region was not reset."
            ));
            return true;
        }

        ServerLevel level = player.level().getServer().getLevel(region.getDimension());

        if (level == null) {
            player.sendSystemMessage(Component.literal("Region dimension is not loaded."));
            return false;
        }

        long volume = region.getVolume();

        if (volume > MAX_REGION_BLOCK_OPERATION_VOLUME) {
            player.sendSystemMessage(Component.literal("Region is too large to reset safely: " + volume + " blocks."));
            player.sendSystemMessage(Component.literal("Current safety limit: " + MAX_REGION_BLOCK_OPERATION_VOLUME + " blocks."));
            return false;
        }

        try {
            int restoredBlocks = SimpleServerUtilities.REGION_SNAPSHOTS.reset(level, region);

            player.sendSystemMessage(Component.literal(
                    "Region '" + region.getName() + "' was reset to its saved snapshot. Restored "
                            + restoredBlocks + " block(s)."
            ));

            return true;
        } catch (IOException | IllegalStateException e) {
            SimpleServerUtilities.LOGGER.error("Failed to reset region snapshot for '{}'.", region.getName(), e);
            player.sendSystemMessage(Component.literal("Failed to reset region snapshot: " + e.getMessage()));
            return false;
        }
    }

    private static int setRent(CommandSourceStack source, String name, int amount, int days) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.getRentData().setRentable(true);
        region.getRentData().setAmount(amount);
        region.getRentData().setPeriodDays(days);

        SimpleServerUtilities.REGIONS.save();

        if (days == -1) {
            player.sendSystemMessage(Component.literal("Region '" + name + "' is now rentable for " + amount + " permanently."));
        } else {
            player.sendSystemMessage(Component.literal("Region '" + name + "' is now rentable for " + amount + " every " + days + " day(s)."));
        }

        return 1;
    }

    private static int setRentable(CommandSourceStack source, String name, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.getRentData().setRentable(value);
        SimpleServerUtilities.REGIONS.save();

        if (!value && region.getRentData().isRented()) {
            player.sendSystemMessage(Component.literal(
                    "Region '" + name + "' is still currently rented, but it is no longer rentable after this rent ends."
            ));
            return 1;
        }

        player.sendSystemMessage(Component.literal(
                "Region '" + name + "' rentable set to " + value + "."
        ));

        return 1;
    }

    private static int setFlag(CommandSourceStack source, String name, String flag, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        switch (flag) {
            case "break" -> region.getSettings().setAllowBlockBreak(value);
            case "place" -> region.getSettings().setAllowBlockPlace(value);
            case "interact" -> region.getSettings().setAllowInteract(value);
            case "pvp" -> region.getSettings().setAllowPvp(value);
            case "explosions" -> region.getSettings().setAllowExplosions(value);
            case "pistons" -> region.getSettings().setAllowPistons(value);
            case "water" -> region.getSettings().setAllowWaterFlow(value);
            case "lava" -> region.getSettings().setAllowLavaFlow(value);
            case "redstone" -> region.getSettings().setAllowRedstone(value);
            case "hoppers" -> region.getSettings().setAllowHoppers(value);
            case "allowfirespread" -> region.getSettings().setAllowFireSpread(value);
            default -> {
                player.sendSystemMessage(Component.literal("Unknown region flag."));
                player.sendSystemMessage(Component.literal("Flags: break, place, interact, pvp, explosions, pistons, water, lava, redstone, hoppers"));
                return 0;
            }
        }

        SimpleServerUtilities.REGIONS.save();
        player.sendSystemMessage(Component.literal("Region flag '" + flag + "' set to " + value + "."));
        return 1;
    }

    private static int listPermissionOverrides(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        Map<String, String> overrides = region.getPermissionOverrides();

        player.sendSystemMessage(Component.literal("Permission overrides for region '" + region.getName() + "':"));

        if (overrides.isEmpty()) {
            player.sendSystemMessage(Component.literal(" - none"));
            return 1;
        }

        overrides.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> player.sendSystemMessage(Component.literal(
                        " - " + entry.getKey() + " = " + entry.getValue()
                )));

        return 1;
    }

    private static int setPermissionOverride(CommandSourceStack source, String name, String key, String value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.setPermissionOverride(key, value);
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal(
                "Set region permission override " + key + " = " + value + " for '" + region.getName() + "'."
        ));
        return 1;
    }

    private static int unsetPermissionOverride(CommandSourceStack source, String name, String key) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        boolean existed = region.getPermissionOverrides().containsKey(key);
        region.removePermissionOverride(key);
        SimpleServerUtilities.REGIONS.save();

        if (!existed) {
            player.sendSystemMessage(Component.literal("Permission override was not set on region '" + region.getName() + "': " + key));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Removed region permission override " + key + " from '" + region.getName() + "'."));
        return 1;
    }

    private static int setSpawn(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.setSpawn(player.blockPosition(), player.getYRot(), player.getXRot());
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal("Spawn set for region '" + name + "'."));
        return 1;
    }

    private static int teleport(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        if (region.getSpawnPos() == null) {
            player.sendSystemMessage(Component.literal("This region has no spawn set."));
            return 0;
        }

        if (!region.hasAccess(player.getUUID()) && !isOp(player)) {
            player.sendSystemMessage(Component.literal("You do not have access to this region."));
            return 0;
        }

        ServerLevel level = player.level().getServer().getLevel(region.getDimension());

        if (level == null) {
            player.sendSystemMessage(Component.literal("Region dimension is not loaded."));
            return 0;
        }

        player.teleportTo(
                level,
                region.getSpawnPos().getX() + 0.5,
                region.getSpawnPos().getY(),
                region.getSpawnPos().getZ() + 0.5,
                Set.of(),
                region.getSpawnYaw(),
                region.getSpawnPitch(),
                true
        );

        player.sendSystemMessage(Component.literal("Teleported to region '" + name + "'."));
        return 1;
    }

    private static int redefine(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        RegionSelection selection = SELECTIONS.getSelection(player);

        if (!selection.isComplete()) {
            player.sendSystemMessage(Component.literal("You must set point1 and point2 first."));
            return 0;
        }

        RegionOperationResult result = SimpleServerUtilities.REGIONS.redefine(name, selection.getDimension(), selection.getPoint1(), selection.getPoint2());

        switch (result.getType()) {
            case SUCCESS -> {
                player.sendSystemMessage(Component.literal("Region '" + name + "' redefined."));
                SELECTIONS.clear(player);
                return 1;
            }

            case NAME_ALREADY_EXISTS -> {
                player.sendSystemMessage(Component.literal("A region with that name already exists: " + result.getDetails()));
                return 0;
            }

            case OVERLAPS_PLAYER_CLAIM -> {
                player.sendSystemMessage(Component.literal("This region overlaps an existing player claim: " + result.getDetails()));
                return 0;
            }

            case INVALID_REGION_OVERLAP -> {
                player.sendSystemMessage(Component.literal("This region overlaps another region incorrectly: " + result.getDetails()));
                return 0;
            }

            case REGION_NOT_FOUND -> {
                player.sendSystemMessage(Component.literal("Region not found: " + result.getDetails()));
                return 0;
            }
        }

        return 0;
    }

    private static int save(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        ServerLevel level = player.level().getServer().getLevel(region.getDimension());

        if (level == null) {
            player.sendSystemMessage(Component.literal("Region dimension is not loaded."));
            return 0;
        }

        long volume = region.getVolume();

        if (volume > MAX_REGION_BLOCK_OPERATION_VOLUME) {
            player.sendSystemMessage(Component.literal("Region is too large to save safely: " + volume + " blocks."));
            player.sendSystemMessage(Component.literal("Current safety limit: " + MAX_REGION_BLOCK_OPERATION_VOLUME + " blocks."));
            return 0;
        }

        try {
            int savedBlocks = SimpleServerUtilities.REGION_SNAPSHOTS.save(level, region);

            player.sendSystemMessage(Component.literal(
                    "Saved snapshot for region '" + region.getName() + "'. Stored " + savedBlocks + " non-air block(s)."
            ));

            return 1;
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save region snapshot for '{}'.", region.getName(), e);
            player.sendSystemMessage(Component.literal("Failed to save region snapshot. Check server log for details."));
            return 0;
        }
    }

    private static int reset(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        if (!SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
            player.sendSystemMessage(Component.literal("No saved snapshot exists for region '" + region.getName() + "'."));
            player.sendSystemMessage(Component.literal("Use /regions save " + region.getName() + " first."));
            return 0;
        }

        ServerLevel level = player.level().getServer().getLevel(region.getDimension());

        if (level == null) {
            player.sendSystemMessage(Component.literal("Region dimension is not loaded."));
            return 0;
        }

        long volume = region.getVolume();

        if (volume > MAX_REGION_BLOCK_OPERATION_VOLUME) {
            player.sendSystemMessage(Component.literal("Region is too large to reset safely: " + volume + " blocks."));
            player.sendSystemMessage(Component.literal("Current safety limit: " + MAX_REGION_BLOCK_OPERATION_VOLUME + " blocks."));
            return 0;
        }

        try {
            int restoredBlocks = SimpleServerUtilities.REGION_SNAPSHOTS.reset(level, region);

            player.sendSystemMessage(Component.literal(
                    "Reset region '" + region.getName() + "' to saved snapshot. Restored " + restoredBlocks + " block(s)."
            ));

            return 1;
        } catch (IOException | IllegalStateException e) {
            SimpleServerUtilities.LOGGER.error("Failed to reset region snapshot for '{}'.", region.getName(), e);
            player.sendSystemMessage(Component.literal("Failed to reset region snapshot: " + e.getMessage()));
            return 0;
        }
    }

    private static int clear(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        ServerLevel level = player.level().getServer().getLevel(region.getDimension());

        if (level == null) {
            player.sendSystemMessage(Component.literal("Region dimension is not loaded."));
            return 0;
        }

        long volume = region.getVolume();

        if (volume > MAX_REGION_BLOCK_OPERATION_VOLUME) {
            player.sendSystemMessage(Component.literal("Region is too large to clear safely: " + volume + " blocks."));
            player.sendSystemMessage(Component.literal("Current safety limit: " + MAX_REGION_BLOCK_OPERATION_VOLUME + " blocks."));
            return 0;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int changedBlocks = 0;

        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    mutablePos.set(x, y, z);

                    if (level.isEmptyBlock(mutablePos)) {
                        continue;
                    }

                    level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), 3);
                    changedBlocks++;
                }
            }
        }

        player.sendSystemMessage(Component.literal(
                "Cleared region '" + region.getName() + "'. Removed " + changedBlocks + " block(s)."
        ));
        return 1;
    }

    private static int enableRenting(CommandSourceStack source, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        SimpleServerUtilities.REGIONS.setRentingEnabled(value);
        player.sendSystemMessage(Component.literal("Region renting is now " + (value ? "enabled" : "disabled") + "."));
        return 1;
    }

    private static boolean canCreateRegions(ServerPlayer player) {
        if (!RegionPolicy.canCreateRegion(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to create regions."));
            return false;
        }

        return true;
    }

    private static boolean canDeleteRegions(ServerPlayer player) {
        if (!RegionPolicy.canDeleteRegion(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to delete regions."));
            return false;
        }

        return true;
    }

    private static boolean canEditRegions(ServerPlayer player) {
        if (!RegionPolicy.canEditRegion(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to edit regions."));
            return false;
        }

        return true;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static int help(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("Region commands:"));
        source.sendSystemMessage(Component.literal(" - /regions point1"));
        source.sendSystemMessage(Component.literal(" - /regions point1 <x y z>"));
        source.sendSystemMessage(Component.literal(" - /regions point2"));
        source.sendSystemMessage(Component.literal(" - /regions point2 <x y z>"));
        source.sendSystemMessage(Component.literal(" - /regions create <name>"));
        source.sendSystemMessage(Component.literal(" - /regions delete <name>"));
        source.sendSystemMessage(Component.literal(" - /regions info <name>"));
        source.sendSystemMessage(Component.literal(" - /regions list"));
        source.sendSystemMessage(Component.literal(" - /regions addowner <name> <player>"));
        source.sendSystemMessage(Component.literal(" - /regions removeowner <name> <player>"));
        source.sendSystemMessage(Component.literal(" - /regions addmember <name> <player>"));
        source.sendSystemMessage(Component.literal(" - /regions removemember <name> <player>"));
        source.sendSystemMessage(Component.literal(" - /regions rent <name>"));
        source.sendSystemMessage(Component.literal(" - /regions unrent <name>"));
        source.sendSystemMessage(Component.literal(" - /regions setrent <name> <amount> <days>"));
        source.sendSystemMessage(Component.literal(" - /regions setflag <name> <flag> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /regions perm <name> list"));
        source.sendSystemMessage(Component.literal(" - /regions perm <name> set <permission> <value>"));
        source.sendSystemMessage(Component.literal(" - /regions perm <name> unset <permission>"));
        source.sendSystemMessage(Component.literal(" - /regions setspawn <name>"));
        source.sendSystemMessage(Component.literal(" - /regions tp <name>"));
        source.sendSystemMessage(Component.literal(" - /regions redefine <name>"));
        source.sendSystemMessage(Component.literal(" - /regions save <name>"));
        source.sendSystemMessage(Component.literal(" - /regions reset <name>"));
        source.sendSystemMessage(Component.literal(" - /regions clear <name>"));
        source.sendSystemMessage(Component.literal(" - /regions renting <true|false>"));
        source.sendSystemMessage(Component.literal(" - /regions rentals"));
        source.sendSystemMessage(Component.literal(" - /regions setrentable <name> <true|false>"));
        return 1;
    }

    private static boolean isOp(ServerPlayer player) {
        return RegionPolicy.isRegionAdmin(player);
    }

    private static Optional<UUID> findPlayerUuid(ServerPlayer player, String name) {
        PlayerList playerList = player.level().getServer().getPlayerList();

        ServerPlayer onlinePlayer = playerList.getPlayerByName(name);
        if (onlinePlayer != null) {
            return Optional.of(onlinePlayer.getUUID());
        }

        return Optional.empty();
    }

    private static String formatRentRemaining(RegionRentData rentData) {
        if (rentData.isPermanent()) {
            return "permanent";
        }

        long endTime = rentData.getRentEndTime();

        if (endTime <= 0L) {
            return "no end time";
        }

        long remainingMillis = endTime - System.currentTimeMillis();

        if (remainingMillis <= 0L) {
            return "expired, pending check";
        }

        long totalMinutes = remainingMillis / 60_000L;
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h remaining";
        }

        if (hours > 0L) {
            return hours + "h " + minutes + "m remaining";
        }

        return minutes + "m remaining";
    }
}