package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class RegionRentEvents {

    private static final long CHECK_INTERVAL_TICKS = 20L * 60L;
    private static final long MAX_AUTO_RESET_VOLUME = 1_000_000L;

    private static long nextCheckTick = 0L;

    private RegionRentEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long currentTick = server.getTickCount();

        if (currentTick < nextCheckTick) {
            return;
        }

        nextCheckTick = currentTick + CHECK_INTERVAL_TICKS;
        checkExpiredRentals(server);
    }

    private static void checkExpiredRentals(MinecraftServer server) {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (Region region : SimpleServerUtilities.REGIONS.getAll()) {
            RegionRentData rentData = region.getRentData();

            if (!rentData.isRented()) {
                continue;
            }

            if (rentData.isPermanent()) {
                continue;
            }

            long rentEndTime = rentData.getRentEndTime();

            if (rentEndTime <= 0L || rentEndTime > now) {
                continue;
            }

            expireRental(server, region);
            changed = true;
        }

        if (changed) {
            SimpleServerUtilities.REGIONS.save();
        }
    }

    private static void expireRental(MinecraftServer server, Region region) {
        UUID renter = region.getRentData().getRenter();

        resetRegionIfPossible(server, region);

        if (renter != null) {
            region.removeMember(renter);

            ServerPlayer onlineRenter = server.getPlayerList().getPlayer(renter);

            if (onlineRenter != null) {
                onlineRenter.sendSystemMessage(Component.literal(
                        "Your rent for region '" + region.getName() + "' has expired."
                ));
            }
        }

        region.getRentData().setRenter(null);
        region.getRentData().setRentEndTime(-1L);

        SimpleServerUtilities.LOGGER.info("Rental expired for region '{}'.", region.getName());
    }

    private static void resetRegionIfPossible(MinecraftServer server, Region region) {
        if (!SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
            SimpleServerUtilities.LOGGER.info(
                    "Rental expired for region '{}', but no snapshot exists. Region was not reset.",
                    region.getName()
            );
            return;
        }

        long volume = region.getVolume();

        if (volume > MAX_AUTO_RESET_VOLUME) {
            SimpleServerUtilities.LOGGER.warn(
                    "Rental expired for region '{}', but region is too large to auto-reset safely: {} blocks.",
                    region.getName(),
                    volume
            );
            return;
        }

        ServerLevel level = server.getLevel(region.getDimension());

        if (level == null) {
            SimpleServerUtilities.LOGGER.warn(
                    "Rental expired for region '{}', but dimension is not loaded. Region was not reset.",
                    region.getName()
            );
            return;
        }

        try {
            int restoredBlocks = SimpleServerUtilities.REGION_SNAPSHOTS.reset(level, region);

            SimpleServerUtilities.LOGGER.info(
                    "Auto-reset region '{}' after rent expiry. Restored {} block(s).",
                    region.getName(),
                    restoredBlocks
            );
        } catch (IOException | IllegalStateException e) {
            SimpleServerUtilities.LOGGER.error(
                    "Failed to auto-reset region '{}' after rent expiry.",
                    region.getName(),
                    e
            );
        }
    }
}