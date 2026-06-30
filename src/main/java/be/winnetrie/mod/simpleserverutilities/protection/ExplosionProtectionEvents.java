package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public class ExplosionProtectionEvents {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();

        event.getAffectedBlocks().removeIf(pos -> !canExplosionAffect(level, pos));
        event.getAffectedEntities().removeIf(entity -> !canExplosionAffect(level, entity));
    }

    private static boolean canExplosionAffect(Level level, BlockPos pos) {
        Region region = ProtectionHelper.getRegionAt(level, pos);

        if (region != null) {
            return region.getSettings().isAllowExplosions();
        }

        PlayerClaim claim = ProtectionHelper.getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowExplosions();
    }

    private static boolean canExplosionAffect(Level level, Entity entity) {
        Region region = ProtectionHelper.getRegionAt(level, entity.blockPosition());

        if (region != null) {
            return region.getSettings().isAllowExplosions();
        }

        PlayerClaim claim = ProtectionHelper.getClaimAt(level, entity.blockPosition());

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowExplosions();
    }
}