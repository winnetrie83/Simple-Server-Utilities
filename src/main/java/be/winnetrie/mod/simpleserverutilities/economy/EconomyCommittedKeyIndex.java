package be.winnetrie.mod.simpleserverutilities.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Compact bounded idempotency index kept independently from full transaction history. */
final class EconomyCommittedKeyIndex {
    static final int SCHEMA_VERSION = 1;
    static final int MAX_KEYS = 10_000;

    int schemaVersion = SCHEMA_VERSION;
    List<Entry> entries = new ArrayList<>();

    void normalize() {
        schemaVersion = SCHEMA_VERSION;
        if (entries == null) entries = new ArrayList<>();
        List<Entry> newestFirst = new ArrayList<>(Math.min(MAX_KEYS, entries.size()));
        Set<String> seenKeys = new HashSet<>();
        for (int index = entries.size() - 1; index >= 0 && newestFirst.size() < MAX_KEYS; index--) {
            Entry entry = entries.get(index);
            if (entry == null) continue;
            entry.normalize();
            if (!entry.key.isEmpty() && seenKeys.add(entry.key)) newestFirst.add(entry);
        }
        java.util.Collections.reverse(newestFirst);
        entries = newestFirst;
    }

    static final class Entry {
        String key = "";
        long committedAtEpochMilli;

        Entry() {
        }

        Entry(String key, long committedAtEpochMilli) {
            this.key = key;
            this.committedAtEpochMilli = committedAtEpochMilli;
            normalize();
        }

        void normalize() {
            key = key == null ? "" : key.trim();
            if (key.length() > 256) key = key.substring(0, 256);
            committedAtEpochMilli = Math.max(0L, committedAtEpochMilli);
        }
    }
}
