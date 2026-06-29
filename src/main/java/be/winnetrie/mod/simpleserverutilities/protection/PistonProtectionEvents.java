package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

public class PistonProtectionEvents {

    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }

        Direction direction = event.getDirection();

        BlockPos pistonPos = event.getPos();
        BlockPos frontPos = pistonPos.relative(direction);
        BlockPos targetPos = frontPos.relative(direction);

        if (!canMoveBetween(level, pistonPos, frontPos)) {
            event.setCanceled(true);
            return;
        }

        if (!canMoveBetween(level, frontPos, targetPos)) {
            event.setCanceled(true);
        }
    }

    private static boolean canMoveBetween(Level level, BlockPos from, BlockPos to) {
        PlayerClaim fromClaim = ProtectionHelper.getClaimAt(level, from);
        PlayerClaim toClaim = ProtectionHelper.getClaimAt(level, to);

        if (fromClaim == null && toClaim == null) {
            return true;
        }

        if (fromClaim != null && toClaim != null && sameClaim(fromClaim, toClaim)) {
            return true;
        }

        if (fromClaim != null && !fromClaim.getSettings().isAllowPistons()) {
            return false;
        }

        if (toClaim != null && !toClaim.getSettings().isAllowPistons()) {
            return false;
        }

        return true;
    }

    private static boolean sameClaim(PlayerClaim a, PlayerClaim b) {
        return a.getDimension().equals(b.getDimension())
                && a.getChunkX() == b.getChunkX()
                && a.getChunkZ() == b.getChunkZ();
    }
}