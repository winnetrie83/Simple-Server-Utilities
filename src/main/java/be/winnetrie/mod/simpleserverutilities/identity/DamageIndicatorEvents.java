package be.winnetrie.mod.simpleserverutilities.identity;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.DamageIndicatorPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends bounded, viewer-personalized damage/healing indicators around affected entities. */
public final class DamageIndicatorEvents {
    private static final double RANGE_SQR = 64.0D * 64.0D;
    private DamageIndicatorEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        float amount = Math.max(0.0F, event.getInflictedDamage());
        if (amount > 0.0001F) send(event.getEntity(), amount, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (event.isCanceled()) return;
        LivingEntity entity = event.getEntity();
        float actual = Math.min(Math.max(0.0F, event.getAmount()), Math.max(0.0F, entity.getMaxHealth() - entity.getHealth()));
        if (actual > 0.0001F) send(entity, actual, true);
    }

    private static void send(LivingEntity entity, float amount, boolean healing) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        double x = entity.getX();
        double y = entity.getY() + Math.max(0.8D, entity.getBbHeight() * 0.78D);
        double z = entity.getZ();
        int seed = entity.getId() * 31 + entity.tickCount * 17 + (healing ? 1 : 0);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(entity) > RANGE_SQR) continue;
            var preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(viewer);
            if (!preferences.isDamageIndicatorsEnabled()) continue;
            if (!PermissionService.getBooleanWithoutOperatorBypass(viewer, PermissionKeys.DAMAGE_INDICATORS_USE, true)) continue;
            PacketDistributor.sendToPlayer(viewer, new DamageIndicatorPayload(x, y, z, amount, healing,
                    preferences.getDamageIndicatorStyle().name(), seed));
        }
    }
}
