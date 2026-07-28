package be.winnetrie.mod.simpleserverutilities.command;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import be.winnetrie.mod.simpleserverutilities.region.RegionInteractionEvents;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentalService;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentalService.RentalResult;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelection;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionSnapshotManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionWorldEditManager;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportDestination;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportSafety;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.region.RegionOperationResult;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentData;


import java.util.Set;
import java.io.IOException;
import java.util.Map;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.players.PlayerList;


public class RegionCommands {

    private static final RegionSelectionManager SELECTIONS = new RegionSelectionManager();
    private static final long MAX_REGION_BLOCK_OPERATION_VOLUME = 1_000_000L;

    public static RegionSelectionManager getSelectionManager() {
        return SELECTIONS;
    }

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

                .then(Commands.literal("myrentals")
                        .executes(context -> myRentals(context.getSource())))

                .then(Commands.literal("rentaccept")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> rentAccept(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("rentdecline")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> rentDecline(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("confirmunrent")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> confirmUnrent(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("extend")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> extendRent(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("setrentprice")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> setRentPrice(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "amount")
                                        )))))

                .then(Commands.literal("setrentperiod")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("days", IntegerArgumentType.integer(-1))
                                        .executes(context -> setRentPeriod(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "days")
                                        )))))

                .then(Commands.literal("addtime")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                        .executes(context -> addRentTime(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "days")
                                        )))))

                .then(Commands.literal("pause")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> pauseRent(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                BoolArgumentType.getBool(context, "value")
                                        )))))

                .then(Commands.literal("resetonexpire")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> setResetOnExpire(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                BoolArgumentType.getBool(context, "value")
                                        )))))

                .then(Commands.literal("resetonunrent")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> setResetOnUnrent(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                BoolArgumentType.getBool(context, "value")
                                        )))))

                .then(Commands.literal("welcome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> setWelcomeMessage(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "message")
                                        )))))

                .then(Commands.literal("leave")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> setLeaveMessage(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "message")
                                        )))))

                .then(Commands.literal("clearwelcome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> setWelcomeMessage(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        ""
                                ))))

                .then(Commands.literal("clearleave")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> setLeaveMessage(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        ""
                                ))))

                .then(Commands.literal("borders")
                        .then(Commands.literal("on")
                                .executes(context -> BorderCommands.setRegions(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> BorderCommands.setRegions(context.getSource(), false))))

                .then(Commands.literal("show")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> showRegionBoundary(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("hide")
                        .executes(context -> hideAllRegionBoundaries(context.getSource()))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> hideRegionBoundary(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                .then(Commands.literal("selection")
                        .then(Commands.literal("bind")
                                .executes(context -> bindSelectionTool(context.getSource())))
                        .then(Commands.literal("unbind")
                                .executes(context -> unbindSelectionTool(context.getSource())))
                        .then(Commands.literal("clear")
                                .executes(context -> clearSelection(context.getSource())))
                        .then(Commands.literal("fill")
                                .then(Commands.argument("blocks", StringArgumentType.greedyString())
                                        .executes(context -> fillSelection(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "blocks")
                                        ))))
                        .then(Commands.literal("regen")
                                .executes(context -> regenerateSelection(context.getSource()))))

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
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, SELECTIONS.getSelection(player));
        player.sendSystemMessage(Component.literal("Region point 1 set to " + formatPos(pos) + "."));
        return 1;
    }

    private static int setPoint2(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        SELECTIONS.setPoint2(player, pos);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, SELECTIONS.getSelection(player));
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
                SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
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

        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshShownRegion(player);
        player.sendSystemMessage(Component.literal("Region '" + name + "' deleted."));
        return 1;
    }

    private static int info(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            source.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        if (!canViewRegion(player, region)) {
            player.sendSystemMessage(Component.literal("You do not have access to this region info."));
            return 0;
        }

        RegionRentData rentData = region.getRentData();

        source.sendSystemMessage(Component.literal("Region: " + region.getName()));
        source.sendSystemMessage(Component.literal("Bounds: " + region.getBoundsText()));
        source.sendSystemMessage(Component.literal("Owners: " + region.getOwners().size()));
        source.sendSystemMessage(Component.literal("Members: " + region.getMembers().size()));

        if (region.getSpawnPos() != null) {
            source.sendSystemMessage(Component.literal("Spawn: " + formatPos(region.getSpawnPos()) + " yaw=" + region.getSpawnYaw() + " pitch=" + region.getSpawnPitch()));
        } else {
            source.sendSystemMessage(Component.literal("Spawn: not set"));
        }

        source.sendSystemMessage(Component.literal("Global renting enabled: " + SimpleServerUtilities.REGIONS.isRentingEnabled()));
        source.sendSystemMessage(Component.literal("Rentable: " + rentData.isRentable()));
        source.sendSystemMessage(Component.literal("Rented: " + rentData.isRented()));
        source.sendSystemMessage(Component.literal("Rent amount: " + rentData.getAmount()));
        source.sendSystemMessage(Component.literal("Rent period days: " + rentData.getPeriodDays()));
        source.sendSystemMessage(Component.literal("Reset on expire: " + rentData.isResetOnExpire()));
        source.sendSystemMessage(Component.literal("Reset on unrent: " + rentData.isResetOnUnrent()));

        if (!region.getWelcomeMessage().isBlank()) {
            source.sendSystemMessage(Component.literal("Welcome message: " + region.getWelcomeMessage()));
        }

        if (!region.getLeaveMessage().isBlank()) {
            source.sendSystemMessage(Component.literal("Leave message: " + region.getLeaveMessage()));
        }

        if (rentData.isRented()) {
            source.sendSystemMessage(Component.literal("Renter: " + rentData.getDisplayRenterName()));
            source.sendSystemMessage(Component.literal("Rent remaining: " + formatRentRemaining(rentData)));
            source.sendSystemMessage(Component.literal("Rent paused: " + rentData.isRentPaused()));

            if (isOp(player) && rentData.getRenter() != null) {
                source.sendSystemMessage(Component.literal("Renter UUID: " + rentData.getRenter()));
            }
        }

        return 1;
    }


    private static int list(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        boolean admin = isOp(player);
        int count = 0;

        source.sendSystemMessage(Component.literal("Regions:"));

        for (Region region : SimpleServerUtilities.REGIONS.getAll()) {
            if (!admin && !canViewRegion(player, region)) {
                continue;
            }

            count++;
            source.sendSystemMessage(Component.literal(" - " + region.getName() + " " + region.getBoundsText()));
        }

        if (count == 0) {
            source.sendSystemMessage(Component.literal("No regions found."));
            return 0;
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

            if (!admin && !canViewRegion(player, region)) {
                continue;
            }

            if (!rentData.isRentable() && !rentData.isRented()) {
                continue;
            }

            count++;
            source.sendSystemMessage(Component.literal(formatRentalLine(region, admin)));
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

        if (!RegionPolicy.canRentRegion(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to rent regions."));
            return 0;
        }

        sendRentOffer(player, region);
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

        Component confirm = Component.literal("[CONFIRM UNRENT]")
                .withStyle(style -> style.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/regions confirmunrent " + region.getName())));

        player.sendSystemMessage(Component.literal("Unrenting may remove your access and may reset the region."));
        player.sendSystemMessage(Component.literal("Click to confirm: ").append(confirm));
        return 1;
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

        if (region.getRentData().isRented()) {
            player.sendSystemMessage(Component.literal("Current rent was not reset. New price applies to the next extension."));
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


    private static int myRentals(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        int count = 0;

        player.sendSystemMessage(Component.literal("Your rented regions:"));

        for (Region region : SimpleServerUtilities.REGIONS.getAll()) {
            if (!player.getUUID().equals(region.getRentData().getRenter())) {
                continue;
            }

            count++;
            player.sendSystemMessage(Component.literal(formatRentalLine(region, false)));
        }

        if (count == 0) {
            player.sendSystemMessage(Component.literal("You are not renting any regions."));
            return 0;
        }

        return 1;
    }

    private static int rentAccept(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        if (!RegionPolicy.canRentRegion(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to rent regions."));
            return 0;
        }

        RentalResult result = RegionRentalService.rent(player, region);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int rentDecline(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        player.sendSystemMessage(Component.literal("Rent offer declined for region '" + name + "'."));
        return 1;
    }

    private static int confirmUnrent(CommandSourceStack source, String name) {
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

        RentalResult result = RegionRentalService.unrent(player.level().getServer(), region, true);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int extendRent(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        RentalResult result = RegionRentalService.extend(player, region);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int setRentPrice(CommandSourceStack source, String name, int amount) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.getRentData().setAmount(amount);
        SimpleServerUtilities.REGIONS.save();

        player.sendSystemMessage(Component.literal("Rent price for region '" + name + "' is now " + amount + "."));

        if (region.getRentData().isRented()) {
            player.sendSystemMessage(Component.literal("Current rent timer was not reset. New price applies to the next extension."));
        }

        return 1;
    }

    private static int setRentPeriod(CommandSourceStack source, String name, int days) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.getRentData().setPeriodDays(days);
        SimpleServerUtilities.REGIONS.save();
        player.sendSystemMessage(Component.literal("Rent period for region '" + name + "' is now " + days + " day(s)."));
        return 1;
    }

    private static int addRentTime(CommandSourceStack source, String name, int days) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        RentalResult result = RegionRentalService.adminAddTime(region, days);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int pauseRent(CommandSourceStack source, String name, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        RentalResult result = RegionRentalService.setPaused(region, value);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int setResetOnExpire(CommandSourceStack source, String name, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.getRentData().setResetOnExpire(value);
        SimpleServerUtilities.REGIONS.save();
        player.sendSystemMessage(Component.literal("Reset on expire for region '" + name + "' set to " + value + "."));
        return 1;
    }

    private static int setResetOnUnrent(CommandSourceStack source, String name, boolean value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        region.getRentData().setResetOnUnrent(value);
        SimpleServerUtilities.REGIONS.save();
        player.sendSystemMessage(Component.literal("Reset on unrent for region '" + name + "' set to " + value + "."));
        return 1;
    }

    private static int setWelcomeMessage(CommandSourceStack source, String name, String message) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        if (!canManageOwnRegion(player, region)) {
            player.sendSystemMessage(Component.literal("You cannot edit messages for this region."));
            return 0;
        }

        region.setWelcomeMessage(message);
        SimpleServerUtilities.REGIONS.save();
        player.sendSystemMessage(Component.literal(message.isBlank()
                ? "Welcome message cleared for region '" + name + "'."
                : "Welcome message set for region '" + name + "'."));
        return 1;
    }

    private static int setLeaveMessage(CommandSourceStack source, String name, String message) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        if (!canManageOwnRegion(player, region)) {
            player.sendSystemMessage(Component.literal("You cannot edit messages for this region."));
            return 0;
        }

        region.setLeaveMessage(message);
        SimpleServerUtilities.REGIONS.save();
        player.sendSystemMessage(Component.literal(message.isBlank()
                ? "Leave message cleared for region '" + name + "'."
                : "Leave message set for region '" + name + "'."));
        return 1;
    }

    private static int clearSelection(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        SELECTIONS.clear(player);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        player.sendSystemMessage(Component.literal("Region selection cleared."));
        return 1;
    }

    private static int bindSelectionTool(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!RegionPolicy.canUseSelectionTool(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to bind a region selector tool."));
            return 0;
        }

        if (player.getMainHandItem().isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item in your main hand first."));
            return 0;
        }

        SimpleServerUtilities.REGION_SELECTION_TOOLS.bind(player, player.getMainHandItem());
        player.sendSystemMessage(Component.literal("Region selector bound to: "
                + SimpleServerUtilities.REGION_SELECTION_TOOLS.getBoundTool(player)));
        return 1;
    }

    private static int unbindSelectionTool(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!RegionPolicy.canUseSelectionTool(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to unbind a region selector tool."));
            return 0;
        }

        SimpleServerUtilities.REGION_SELECTION_TOOLS.unbind(player);
        player.sendSystemMessage(Component.literal("Region selector tool unbound."));
        return 1;
    }

    private static int fillSelection(CommandSourceStack source, String blocks) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        RegionSelection selection = SELECTIONS.getSelection(player);

        if (!selection.isComplete()) {
            player.sendSystemMessage(Component.literal("You must set point1 and point2 first."));
            return 0;
        }

        ServerLevel level = player.level().getServer().getLevel(selection.getDimension());

        if (level == null) {
            player.sendSystemMessage(Component.literal("Selection dimension is not loaded."));
            return 0;
        }

        try {
            RegionWorldEditManager.RegionFillJob job = RegionWorldEditManager.createFillJob(
                    level,
                    selection,
                    blocks,
                    MAX_REGION_BLOCK_OPERATION_VOLUME
            );
            java.util.UUID playerId = player.getUUID();
            net.minecraft.server.MinecraftServer server = source.getServer();
            java.util.UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> {
                ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                if (online == null) {
                    return;
                }
                if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    online.sendSystemMessage(Component.literal(
                            "Fill job completed. Changed " + job.changedBlocks() + " block(s)."
                    ));
                } else {
                    online.sendSystemMessage(Component.literal(
                            "Fill job " + result.status().name().toLowerCase(java.util.Locale.ROOT)
                                    + (result.error().isBlank() ? "." : ": " + result.error())
                    ));
                }
            });
            player.sendSystemMessage(Component.literal("Scheduled fill job " + jobId + "."));
            return 1;
        } catch (IllegalArgumentException | IllegalStateException e) {
            player.sendSystemMessage(Component.literal("Failed to fill selection: " + e.getMessage()));
            return 0;
        }
    }

    private static int regenerateSelection(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!canEditRegions(player)) {
            return 0;
        }

        player.sendSystemMessage(Component.literal("Selection regeneration is not enabled yet."));
        player.sendSystemMessage(Component.literal("Reason: safe partial world-generation restore needs a dedicated chunk/worldgen implementation."));
        return 0;
    }

    private static int showRegionBoundary(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!RegionPolicy.canVisualizeRegions(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to visualize region boundaries."));
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);

        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        RegionInteractionEvents.showBoundary(player, region.getName());
        player.sendSystemMessage(Component.literal(
                "Showing boundary for region '" + region.getName()
                        + "'. Use /regions hide " + region.getName() + " to hide only this region."
        ));
        return 1;
    }

    private static int hideRegionBoundary(CommandSourceStack source, String name) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!RegionPolicy.canVisualizeRegions(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to visualize region boundaries."));
            return 0;
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) {
            player.sendSystemMessage(Component.literal("Region not found: " + name));
            return 0;
        }

        RegionInteractionEvents.hideBoundary(player, region.getName());
        player.sendSystemMessage(Component.literal("Boundary hidden for region '" + region.getName() + "'."));
        return 1;
    }

    private static int hideAllRegionBoundaries(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        RegionInteractionEvents.hideAllBoundaries(player);
        player.sendSystemMessage(Component.literal("All individually selected region boundaries hidden."));
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

        Optional<TeleportDestination> destination = TeleportSafety.findSafeDestination(
                level,
                region.getSpawnPos().getX() + 0.5,
                region.getSpawnPos().getY(),
                region.getSpawnPos().getZ() + 0.5
        );

        if (destination.isEmpty()) {
            player.sendSystemMessage(Component.literal("No safe teleport location was found near this region spawn."));
            return 0;
        }

        TeleportDestination safe = destination.get();
        player.teleportTo(
                level,
                safe.x(),
                safe.y(),
                safe.z(),
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
                SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
                SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshShownRegion(player);
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
            RegionSnapshotManager.RegionSnapshotResetJob job =
                    SimpleServerUtilities.REGION_SNAPSHOTS.createResetJob(level, region);
            java.util.UUID playerId = player.getUUID();
            net.minecraft.server.MinecraftServer server = source.getServer();
            java.util.UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> {
                ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                if (online == null) {
                    return;
                }
                if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    online.sendSystemMessage(Component.literal(
                            "Reset region '" + region.getName() + "'. Restored " + job.restoredBlocks() + " block(s)."
                    ));
                } else {
                    online.sendSystemMessage(Component.literal(
                            "Region reset " + result.status().name().toLowerCase(java.util.Locale.ROOT)
                                    + (result.error().isBlank() ? "." : ": " + result.error())
                    ));
                }
            });
            player.sendSystemMessage(Component.literal("Scheduled region reset job " + jobId + "."));
            return 1;
        } catch (IOException | IllegalStateException e) {
            SimpleServerUtilities.LOGGER.error("Failed to schedule region snapshot reset for '{}'.", region.getName(), e);
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

        try {
            RegionWorldEditManager.RegionClearJob job = RegionWorldEditManager.createClearJob(
                    level,
                    region,
                    MAX_REGION_BLOCK_OPERATION_VOLUME
            );
            UUID playerId = player.getUUID();
            net.minecraft.server.MinecraftServer server = source.getServer();
            UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> {
                ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                if (online == null) {
                    return;
                }
                if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    online.sendSystemMessage(Component.literal(
                            "Cleared region '" + region.getName() + "'. Removed "
                                    + job.changedBlocks() + " block(s)."
                    ));
                } else {
                    online.sendSystemMessage(Component.literal(
                            "Region clear " + result.status().name().toLowerCase(java.util.Locale.ROOT)
                                    + (result.error().isBlank() ? "." : ": " + result.error())
                    ));
                }
            });
            player.sendSystemMessage(Component.literal("Scheduled region clear job " + jobId + "."));
            return 1;
        } catch (IllegalArgumentException | IllegalStateException e) {
            player.sendSystemMessage(Component.literal("Failed to schedule region clear: " + e.getMessage()));
            return 0;
        }
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

    private static boolean canViewRegion(ServerPlayer player, Region region) {
        return isOp(player)
                || RegionPolicy.canEditRegion(player)
                || region.isOwner(player.getUUID())
                || player.getUUID().equals(region.getRentData().getRenter());
    }

    private static boolean canManageOwnRegion(ServerPlayer player, Region region) {
        return isOp(player)
                || RegionPolicy.canEditRegion(player)
                || region.isOwner(player.getUUID())
                || player.getUUID().equals(region.getRentData().getRenter());
    }

    private static String formatRentalLine(Region region, boolean showAdminDetails) {
        RegionRentData rentData = region.getRentData();
        String status;

        if (rentData.isRented()) {
            status = rentData.isRentPaused() ? "rented, paused" : "rented";
        } else if (rentData.isRentable()) {
            status = "available";
        } else {
            status = "not rentable";
        }

        String period = rentData.isPermanent()
                ? "permanent"
                : rentData.getPeriodDays() + " day(s)";

        String line = " - " + region.getName()
                + " | " + status
                + " | " + rentData.getAmount()
                + " / " + period
                + " | reset expire: " + rentData.isResetOnExpire();

        if (rentData.isRented()) {
            line += " | " + formatRentRemaining(rentData)
                    + " | renter: " + rentData.getDisplayRenterName();

            if (showAdminDetails && rentData.getRenter() != null) {
                line += " (" + rentData.getRenter() + ")";
            }
        }

        return line;
    }

    private static void sendRentOffer(ServerPlayer player, Region region) {
        RegionRentData rentData = region.getRentData();

        if (!SimpleServerUtilities.REGIONS.isRentingEnabled()) {
            player.sendSystemMessage(Component.literal("Region renting is currently disabled."));
            return;
        }

        if (!rentData.isRentable()) {
            player.sendSystemMessage(Component.literal("This region is not rentable."));
            return;
        }

        if (rentData.isRented()) {
            player.sendSystemMessage(Component.literal("This region is already rented."));
            return;
        }

        player.sendSystemMessage(Component.literal("Rent region: " + region.getName()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Price: " + rentData.getAmount() + " / "
                + (rentData.isPermanent() ? "permanent" : rentData.getPeriodDays() + " day(s)")));

        Component accept = Component.literal("[ACCEPT]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/regions rentaccept " + region.getName())));

        Component decline = Component.literal(" [DECLINE]")
                .withStyle(style -> style.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/regions rentdecline " + region.getName())));

        player.sendSystemMessage(Component.literal("").append(accept).append(decline));
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
        source.sendSystemMessage(Component.literal(" - /regions myrentals"));
        source.sendSystemMessage(Component.literal(" - /regions extend <name>"));
        source.sendSystemMessage(Component.literal(" - /regions setrentable <name> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /regions setrentprice <name> <amount>"));
        source.sendSystemMessage(Component.literal(" - /regions setrentperiod <name> <days>"));
        source.sendSystemMessage(Component.literal(" - /regions addtime <name> <days>"));
        source.sendSystemMessage(Component.literal(" - /regions pause <name> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /regions resetonexpire <name> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /regions resetonunrent <name> <true|false>"));
        source.sendSystemMessage(Component.literal(" - /regions welcome <name> <message>"));
        source.sendSystemMessage(Component.literal(" - /regions leave <name> <message>"));
        source.sendSystemMessage(Component.literal(" - /regions clearwelcome <name>"));
        source.sendSystemMessage(Component.literal(" - /regions clearleave <name>"));
        source.sendSystemMessage(Component.literal(" - /regions show <name>"));
        source.sendSystemMessage(Component.literal(" - /regions hide"));
        source.sendSystemMessage(Component.literal(" - /regions selection bind"));
        source.sendSystemMessage(Component.literal(" - /regions selection unbind"));
        source.sendSystemMessage(Component.literal(" - /regions selection clear"));
        source.sendSystemMessage(Component.literal(" - /regions selection fill <block=weight,block=weight>"));
        source.sendSystemMessage(Component.literal(" - /regions selection regen"));
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

        if (rentData.isRentPaused()) {
            return "paused with " + formatDuration(Math.max(0L, rentData.getPausedRemainingMillis())) + " remaining";
        }

        long endTime = rentData.getRentEndTime();

        if (endTime <= 0L) {
            return "no end time";
        }

        long remainingMillis = endTime - System.currentTimeMillis();

        if (remainingMillis <= 0L) {
            return "expired, pending check";
        }

        return formatDuration(remainingMillis) + " remaining";
    }

    private static String formatDuration(long millis) {
        long totalMinutes = millis / 60_000L;
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }

        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }

        return minutes + "m";
    }

}