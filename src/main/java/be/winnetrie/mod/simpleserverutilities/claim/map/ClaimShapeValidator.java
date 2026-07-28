package be.winnetrie.mod.simpleserverutilities.claim.map;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk;

/** Pure connectivity validation shared by claim commands and the interactive map. */
public final class ClaimShapeValidator {

    private ClaimShapeValidator() {
    }

    public static boolean isConnected(Set<ClaimChunk> chunks) {
        if (chunks == null || chunks.size() <= 1) {
            return true;
        }

        Set<ClaimChunk> visited = new HashSet<>();
        Queue<ClaimChunk> queue = new ArrayDeque<>();
        ClaimChunk start = chunks.iterator().next();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            ClaimChunk current = queue.poll();
            addNeighbor(chunks, visited, queue, current.getX() + 1, current.getZ());
            addNeighbor(chunks, visited, queue, current.getX() - 1, current.getZ());
            addNeighbor(chunks, visited, queue, current.getX(), current.getZ() + 1);
            addNeighbor(chunks, visited, queue, current.getX(), current.getZ() - 1);
        }

        return visited.size() == chunks.size();
    }

    private static void addNeighbor(
            Set<ClaimChunk> chunks,
            Set<ClaimChunk> visited,
            Queue<ClaimChunk> queue,
            int x,
            int z
    ) {
        ClaimChunk neighbor = new ClaimChunk(x, z);
        if (chunks.contains(neighbor) && visited.add(neighbor)) {
            queue.add(neighbor);
        }
    }
}
