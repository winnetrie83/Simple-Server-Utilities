package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public class ClaimProtectionEvents {

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        if (PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), chunkPos);

        if (claim == null) {
            return;
        }

        if (claim.canBuild(player.getUUID())) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot break blocks in this claim."));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), chunkPos);

        if (claim == null) {
            return;
        }

        if (claim.canBuild(player.getUUID())) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot place blocks in this claim."));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(event.getPos().getX() >> 4, event.getPos().getZ() >> 4);

        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), chunkPos);

        if (claim == null) {
            return;
        }

        if (claim.canBuild(player.getUUID())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.sendSystemMessage(Component.literal("You cannot interact with blocks in this claim."));
    }
}