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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
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
                operationLocks(level, minX, minY, minZ, maxX, maxY, maxZ)
        );
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
                region.getName(),
                region.getMinX(),
                region.getMinY(),
                region.getMinZ(),
                region.getMaxX(),
                region.getMaxY(),
                region.getMaxZ()
        );
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
                    : BuiltInRegistries.BLOCK.getOptional(Identifier.parse(parsedBlockId)).orElseThrow(
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
                Set<String> resourceLocks
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
            this.totalWeight = blocks.stream().mapToInt(WeightedBlock::weight).sum();
            this.total = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
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

    public static final class RegionClearJob implements SsuJob {
        private final ServerLevel level;
        private final String regionName;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final long total;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private int x;
        private int y;
        private int z;
        private long visited;
        private long changed;
        private boolean complete;

        private RegionClearJob(
                ServerLevel level,
                String regionName,
                int minX,
                int minY,
                int minZ,
                int maxX,
                int maxY,
                int maxZ
        ) {
            this.level = level;
            this.regionName = regionName;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.total = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            this.x = minX;
            this.y = minY;
            this.z = minZ;
        }

        @Override
        public String description() {
            return "Clear region '" + regionName + "' (" + total + " blocks)";
        }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) {
            int used = 0;
            while (!complete && used < operationBudget) {
                mutablePos.set(x, y, z);
                if (!level.isEmptyBlock(mutablePos)) {
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
        public Set<String> resourceLocks() {
            return Set.of(SsuJobLocks.region(level.dimension(), regionName));
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
