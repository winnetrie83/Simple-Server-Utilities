package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk;

/**
 * Converts claimed chunks into merged outer contour segments. Internal chunk
 * borders are intentionally omitted.
 */
public final class ClaimBorderGeometry {

    private static final int CHUNK_SIZE = 16;

    private ClaimBorderGeometry() {
    }

    public static List<Edge> buildOuterEdges(Set<ClaimChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Map<AxisKey, List<Interval>> intervals = new HashMap<>();

        for (ClaimChunk chunk : chunks) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            int minX = chunkX * CHUNK_SIZE;
            int minZ = chunkZ * CHUNK_SIZE;
            int maxX = minX + CHUNK_SIZE;
            int maxZ = minZ + CHUNK_SIZE;

            if (!chunks.contains(new ClaimChunk(chunkX, chunkZ - 1))) {
                add(intervals, Orientation.HORIZONTAL, minZ, minX, maxX);
            }

            if (!chunks.contains(new ClaimChunk(chunkX, chunkZ + 1))) {
                add(intervals, Orientation.HORIZONTAL, maxZ, minX, maxX);
            }

            if (!chunks.contains(new ClaimChunk(chunkX - 1, chunkZ))) {
                add(intervals, Orientation.VERTICAL, minX, minZ, maxZ);
            }

            if (!chunks.contains(new ClaimChunk(chunkX + 1, chunkZ))) {
                add(intervals, Orientation.VERTICAL, maxX, minZ, maxZ);
            }
        }

        List<Edge> result = new ArrayList<>();

        for (Map.Entry<AxisKey, List<Interval>> entry : intervals.entrySet()) {
            List<Interval> values = entry.getValue();
            values.sort(Comparator.comparingInt(Interval::start));

            int start = values.getFirst().start();
            int end = values.getFirst().end();

            for (int i = 1; i < values.size(); i++) {
                Interval next = values.get(i);

                if (next.start() <= end) {
                    end = Math.max(end, next.end());
                    continue;
                }

                result.add(toEdge(entry.getKey(), start, end));
                start = next.start();
                end = next.end();
            }

            result.add(toEdge(entry.getKey(), start, end));
        }

        result.sort(Comparator
                .comparingInt(Edge::x1)
                .thenComparingInt(Edge::z1)
                .thenComparingInt(Edge::x2)
                .thenComparingInt(Edge::z2));

        return List.copyOf(result);
    }


    /** Greedy rectangle decomposition used for translucent claim fills. */
    public static List<Rectangle> buildRectangles(Set<ClaimChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Set<ClaimChunk> remaining = new java.util.HashSet<>(chunks);
        List<Rectangle> result = new ArrayList<>();

        while (!remaining.isEmpty()) {
            ClaimChunk start = remaining.stream()
                    .min(Comparator.comparingInt(ClaimChunk::getZ).thenComparingInt(ClaimChunk::getX))
                    .orElseThrow();

            int minX = start.getX();
            int minZ = start.getZ();
            int maxX = minX;
            while (remaining.contains(new ClaimChunk(maxX + 1, minZ))) {
                maxX++;
            }

            int maxZ = minZ;
            boolean canExtend = true;
            while (canExtend) {
                int nextZ = maxZ + 1;
                for (int x = minX; x <= maxX; x++) {
                    if (!remaining.contains(new ClaimChunk(x, nextZ))) {
                        canExtend = false;
                        break;
                    }
                }
                if (canExtend) {
                    maxZ = nextZ;
                }
            }

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    remaining.remove(new ClaimChunk(x, z));
                }
            }

            result.add(new Rectangle(
                    minX * CHUNK_SIZE,
                    minZ * CHUNK_SIZE,
                    ((maxX + 1) * CHUNK_SIZE) - 1,
                    ((maxZ + 1) * CHUNK_SIZE) - 1
            ));
        }

        result.sort(Comparator
                .comparingInt(Rectangle::minX)
                .thenComparingInt(Rectangle::minZ)
                .thenComparingInt(Rectangle::maxX)
                .thenComparingInt(Rectangle::maxZ));
        return List.copyOf(result);
    }

    private static void add(
            Map<AxisKey, List<Interval>> intervals,
            Orientation orientation,
            int fixed,
            int start,
            int end
    ) {
        intervals.computeIfAbsent(new AxisKey(orientation, fixed), ignored -> new ArrayList<>())
                .add(new Interval(start, end));
    }

    private static Edge toEdge(AxisKey key, int start, int end) {
        if (key.orientation() == Orientation.HORIZONTAL) {
            return new Edge(start, key.fixed(), end, key.fixed());
        }

        return new Edge(key.fixed(), start, key.fixed(), end);
    }

    private enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    private record AxisKey(Orientation orientation, int fixed) {
    }

    private record Interval(int start, int end) {
    }

    public record Edge(int x1, int z1, int x2, int z2) {
    }

    public record Rectangle(int minX, int minZ, int maxX, int maxZ) {
    }
}
