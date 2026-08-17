package be.winnetrie.mod.simpleserverutilities.region;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJob;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RegionWorldEditManager {

    private RegionWorldEditManager() {
    }

    public static RegionFillJob createFillJob(
            ServerLevel level,
            RegionSelection selection,
            String weightedBlockList,
            long maxVolume
    ) {
        return createFillJob(level, selection, weightedBlockList, maxVolume, false);
    }

    public static RegionFillJob createFillJob(
            ServerLevel level,
            RegionSelection selection,
            String weightedBlockList,
            long maxVolume,
            boolean suppressContainerDrops
    ) {
        if (!selection.isComplete()) {
            throw new IllegalArgumentException("Selection is incomplete.");
        }

        List<WeightedBlock> blocks = parseWeightedBlocks(weightedBlockList);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("No valid blocks were provided.");
        }

        int minX = Math.min(selection.getPoint1().getX(), selection.getPoint2().getX());
        int minY = Math.min(selection.getPoint1().getY(), selection.getPoint2().getY());
        int minZ = Math.min(selection.getPoint1().getZ(), selection.getPoint2().getZ());
        int maxX = Math.max(selection.getPoint1().getX(), selection.getPoint2().getX());
        int maxY = Math.max(selection.getPoint1().getY(), selection.getPoint2().getY());
        int maxZ = Math.max(selection.getPoint1().getZ(), selection.getPoint2().getZ());

        long volume = safeVolume(minX, minY, minZ, maxX, maxY, maxZ);
        if (volume > maxVolume) {
            throw new IllegalArgumentException("Selection is too large: " + volume + " blocks. Limit: " + maxVolume + ".");
        }

        return new RegionFillJob(
                level,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                blocks,
                operationLocks(level, minX, minY, minZ, maxX, maxY, maxZ),
                suppressContainerDrops
        );
    }


    public static RegionFillJob createFillJob(
            ServerLevel level,
            Region region,
            String weightedBlockList,
            long maxVolume,
            boolean suppressContainerDrops
    ) {
        if (region == null) throw new IllegalArgumentException("Region is required.");
        if (!level.dimension().equals(region.getDimension())) {
            throw new IllegalArgumentException("The region belongs to another dimension.");
        }
        List<WeightedBlock> blocks = parseWeightedBlocks(weightedBlockList);
        if (blocks.isEmpty()) throw new IllegalArgumentException("No valid preset blocks were provided.");
        if (region.getVolume() > maxVolume) {
            throw new IllegalArgumentException("Region is too large: " + region.getVolume() + " blocks. Limit: " + maxVolume + ".");
        }
        return new RegionFillJob(level, region.getMinX(), region.getMinY(), region.getMinZ(),
                region.getMaxX(), region.getMaxY(), region.getMaxZ(), blocks,
                Set.of(SsuJobLocks.region(level.dimension(), region.getName())), suppressContainerDrops);
    }

    public static RegionClearJob createClearJob(ServerLevel level, Region region, long maxVolume) {
        long volume = region.getVolume();
        if (volume > maxVolume) {
            throw new IllegalArgumentException(
                    "Region is too large: " + volume + " blocks. Limit: " + maxVolume + "."
            );
        }

        return new RegionClearJob(
                level,
                "region '" + region.getName() + "'",
                region.getMinX(),
                region.getMinY(),
                region.getMinZ(),
                region.getMaxX(),
                region.getMaxY(),
                region.getMaxZ(),
                Set.of(SsuJobLocks.region(level.dimension(), region.getName())),
                false
        );
    }

    public static RegionClearJob createClearJob(ServerLevel level, RegionSelection selection, long maxVolume) {
        if (selection == null || !selection.isComplete()) {
            throw new IllegalArgumentException("Selection is incomplete.");
        }
        int minX = Math.min(selection.getPoint1().getX(), selection.getPoint2().getX());
        int minY = Math.min(selection.getPoint1().getY(), selection.getPoint2().getY());
        int minZ = Math.min(selection.getPoint1().getZ(), selection.getPoint2().getZ());
        int maxX = Math.max(selection.getPoint1().getX(), selection.getPoint2().getX());
        int maxY = Math.max(selection.getPoint1().getY(), selection.getPoint2().getY());
        int maxZ = Math.max(selection.getPoint1().getZ(), selection.getPoint2().getZ());
        long volume = safeVolume(minX, minY, minZ, maxX, maxY, maxZ);
        if (volume > maxVolume) {
            throw new IllegalArgumentException("Selection is too large: " + volume + " blocks. Limit: " + maxVolume + ".");
        }
        return new RegionClearJob(level, "selection", minX, minY, minZ, maxX, maxY, maxZ,
                operationLocks(level, minX, minY, minZ, maxX, maxY, maxZ), true);
    }

    public static RegionReplaceJob createReplaceJob(
            ServerLevel level,
            RegionSelection selection,
            List<String> sourceBlockIds,
            String weightedTargetList,
            long maxVolume
    ) {
        if (selection == null || !selection.isComplete()) {
            throw new IllegalArgumentException("Selection is incomplete.");
        }
        if (sourceBlockIds == null || sourceBlockIds.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one source block to replace.");
        }
        Set<Block> sourceBlocks = new HashSet<>();
        for (String raw : sourceBlockIds) {
            String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (value.isBlank()) continue;
            String id = value.contains(":") ? value : "minecraft:" + value;
            Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(
                    () -> new IllegalArgumentException("Unknown source block: " + id));
            sourceBlocks.add(block);
        }
        if (sourceBlocks.isEmpty()) throw new IllegalArgumentException("Choose at least one valid source block.");
        List<WeightedBlock> targets = parseWeightedBlocks(weightedTargetList);
        if (targets.isEmpty()) throw new IllegalArgumentException("Choose at least one replacement block.");

        int minX = Math.min(selection.getPoint1().getX(), selection.getPoint2().getX());
        int minY = Math.min(selection.getPoint1().getY(), selection.getPoint2().getY());
        int minZ = Math.min(selection.getPoint1().getZ(), selection.getPoint2().getZ());
        int maxX = Math.max(selection.getPoint1().getX(), selection.getPoint2().getX());
        int maxY = Math.max(selection.getPoint1().getY(), selection.getPoint2().getY());
        int maxZ = Math.max(selection.getPoint1().getZ(), selection.getPoint2().getZ());
        long volume = safeVolume(minX, minY, minZ, maxX, maxY, maxZ);
        if (volume > maxVolume) {
            throw new IllegalArgumentException("Selection is too large: " + volume + " blocks. Limit: " + maxVolume + ".");
        }
        return new RegionReplaceJob(level, minX, minY, minZ, maxX, maxY, maxZ,
                Set.copyOf(sourceBlocks), targets, operationLocks(level, minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static long safeVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long sizeX = (long) maxX - minX + 1L;
        long sizeY = (long) maxY - minY + 1L;
        long sizeZ = (long) maxZ - minZ + 1L;
        if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L) return Long.MAX_VALUE;
        try {
            return Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static Set<String> operationLocks(
            ServerLevel level,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        Set<String> locks = new HashSet<>();
        locks.add(SsuJobLocks.cuboid(level.dimension(), minX, minY, minZ, maxX, maxY, maxZ));

        for (Region region : SimpleServerUtilities.REGIONS.getIntersecting2D(
                level.dimension(), minX, minZ, maxX, maxZ)) {
            boolean overlapsY = minY <= region.getMaxY() && maxY >= region.getMinY();
            if (overlapsY) {
                locks.add(SsuJobLocks.region(region.getDimension(), region.getName()));
            }
        }
        return Set.copyOf(locks);
    }

    private static void clearContainerContents(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            blockEntity.setChanged();
        }
    }

    private static List<WeightedBlock> parseWeightedBlocks(String raw) {
        List<WeightedBlock> result = new ArrayList<>();

        if (raw == null || raw.isBlank()) {
            return result;
        }

        String[] parts = raw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            String[] blockAndWeight = trimmed.split("=", 2);
            String rawBlockId = blockAndWeight[0].trim().toLowerCase(Locale.ROOT);
            String blockId = rawBlockId.contains(":") ? rawBlockId : "minecraft:" + rawBlockId;
            int weight = blockAndWeight.length == 2 ? Integer.parseInt(blockAndWeight[1].trim()) : 1;

            if (weight <= 0) {
                continue;
            }

            final String parsedBlockId = blockId;
            Block block = parsedBlockId.equals("minecraft:air")
                    ? Blocks.AIR
                    : BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(parsedBlockId)).orElseThrow(
                            () -> new IllegalArgumentException("Unknown block: " + parsedBlockId)
                    );
            result.add(new WeightedBlock(block, weight));
        }

        return List.copyOf(result);
    }

    private static Block pickBlock(List<WeightedBlock> blocks, int totalWeight, Random random) {
        int value = random.nextInt(totalWeight);
        int cursor = 0;

        for (WeightedBlock block : blocks) {
            cursor += block.weight();
            if (value < cursor) {
                return block.block();
            }
        }
        return blocks.getLast().block();
    }

    private record WeightedBlock(Block block, int weight) {
    }

    public static final class RegionFillJob implements SsuJob {
        private final ServerLevel level;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final List<WeightedBlock> blocks;
        private final Set<String> resourceLocks;
        private final boolean suppressContainerDrops;
        private final int totalWeight;
        private final long total;
        private final Random random = new Random();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private int x;
        private int y;
        private int z;
        private long changed;
        private boolean complete;

        private RegionFillJob(
                ServerLevel level,
                int minX,
                int minY,
                int minZ,
                int maxX,
                int maxY,
                int maxZ,
                List<WeightedBlock> blocks,
                Set<String> resourceLocks,
                boolean suppressContainerDrops
        ) {
            this.level = level;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.blocks = blocks;
            this.resourceLocks = resourceLocks;
            this.suppressContainerDrops = suppressContainerDrops;
            this.totalWeight = blocks.stream().mapToInt(WeightedBlock::weight).sum();
            this.total = safeVolume(minX, minY, minZ, maxX, maxY, maxZ);
            this.x = minX;
            this.y = minY;
            this.z = minZ;
        }

        @Override
        public String description() {
            return "Fill region selection (" + total + " blocks)";
        }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) {
            int used = 0;
            while (!complete && used < operationBudget) {
                BlockState state = pickBlock(blocks, totalWeight, random).defaultBlockState();
                mutablePos.set(x, y, z);
                if (suppressContainerDrops) clearContainerContents(level, mutablePos);
                level.setBlock(mutablePos, state, 3);
                changed++;
                used++;
                advance();
            }
            return used;
        }

        private void advance() {
            z++;
            if (z <= maxZ) {
                return;
            }
            z = minZ;
            y++;
            if (y <= maxY) {
                return;
            }
            y = minY;
            x++;
            if (x > maxX) {
                complete = true;
            }
        }

        @Override
        public String ownerModule() {
            return "regions";
        }

        @Override
        public Set<String> resourceLocks() {
            return resourceLocks;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public double progress() {
            return total == 0L ? 1.0D : Math.min(1.0D, changed / (double) total);
        }

        public long changedBlocks() {
            return changed;
        }
    }

    public static final class RegionReplaceJob implements SsuJob {
        private final ServerLevel level;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final Set<Block> sourceBlocks;
        private final List<WeightedBlock> targets;
        private final int totalWeight;
        private final long total;
        private final Set<String> resourceLocks;
        private final Random random = new Random();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private int x, y, z;
        private long visited, changed;
        private boolean complete;

        private RegionReplaceJob(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                 Set<Block> sourceBlocks, List<WeightedBlock> targets, Set<String> resourceLocks) {
            this.level = level; this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.sourceBlocks = sourceBlocks; this.targets = targets; this.resourceLocks = resourceLocks;
            this.totalWeight = targets.stream().mapToInt(WeightedBlock::weight).sum();
            this.total = safeVolume(minX, minY, minZ, maxX, maxY, maxZ);
            this.x = minX; this.y = minY; this.z = minZ;
        }

        @Override public String description() { return "Replace blocks in region selection (" + total + " blocks)"; }

        @Override public int runStep(MinecraftServer server, int operationBudget) {
            int used = 0;
            while (!complete && used < operationBudget) {
                mutablePos.set(x, y, z);
                BlockState current = level.getBlockState(mutablePos);
                if (sourceBlocks.contains(current.getBlock())) {
                    clearContainerContents(level, mutablePos);
                    BlockState replacement = pickBlock(targets, totalWeight, random).defaultBlockState();
                    level.setBlock(mutablePos, replacement, 3);
                    changed++;
                }
                visited++; used++; advance();
            }
            return used;
        }

        private void advance() {
            z++;
            if (z <= maxZ) return;
            z = minZ; y++;
            if (y <= maxY) return;
            y = minY; x++;
            if (x > maxX) complete = true;
        }

        @Override public String ownerModule() { return "regions"; }
        @Override public Set<String> resourceLocks() { return resourceLocks; }
        @Override public boolean isComplete() { return complete; }
        @Override public double progress() { return total == 0L ? 1.0D : Math.min(1.0D, visited / (double) total); }
        public long changedBlocks() { return changed; }
    }

    public static final class RegionClearJob implements SsuJob {
        private final ServerLevel level;
        private final String targetDescription;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final long total;
        private final Set<String> resourceLocks;
        private final boolean suppressContainerDrops;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private int x;
        private int y;
        private int z;
        private long visited;
        private long changed;
        private boolean complete;

        private RegionClearJob(
                ServerLevel level,
                String targetDescription,
                int minX,
                int minY,
                int minZ,
                int maxX,
                int maxY,
                int maxZ,
                Set<String> resourceLocks,
                boolean suppressContainerDrops
        ) {
            this.level = level;
            this.targetDescription = targetDescription;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.total = safeVolume(minX, minY, minZ, maxX, maxY, maxZ);
            this.resourceLocks = resourceLocks;
            this.suppressContainerDrops = suppressContainerDrops;
            this.x = minX;
            this.y = minY;
            this.z = minZ;
        }

        @Override
        public String description() {
            return "Clear " + targetDescription + " (" + total + " blocks)";
        }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) {
            int used = 0;
            while (!complete && used < operationBudget) {
                mutablePos.set(x, y, z);
                if (!level.isEmptyBlock(mutablePos)) {
                    if (suppressContainerDrops) clearContainerContents(level, mutablePos);
                    level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), 3);
                    changed++;
                }
                visited++;
                used++;
                advance();
            }
            return used;
        }

        private void advance() {
            z++;
            if (z <= maxZ) {
                return;
            }
            z = minZ;
            y++;
            if (y <= maxY) {
                return;
            }
            y = minY;
            x++;
            if (x > maxX) {
                complete = true;
            }
        }

        @Override
        public String ownerModule() {
            return "regions";
        }

        @Override
        public Set<String> resourceLocks() {
            return resourceLocks;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public double progress() {
            return total == 0L ? 1.0D : Math.min(1.0D, visited / (double) total);
        }

        public long changedBlocks() {
            return changed;
        }
    }

}
