package be.winnetrie.mod.simpleserverutilities.core.performance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Coarse two-dimensional spatial index for admin regions.
 *
 * <p>Each index cell covers 8x8 Minecraft chunks (128x128 blocks). Very large
 * regions are kept in a small overflow list so one unusual region cannot
 * allocate millions of index references.</p>
 */
public final class RegionSpatialIndex {

    private static final int CHUNKS_PER_CELL_SHIFT = 3;
    private static final int MAX_CELLS_PER_REGION = 4_096;
    private static final int MAX_QUERY_CELLS = 8_192;

    private final Map<String, DimensionIndex> dimensions = new HashMap<>();
    private final Map<Region, IndexedRegion> reverse = new HashMap<>();

    public synchronized void clear() {
        dimensions.clear();
        reverse.clear();
    }

    public synchronized void rebuild(Collection<Region> regions) {
        clear();
        if (regions == null) {
            return;
        }
        for (Region region : regions) {
            add(region);
        }
    }

    public synchronized void add(Region region) {
        if (region == null || reverse.containsKey(region)) {
            return;
        }

        String dimension = dimensionId(region);
        DimensionIndex dimensionIndex = dimensions.computeIfAbsent(dimension, ignored -> new DimensionIndex());
        dimensionIndex.allRegions.add(region);

        int minCellX = chunkToCell(region.getMinX() >> 4);
        int maxCellX = chunkToCell(region.getMaxX() >> 4);
        int minCellZ = chunkToCell(region.getMinZ() >> 4);
        int maxCellZ = chunkToCell(region.getMaxZ() >> 4);

        long cellCount = (long) (maxCellX - minCellX + 1) * (maxCellZ - minCellZ + 1);
        if (cellCount > MAX_CELLS_PER_REGION) {
            dimensionIndex.largeRegions.add(region);
            reverse.put(region, new IndexedRegion(dimension, Set.of(), true));
            return;
        }

        Set<Long> cells = new LinkedHashSet<>((int) Math.min(cellCount, Integer.MAX_VALUE));
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                long key = key(cellX, cellZ);
                cells.add(key);
                dimensionIndex.cells.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(region);
            }
        }
        reverse.put(region, new IndexedRegion(dimension, Set.copyOf(cells), false));
    }

    public synchronized void remove(Region region) {
        IndexedRegion indexed = reverse.remove(region);
        if (indexed == null) {
            return;
        }

        DimensionIndex dimensionIndex = dimensions.get(indexed.dimension());
        if (dimensionIndex == null) {
            return;
        }

        dimensionIndex.allRegions.remove(region);
        if (indexed.large()) {
            dimensionIndex.largeRegions.remove(region);
        } else {
            for (long cell : indexed.cells()) {
                Set<Region> bucket = dimensionIndex.cells.get(cell);
                if (bucket == null) {
                    continue;
                }
                bucket.remove(region);
                if (bucket.isEmpty()) {
                    dimensionIndex.cells.remove(cell);
                }
            }
        }

        if (dimensionIndex.allRegions.isEmpty()) {
            dimensions.remove(indexed.dimension());
        }
    }

    public synchronized void replace(Region oldRegion, Region newRegion) {
        remove(oldRegion);
        add(newRegion);
    }

    public synchronized CandidateResult candidatesAt(ResourceKey<Level> dimension, BlockPos pos) {
        String dimensionId = dimension.location().toString();
        DimensionIndex index = dimensions.get(dimensionId);
        if (index == null) {
            return CandidateResult.empty();
        }

        int cellX = chunkToCell(pos.getX() >> 4);
        int cellZ = chunkToCell(pos.getZ() >> 4);
        LinkedHashSet<Region> result = new LinkedHashSet<>();
        Set<Region> bucket = index.cells.get(key(cellX, cellZ));
        if (bucket != null) {
            result.addAll(bucket);
        }
        result.addAll(index.largeRegions);
        return new CandidateResult(List.copyOf(result), false);
    }

    public synchronized CandidateResult query2D(
            ResourceKey<Level> dimension,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        String dimensionId = dimension.location().toString();
        DimensionIndex index = dimensions.get(dimensionId);
        if (index == null) {
            return CandidateResult.empty();
        }

        int minCellX = chunkToCell(Math.floorDiv(Math.min(minX, maxX), 16));
        int maxCellX = chunkToCell(Math.floorDiv(Math.max(minX, maxX), 16));
        int minCellZ = chunkToCell(Math.floorDiv(Math.min(minZ, maxZ), 16));
        int maxCellZ = chunkToCell(Math.floorDiv(Math.max(minZ, maxZ), 16));

        long cells = (long) (maxCellX - minCellX + 1) * (maxCellZ - minCellZ + 1);
        if (cells > MAX_QUERY_CELLS) {
            return new CandidateResult(List.copyOf(index.allRegions), true);
        }

        LinkedHashSet<Region> result = new LinkedHashSet<>();
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                Set<Region> bucket = index.cells.get(key(cellX, cellZ));
                if (bucket != null) {
                    result.addAll(bucket);
                }
            }
        }
        result.addAll(index.largeRegions);
        return new CandidateResult(List.copyOf(result), false);
    }

    public synchronized Statistics statistics() {
        int cells = 0;
        int references = 0;
        int large = 0;
        int regions = 0;
        int maxBucket = 0;

        for (DimensionIndex dimension : dimensions.values()) {
            cells += dimension.cells.size();
            large += dimension.largeRegions.size();
            regions += dimension.allRegions.size();
            for (Set<Region> bucket : dimension.cells.values()) {
                references += bucket.size();
                maxBucket = Math.max(maxBucket, bucket.size());
            }
        }

        return new Statistics(dimensions.size(), regions, cells, references, large, maxBucket);
    }

    private static int chunkToCell(int chunk) {
        return Math.floorDiv(chunk, 1 << CHUNKS_PER_CELL_SHIFT);
    }

    private static String dimensionId(Region region) {
        return region.getDimension().location().toString();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public record CandidateResult(List<Region> regions, boolean fallback) {
        public CandidateResult {
            regions = regions == null ? List.of() : List.copyOf(regions);
        }

        static CandidateResult empty() {
            return new CandidateResult(List.of(), false);
        }
    }

    public record Statistics(
            int dimensions,
            int regions,
            int cells,
            int references,
            int largeRegions,
            int maxBucketSize
    ) {
    }

    private record IndexedRegion(String dimension, Set<Long> cells, boolean large) {
    }

    private static final class DimensionIndex {
        private final Map<Long, Set<Region>> cells = new HashMap<>();
        private final Set<Region> largeRegions = new LinkedHashSet<>();
        private final Set<Region> allRegions = new LinkedHashSet<>();
    }
}
