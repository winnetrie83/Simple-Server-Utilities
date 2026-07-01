package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class EntityProtectionEvents {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (ProtectionHelper.canPlayerInteract(player, player.level(), event.getTarget().blockPosition())) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot damage entities here."));
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        ServerPlayer player = getAttackingPlayer(event.getSource().getEntity());

        if (player == null) {
            return;
        }

        if (ProtectionHelper.canPlayerInteract(player, player.level(), event.getEntity().blockPosition())) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot damage entities here."));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (ProtectionHelper.canPlayerInteract(player, player.level(), event.getTarget().blockPosition())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.sendSystemMessage(Component.literal("You cannot interact with entities here."));
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }

        HitResult hitResult = event.getRayTraceResult();

        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return;
        }

        Entity hitEntity = entityHitResult.getEntity();

        if (projectile.getOwner() instanceof ServerPlayer player) {
            if (ProtectionHelper.canPlayerInteract(player, player.level(), hitEntity.blockPosition())) {
                return;
            }

            event.setCanceled(true);
            return;
        }

        if (!ProtectionHelper.canOwnerlessProjectileHit(projectile.level(), hitEntity.blockPosition())) {
            event.setCanceled(true);
        }
    }

    private static ServerPlayer getAttackingPlayer(Entity sourceEntity) {
        if (sourceEntity instanceof ServerPlayer player) {
            return player;
        }

        if (sourceEntity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }

        return null;
    }
}