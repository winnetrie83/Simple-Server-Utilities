package be.winnetrie.mod.simpleserverutilities.utilitymining;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Executes validated multi-block mining and tracks player-placed tree materials. */
public final class UtilityMiningEvents {
    private static final Set<UUID> ACTIVE_BREAK_CHAINS = new HashSet<>();

    private UtilityMiningEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!SsuModuleAccess.active("utility_mining")) return;
        if (!Config.ENABLE_TREECAPITATOR.get()) return;
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer)
                || !(event.getLevel() instanceof ServerLevel level)
                || !UtilityMiningResolver.isTrackableTreeMaterial(event.getPlacedBlock())) {
            return;
        }
        SimpleServerUtilities.TREE_PLACEMENTS.markPlaced(level, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!SsuModuleAccess.active("utility_mining")) return;
        if (!Config.ENABLE_TREECAPITATOR.get() && !Config.ENABLE_VEINMINER.get()) return;
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || ACTIVE_BREAK_CHAINS.contains(player.getUUID())) {
            return;
        }

        // Player-placed leaves remain excluded from natural-canopy validation, but
        // log provenance must not reject a valid canopy-backed trunk. Stripping a
        // natural log and replacing one trunk segment with matching wood both pass
        // through placement/modification events on some loaders and were therefore
        // incorrectly rejected before the wood-family comparison could run.
        boolean trackedOrigin = Config.ENABLE_TREECAPITATOR.get()
                && UtilityMiningResolver.isTrackableTreeMaterial(event.getState())
                && SimpleServerUtilities.TREE_PLACEMENTS.isPlayerPlaced(level, event.getPos());

        if (!SimpleServerUtilities.UTILITY_MINING.hasAnyActiveMode(player)) {
            if (trackedOrigin) SimpleServerUtilities.TREE_PLACEMENTS.forget(level, event.getPos());
            return;
        }

        UtilityMiningTarget target = UtilityMiningResolver.resolve(player, event.getPos());
        // The block is about to be removed by the original break action. Clear its
        // persisted provenance only after resolution so a matching stripped/wood
        // origin can participate in the current canopy-backed tree selection.
        if (trackedOrigin) SimpleServerUtilities.TREE_PLACEMENTS.forget(level, event.getPos());
        if (target.isEmpty() || !SimpleServerUtilities.UTILITY_MINING.isEnabledAndActive(player, target.type())) return;

        BlockState originalState = event.getState();
        List<BlockPos> extraBlocks = target.blocks().stream()
                .filter(pos -> !pos.equals(event.getPos()))
                .toList();
        List<BlockPos> naturalLeaves = target.naturalLeaves();
        if (extraBlocks.isEmpty() && naturalLeaves.isEmpty()) return;

        level.getServer().execute(() -> breakValidatedChain(
                player, level, target.type(), originalState, extraBlocks, naturalLeaves, target.completeSelectedTreeSection()
        ));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!SsuModuleAccess.active("utility_mining")) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.UTILITY_MINING.forget(player.getUUID());
            ACTIVE_BREAK_CHAINS.remove(player.getUUID());
        }
    }

    private static void breakValidatedChain(
            ServerPlayer player,
            ServerLevel level,
            UtilityMiningType expectedType,
            BlockState originalState,
            List<BlockPos> positions,
            List<BlockPos> naturalLeaves,
            boolean completeSelectedTreeSection
    ) {
        if (!SsuModuleAccess.active("utility_mining")) return;
        if (!player.isAlive() || player.level() != level
                || !UtilityMiningResolver.hasRequiredTool(player, expectedType)
                || !SimpleServerUtilities.UTILITY_MINING.isEnabledAndActive(player, expectedType)) {
            return;
        }

        UUID playerId = player.getUUID();
        if (!ACTIVE_BREAK_CHAINS.add(playerId)) return;
        try {
            for (BlockPos pos : positions) {
                if (!UtilityMiningResolver.hasRequiredTool(player, expectedType)) break;
                if (!level.hasChunkAt(pos) || !ProtectionHelper.canPlayerBreak(player, level, pos)) continue;
                BlockState current = level.getBlockState(pos);
                if (current.isAir()
                        || !UtilityMiningResolver.matchesSelectedBlock(player, originalState, current, expectedType)) {
                    continue;
                }
                SimpleServerUtilities.TREE_PLACEMENTS.forget(level, pos);
                if (!destroyWithSingleDurabilityCost(player, pos)) break;
            }

            if (expectedType == UtilityMiningType.TREECAPITATOR
                    && completeSelectedTreeSection
                    && Config.TREECAPITATOR_BREAK_NATURAL_LEAVES.get()) {
                breakNaturalLeaves(player, level, naturalLeaves);
            }
        } finally {
            ACTIVE_BREAK_CHAINS.remove(playerId);
        }
    }

    /**
     * Breaks one automatically selected block while charging the real held tool
     * exactly one normal durability attempt. The temporary copy lets vanilla and
     * modded drop logic see the same item type, enchantments and mining capabilities
     * without also charging the real stack a second, implementation-dependent time.
     */
    private static boolean destroyWithSingleDurabilityCost(ServerPlayer player, BlockPos pos) {
        ItemStack originalTool = player.getMainHandItem();
        if (originalTool.isEmpty()) return false;

        ItemStack miningCopy = originalTool.copy();
        if (miningCopy.isDamageableItem()) {
            // The copy is only a drop/hook context. Keep it away from its breaking
            // threshold so a nearly-broken real tool does not emit a duplicate
            // break event before the single authoritative damage attempt below.
            miningCopy.setDamageValue(0);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, miningCopy);
        boolean destroyed;
        try {
            destroyed = player.gameMode.destroyBlock(pos);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalTool);
        }

        if (destroyed && !player.getAbilities().instabuild && originalTool.isDamageableItem()) {
            originalTool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
        }
        return destroyed;
    }

    private static void breakNaturalLeaves(ServerPlayer player, ServerLevel level, List<BlockPos> leaves) {
        for (BlockPos pos : leaves) {
            if (!UtilityMiningResolver.hasRequiredTool(player, UtilityMiningType.TREECAPITATOR)) break;
            if (!level.hasChunkAt(pos) || !ProtectionHelper.canPlayerBreak(player, level, pos)) continue;
            BlockState current = level.getBlockState(pos);
            if (!UtilityMiningResolver.isNaturalLeaf(level, pos, current)) continue;
            SimpleServerUtilities.TREE_PLACEMENTS.forget(level, pos);
            // Direct server destruction is intentionally instant and preserves normal leaf drops.
            level.destroyBlock(pos, true, player);
        }
    }
}
