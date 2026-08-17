package be.winnetrie.mod.simpleserverutilities.cropharvesting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-authoritative right-click harvesting for mature conventional crops. */
public final class CropsHarvestingEvents {
    private static final TagKey<Block> VANILLA_CROPS = cropTag("minecraft", "crops");
    private static final TagKey<Block> COMMON_CROPS = cropTag("c", "crops");
    private static final TagKey<Block> LEGACY_FORGE_CROPS = cropTag("forge", "crops");

    private static final Set<String> GROWTH_PROPERTIES = Set.of(
            "age", "growth", "stage", "maturity", "mature", "ripe", "grown"
    );
    private static final Set<String> NATIVE_OR_NON_HARVESTABLE_CROPS = Set.of(
            "minecraft:sweet_berry_bush",
            "minecraft:melon_stem",
            "minecraft:pumpkin_stem",
            "minecraft:attached_melon_stem",
            "minecraft:attached_pumpkin_stem"
    );

    private CropsHarvestingEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !Config.ENABLE_CROPS_HARVESTING.get()
                || !PermissionService.getBoolean(player, PermissionKeys.CROPS_HARVESTING_USE, true)) {
            return;
        }

        CropTarget target = cropTarget(level, event.getPos());
        if (target == null || !level.hasChunkAt(target.pos())) return;
        if (!ProtectionHelper.canPlayerModify(player, level, target.pos())) return;

        BlockState matureState = target.state();
        if (!isSupportedCrop(matureState)) return;

        GrowthCycle growth = findGrowthCycle(matureState);
        if (growth == null || !growth.isMature(matureState)) return;

        BlockPos upperPos = target.pos().above();
        BlockState upperState = level.getBlockState(upperPos);
        boolean hasUpperPart = upperState.getBlock() == matureState.getBlock() && isUpperHalf(upperState);
        if (hasUpperPart && !ProtectionHelper.canPlayerModify(player, level, upperPos)) return;

        ItemStack harvestingItem = event.getItemStack();
        List<ItemStack> drops = Block.getDrops(
                matureState,
                level,
                target.pos(),
                level.getBlockEntity(target.pos()),
                player,
                harvestingItem
        );

        BlockState replantedState = growth.reset(matureState);
        if (hasUpperPart) {
            // Remove a mature double-height upper half without producing a second
            // loot roll. Roll it back if resetting the root unexpectedly fails.
            if (!level.removeBlock(upperPos, false)) return;
        }
        if (!level.setBlock(target.pos(), replantedState, Block.UPDATE_ALL)) {
            if (hasUpperPart) level.setBlock(upperPos, upperState, Block.UPDATE_ALL);
            return;
        }

        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) Block.popResource(level, target.pos(), drop);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static CropTarget cropTarget(ServerLevel level, BlockPos clickedPos) {
        if (!level.hasChunkAt(clickedPos)) return null;
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!isUpperHalf(clickedState)) return new CropTarget(clickedPos.immutable(), clickedState);

        BlockPos lowerPos = clickedPos.below();
        if (!level.hasChunkAt(lowerPos)) return null;
        BlockState lowerState = level.getBlockState(lowerPos);
        return lowerState.getBlock() == clickedState.getBlock()
                ? new CropTarget(lowerPos.immutable(), lowerState)
                : null;
    }

    private static boolean isSupportedCrop(BlockState state) {
        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String key = id.toString();
        if (NATIVE_OR_NON_HARVESTABLE_CROPS.contains(key)
                || configuredSet(Config.CROPS_HARVESTING_DISABLED_BLOCKS.get()).contains(key)
                || block instanceof SweetBerryBushBlock) {
            return false;
        }
        return block instanceof CropBlock
                || block instanceof NetherWartBlock
                || block instanceof CocoaBlock
                || state.is(VANILLA_CROPS)
                || state.is(COMMON_CROPS)
                || state.is(LEGACY_FORGE_CROPS)
                || configuredSet(Config.CROPS_HARVESTING_CUSTOM_BLOCKS.get()).contains(key);
    }

    private static GrowthCycle findGrowthCycle(BlockState state) {
        GrowthCycle fallback = null;
        for (Property<?> property : state.getProperties()) {
            String name = property.getName().toLowerCase(Locale.ROOT);
            if (!GROWTH_PROPERTIES.contains(name)) continue;

            GrowthCycle cycle = growthCycle(property);
            if (cycle == null) continue;
            if ("age".equals(name)) return cycle;
            if (fallback == null) fallback = cycle;
        }
        return fallback;
    }

    private static GrowthCycle growthCycle(Property<?> property) {
        ArrayList<Comparable<?>> values = new ArrayList<>();
        for (Object value : property.getPossibleValues()) {
            if (value instanceof Comparable<?> comparable) values.add(comparable);
        }
        if (values.size() < 2) return null;

        if (values.stream().allMatch(Number.class::isInstance)) {
            values.sort(Comparator.comparingDouble(value -> ((Number) value).doubleValue()));
        } else if (values.stream().allMatch(Boolean.class::isInstance)) {
            values.sort(Comparator.comparing(value -> (Boolean) value));
        }
        return new GrowthCycle(property, values.getFirst(), values.getLast());
    }

    private static boolean isUpperHalf(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!"half".equalsIgnoreCase(property.getName())) continue;
            Comparable<?> value = propertyValue(state, property);
            return value != null && "upper".equalsIgnoreCase(value.toString());
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> propertyValue(BlockState state, Property<?> property) {
        return (Comparable<?>) state.getValue((Property) property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, Property<?> property, Comparable<?> value) {
        return state.setValue((Property) property, (Comparable) value);
    }

    private static Set<String> configuredSet(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        HashSet<String> values = new HashSet<>();
        for (String token : raw.split(",")) {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank()) values.add(value);
        }
        return Set.copyOf(values);
    }

    private static TagKey<Block> cropTag(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private record CropTarget(BlockPos pos, BlockState state) {
        private CropTarget {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(state, "state");
        }
    }

    private record GrowthCycle(Property<?> property, Comparable<?> initial, Comparable<?> mature) {
        private GrowthCycle {
            Objects.requireNonNull(property, "property");
            Objects.requireNonNull(initial, "initial");
            Objects.requireNonNull(mature, "mature");
        }

        private boolean isMature(BlockState state) {
            return mature.equals(propertyValue(state, property));
        }

        private BlockState reset(BlockState state) {
            return withProperty(state, property, initial);
        }
    }
}
