package be.winnetrie.mod.simpleserverutilities.region;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class RegionRentEvents {

    private static final long CHECK_INTERVAL_TICKS = 20L * 60L;

    private static long nextCheckTick = 0L;

    private RegionRentEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SsuModuleAccess.active("regions") || !SsuModuleAccess.active("economy")) {
            nextCheckTick = 0L;
            return;
        }

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

            if (rentData.isPermanent() || rentData.isRentPaused()) {
                continue;
            }

            long rentEndTime = rentData.getRentEndTime();

            if (rentEndTime <= 0L || rentEndTime > now) {
                continue;
            }

            RegionRentalService.expireRental(server, region);
            changed = true;
        }

        if (changed) {
            SimpleServerUtilities.REGIONS.save();
        }
    }
}
