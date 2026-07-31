package be.winnetrie.mod.simpleserverutilities.utilitymining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class UtilityMiningResolver {
    public static final int HARD_MAX_BLOCKS = 2048;
    private static final int MIN_NATURAL_CANOPY_LEAVES = 3;

    private static final TagKey<Block> COMMON_ORES = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath("c", "ores")
    );

    private static final List<OreGroup> VANILLA_ORES = List.of(
            new OreGroup("coal", PermissionKeys.VEINMINER_ORE_COAL, Set.of(
                    "minecraft:coal_ore", "minecraft:deepslate_coal_ore")),
            new OreGroup("iron", PermissionKeys.VEINMINER_ORE_IRON, Set.of(
                    "minecraft:iron_ore", "minecraft:deepslate_iron_ore")),
            new OreGroup("copper", PermissionKeys.VEINMINER_ORE_COPPER, Set.of(
                    "minecraft:copper_ore", "minecraft:deepslate_copper_ore")),
            new OreGroup("gold", PermissionKeys.VEINMINER_ORE_GOLD, Set.of(
                    "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore")),
            new OreGroup("redstone", PermissionKeys.VEINMINER_ORE_REDSTONE, Set.of(
                    "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore")),
            new OreGroup("emerald", PermissionKeys.VEINMINER_ORE_EMERALD, Set.of(
                    "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore")),
            new OreGroup("lapis", PermissionKeys.VEINMINER_ORE_LAPIS, Set.of(
                    "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore")),
            new OreGroup("diamond", PermissionKeys.VEINMINER_ORE_DIAMOND, Set.of(
                    "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"))
    );

    private UtilityMiningResolver() {
    }

    public static UtilityMiningTarget resolve(ServerPlayer player, BlockPos origin) {
        if (!(player.level() instanceof ServerLevel level) || origin == null || !level.hasChunkAt(origin)) {
            return UtilityMiningTarget.empty();
        }

        BlockState state = level.getBlockState(origin);
        if (isLog(state) && canUseTreecapitator(player, state)) {
            return resolveTree(player, level, origin);
        }

        OreGroup ore = oreGroup(state);
        if (ore != null && canUseVeinminer(player, state, ore)) {
            return new UtilityMiningTarget(UtilityMiningType.VEINMINER, resolveVein(player, level, origin, ore));
        }

        return UtilityMiningTarget.empty();
    }

    public static boolean canUseTreecapitator(ServerPlayer player, BlockState originState) {
        if (!hasRequiredTool(player, UtilityMiningType.TREECAPITATOR)
                || !Config.ENABLE_TREECAPITATOR.get()
                || !PermissionService.getBoolean(player, PermissionKeys.TREECAPITATOR_USE, true)) {
            return false;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(originState.getBlock());
        return PermissionService.getBoolean(player, PermissionKeys.TREECAPITATOR_BLOCKS, true)
                && PermissionService.getBoolean(player, PermissionKeys.treecapitatorBlock(id), true);
    }

    public static boolean canUseVeinminer(ServerPlayer player, BlockState originState, OreGroup ore) {
        if (!hasRequiredTool(player, UtilityMiningType.VEINMINER)
                || !Config.ENABLE_VEINMINER.get()
                || !PermissionService.getBoolean(player, PermissionKeys.VEINMINER_USE, true)) {
            return false;
        }
        boolean groupAllowed = PermissionService.getBoolean(player, ore.permission(), ore.defaultAllowed());
        Identifier id = BuiltInRegistries.BLOCK.getKey(originState.getBlock());
        return groupAllowed && PermissionService.getBoolean(player, PermissionKeys.veinminerBlock(id), groupAllowed);
    }

    public static boolean hasRequiredTool(ServerPlayer player, UtilityMiningType type) {
        if (player == null || type == null || player.getMainHandItem().isEmpty()) return false;
        return switch (type) {
            case NONE -> false;
            case TREECAPITATOR -> player.getMainHandItem().is(ItemTags.AXES);
            case VEINMINER -> player.getMainHandItem().is(ItemTags.PICKAXES);
        };
    }

    /** Lightweight revalidation for blocks already selected by a server preview. */
    static boolean matchesSelectedBlock(
            ServerPlayer player,
            BlockState originalState,
            BlockState currentState,
            UtilityMiningType expectedType
    ) {
        if (!hasRequiredTool(player, expectedType)) return false;
        if (expectedType == UtilityMiningType.TREECAPITATOR) {
            return isLog(originalState)
                    && isLog(currentState)
                    && treeFamily(currentState).equals(treeFamily(originalState))
                    && canUseTreeBlock(player, currentState);
        }
        if (expectedType == UtilityMiningType.VEINMINER) {
            OreGroup original = oreGroup(originalState);
            OreGroup current = oreGroup(currentState);
            return original != null && current != null
                    && original.id().equals(current.id())
                    && canUseVeinminer(player, currentState, current);
        }
        return false;
    }

    public static boolean isTrackableTreeMaterial(BlockState state) {
        return isLog(state) || state.is(BlockTags.LEAVES);
    }

    public static boolean isNaturalLeaf(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.LEAVES)
                || SimpleServerUtilities.TREE_PLACEMENTS.isPlayerPlaced(level, pos)) {
            return false;
        }

        // Vanilla's LeavesBlock property constants/helpers are package-private in
        // 26.2. Read the conventional property names through BlockState instead;
        // this also works for modded leaf blocks that expose the same properties.
        Comparable<?> persistent = propertyValue(state, "persistent");
        if (Boolean.TRUE.equals(persistent)) return false;

        Comparable<?> distance = propertyValue(state, "distance");
        return !(distance instanceof Number number) || number.intValue() < LeavesBlock.DECAY_DISTANCE;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> propertyValue(BlockState state, String name) {
        for (Property property : state.getProperties()) {
            if (name.equals(property.getName())) {
                return (Comparable<?>) state.getValue(property);
            }
        }
        return null;
    }

    private static UtilityMiningTarget resolveTree(ServerPlayer player, ServerLevel level, BlockPos origin) {
        BlockState originState = level.getBlockState(origin);
        String targetFamily = treeFamily(originState);
        List<BlockPos> allLogs = discoverNaturalLogs(player, level, origin, targetFamily, Integer.MIN_VALUE);

        int leafRange = Config.TREECAPITATOR_LEAF_SEARCH_RANGE.get();
        List<BlockPos> canopyLeaves = leafRange <= 0 ? List.of()
                : discoverNaturalLeaves(level, allLogs, leafRange, false);
        if (!looksLikeNaturalTree(allLogs) || !hasNaturalCanopy(allLogs, canopyLeaves)) {
            return UtilityMiningTarget.empty();
        }
        // Cleanup remains deliberately more conservative than validation. A nearby
        // different tree species may share a canopy without disabling the selected
        // tree, but leaves that are also close to another trunk are left untouched.
        List<BlockPos> naturalLeaves = leafRange <= 0 ? List.of()
                : discoverNaturalLeaves(level, allLogs, leafRange, true);

        int max = blockLimit(player, PermissionKeys.TREECAPITATOR_MAX_BLOCKS,
                Config.TREECAPITATOR_DEFAULT_MAX_BLOCKS.get());
        if (max <= 0) return UtilityMiningTarget.empty();

        // The target log is the lower boundary of this action. Logs below it are
        // intentionally excluded from both the preview and the actual break chain.
        List<BlockPos> upwardLogs = discoverNaturalLogs(player, level, origin, targetFamily, origin.getY());
        List<BlockPos> selectedLogs = selectTreeLogs(upwardLogs, origin, max);
        // Completion is relative to the upward section selected by this action. Logs
        // below the targeted block intentionally remain, so comparing against the full
        // tree incorrectly disabled canopy cleanup whenever mining started above ground.
        boolean complete = selectedLogs.size() == upwardLogs.size() && selectedLogs.containsAll(upwardLogs);
        List<BlockPos> leavesToBreak = complete && Config.TREECAPITATOR_BREAK_NATURAL_LEAVES.get()
                ? orderLeaves(naturalLeaves)
                : List.of();
        return new UtilityMiningTarget(
                UtilityMiningType.TREECAPITATOR,
                selectedLogs,
                leavesToBreak,
                complete
        );
    }

    private static List<BlockPos> discoverNaturalLogs(
            ServerPlayer player,
            ServerLevel level,
            BlockPos origin,
            String targetFamily,
            int minimumY
    ) {
        ArrayList<BlockPos> logs = new ArrayList<>();
        HashSet<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos immutableOrigin = origin.immutable();
        queue.add(immutableOrigin);
        visited.add(immutableOrigin);

        while (!queue.isEmpty() && logs.size() < HARD_MAX_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            if (pos.getY() < minimumY || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isLog(state)
                    || !treeFamily(state).equals(targetFamily)
                    || !canUseTreeBlock(player, state)
                    || !ProtectionHelper.canPlayerBreak(player, level, pos)) {
                continue;
            }
            logs.add(pos.immutable());
            addTreeLogNeighbours(level, pos, queue, visited);
        }
        return List.copyOf(logs);
    }

    private static boolean looksLikeNaturalTree(List<BlockPos> logs) {
        // The authoritative canopy check below is what distinguishes a natural tree
        // from an ordinary loose log. Do not reject a small remaining crown merely
        // because the player already removed one or more trunk blocks manually.
        return logs != null && !logs.isEmpty();
    }

    private static List<BlockPos> discoverNaturalLeaves(
            ServerLevel level,
            List<BlockPos> logs,
            int range,
            boolean requireExclusiveOwnership
    ) {
        Set<BlockPos> logSet = new HashSet<>(logs);
        ArrayList<BlockPos> leaves = new ArrayList<>();
        HashSet<BlockPos> visited = new HashSet<>(logSet);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos log : logs) addNeighbours26(log, queue, visited);

        while (!queue.isEmpty() && leaves.size() < HARD_MAX_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isNaturalLeaf(level, pos, state)) continue;
            int selectedDistance = distanceToNearest(pos, logs, range);
            if (selectedDistance > range
                    || (requireExclusiveOwnership
                    && !belongsToSelectedTree(level, pos, logSet, selectedDistance, range))) {
                continue;
            }
            leaves.add(pos.immutable());
            addNeighbours26(pos, queue, visited);
        }
        return List.copyOf(leaves);
    }

    /** Avoids crossing a touching canopy into a neighbouring trunk. */
    private static boolean belongsToSelectedTree(
            ServerLevel level,
            BlockPos leaf,
            Set<BlockPos> selectedLogs,
            int selectedDistance,
            int range
    ) {
        int radius = Math.max(0, Math.min(range, selectedDistance));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = leaf.offset(dx, dy, dz);
                    if (selectedLogs.contains(candidate) || !level.hasChunkAt(candidate)) continue;
                    if (isLog(level.getBlockState(candidate))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasNaturalCanopy(List<BlockPos> logs, List<BlockPos> leaves) {
        if (leaves.size() < MIN_NATURAL_CANOPY_LEAVES) return false;
        int top = logs.stream().mapToInt(BlockPos::getY).max().orElse(Integer.MIN_VALUE);
        for (BlockPos leaf : leaves) {
            if (leaf.getY() >= top - 1) return true;
        }
        return false;
    }

    private static List<BlockPos> selectTreeLogs(List<BlockPos> logs, BlockPos origin, int maximum) {
        if (logs.isEmpty() || maximum <= 0) return List.of();
        int count = Math.min(maximum, logs.size());
        // discoverNaturalLogs is breadth-first from the selected log. Taking its first
        // entries preserves a connected upward section instead of cherry-picking a
        // distant top branch when a rank limit is reached.
        return orderLogs(logs.subList(0, count), origin);
    }

    private static List<BlockPos> orderLogs(List<BlockPos> logs, BlockPos origin) {
        ArrayList<BlockPos> ordered = new ArrayList<>(logs);
        ordered.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()
                .thenComparingDouble(pos -> pos.distSqr(origin)));
        return List.copyOf(ordered);
    }

    private static List<BlockPos> orderLeaves(List<BlockPos> leaves) {
        ArrayList<BlockPos> ordered = new ArrayList<>(leaves);
        ordered.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        return List.copyOf(ordered);
    }

    private static int distanceToNearest(BlockPos pos, List<BlockPos> logs, int maximum) {
        int result = maximum + 1;
        for (BlockPos log : logs) {
            int distance = Math.max(Math.abs(pos.getX() - log.getX()),
                    Math.max(Math.abs(pos.getY() - log.getY()), Math.abs(pos.getZ() - log.getZ())));
            result = Math.min(result, distance);
        }
        return result;
    }

    private static List<BlockPos> resolveVein(ServerPlayer player, ServerLevel level, BlockPos origin, OreGroup targetOre) {
        int max = blockLimit(player, PermissionKeys.VEINMINER_MAX_BLOCKS,
                Config.VEINMINER_DEFAULT_MAX_BLOCKS.get());
        if (max <= 0) return List.of();

        ArrayList<BlockPos> result = new ArrayList<>();
        HashSet<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        visited.add(origin.immutable());

        while (!queue.isEmpty() && result.size() < max) {
            BlockPos pos = queue.removeFirst();
            BlockState state = level.getBlockState(pos);
            OreGroup current = oreGroup(state);
            if (current == null || !current.id().equals(targetOre.id())
                    || !canUseVeinminer(player, state, current)
                    || !ProtectionHelper.canPlayerBreak(player, level, pos)) {
                continue;
            }
            result.add(pos.immutable());
            addNeighbours26(pos, queue, visited);
        }
        result.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        return List.copyOf(result);
    }

    private static boolean canUseTreeBlock(ServerPlayer player, BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return PermissionService.getBoolean(player, PermissionKeys.TREECAPITATOR_BLOCKS, true)
                && PermissionService.getBoolean(player, PermissionKeys.treecapitatorBlock(id), true);
    }

    private static boolean isLog(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String key = id.toString().toLowerCase(Locale.ROOT);
        if (configuredSet(Config.TREECAPITATOR_DISABLED_LOG_BLOCKS.get()).contains(key)) return false;
        if (state.is(BlockTags.LOGS)
                || configuredSet(Config.TREECAPITATOR_CUSTOM_LOG_BLOCKS.get()).contains(key)) {
            return true;
        }

        // Compatibility fallback for modded woods that use the normal Minecraft
        // naming convention but forgot to include every stripped/wood variant in
        // the logs tag. Disabled blocks above always remain authoritative.
        int separator = key.indexOf(':');
        String path = separator < 0 ? key : key.substring(separator + 1);
        if (path.startsWith("stripped_")) path = path.substring("stripped_".length());
        return path.endsWith("_log")
                || path.endsWith("_wood")
                || path.endsWith("_stem")
                || path.endsWith("_hyphae")
                || "bamboo_block".equals(path);
    }


    /**
     * Groups the normal, stripped and bark-on-all-sides variants of one wood type.
     * Vanilla and conventionally named modded logs therefore remain one tree family,
     * while different wood species and namespaces never merge.
     */
    private static String treeFamily(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String key = id.toString().toLowerCase(Locale.ROOT);
        int separator = key.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : key.substring(0, separator);
        String path = separator < 0 ? key : key.substring(separator + 1);

        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        for (String suffix : List.of("_hyphae", "_stem", "_wood", "_log")) {
            if (path.endsWith(suffix) && path.length() > suffix.length()) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        // Bamboo uses *_bamboo_block rather than the regular log/wood suffixes.
        if ("bamboo_block".equals(path)) {
            path = "bamboo";
        }
        return namespace + ':' + path;
    }

    private static OreGroup oreGroup(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String blockId = id.toString();
        if (configuredSet(Config.VEINMINER_DISABLED_ORE_BLOCKS.get()).contains(blockId)) return null;
        for (OreGroup group : VANILLA_ORES) {
            if (group.blockIds().contains(blockId)) return group;
        }
        if (state.is(COMMON_ORES) || configuredSet(Config.VEINMINER_CUSTOM_ORE_BLOCKS.get()).contains(blockId)) {
            return new OreGroup(blockId, PermissionKeys.veinminerBlock(id), Set.of(blockId), false);
        }
        return null;
    }

    private static int blockLimit(ServerPlayer player, String permission, int fallback) {
        return Math.max(0, Math.min(HARD_MAX_BLOCKS, PermissionService.getInt(player, permission, fallback)));
    }

    /**
     * Discovers directly connected logs and reconnects across one missing vertical
     * trunk block. This lets Treecapitator continue working when a player already
     * removed one log from the middle of an otherwise natural tree, without
     * jumping horizontally into neighbouring trees.
     */
    private static void addTreeLogNeighbours(
            ServerLevel level,
            BlockPos center,
            ArrayDeque<BlockPos> queue,
            Set<BlockPos> visited
    ) {
        addNeighbours26(center, queue, visited);
        for (int direction : new int[] {-1, 1}) {
            BlockPos gap = center.offset(0, direction, 0);
            BlockPos candidate = center.offset(0, direction * 2, 0).immutable();
            if (!level.hasChunkAt(gap) || !level.hasChunkAt(candidate)) continue;
            if (isLog(level.getBlockState(gap))) continue;
            if (visited.add(candidate)) queue.addLast(candidate);
        }
    }

    private static void addNeighbours26(BlockPos center, ArrayDeque<BlockPos> queue, Set<BlockPos> visited) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos next = center.offset(dx, dy, dz).immutable();
                    if (visited.add(next)) queue.addLast(next);
                }
            }
        }
    }

    private static Set<String> configuredSet(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        HashSet<String> result = new HashSet<>();
        for (String value : raw.split(",")) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return result;
    }

    public record OreGroup(String id, String permission, Set<String> blockIds, boolean defaultAllowed) {
        private OreGroup(String id, String permission, Set<String> blockIds) {
            this(id, permission, Set.copyOf(blockIds), "coal".equals(id));
        }

        public OreGroup {
            blockIds = blockIds == null ? Set.of() : Set.copyOf(blockIds);
        }
    }
}
