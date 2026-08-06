package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
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

        Entity target = event.getTarget();
        if (SimpleServerUtilities.NPCS.isManagedEntity(target.getUUID())) return;

        if (target instanceof ServerPlayer) {
            if (ProtectionHelper.canPlayerPvp(player, player.level(), target.blockPosition())) {
                return;
            }

            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("PvP is not allowed here."));
            return;
        }

        boolean allowed = target instanceof LivingEntity && !(target instanceof ArmorStand)
                ? ProtectionHelper.canDamageClaimLiving(player, player.level(), target.blockPosition())
                : ProtectionHelper.canModifyClaimNonLiving(player, player.level(), target.blockPosition());
        if (allowed) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot damage entities here."));
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        ServerPlayer player = getAttackingPlayer(event.getSource().getEntity());

        if (player == null) {
            return;
        }

        Entity target = event.getEntity();
        if (SimpleServerUtilities.NPCS.isManagedEntity(target.getUUID())) return;

        if (target instanceof ServerPlayer) {
            if (ProtectionHelper.canPlayerPvp(player, player.level(), target.blockPosition())) {
                return;
            }

            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("PvP is not allowed here."));
            return;
        }

        boolean allowed = target instanceof ArmorStand
                ? ProtectionHelper.canModifyClaimNonLiving(player, player.level(), target.blockPosition())
                : ProtectionHelper.canDamageClaimLiving(player, player.level(), target.blockPosition());
        if (allowed) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot damage entities here."));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (SimpleServerUtilities.NPCS.isManagedEntity(event.getTarget().getUUID())) return;

        boolean allowed = event.getTarget() instanceof LivingEntity && !(event.getTarget() instanceof ArmorStand)
                ? ProtectionHelper.canInteractClaimEntity(player, player.level(), event.getTarget().blockPosition())
                : ProtectionHelper.canModifyClaimNonLiving(player, player.level(), event.getTarget().blockPosition());
        if (allowed) return;

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
        if (SimpleServerUtilities.NPCS.isManagedEntity(hitEntity.getUUID())) return;

        if (projectile.getOwner() instanceof ServerPlayer player) {
            if (hitEntity instanceof ServerPlayer) {
                if (ProtectionHelper.canPlayerPvp(player, player.level(), hitEntity.blockPosition())) {
                    return;
                }

                event.setCanceled(true);
                return;
            }

            boolean allowed = hitEntity instanceof LivingEntity && !(hitEntity instanceof ArmorStand)
                    ? ProtectionHelper.canDamageClaimLiving(player, player.level(), hitEntity.blockPosition())
                    : ProtectionHelper.canModifyClaimNonLiving(player, player.level(), hitEntity.blockPosition());
            if (allowed) return;

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
