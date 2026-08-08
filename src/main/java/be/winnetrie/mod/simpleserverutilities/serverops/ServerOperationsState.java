package be.winnetrie.mod.simpleserverutilities.serverops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistent, compact configuration/state for the GUI-first server operations suite. */
public final class ServerOperationsState {
    public int schemaVersion = 3;

    public boolean activityLoggingEnabled = true;
    public boolean activityBreaks = true;
    public boolean activityPlaces = true;
    public int activityRetentionDays = 14;
    public int activityMaxEntries = 20_000;
    public int rollbackBlocksPerTick = 64;

    public boolean automaticBackups = false;
    public int automaticBackupIntervalMinutes = 360;
    public int backupRetentionCount = 7;

    public boolean maintenanceEnabled = false;
    public String maintenanceMessage = "Server maintenance is in progress. Please try again later.";

    public boolean chatModerationEnabled = false;
    public int chatSlowModeSeconds = 0;
    public int chatDuplicateWindowSeconds = 8;
    public int chatBurstWindowSeconds = 10;
    public int chatBurstMaxMessages = 8;
    public int chatCapsPercent = 85;
    public int chatCapsMinimumLength = 12;
    public boolean chatLinksAllowed = true;
    public boolean staffChatEnabled = true;
    public List<String> blockedWords = new ArrayList<>();

    public int pregenChunksPerTick = 1;
    public double pregenPauseAboveMspt = 48.0D;

    public long economyAlertThresholdMinor = 1_000_000L;
    public int closedTicketRetentionHours = 24;

    public Map<String, MuteRecord> mutes = new HashMap<>();
    public List<ScheduledTask> tasks = new ArrayList<>();
    public List<SupportTicket> tickets = new ArrayList<>();

    public void normalize() {
        schemaVersion = 3;
        activityRetentionDays = clamp(activityRetentionDays, 1, 90);
        activityMaxEntries = clamp(activityMaxEntries, 1_000, 100_000);
        rollbackBlocksPerTick = clamp(rollbackBlocksPerTick, 8, 256);
        automaticBackupIntervalMinutes = clamp(automaticBackupIntervalMinutes, 15, 10_080);
        backupRetentionCount = clamp(backupRetentionCount, 1, 50);
        maintenanceMessage = bounded(maintenanceMessage, 512, "Server maintenance is in progress. Please try again later.");
        chatSlowModeSeconds = clamp(chatSlowModeSeconds, 0, 300);
        chatDuplicateWindowSeconds = clamp(chatDuplicateWindowSeconds, 0, 300);
        chatBurstWindowSeconds = clamp(chatBurstWindowSeconds, 1, 120);
        chatBurstMaxMessages = clamp(chatBurstMaxMessages, 2, 100);
        chatCapsPercent = clamp(chatCapsPercent, 0, 100);
        chatCapsMinimumLength = clamp(chatCapsMinimumLength, 1, 256);
        pregenChunksPerTick = clamp(pregenChunksPerTick, 1, 4);
        pregenPauseAboveMspt = Math.max(20.0D, Math.min(200.0D, pregenPauseAboveMspt));
        economyAlertThresholdMinor = Math.max(0L, economyAlertThresholdMinor);
        closedTicketRetentionHours = clamp(closedTicketRetentionHours, 1, 720);
        if (blockedWords == null) blockedWords = new ArrayList<>();
        ArrayList<String> words = new ArrayList<>();
        for (String value : blockedWords) {
            String word = bounded(value, 48, "").trim().toLowerCase(java.util.Locale.ROOT);
            if (!word.isBlank() && !words.contains(word) && words.size() < 128) words.add(word);
        }
        blockedWords = words;
        if (mutes == null) mutes = new HashMap<>();
        mutes.values().removeIf(v -> v == null);
        for (MuteRecord value : mutes.values()) value.normalize();
        if (tasks == null) tasks = new ArrayList<>();
        tasks.removeIf(v -> v == null);
        for (ScheduledTask value : tasks) value.normalize();
        if (tickets == null) tickets = new ArrayList<>();
        tickets.removeIf(v -> v == null);
        for (SupportTicket value : tickets) value.normalize();
    }

    public static final class MuteRecord {
        public String playerId = "";
        public String playerName = "";
        public long expiresAt = 0L;
        public String reason = "";
        public String actor = "";
        public long createdAt = 0L;
        public void normalize() {
            playerId = bounded(playerId, 64, "");
            playerName = bounded(playerName, 64, "");
            reason = bounded(reason, 256, "");
            actor = bounded(actor, 64, "");
            expiresAt = Math.max(0L, expiresAt);
            createdAt = Math.max(0L, createdAt);
        }
        public boolean active(long now) { return expiresAt <= 0L || expiresAt > now; }
    }

