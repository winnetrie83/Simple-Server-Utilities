package be.winnetrie.mod.simpleserverutilities.region;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative creation service for the region admin tool GUI. */
public final class RegionEditorService {
    private RegionEditorService() {
    }

    public static boolean open(ServerPlayer player) {
        if (!RegionPolicy.canUseSelectionTool(player) || !RegionPolicy.canCreateRegion(player)) return false;
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) return false;
        PacketDistributor.sendToPlayer(player, new RegionEditorOpenPayload(
                selection.getDimension().location().toString(),
                selection.getPoint1().asLong(),
                selection.getPoint2().asLong()
        ));
        return true;
    }

    public static void handleSubmit(RegionEditorSubmitPayload payload, IPayloadContext context) {
        if (!SsuModuleAccess.active("regions")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        Result result = create(player, payload);
        PacketDistributor.sendToPlayer(player,
                new RegionEditorResultPayload(result.success(), result.message(), payload.requestId()));
    }

    private static Result create(ServerPlayer player, RegionEditorSubmitPayload payload) {
        if (!RegionPolicy.canCreateRegion(player) || !RegionPolicy.canUseSelectionTool(player)) {
            return Result.fail("You do not have permission to create regions.");
        }
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) return Result.fail("Set both region points again first.");
        if (!selection.getDimension().equals(player.level().dimension())) {
            return Result.fail("The selection belongs to another dimension.");
        }

        String name = payload.name().trim();
        if (!name.matches("[A-Za-z0-9._-]{1,64}")) {
            return Result.fail("Use 1-64 letters, numbers, dots, underscores or dashes for the name.");
        }

        boolean economyActive = SsuModuleAccess.active("economy");
        long priceMinor = 0L;
        if (payload.rentable()) {
            if (!economyActive) return Result.fail("Economy is disabled, so a new rentable region cannot be configured.");
            try {
                priceMinor = MoneyFormat.parseMinor(payload.rentPrice().isBlank() ? "0" : payload.rentPrice(),
                        SimpleServerUtilities.ECONOMY.settings());
            } catch (IllegalArgumentException exception) {
                return Result.fail(exception.getMessage());
            }
            if (payload.rentPeriodDays() == 0 || payload.rentPeriodDays() < -1) {
                return Result.fail("Rent period must be -1 for permanent or at least 1 day.");
            }
        }

        RegionOperationResult operation = SimpleServerUtilities.REGIONS.create(
                name, selection.getDimension(), selection.getPoint1(), selection.getPoint2()
        );
        if (!operation.isSuccess()) {
            return Result.fail(switch (operation.getType()) {
                case NAME_ALREADY_EXISTS -> "A region with that name already exists.";
                case OVERLAPS_PLAYER_CLAIM -> "The selection overlaps a player claim: " + operation.getDetails();
                case INVALID_REGION_OVERLAP -> "The selection overlaps another region incorrectly: " + operation.getDetails();
                case REGION_NOT_FOUND -> "Region not found: " + operation.getDetails();
                case SUCCESS -> "The region could not be created.";
            });
        }

        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return Result.fail("The region was created but could not be loaded for configuration.");
        region.setPriority(payload.priority());
        RegionSettings settings = region.getSettings();
        settings.setAllowBlockBreak(payload.allowBreak());
        settings.setAllowBlockPlace(payload.allowPlace());
        settings.setAllowInteract(payload.allowInteract());
        settings.setAllowPvp(payload.allowPvp());
        settings.setAllowExplosions(payload.allowExplosions());
        settings.setAllowPistons(payload.allowPistons());
        settings.setAllowWaterFlow(payload.allowWater());
        settings.setAllowLavaFlow(payload.allowLava());
        settings.setAllowRedstone(payload.allowRedstone());
        settings.setAllowHoppers(payload.allowHoppers());
        settings.setAllowFireSpread(payload.allowFireSpread());

        RegionRentData rent = region.getRentData();
        rent.setRentable(payload.rentable());
        if (economyActive) rent.setPriceMinor(priceMinor, SimpleServerUtilities.ECONOMY.settings());
        rent.setPeriodDays(payload.rentable() ? payload.rentPeriodDays() : -1);
        rent.setResetOnExpire(payload.resetOnExpire());
        rent.setResetOnUnrent(payload.resetOnUnrent());

        SimpleServerUtilities.REGIONS.save();
        RegionCommands.getSelectionManager().clear(player);
        if (SsuModuleAccess.active("visualization")) SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        return Result.ok("Region '" + name + "' created.");
    }

    private record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message == null ? "Operation failed." : message); }
    }
}
