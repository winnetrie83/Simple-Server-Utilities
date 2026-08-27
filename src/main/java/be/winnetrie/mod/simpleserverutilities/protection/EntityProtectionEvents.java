package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class EntityProtectionEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Projectile projectile && !event.getLevel().isClientSide()) {
            ProjectileProtectionTracker.track(projectile);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Projectile projectile) {
            ProjectileProtectionTracker.forget(projectile);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity target = event.getTarget();
        if (SsuModuleAccess.active("npcs") && SimpleServerUtilities.NPCS.isManagedEntity(target.getUUID())) return;

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
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        ServerPlayer player = getAttackingPlayer(event.getSource().getEntity());
        Entity target = event.getEntity();

        if (player == null) {
            Entity direct = event.getSource().getDirectEntity();
            if (direct instanceof Projectile projectile) {
                BlockPos targetPos = target.blockPosition();
                if (!ProjectileProtectionTracker.canReach(projectile, targetPos)
                        || !ProtectionHelper.canOwnerlessProjectileHit(projectile.level(), targetPos)) {
                    event.setCanceled(true);
                }
            }
            return;
        }

        if (SsuModuleAccess.active("npcs") && SimpleServerUtilities.NPCS.isManagedEntity(target.getUUID())) return;

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
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (SsuModuleAccess.active("npcs") && SimpleServerUtilities.NPCS.isManagedEntity(event.getTarget().getUUID())) return;

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
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        Projectile projectile = event.getProjectile();
        HitResult hitResult = event.getRayTraceResult();

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity hitEntity = entityHitResult.getEntity();
            if (SsuModuleAccess.active("npcs") && SimpleServerUtilities.NPCS.isManagedEntity(hitEntity.getUUID())) return;

            if (projectile.getOwner() instanceof ServerPlayer player) {
                if (hitEntity instanceof ServerPlayer) {
                    if (ProtectionHelper.canPlayerPvp(player, player.level(), hitEntity.blockPosition())) {
                        return;
                    }
                    denyProjectileImpact(event, projectile);
                    return;
                }

                boolean allowed = hitEntity instanceof LivingEntity && !(hitEntity instanceof ArmorStand)
                        ? ProtectionHelper.canDamageClaimLiving(player, player.level(), hitEntity.blockPosition())
                        : ProtectionHelper.canModifyClaimNonLiving(player, player.level(), hitEntity.blockPosition());
                if (!allowed) {
                    denyProjectileImpact(event, projectile);
                }
                return;
            }

            BlockPos targetPos = hitEntity.blockPosition();
            if (!ProjectileProtectionTracker.canReach(projectile, targetPos)
                    || !ProtectionHelper.canOwnerlessProjectileHit(projectile.level(), targetPos)) {
                denyProjectileImpact(event, projectile);
            }
            return;
        }

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos targetPos = blockHitResult.getBlockPos();

            if (projectile.getOwner() instanceof ServerPlayer player) {
                // A projectile can activate or otherwise affect blocks on impact.
                // Use the normal SSU interaction permission at the impact block.
                if (!ProtectionHelper.canPlayerInteract(player, player.level(), targetPos)) {
                    denyProjectileImpact(event, projectile);
                }
                return;
            }

            if (!ProjectileProtectionTracker.canReach(projectile, targetPos)
                    || !ProtectionHelper.canOwnerlessProjectileHit(projectile.level(), targetPos)) {
                denyProjectileImpact(event, projectile);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        if (event.getEntity() instanceof ServerPlayer) {
            return;
        }

        Level level = event.getEntity().level();
        BlockPos target = event.getPos();
        // Non-player entities do not receive claim membership. They may destroy
        // wilderness blocks, but protected blocks remain protected regardless
        // of where the mob originated. This catches targeted vanilla/modded
        // LivingEntity destruction without globally disabling harmless mob AI.
        if (ProtectionHelper.getRegionAt(level, target) != null
                || ProtectionHelper.getClaimAt(level, target) != null) {
            event.setCanceled(true);
        }
    }

    private static void denyProjectileImpact(ProjectileImpactEvent event, Projectile projectile) {
        // NeoForge specifies that cancelling ProjectileImpactEvent alone lets
        // the projectile continue flying. Discard it first so protection acts
        // as a solid boundary instead of turning protected blocks/entities
        // intangible to denied arrows/fireballs/etc.
        projectile.discard();
        event.setCanceled(true);
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
