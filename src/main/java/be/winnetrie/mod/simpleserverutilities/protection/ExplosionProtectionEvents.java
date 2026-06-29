package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
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
        PlayerClaim claim = ProtectionHelper.getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowExplosions();
    }

    private static boolean canExplosionAffect(Level level, Entity entity) {
        PlayerClaim claim = ProtectionHelper.getClaimAt(level, entity.blockPosition());

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowExplosions();
    }
}