    public static final class ScheduledTask {
        public String id = "";
        public String name = "";
        public String action = "BACKUP";
        public String payload = "";
        public int intervalMinutes = 60;
        public String scheduleMode = "INTERVAL";
        public String scheduleSpec = "60";
        public long nextRunAt = 0L;
        public boolean enabled = true;
        public boolean system = false;
        public long lastRunAt = 0L;
        public String lastResult = "";
        public void normalize() {
            id = bounded(id, 48, "").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
            name = bounded(name, 80, id.isBlank() ? "Scheduled task" : id);
            action = bounded(action, 32, "BACKUP").toUpperCase(java.util.Locale.ROOT);
            payload = bounded(payload, 512, "");
            intervalMinutes = clamp(intervalMinutes, 0, 525_600);
            scheduleMode = bounded(scheduleMode, 16, "INTERVAL").toUpperCase(java.util.Locale.ROOT);
            if (!List.of("INTERVAL", "DAILY", "ONCE").contains(scheduleMode)) scheduleMode = "INTERVAL";
            scheduleSpec = bounded(scheduleSpec, 48, Integer.toString(intervalMinutes)).trim();
            if (scheduleSpec.isBlank()) scheduleSpec = Integer.toString(intervalMinutes);
            nextRunAt = Math.max(0L, nextRunAt);
            lastRunAt = Math.max(0L, lastRunAt);
            lastResult = bounded(lastResult, 256, "");
        }
    }

    public static final class SupportTicket {
        public long id = 0L;
        public String playerId = "";
        public String playerName = "";
        public String category = SupportTicketCategory.HELP.name();
        public String reportTargetId = "";
        public String reportTargetName = "";
        public String status = "OPEN";
        public String assignedTo = "";
        public boolean unreadForPlayer = false;
        public boolean unreadForStaff = false;
        public List<TicketMessage> messages = new ArrayList<>();
        public long createdAt = 0L;
        public long updatedAt = 0L;
        public long closedAt = 0L;
        public String closeReason = "";

        // Schema-1 compatibility fields. normalize() migrates these once into the threaded history.
        public String message = "";
        public String staffNote = "";

        public void normalize() {
            id = Math.max(0L, id);
            playerId = bounded(playerId, 64, "");
            playerName = bounded(playerName, 64, "");
            category = SupportTicketCategory.parse(category).name();
            reportTargetId = bounded(reportTargetId, 64, "");
            reportTargetName = bounded(reportTargetName, 64, "");
            status = bounded(status, 16, "OPEN").toUpperCase(java.util.Locale.ROOT);
            if (!List.of("OPEN", "ASSIGNED", "RESOLVED", "CLOSED").contains(status)) status = "OPEN";
            assignedTo = bounded(assignedTo, 64, "");
            createdAt = Math.max(0L, createdAt);
            updatedAt = Math.max(createdAt, updatedAt);
            closedAt = Math.max(0L, closedAt);
            closeReason = bounded(closeReason, 512, "");
            if (status.equals("CLOSED") && closedAt <= 0L) closedAt = updatedAt;
            if (messages == null) messages = new ArrayList<>();
            messages.removeIf(v -> v == null);
            for (TicketMessage value : messages) value.normalize();

            String legacyPlayer = SupportRichText.normalize(message);
            String legacyStaff = SupportRichText.normalize(staffNote);
            if (messages.isEmpty() && !SupportRichText.plainText(legacyPlayer).isBlank()) {
                TicketMessage migrated = new TicketMessage();
                migrated.authorId = playerId;
                migrated.authorName = playerName;
                migrated.role = "PLAYER";
                migrated.body = legacyPlayer;
                migrated.createdAt = createdAt;
                migrated.normalize();
                messages.add(migrated);
            }
            if (!SupportRichText.plainText(legacyStaff).isBlank()) {
                boolean alreadyPresent = messages.stream().anyMatch(v -> v.role.equals("STAFF") && v.body.equals(legacyStaff));
                if (!alreadyPresent) {
                    TicketMessage migrated = new TicketMessage();
                    migrated.authorName = assignedTo.isBlank() ? "Staff" : assignedTo;
                    migrated.role = "STAFF";
                    migrated.body = legacyStaff;
                    migrated.createdAt = Math.max(createdAt, updatedAt);
                    migrated.normalize();
                    messages.add(migrated);
                }
            }
            message = "";
            staffNote = "";
            if (messages.size() > SupportRichText.MAX_MESSAGES_PER_TICKET) {
                messages = new ArrayList<>(messages.subList(messages.size() - SupportRichText.MAX_MESSAGES_PER_TICKET, messages.size()));
            }
            messages.sort(java.util.Comparator.comparingLong(v -> v.createdAt));
        }

        public String latestBody() {
            return messages == null || messages.isEmpty() ? "" : messages.get(messages.size() - 1).body;
        }
    }

    public static final class TicketMessage {
        public String authorId = "";
        public String authorName = "";
        public String role = "PLAYER";
        public String body = "";
        public long createdAt = 0L;

        public void normalize() {
            authorId = bounded(authorId, 64, "");
            role = bounded(role, 12, "PLAYER").toUpperCase(java.util.Locale.ROOT);
            authorName = bounded(authorName, 64, role.equals("STAFF") ? "Staff" : "Player");
            if (!List.of("PLAYER", "STAFF", "SYSTEM").contains(role)) role = "PLAYER";
            body = SupportRichText.normalize(body);
            createdAt = Math.max(0L, createdAt);
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String bounded(String value, int max, String fallback) {
        String result = value == null ? fallback : value;
        return result.length() <= max ? result : result.substring(0, max);
    }
}
