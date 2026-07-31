package be.winnetrie.mod.simpleserverutilities.client.utilitymining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Builds one continuous outer wireframe and removes all internal block-grid edges. */
public final class UtilityMiningOutlineMath {
    private UtilityMiningOutlineMath() {
    }

    public static List<Line> outerOutline(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }

        Set<BlockPos> blocks = new HashSet<>(positions);
        Map<FacePlane, Set<PlaneEdge>> boundaries = new HashMap<>();

        for (BlockPos pos : blocks) {
            for (Direction direction : Direction.values()) {
                if (blocks.contains(pos.relative(direction))) {
                    continue;
                }
                Face face = Face.of(pos, direction);
                Set<PlaneEdge> edges = boundaries.computeIfAbsent(face.plane(), ignored -> new HashSet<>());
                toggle(edges, new PlaneEdge(face.u(), face.v(), face.u() + 1, face.v()));
                toggle(edges, new PlaneEdge(face.u() + 1, face.v(), face.u() + 1, face.v() + 1));
                toggle(edges, new PlaneEdge(face.u(), face.v() + 1, face.u() + 1, face.v() + 1));
                toggle(edges, new PlaneEdge(face.u(), face.v(), face.u(), face.v() + 1));
            }
        }

        Set<UnitEdge> spatialEdges = new HashSet<>();
        for (Map.Entry<FacePlane, Set<PlaneEdge>> entry : boundaries.entrySet()) {
            for (PlaneEdge edge : entry.getValue()) {
                spatialEdges.add(toSpatial(entry.getKey(), edge));
            }
        }

        return merge(spatialEdges);
    }

    private static void toggle(Set<PlaneEdge> set, PlaneEdge edge) {
        PlaneEdge normalized = edge.normalized();
        if (!set.remove(normalized)) {
            set.add(normalized);
        }
    }

    private static UnitEdge toSpatial(FacePlane plane, PlaneEdge edge) {
        return switch (plane.axis()) {
            case X -> new UnitEdge(
                    new Point(plane.coordinate(), edge.u1(), edge.v1()),
                    new Point(plane.coordinate(), edge.u2(), edge.v2())
            ).normalized();
            case Y -> new UnitEdge(
                    new Point(edge.u1(), plane.coordinate(), edge.v1()),
                    new Point(edge.u2(), plane.coordinate(), edge.v2())
            ).normalized();
            case Z -> new UnitEdge(
                    new Point(edge.u1(), edge.v1(), plane.coordinate()),
                    new Point(edge.u2(), edge.v2(), plane.coordinate())
            ).normalized();
        };
    }

    private static List<Line> merge(Set<UnitEdge> unitEdges) {
        Map<LineKey, List<Interval>> groups = new HashMap<>();
        for (UnitEdge edge : unitEdges) {
            Axis axis = edge.axis();
            LineKey key = switch (axis) {
                case X -> new LineKey(axis, edge.start().y(), edge.start().z());
                case Y -> new LineKey(axis, edge.start().x(), edge.start().z());
                case Z -> new LineKey(axis, edge.start().x(), edge.start().y());
            };
            int start = edge.coordinate(edge.start());
            int end = edge.coordinate(edge.end());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Interval(start, end));
        }

        List<Line> result = new ArrayList<>();
        for (Map.Entry<LineKey, List<Interval>> entry : groups.entrySet()) {
            List<Interval> intervals = entry.getValue();
            intervals.sort(Comparator.comparingInt(Interval::start));
            int start = intervals.getFirst().start();
            int end = intervals.getFirst().end();
            for (int index = 1; index < intervals.size(); index++) {
                Interval next = intervals.get(index);
                if (next.start() <= end) {
                    end = Math.max(end, next.end());
                } else {
                    result.add(line(entry.getKey(), start, end));
                    start = next.start();
                    end = next.end();
                }
            }
            result.add(line(entry.getKey(), start, end));
        }
        return List.copyOf(result);
    }

    private static Line line(LineKey key, int start, int end) {
        return switch (key.axis()) {
            case X -> new Line(new Vec3(start, key.fixedA(), key.fixedB()), new Vec3(end, key.fixedA(), key.fixedB()));
            case Y -> new Line(new Vec3(key.fixedA(), start, key.fixedB()), new Vec3(key.fixedA(), end, key.fixedB()));
            case Z -> new Line(new Vec3(key.fixedA(), key.fixedB(), start), new Vec3(key.fixedA(), key.fixedB(), end));
        };
    }

    public record Line(Vec3 start, Vec3 end) {
    }

    private enum Axis { X, Y, Z }

    private record FacePlane(Axis axis, int coordinate) {
    }

    private record Face(FacePlane plane, int u, int v) {
        private static Face of(BlockPos pos, Direction direction) {
            return switch (direction) {
                case WEST -> new Face(new FacePlane(Axis.X, pos.getX()), pos.getY(), pos.getZ());
                case EAST -> new Face(new FacePlane(Axis.X, pos.getX() + 1), pos.getY(), pos.getZ());
                case DOWN -> new Face(new FacePlane(Axis.Y, pos.getY()), pos.getX(), pos.getZ());
                case UP -> new Face(new FacePlane(Axis.Y, pos.getY() + 1), pos.getX(), pos.getZ());
                case NORTH -> new Face(new FacePlane(Axis.Z, pos.getZ()), pos.getX(), pos.getY());
                case SOUTH -> new Face(new FacePlane(Axis.Z, pos.getZ() + 1), pos.getX(), pos.getY());
            };
        }
    }

    private record PlaneEdge(int u1, int v1, int u2, int v2) {
        private PlaneEdge normalized() {
            if (u1 < u2 || (u1 == u2 && v1 <= v2)) {
                return this;
            }
            return new PlaneEdge(u2, v2, u1, v1);
        }
    }

    private record Point(int x, int y, int z) implements Comparable<Point> {
        @Override
        public int compareTo(Point other) {
            int xCompare = Integer.compare(x, other.x);
            if (xCompare != 0) return xCompare;
            int yCompare = Integer.compare(y, other.y);
            return yCompare != 0 ? yCompare : Integer.compare(z, other.z);
        }
    }

    private record UnitEdge(Point start, Point end) {
        private UnitEdge normalized() {
            return start.compareTo(end) <= 0 ? this : new UnitEdge(end, start);
        }

        private Axis axis() {
            if (start.x() != end.x()) return Axis.X;
            if (start.y() != end.y()) return Axis.Y;
            return Axis.Z;
        }

        private int coordinate(Point point) {
            return switch (axis()) {
                case X -> point.x();
                case Y -> point.y();
                case Z -> point.z();
            };
        }
    }

    private record LineKey(Axis axis, int fixedA, int fixedB) {
    }

    private record Interval(int start, int end) {
    }
}
