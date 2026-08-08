package be.winnetrie.mod.simpleserverutilities.region;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Small bounded in-memory undo/redo history for administrator World Edit operations.
 * History is deliberately session-only: it is discarded on logout/server stop and never
 * becomes persistent world data.
 */
public final class WorldEditHistoryManager {
    public static final int MAX_ENTRIES = 8;
    public static final long MAX_TOTAL_VOLUME = 2_000_000L;

    private static final Map<UUID, History> HISTORIES = new ConcurrentHashMap<>();

    private WorldEditHistoryManager() { }

    public static boolean hasUndo(UUID playerId) {
        History history = playerId == null ? null : HISTORIES.get(playerId);
        return history != null && !history.undo.isEmpty();
    }

    public static boolean hasRedo(UUID playerId) {
        History history = playerId == null ? null : HISTORIES.get(playerId);
        return history != null && !history.redo.isEmpty();
    }

    /** New world edit: push undo and invalidate the old redo branch. */
    public static void pushUndo(UUID playerId, Entry entry) {
        if (playerId == null || entry == null) return;
        History history = HISTORIES.computeIfAbsent(playerId, ignored -> new History());
        history.redo.clear();
        history.undo.addLast(entry);
        trim(history.undo);
    }

    /** Redo bookkeeping: push an undo entry without destroying the remaining redo chain. */
    public static void pushUndoFromRedo(UUID playerId, Entry entry) {
        if (playerId == null || entry == null) return;
        History history = HISTORIES.computeIfAbsent(playerId, ignored -> new History());
        history.undo.addLast(entry);
        trim(history.undo);
    }

    public static void pushRedo(UUID playerId, Entry entry) {
        if (playerId == null || entry == null) return;
        History history = HISTORIES.computeIfAbsent(playerId, ignored -> new History());
        history.redo.addLast(entry);
        trim(history.redo);
    }

    public static Entry popUndo(UUID playerId) {
        History history = playerId == null ? null : HISTORIES.get(playerId);
        return history == null || history.undo.isEmpty() ? null : history.undo.removeLast();
    }

    public static Entry popRedo(UUID playerId) {
        History history = playerId == null ? null : HISTORIES.get(playerId);
        return history == null || history.redo.isEmpty() ? null : history.redo.removeLast();
    }

    public static void clear(UUID playerId) {
        if (playerId != null) HISTORIES.remove(playerId);
    }

    public static void clearAll() {
        HISTORIES.clear();
    }

    private static void trim(Deque<Entry> entries) {
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        long total = entries.stream().mapToLong(Entry::nominalVolume).sum();
        while (entries.size() > 1 && total > MAX_TOTAL_VOLUME) {
            Entry removed = entries.removeFirst();
            total -= removed.nominalVolume();
        }
    }

    private static final class History {
        private final Deque<Entry> undo = new ArrayDeque<>();
        private final Deque<Entry> redo = new ArrayDeque<>();
    }

    public record Entry(
            ResourceKey<Level> dimension,
            RegionSelectionSchematicManager.Bounds affectedBounds,
            RegionSelectionSchematicManager.Bounds restoreSelectionBounds,
            RegionSelectionSnapshotManager.SnapshotTemplate snapshot
    ) {
        public Entry {
            if (dimension == null || affectedBounds == null || restoreSelectionBounds == null || snapshot == null) {
                throw new IllegalArgumentException("World Edit history entry is incomplete.");
            }
            if (snapshot.sizeX() != affectedBounds.maxX() - affectedBounds.minX() + 1
                    || snapshot.sizeY() != affectedBounds.maxY() - affectedBounds.minY() + 1
                    || snapshot.sizeZ() != affectedBounds.maxZ() - affectedBounds.minZ() + 1) {
                throw new IllegalArgumentException("World Edit history snapshot dimensions do not match its affected bounds.");
            }
        }

        public long nominalVolume() { return affectedBounds.volume(); }
    }
}
