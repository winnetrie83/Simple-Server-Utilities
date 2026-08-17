package be.winnetrie.mod.simpleserverutilities.serverops;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.core.performance.SsuPerformanceMonitor;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyAccount;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionRecord;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionStatus;
import be.winnetrie.mod.simpleserverutilities.maintenance.SsuReloadService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Performance-first server management core.
 *
 * <p>Continuous work is deliberately tiny: one once-per-second scheduler pass,
 * bounded log batching and an optional throttled pregenerator. Expensive reports,
 * economy analytics and filesystem scans are calculated only when an administrator
 * opens the relevant GUI.</p>
 */
public final class ServerOperationsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson LINE_GSON = new GsonBuilder().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_VISIBLE_LOGS = 40;
    private static final int MAX_VISIBLE_TICKETS = 80;
    private static final int HEALTH_TICK_WINDOW = 200;
    private static final int MAX_HEALTH_HISTORY = 120;
    private static final String AUTO_BACKUP_TASK_ID = "auto_backup";

    private final DirtyJsonRecordStore stateStore = new DirtyJsonRecordStore();
    private final ConcurrentLinkedQueue<String> activityWriteQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> auditWriteQueue = new ConcurrentLinkedQueue<>();
    private final Deque<ActivityEntry> activity = new ArrayDeque<>();
    private final Deque<AuditEntry> audit = new ArrayDeque<>();
    private final Deque<Double> msptWindow = new ArrayDeque<>();
    private final Deque<Double> tickPeriodWindow = new ArrayDeque<>();
    private final Deque<HealthSample> healthHistory = new ArrayDeque<>();
    private final Deque<ChatLogEntry> chatHistory = new ArrayDeque<>();
    private final Map<UUID, ChatWindow> chatWindows = new HashMap<>();

    private ServerOperationsState state = new ServerOperationsState();
    private MinecraftServer server;
    private Path ssuRoot;
    private Path operationsRoot;
    private Path stateFile;
    private Path activityFile;
    private Path auditFile;
    private Path backupFolder;
    private Path profileFolder;
    private ExecutorService io;

    private long tickCounter;
    private long tickStartedAtNanos;
    private long previousTickStartedAtNanos;
    private long lastHealthSampleAt;
    private long lastLogPruneAt;
    private long nextTicketId = 1L;

    private final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private volatile String backupStatus = "Idle";
    private final AtomicLong backupFilesDone = new AtomicLong();
    private final AtomicLong backupFilesTotal = new AtomicLong();
    private volatile PregenJob pregen;
    private volatile RollbackJob rollback;

    private static volatile PendingRestore PENDING_RESTORE;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        this.ssuRoot = StoragePaths.root(server);
        this.operationsRoot = ssuRoot.resolve("server_operations");
        this.stateFile = operationsRoot.resolve("state.json");
        this.activityFile = operationsRoot.resolve("activity.jsonl");
        this.auditFile = operationsRoot.resolve("staff_audit.jsonl");
        this.backupFolder = operationsRoot.resolve("backups");
        this.profileFolder = operationsRoot.resolve("profiles");
        stateStore.reset();
        activity.clear();
        audit.clear();
        chatWindows.clear();
        msptWindow.clear();
        tickPeriodWindow.clear();
        healthHistory.clear();
        chatHistory.clear();
        tickCounter = 0L;
        backupStatus = "Idle";
        backupRunning.set(false);
        backupFilesDone.set(0L);
        backupFilesTotal.set(0L);
        pregen = null;
        rollback = null;
        if (io != null) {
            io.shutdownNow();
        }
        io = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SSU-ServerOperations-IO");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Files.createDirectories(operationsRoot);
            Files.createDirectories(backupFolder);
            Files.createDirectories(profileFolder);
            if (Files.isRegularFile(stateFile)) {
                state = JsonStorage.read(GSON, stateFile, ServerOperationsState.class);
                if (state == null) state = new ServerOperationsState();
                stateStore.discoverFile(stateFile);
            } else {
                state = new ServerOperationsState();
            }
            state.normalize();
            loadJsonLines(activityFile, ActivityEntry.class, activity, state.activityMaxEntries);
            loadJsonLines(auditFile, AuditEntry.class, audit, 20_000);
            nextTicketId = state.tickets.stream().mapToLong(t -> t.id).max().orElse(0L) + 1L;
            ensureAutomaticBackupTask();
            saveState();
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU server operations.", exception);
            state = new ServerOperationsState();
            state.normalize();
        }
        SimpleServerUtilities.LOGGER.info("Loaded SSU Server Operations: activity={}, audit={}, tasks={}, tickets={}",
                activity.size(), audit.size(), state.tasks.size(), state.tickets.size());
    }

    public synchronized void save() {
        saveState();
    }

    public synchronized void beforeStop() {
        saveState();
        flushLogQueuesAsync(true);
    }

    public synchronized void clearRuntime() {
        flushLogQueuesAsync(true);
        if (io != null) {
            io.shutdown();
            try { io.awaitTermination(10L, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
        server = null;
        pregen = null;
        rollback = null;
        chatWindows.clear();
        chatHistory.clear();
    }

    public ServerOperationsState state() { return state; }

    public boolean canAdmin(ServerPlayer player) {
        return SimpleServerUtilities.CORE.modules().isActive("server_operations") && player != null && (PermissionService.isAdmin(player)
                || PermissionService.getBoolean(player, PermissionKeys.SERVER_OPERATIONS_ADMIN, false));
    }

    public boolean canUseReports(ServerPlayer player) {
        return SimpleServerUtilities.CORE.modules().isActive("server_operations") && player != null && PermissionService.getBoolean(player, PermissionKeys.REPORTS_USE, true);
    }

    public void beginTick() {
        long now = System.nanoTime();
        tickStartedAtNanos = now;
        if (previousTickStartedAtNanos > 0L) {
            addRolling(tickPeriodWindow, (now - previousTickStartedAtNanos) / 1_000_000.0D, HEALTH_TICK_WINDOW);
        }
        previousTickStartedAtNanos = now;
    }

    public void endTick(MinecraftServer server) {
        if (tickStartedAtNanos > 0L) {
            addRolling(msptWindow, (System.nanoTime() - tickStartedAtNanos) / 1_000_000.0D, HEALTH_TICK_WINDOW);
        }
        tickCounter++;
        if (tickCounter % 20L == 0L) {
            runScheduler(server);
            sampleHealth(server);
        }
        if (tickCounter % 1_200L == 0L) purgeExpiredClosedTickets();
        if (tickCounter % 100L == 0L) flushLogQueuesAsync(false);
        if (System.currentTimeMillis() - lastLogPruneAt > 600_000L) {
            lastLogPruneAt = System.currentTimeMillis();
            pruneLogsAsync();
        }
        tickRollback();
        tickPregeneration();
    }

    public void onLogin(ServerPlayer player) {
        if (player == null) return;
        if (state.maintenanceEnabled
                && !PermissionService.isAdmin(player)
                && !PermissionService.getBoolean(player, PermissionKeys.MAINTENANCE_BYPASS, false)) {
            player.connection.disconnect(Component.literal(state.maintenanceMessage));
        }
    }

    public void logBlockBreak(ServerPlayer player, BlockPos pos, BlockState before) {
        if (player == null || pos == null || before == null || !state.activityLoggingEnabled || !state.activityBreaks || transientGameplay(player)) return;
        recordActivity(new ActivityEntry(System.currentTimeMillis(), player.getUUID().toString(), player.getName().getString(),
                "BREAK", player.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(),
                blockId(before), "minecraft:air"));
    }

    public void logBlockPlace(ServerPlayer player, BlockPos pos, BlockState before, BlockState after) {
        if (player == null || pos == null || before == null || after == null || !state.activityLoggingEnabled || !state.activityPlaces || transientGameplay(player)) return;
        recordActivity(new ActivityEntry(System.currentTimeMillis(), player.getUUID().toString(), player.getName().getString(),
                "PLACE", player.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(),
                blockId(before), blockId(after)));
    }

    private static boolean transientGameplay(ServerPlayer player) {
        UUID id = player.getUUID();
        return (SsuModuleAccess.active("minigames") && SimpleServerUtilities.MINIGAMES.isInMatch(id, ""))
                || (SsuModuleAccess.active("dungeons") && SimpleServerUtilities.DUNGEONS.isInRun(id, ""));
    }

    private synchronized void recordActivity(ActivityEntry entry) {
        activity.addLast(entry);
        while (activity.size() > state.activityMaxEntries) activity.removeFirst();
        activityWriteQueue.add(LINE_GSON.toJson(entry));
    }

    public synchronized void audit(ServerPlayer actor, String action, String target, String detail) {
        if (actor == null) return;
        audit(actor.getName().getString(), actor.getUUID().toString(), action, target, detail);
    }

    public synchronized void audit(String actor, String actorId, String action, String target, String detail) {
        AuditEntry entry = new AuditEntry(System.currentTimeMillis(), bounded(actor, 64), bounded(actorId, 64),
                bounded(action, 96), bounded(target, 128), bounded(detail, 512));
        audit.addLast(entry);
        while (audit.size() > 20_000) audit.removeFirst();
        auditWriteQueue.add(LINE_GSON.toJson(entry));
    }

    public ChatDecision chat(ServerPlayer player, String rawText) {
        if (player == null) return ChatDecision.allow();
        String text = rawText == null ? "" : rawText.trim();
        long now = System.currentTimeMillis();

        ServerOperationsState.MuteRecord mute;
        synchronized (this) {
            mute = state.mutes.get(player.getUUID().toString());
            if (mute != null && !mute.active(now)) {
                state.mutes.remove(player.getUUID().toString());
                saveState();
                mute = null;
            }
        }
        if (mute != null) {
            long seconds = mute.expiresAt <= 0L ? 0L : Math.max(1L, (mute.expiresAt - now + 999L) / 1000L);
            return ChatDecision.block(mute.expiresAt <= 0L
                    ? "You are muted. " + mute.reason
                    : "You are muted for another " + seconds + "s. " + mute.reason);
        }

        if (state.staffChatEnabled && text.startsWith("#")
                && PermissionService.getBoolean(player, PermissionKeys.STAFF_CHAT, false)) {
            String message = text.substring(1).trim();
            if (!message.isBlank()) {
                Component line = Component.literal("[Staff] " + player.getName().getString() + ": " + message);
                for (ServerPlayer recipient : player.level().getServer().getPlayerList().getPlayers()) {
                    if (PermissionService.getBoolean(recipient, PermissionKeys.STAFF_CHAT, false)) recipient.sendSystemMessage(line);
                }
                recordChat(player, message, true);
            }
            return ChatDecision.silentBlock();
        }

        if (!state.chatModerationEnabled || PermissionService.getBoolean(player, PermissionKeys.CHAT_MOD_BYPASS, false)) {
            recordChat(player, text, false);
            return ChatDecision.allow();
        }

        String lower = text.toLowerCase(Locale.ROOT);
        for (String blocked : state.blockedWords) {
            if (!blocked.isBlank() && lower.contains(blocked)) return ChatDecision.block("That message contains a blocked word or phrase.");
        }
        if (!state.chatLinksAllowed && containsLink(lower)) return ChatDecision.block("Links are not allowed in chat.");
        if (state.chatCapsPercent > 0 && text.length() >= state.chatCapsMinimumLength) {
            int letters = 0, upper = 0;
            for (int i = 0; i < text.length(); i++) { char ch = text.charAt(i); if (Character.isLetter(ch)) { letters++; if (Character.isUpperCase(ch)) upper++; } }
            if (letters >= state.chatCapsMinimumLength && upper * 100 > letters * state.chatCapsPercent)
                return ChatDecision.block("Please avoid excessive capital letters.");
        }

        synchronized (this) {
            ChatWindow window = chatWindows.computeIfAbsent(player.getUUID(), ignored -> new ChatWindow());
            while (!window.timestamps.isEmpty() && now - window.timestamps.peekFirst() > state.chatBurstWindowSeconds * 1000L) {
                window.timestamps.removeFirst();
            }
            if (state.chatSlowModeSeconds > 0 && now - window.lastAcceptedAt < state.chatSlowModeSeconds * 1000L) {
                long seconds = Math.max(1L, (state.chatSlowModeSeconds * 1000L - (now - window.lastAcceptedAt) + 999L) / 1000L);
                return ChatDecision.block("Chat slow mode: wait " + seconds + "s.");
            }
            if (state.chatDuplicateWindowSeconds > 0 && lower.equals(window.lastText)
                    && now - window.lastAcceptedAt < state.chatDuplicateWindowSeconds * 1000L) {
                return ChatDecision.block("Please do not repeat the same message.");
            }
            if (window.timestamps.size() >= state.chatBurstMaxMessages) {
                return ChatDecision.block("You are sending messages too quickly.");
            }
            window.timestamps.addLast(now);
            window.lastAcceptedAt = now;
            window.lastText = lower;
        }
        recordChat(player, text, false);
        return ChatDecision.allow();
    }

    private synchronized void recordChat(ServerPlayer player, String message, boolean staff) {
        if (player == null || message == null || message.isBlank()) return;
        chatHistory.addLast(new ChatLogEntry(System.currentTimeMillis(), player.getUUID().toString(), player.getName().getString(), bounded(message, 512), staff));
        while (chatHistory.size() > 100) chatHistory.removeFirst();
    }

    private static boolean containsLink(String lower) {
        if (lower == null || lower.isBlank()) return false;
        return lower.contains("http://") || lower.contains("https://") || lower.contains("www.")
                || lower.matches(".*\\b[a-z0-9][a-z0-9.-]*\\.(com|net|org|io|gg|be|nl|de|fr|uk|dev|app)(/[^ ]*)?.*");
    }

    public synchronized String setActivitySettings(ServerPlayer actor, boolean enabled, int retentionDays, boolean breaks, boolean places) {
        state.activityLoggingEnabled = enabled;
        state.activityBreaks = breaks;
        state.activityPlaces = places;
        state.activityRetentionDays = Math.max(1, Math.min(90, retentionDays));
        saveState();
        audit(actor, "activity.settings", "activity", "enabled=" + enabled + ", break=" + breaks + ", place=" + places + ", retentionDays=" + state.activityRetentionDays);
        return "Activity logging settings saved.";
    }

    public synchronized String startRollback(ServerPlayer actor, String rawTarget, int hours, int radius) {
        if (rollback != null && !rollback.done()) throw new IllegalArgumentException("Another activity rollback is already running.");
        String target = rawTarget == null ? "" : rawTarget.trim();
        if (target.isBlank()) throw new IllegalArgumentException("Enter a player name or UUID.");
        int boundedHours = Math.max(1, Math.min(24 * 90, hours));
        int boundedRadius = Math.max(1, Math.min(256, radius));
        long cutoff = System.currentTimeMillis() - boundedHours * 3_600_000L;
        String dimension = actor.level().dimension().location().toString();
        BlockPos center = actor.blockPosition();
        ArrayList<ActivityEntry> entries = new ArrayList<>();
        var iterator = activity.descendingIterator();
        while (iterator.hasNext() && entries.size() < 5_000) {
            ActivityEntry entry = iterator.next();
            if (entry.time < cutoff) break;
            if (!entry.dimension.equals(dimension)) continue;
            if (!entry.playerName.equalsIgnoreCase(target) && !entry.playerId.equalsIgnoreCase(target)) continue;
            long dx = (long) entry.x - center.getX();
            long dy = (long) entry.y - center.getY();
            long dz = (long) entry.z - center.getZ();
            long radiusSquared = (long) boundedRadius * boundedRadius;
            if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
            entries.add(entry);
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("No matching lightweight block activity was found.");
        rollback = new RollbackJob(actor.getUUID(), target, dimension, entries, state.rollbackBlocksPerTick);
        audit(actor, "activity.rollback", target, "hours=" + boundedHours + ", radius=" + boundedRadius + ", entries=" + entries.size());
        return "Rollback queued for " + entries.size() + " block change(s).";
    }

    private void tickRollback() {
        RollbackJob job = rollback;
        MinecraftServer currentServer = server;
        if (job == null || currentServer == null || job.done()) return;
        ResourceKey<Level> key;
        try { key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(job.dimension)); }
        catch (Exception exception) { job.fail("Invalid rollback dimension."); return; }
        ServerLevel level = currentServer.getLevel(key);
        if (level == null) { job.fail("Rollback dimension is unavailable."); return; }
        int budget = job.blocksPerTick;
        while (budget-- > 0 && job.index < job.entries.size()) {
            ActivityEntry entry = job.entries.get(job.index++);
            BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
            String current = blockId(level.getBlockState(pos));
            if (!current.equals(entry.afterBlock)) {
                job.skipped++;
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(entry.beforeBlock)).orElse(Blocks.AIR);
            if (level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL)) job.restored++;
            else job.skipped++;
        }
        if (job.index >= job.entries.size()) job.completed = true;
    }

    public String createBackup(ServerPlayer actor, String label) {
        MinecraftServer currentServer = server;
        if (currentServer == null || io == null) throw new IllegalArgumentException("Server is not available.");
        if (!backupRunning.compareAndSet(false, true)) throw new IllegalArgumentException("A backup is already running.");
        SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));
        flushWorldForBackup(currentServer);
        backupStatus = "Preparing backup";
        backupFilesDone.set(0L);
        backupFilesTotal.set(0L);
        Path worldRoot = currentServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path backups = backupFolder.toAbsolutePath().normalize();
        String safeLabel = sanitize(label == null || label.isBlank() ? "backup" : label);
        String fileName = FILE_TIME.format(Instant.now()) + "-" + safeLabel + ".zip";
        Path destination = backups.resolve(fileName);
        if (actor != null) audit(actor, "backup.create", fileName, "manual");
        io.execute(() -> {
            try {
                Files.createDirectories(backups);
                ArrayList<Path> files = new ArrayList<>();
                try (var stream = Files.walk(worldRoot)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> !path.toAbsolutePath().normalize().startsWith(backups))
                            .filter(path -> !path.getFileName().toString().equals("session.lock"))
                            .filter(path -> !path.getFileName().toString().endsWith(".lock"))
                            .forEach(files::add);
                }
                backupFilesTotal.set(files.size());
                backupStatus = "Writing " + fileName;
                try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW));
                     ZipOutputStream zip = new ZipOutputStream(output)) {
                    byte[] buffer = new byte[64 * 1024];
                    for (Path file : files) {
                        String relative = worldRoot.relativize(file).toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(relative));
                        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
                            int read;
                            while ((read = input.read(buffer)) >= 0) if (read > 0) zip.write(buffer, 0, read);
                        }
                        zip.closeEntry();
                        backupFilesDone.incrementAndGet();
                    }
                }
                pruneBackups();
                backupStatus = "Completed " + fileName;
            } catch (Exception exception) {
                backupStatus = "Backup failed: " + bounded(exception.getMessage(), 160);
                SimpleServerUtilities.LOGGER.error("SSU backup failed.", exception);
                try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
            } finally {
                backupRunning.set(false);
            }
        });
        return "Backup started: " + fileName;
    }

    private static void flushWorldForBackup(MinecraftServer server) {
        if (server == null) return;
        try {
            Method method = MinecraftServer.class.getMethod("saveEverything", boolean.class, boolean.class, boolean.class);
            method.invoke(server, true, true, true);
        } catch (ReflectiveOperationException exception) {
            // 26.x mappings can rename save entry points. SSU data was already flushed above; keep
            // the backup available rather than failing solely because a vanilla save hook changed.
            SimpleServerUtilities.LOGGER.warn("Could not explicitly flush vanilla world data before SSU backup; continuing with the latest files on disk.", exception);
        }
    }

    public synchronized String setAutomaticBackup(ServerPlayer actor, boolean enabled, int intervalMinutes, int retention) {
        state.automaticBackups = enabled;
        state.automaticBackupIntervalMinutes = Math.max(15, Math.min(10_080, intervalMinutes));
        state.backupRetentionCount = Math.max(1, Math.min(50, retention));
        ensureAutomaticBackupTask();
        saveState();
        audit(actor, "backup.settings", "automatic", "enabled=" + enabled + ", interval=" + state.automaticBackupIntervalMinutes
                + ", retention=" + state.backupRetentionCount);
        return "Automatic backup settings saved.";
    }

    public synchronized String deleteBackup(ServerPlayer actor, String fileName) {
        Path file = safeChild(backupFolder, fileName);
        try {
            if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Backup not found.");
            long backupCount;
            try (var stream = Files.list(backupFolder)) {
                backupCount = stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".zip")).count();
            }
            if (backupCount <= 1L) throw new IllegalArgumentException("The last available backup is protected. Create another backup before deleting this one.");
            if (!Files.deleteIfExists(file)) throw new IllegalArgumentException("Backup not found.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Backup could not be deleted: " + exception.getMessage());
        }
        audit(actor, "backup.delete", fileName, "");
        return "Backup deleted.";
    }

    public String requestRestore(ServerPlayer actor, String fileName) {
        MinecraftServer currentServer = server;
        if (currentServer == null || io == null) throw new IllegalArgumentException("Server is not available.");
        if (backupRunning.get()) throw new IllegalArgumentException("Wait for the active backup to finish first.");
        Path source = safeChild(backupFolder, fileName);
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Backup not found.");
        Path worldRoot = currentServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path external = worldRoot.resolveSibling(worldRoot.getFileName() + ".ssu-restore.zip");
        backupStatus = "Preparing restore " + fileName;
        backupRunning.set(true);
        audit(actor, "backup.restore", fileName, "restore requested; server will stop after staging");
        io.execute(() -> {
            try {
                Files.copy(source, external, StandardCopyOption.REPLACE_EXISTING);
                PENDING_RESTORE = new PendingRestore(worldRoot, external, fileName);
                backupStatus = "Restore staged; stopping server";
                currentServer.execute(() -> stopServer(currentServer));
            } catch (Exception exception) {
                backupStatus = "Restore staging failed: " + bounded(exception.getMessage(), 160);
                backupRunning.set(false);
                SimpleServerUtilities.LOGGER.error("Failed to stage SSU restore.", exception);
            }
        });
        return "Restore is being staged. The server will stop automatically when ready.";
    }

    public static void applyPendingRestoreAfterStop() {
        PendingRestore pending = PENDING_RESTORE;
        if (pending == null) return;
        PENDING_RESTORE = null;
        Path worldRoot = pending.worldRoot;
        Path restoreZip = pending.externalZip;
        Path safety = worldRoot.resolveSibling(worldRoot.getFileName() + "-pre-restore-" + FILE_TIME.format(Instant.now()));
        try {
            if (!Files.isRegularFile(restoreZip)) throw new IOException("Staged restore archive is missing.");
            Files.move(worldRoot, safety);
            Files.createDirectories(worldRoot);
            extractZip(restoreZip, worldRoot);
            Path oldBackups = safety.resolve(StoragePaths.ROOT_FOLDER).resolve("server_operations").resolve("backups");
            Path newBackups = worldRoot.resolve(StoragePaths.ROOT_FOLDER).resolve("server_operations").resolve("backups");
            if (Files.isDirectory(oldBackups)) copyDirectory(oldBackups, newBackups);
            Files.deleteIfExists(restoreZip);
            SimpleServerUtilities.LOGGER.warn("SSU restored backup '{}'. Pre-restore world retained at {}.", pending.backupName, safety);
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("SSU restore failed; attempting safety rollback.", exception);
            try {
                if (Files.exists(worldRoot)) deleteTree(worldRoot);
                if (Files.exists(safety)) Files.move(safety, worldRoot);
            } catch (Exception rollbackFailure) {
                SimpleServerUtilities.LOGGER.error("CRITICAL: failed to restore pre-restore safety world.", rollbackFailure);
            }
        } finally {
            try { Files.deleteIfExists(restoreZip); } catch (IOException ignored) { }
        }
    }

    private static void configureSchedule(ServerOperationsState.ScheduledTask task, String rawSpec, long now) {
        String spec = rawSpec == null ? "" : rawSpec.trim();
        if (spec.isBlank()) spec = "60";
        String lower = spec.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("daily@")) {
                String time = spec.substring(6).trim();
                LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
                task.scheduleMode = "DAILY"; task.scheduleSpec = time; task.intervalMinutes = 0;
            } else if (lower.startsWith("once@")) {
                String when = spec.substring(5).trim();
                LocalDateTime.parse(when, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                task.scheduleMode = "ONCE"; task.scheduleSpec = when; task.intervalMinutes = 0;
            } else {
                String minutesRaw = lower.startsWith("every@") ? spec.substring(6).trim() : spec;
                if (minutesRaw.toLowerCase(Locale.ROOT).endsWith("m")) minutesRaw = minutesRaw.substring(0, minutesRaw.length()-1).trim();
                int minutes = Integer.parseInt(minutesRaw);
                if (minutes < 1 || minutes > 525_600) throw new IllegalArgumentException();
                task.scheduleMode = "INTERVAL"; task.scheduleSpec = Integer.toString(minutes); task.intervalMinutes = minutes;
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Schedule must be minutes, daily@HH:mm, or once@yyyy-MM-ddTHH:mm.");
        }
        task.nextRunAt = nextScheduledRun(task, now);
    }

    private static long nextScheduledRun(ServerOperationsState.ScheduledTask task, long afterMillis) {
        ZoneId zone = ZoneId.systemDefault();
        if ("DAILY".equals(task.scheduleMode)) {
            LocalTime time = LocalTime.parse(task.scheduleSpec, DateTimeFormatter.ofPattern("HH:mm"));
            ZonedDateTime after = Instant.ofEpochMilli(afterMillis).atZone(zone);
            ZonedDateTime candidate = after.toLocalDate().atTime(time).atZone(zone);
            if (!candidate.toInstant().isAfter(Instant.ofEpochMilli(afterMillis))) candidate = candidate.plusDays(1);
            return candidate.toInstant().toEpochMilli();
        }
        if ("ONCE".equals(task.scheduleMode)) {
            LocalDateTime dateTime = LocalDateTime.parse(task.scheduleSpec, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            return dateTime.atZone(zone).toInstant().toEpochMilli();
        }
        return afterMillis + Math.max(1L, task.intervalMinutes) * 60_000L;
    }

    public synchronized String addTask(ServerPlayer actor, String name, String action, String scheduleSpec, String payload) {
        String id = sanitize(name).toLowerCase(Locale.ROOT);
        if (id.isBlank()) id = "task_" + System.currentTimeMillis();
        for (ServerOperationsState.ScheduledTask existing : state.tasks) {
            if (existing.id.equals(id)) throw new IllegalArgumentException("A task with that ID already exists.");
        }
        ServerOperationsState.ScheduledTask task = new ServerOperationsState.ScheduledTask();
        task.id = id;
        task.name = name;
        task.action = action;
        task.payload = payload;
        configureSchedule(task, scheduleSpec, System.currentTimeMillis());
        task.enabled = true;
        task.normalize();
        validateTaskAction(task.action);
        state.tasks.add(task);
        saveState();
        audit(actor, "scheduler.create", task.id, task.action + " • " + task.scheduleMode + " " + task.scheduleSpec);
        return "Scheduled task created.";
    }

    public synchronized String setTaskEnabled(ServerPlayer actor, String id, boolean enabled) {
        ServerOperationsState.ScheduledTask task = task(id);
        if (task == null) throw new IllegalArgumentException("Scheduled task not found.");
        long now = System.currentTimeMillis();
        if (enabled && "ONCE".equals(task.scheduleMode) && task.nextRunAt <= now) {
            throw new IllegalArgumentException("This one-time schedule is already in the past. Delete it and create a new one.");
        }
        task.enabled = enabled;
        if (enabled && task.nextRunAt <= now) task.nextRunAt = nextScheduledRun(task, now);
        saveState();
        audit(actor, "scheduler.toggle", task.id, "enabled=" + enabled);
        return "Scheduled task " + (enabled ? "enabled." : "disabled.");
    }

    public synchronized String deleteTask(ServerPlayer actor, String id) {
        ServerOperationsState.ScheduledTask task = task(id);
        if (task == null) throw new IllegalArgumentException("Scheduled task not found.");
        if (task.system) throw new IllegalArgumentException("This system task is controlled by its feature settings.");
        state.tasks.remove(task);
        saveState();
        audit(actor, "scheduler.delete", task.id, task.action);
        return "Scheduled task deleted.";
    }

    public String runTaskNow(ServerPlayer actor, String id) {
        ServerOperationsState.ScheduledTask task;
        synchronized (this) {
            task = task(id);
            if (task == null) throw new IllegalArgumentException("Scheduled task not found.");
        }
        audit(actor, "scheduler.run_now", task.id, task.action);
        executeTask(task, server);
        return "Scheduled task executed.";
    }

    private void runScheduler(MinecraftServer server) {
        long now = System.currentTimeMillis();
        ArrayList<ServerOperationsState.ScheduledTask> due = new ArrayList<>();
        synchronized (this) {
            for (ServerOperationsState.ScheduledTask task : state.tasks) {
                if (task.enabled && task.nextRunAt > 0L && task.nextRunAt <= now) due.add(task);
            }
        }
        for (ServerOperationsState.ScheduledTask task : due) executeTask(task, server);
    }

    private void executeTask(ServerOperationsState.ScheduledTask task, MinecraftServer server) {
        String result = "OK";
        try {
            switch (task.action) {
                case "BACKUP" -> createBackup(null, task.id);
                case "BROADCAST" -> broadcast(server, task.payload);
                case "MAINTENANCE_ON" -> setMaintenanceInternal(true, task.payload);
                case "MAINTENANCE_OFF" -> setMaintenanceInternal(false, task.payload);
                case "SAVE_SSU" -> SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));
                case "SSU_RELOAD" -> {
                    SsuReloadService.ReloadResult reload = SsuReloadService.reloadAll(server);
                    if (!reload.successful()) throw new IllegalStateException(reload.message());
                }
                case "STOP_SERVER" -> stopServer(server);
                default -> throw new IllegalArgumentException("Unsupported scheduler action: " + task.action);
            }
        } catch (Exception exception) {
            result = bounded(exception.getMessage(), 220);
            SimpleServerUtilities.LOGGER.warn("Scheduled SSU task {} failed: {}", task.id, result);
        }
        synchronized (this) {
            task.lastRunAt = System.currentTimeMillis();
            task.lastResult = result;
            if ("ONCE".equals(task.scheduleMode) && !task.system) task.enabled = false;
            else task.nextRunAt = nextScheduledRun(task, task.lastRunAt);
            saveState();
        }
        audit("Scheduler", "", "scheduler.execute", task.id, task.action + ": " + result);
    }

    public synchronized String setMaintenance(ServerPlayer actor, boolean enabled, String message, boolean kickPlayers) {
        setMaintenanceInternal(enabled, message);
        if (enabled && kickPlayers && server != null) {
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                if (!PermissionService.isAdmin(player)
                        && !PermissionService.getBoolean(player, PermissionKeys.MAINTENANCE_BYPASS, false)) {
                    player.connection.disconnect(Component.literal(state.maintenanceMessage));
                }
            }
        }
        audit(actor, "maintenance.toggle", Boolean.toString(enabled), "kickPlayers=" + kickPlayers);
        return "Maintenance mode " + (enabled ? "enabled." : "disabled.");
    }

    private synchronized void setMaintenanceInternal(boolean enabled, String message) {
        state.maintenanceEnabled = enabled;
        if (message != null && !message.isBlank()) state.maintenanceMessage = bounded(message, 512);
        saveState();
    }

    public synchronized String setChatSettings(ServerPlayer actor, String json) {
        JsonObject input;
        try { input = GSON.fromJson(json == null ? "{}" : json, JsonObject.class); }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid chat settings payload."); }
        if (input == null) input = new JsonObject();
        state.chatModerationEnabled = input.has("enabled") ? input.get("enabled").getAsBoolean() : state.chatModerationEnabled;
        state.chatSlowModeSeconds = input.has("slow") ? input.get("slow").getAsInt() : state.chatSlowModeSeconds;
        state.chatDuplicateWindowSeconds = input.has("duplicate") ? input.get("duplicate").getAsInt() : state.chatDuplicateWindowSeconds;
        state.chatBurstWindowSeconds = input.has("burstWindow") ? input.get("burstWindow").getAsInt() : state.chatBurstWindowSeconds;
        state.chatBurstMaxMessages = input.has("burstMax") ? input.get("burstMax").getAsInt() : state.chatBurstMaxMessages;
        state.chatCapsPercent = input.has("capsPercent") ? input.get("capsPercent").getAsInt() : state.chatCapsPercent;
        state.chatCapsMinimumLength = input.has("capsMin") ? input.get("capsMin").getAsInt() : state.chatCapsMinimumLength;
        state.chatLinksAllowed = input.has("linksAllowed") ? input.get("linksAllowed").getAsBoolean() : state.chatLinksAllowed;
        state.staffChatEnabled = input.has("staffChat") ? input.get("staffChat").getAsBoolean() : state.staffChatEnabled;
        String blockedWords = input.has("blockedWords") ? input.get("blockedWords").getAsString() : String.join(",", state.blockedWords);
        ArrayList<String> words = new ArrayList<>();
        for (String raw : blockedWords.split(",")) {
            String word = raw.trim().toLowerCase(Locale.ROOT);
            if (!word.isBlank() && !words.contains(word) && words.size() < 128) words.add(bounded(word, 48));
        }
        state.blockedWords = words; state.normalize(); saveState();
        audit(actor, "chat.settings", "chat", "enabled=" + state.chatModerationEnabled + ", slow=" + state.chatSlowModeSeconds
                + "s, duplicate=" + state.chatDuplicateWindowSeconds + "s, burst=" + state.chatBurstMaxMessages + "/" + state.chatBurstWindowSeconds
                + "s, caps=" + state.chatCapsPercent + "%, links=" + state.chatLinksAllowed + ", blocked=" + words.size());
        return "Chat moderation settings saved.";
    }

    public synchronized String mute(ServerPlayer actor, String target, int minutes, String reason) {
        ResolvedPlayer resolved = resolvePlayer(target);
        if (resolved == null) throw new IllegalArgumentException("Player name or UUID was not found.");
        ServerOperationsState.MuteRecord mute = new ServerOperationsState.MuteRecord();
        mute.playerId = resolved.id.toString();
        mute.playerName = resolved.name;
        mute.expiresAt = minutes <= 0 ? 0L : System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        mute.reason = bounded(reason, 256);
        mute.actor = actor.getName().getString();
        mute.createdAt = System.currentTimeMillis();
        mute.normalize();
        state.mutes.put(mute.playerId, mute);
        saveState();
        audit(actor, "chat.mute", resolved.name, minutes <= 0 ? "permanent" : minutes + " minutes");
        return resolved.name + " muted.";
    }

    public synchronized String unmute(ServerPlayer actor, String target) {
        ResolvedPlayer resolved = resolvePlayer(target);
        if (resolved == null) throw new IllegalArgumentException("Player name or UUID was not found.");
        if (state.mutes.remove(resolved.id.toString()) == null) throw new IllegalArgumentException("That player is not muted.");
        saveState();
        audit(actor, "chat.unmute", resolved.name, "");
        return resolved.name + " unmuted.";
    }

    public synchronized String createTicket(ServerPlayer player, String rawCategory, String message, String rawReportTarget) {
        if (!canUseReports(player)) throw new IllegalArgumentException("Support tickets are not available to you.");
        SupportTicketCategory category = SupportTicketCategory.parse(rawCategory);
        String body = SupportRichText.normalize(message);
        if (SupportRichText.plainText(body).trim().length() < 3) {
            throw new IllegalArgumentException("Enter a short description of the issue.");
        }
        long open = state.tickets.stream().filter(ticket -> ticket.playerId.equals(player.getUUID().toString()))
                .filter(ticket -> ticket.status.equals("OPEN") || ticket.status.equals("ASSIGNED")).count();
        if (open >= 3) throw new IllegalArgumentException("You already have three open support tickets.");

        ResolvedPlayer reportTarget = null;
        if (category.requiresTarget()) {
            reportTarget = resolvePlayer(rawReportTarget);
            if (reportTarget == null) throw new IllegalArgumentException("Choose a valid player to report.");
            if (reportTarget.id.equals(player.getUUID())) throw new IllegalArgumentException("You cannot report yourself.");
        }

        long now = System.currentTimeMillis();
        ServerOperationsState.SupportTicket ticket = new ServerOperationsState.SupportTicket();
        ticket.id = nextTicketId++;
        ticket.playerId = player.getUUID().toString();
        ticket.playerName = player.getName().getString();
        ticket.category = category.name();
        ticket.status = "OPEN";
        ticket.createdAt = now;
        ticket.updatedAt = now;
        ticket.unreadForPlayer = false;
        ticket.unreadForStaff = true;
        if (reportTarget != null) {
            ticket.reportTargetId = reportTarget.id.toString();
            ticket.reportTargetName = reportTarget.name;
        }
        ticket.messages.add(ticketMessage(player, "PLAYER", body, now));
        ticket.normalize();
        state.tickets.add(ticket);
        trimClosedTickets();
        saveState();
        notifyStaff("New " + category.label().toLowerCase(Locale.ROOT) + " ticket #" + ticket.id + " from " + ticket.playerName
                + (ticket.reportTargetName.isBlank() ? "." : " about " + ticket.reportTargetName + "."));
        return "Support ticket #" + ticket.id + " created.";
    }

    public synchronized String replyTicket(ServerPlayer actor, long id, String rawBody, boolean staff) {
        ServerOperationsState.SupportTicket ticket = ticket(id);
        if (!staff && !ticket.playerId.equals(actor.getUUID().toString())) throw new IllegalArgumentException("Ticket not found.");
        if (ticket.status.equals("CLOSED")) throw new IllegalArgumentException("Closed tickets cannot receive new replies.");
        if (ticket.messages.size() >= SupportRichText.MAX_MESSAGES_PER_TICKET) {
            throw new IllegalArgumentException("This ticket reached its message limit. Close it and create a follow-up ticket.");
        }
        String body = SupportRichText.normalize(rawBody);
        if (SupportRichText.plainText(body).trim().isEmpty()) throw new IllegalArgumentException("Reply cannot be empty.");
        long now = System.currentTimeMillis();
        ticket.messages.add(ticketMessage(actor, staff ? "STAFF" : "PLAYER", body, now));
        ticket.updatedAt = now;
        if (staff) {
            if (ticket.assignedTo.isBlank()) {
                ticket.assignedTo = actor.getName().getString();
                if (ticket.status.equals("OPEN")) ticket.status = "ASSIGNED";
            }
            ticket.unreadForStaff = false;
            ticket.unreadForPlayer = true;
            notifyTicketOwner(ticket, "Staff replied to support ticket #" + ticket.id + ".");
            audit(actor, "support.reply", "ticket #" + id, ticket.playerName);
        } else {
            if (ticket.status.equals("RESOLVED")) ticket.status = ticket.assignedTo.isBlank() ? "OPEN" : "ASSIGNED";
            ticket.unreadForPlayer = false;
            ticket.unreadForStaff = true;
            notifyStaff(ticket.playerName + " replied to support ticket #" + ticket.id + ".");
        }
        ticket.normalize();
        saveState();
        return "Reply added to ticket #" + id + ".";
    }

    public synchronized String updateTicket(ServerPlayer actor, long id, String action) {
        return updateTicket(actor, id, action, "");
    }

    public synchronized String updateTicket(ServerPlayer actor, long id, String action, String reason) {
        ServerOperationsState.SupportTicket ticket = ticket(id);
        String oldStatus = ticket.status;
        long now = System.currentTimeMillis();
        switch (action) {
            case "assign" -> {
                ticket.status = "ASSIGNED";
                ticket.assignedTo = actor.getName().getString();
            }
            case "resolve" -> ticket.status = "RESOLVED";
            case "close" -> closeTicket(ticket, actor, reason, true, now);
            case "reopen" -> {
                ticket.status = ticket.assignedTo.isBlank() ? "OPEN" : "ASSIGNED";
                ticket.closedAt = 0L;
                ticket.closeReason = "";
            }
            default -> throw new IllegalArgumentException("Unknown ticket action.");
        }
        ticket.unreadForStaff = false;
        ticket.updatedAt = now;
        if (!oldStatus.equals(ticket.status) || action.equals("assign")) {
            ticket.unreadForPlayer = true;
            notifyTicketOwner(ticket, "Support ticket #" + id + " is now " + ticket.status.toLowerCase(Locale.ROOT) + ".");
        }
        ticket.normalize();
        saveState();
        audit(actor, "support." + action, "ticket #" + id, ticket.playerName + (reason == null || reason.isBlank() ? "" : " • " + bounded(reason, 128)));
        return "Ticket #" + id + " updated.";
    }

    public synchronized String closeOwnTicket(ServerPlayer player, long id, String reason) {
        ServerOperationsState.SupportTicket ticket = ticket(id);
        if (!ticket.playerId.equals(player.getUUID().toString())) throw new IllegalArgumentException("Ticket not found.");
        if (ticket.status.equals("CLOSED")) return "Ticket #" + id + " is already closed.";
        long now = System.currentTimeMillis();
        closeTicket(ticket, player, reason, false, now);
        ticket.unreadForPlayer = false;
        ticket.unreadForStaff = true;
        ticket.updatedAt = now;
        ticket.normalize();
        saveState();
        notifyStaff(ticket.playerName + " closed support ticket #" + ticket.id + ".");
        return "Ticket #" + id + " closed.";
    }

    private void closeTicket(ServerOperationsState.SupportTicket ticket, ServerPlayer actor, String rawReason, boolean staff, long now) {
        String reason = bounded(rawReason, 512).trim();
        if (reason.length() < 3) throw new IllegalArgumentException("Enter a reason for closing this ticket.");
        ticket.status = "CLOSED";
        ticket.closedAt = now;
        ticket.closeReason = reason;
        ServerOperationsState.TicketMessage message = ticketMessage(actor, "SYSTEM",
                SupportRichText.normalize("Closed by " + (staff ? "staff" : "player") + ": " + reason), now);
        ticket.messages.add(message);
    }

    public synchronized String setClosedTicketRetention(ServerPlayer actor, int hours) {
        state.closedTicketRetentionHours = Math.max(1, Math.min(720, hours));
        purgeExpiredClosedTickets();
        saveState();
        audit(actor, "support.retention", "closed tickets", state.closedTicketRetentionHours + " hours");
        return "Closed ticket retention set to " + state.closedTicketRetentionHours + " hours.";
    }

    public synchronized String markTicketRead(ServerPlayer viewer, long id, boolean staff) {
        ServerOperationsState.SupportTicket ticket = ticket(id);
        if (!staff && !ticket.playerId.equals(viewer.getUUID().toString())) throw new IllegalArgumentException("Ticket not found.");
        if (staff) ticket.unreadForStaff = false;
        else ticket.unreadForPlayer = false;
        saveState();
        return "";
    }

    private ServerOperationsState.SupportTicket ticket(long id) {
        ServerOperationsState.SupportTicket ticket = state.tickets.stream().filter(value -> value.id == id).findFirst().orElse(null);
        if (ticket == null) throw new IllegalArgumentException("Ticket not found.");
        return ticket;
    }

    private static ServerOperationsState.TicketMessage ticketMessage(ServerPlayer actor, String role, String body, long time) {
        ServerOperationsState.TicketMessage message = new ServerOperationsState.TicketMessage();
        message.authorId = actor.getUUID().toString();
        message.authorName = actor.getName().getString();
        message.role = role;
        message.body = body;
        message.createdAt = time;
        message.normalize();
        return message;
    }

    public synchronized String setWorldBorder(ServerPlayer actor, String dimensionId, double centerX, double centerZ, double size) {
        ServerLevel level = level(dimensionId);
        double boundedSize = Math.max(16.0D, Math.min(59_999_968.0D, size));
        level.getWorldBorder().setCenter(centerX, centerZ);
        level.getWorldBorder().setSize(boundedSize);
        audit(actor, "world.border", dimensionId, "center=" + centerX + "," + centerZ + " size=" + boundedSize);
        return "World border updated.";
    }

    public synchronized String startPregeneration(ServerPlayer actor, String dimensionId, int radiusChunks) {
        if (pregen != null && !pregen.done()) throw new IllegalArgumentException("A chunk pregeneration job is already active.");
        ServerLevel level = level(dimensionId);
        int radius = Math.max(1, Math.min(512, radiusChunks));
        int centerChunkX = ((int) Math.floor(level.getWorldBorder().getCenterX())) >> 4;
        int centerChunkZ = ((int) Math.floor(level.getWorldBorder().getCenterZ())) >> 4;
        pregen = new PregenJob(level.dimension().location().toString(), centerChunkX, centerChunkZ, radius,
                state.pregenChunksPerTick, state.pregenPauseAboveMspt);
        audit(actor, "world.pregenerate", dimensionId, "radiusChunks=" + radius + ", total=" + pregen.total);
        return "Chunk pregeneration started for " + pregen.total + " chunk(s).";
    }

    public synchronized String stopPregeneration(ServerPlayer actor) {
        if (pregen == null || pregen.done()) throw new IllegalArgumentException("No chunk pregeneration job is active.");
        pregen.cancelled = true;
        audit(actor, "world.pregenerate.stop", pregen.dimension, "generated=" + pregen.generated);
        return "Chunk pregeneration stopped.";
    }

    public synchronized String setPregenSettings(ServerPlayer actor, int chunksPerTick, double pauseMspt) {
        state.pregenChunksPerTick = Math.max(1, Math.min(4, chunksPerTick));
        state.pregenPauseAboveMspt = Math.max(20.0D, Math.min(200.0D, pauseMspt));
        saveState();
        audit(actor, "world.pregenerate.settings", "pregen", "chunks/tick=" + state.pregenChunksPerTick + ", pauseMspt=" + state.pregenPauseAboveMspt);
        return "Pregeneration throttle saved.";
    }

    private void tickPregeneration() {
        PregenJob job = pregen;
        MinecraftServer currentServer = server;
        if (job == null || currentServer == null || job.done()) return;
        double mspt = average(msptWindow);
        job.pausedForLoad = mspt > job.pauseAboveMspt;
        if (job.pausedForLoad) return;
        ServerLevel level;
        try { level = level(job.dimension); }
        catch (Exception exception) { job.failure = bounded(exception.getMessage(), 160); return; }
        int budget = job.chunksPerTick;
        while (budget-- > 0 && job.index < job.total && !job.cancelled) {
            int side = job.radius * 2 + 1;
            int localX = job.index % side - job.radius;
            int localZ = job.index / side - job.radius;
            job.index++;
            level.getChunk(job.centerChunkX + localX, job.centerChunkZ + localZ);
            job.generated++;
        }
        if (job.index >= job.total) job.completed = true;
    }

    public synchronized String setEconomyAlertThreshold(ServerPlayer actor, long minor) {
        state.economyAlertThresholdMinor = Math.max(0L, minor);
        saveState();
        audit(actor, "economy.analytics.settings", "large transaction threshold", Long.toString(state.economyAlertThresholdMinor));
        return "Economy alert threshold saved.";
    }

    public String exportProfile(ServerPlayer actor, String rawName) {
        if (io == null || ssuRoot == null) throw new IllegalArgumentException("Server storage is unavailable.");
        String name = sanitize(rawName);
        if (name.isBlank()) throw new IllegalArgumentException("Enter a profile name.");
        Path destination = profileFolder.resolve(name + ".zip");
        if (Files.exists(destination)) throw new IllegalArgumentException("A profile with that name already exists.");
        List<String> includes = configurationProfileIncludes();
        try {
            Files.createDirectories(profileFolder);
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)))) {
                for (String relative : includes) addPathToZip(ssuRoot, relative, zip);
                addTextToZip(zip, "server_operations/profile_settings.json", GSON.toJson(serverOperationsProfileJson()));
            }
        } catch (IOException exception) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
            throw new IllegalArgumentException("Profile export failed: " + exception.getMessage());
        }
        audit(actor, "config.export", name, "configuration-only profile");
        return "Configuration profile '" + name + "' exported.";
    }

    public synchronized String importProfile(ServerPlayer actor, String rawName) {
        String name = sanitize(rawName);
        Path source = safeChild(profileFolder, name + ".zip");
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Configuration profile not found.");

        // Always preserve the pre-import configuration as a persistent profile first.
        // This remains available even if a later file copy or SSU reload fails.
        String safetyName = sanitize("pre-import-" + FILE_TIME.format(Instant.now()) + "-" + Long.toUnsignedString(System.nanoTime(), 36));
        exportProfile(actor, safetyName);

        Path temp = operationsRoot.resolve("profile-import-" + System.nanoTime());
        try {
            Files.createDirectories(temp);
            extractZip(source, temp);
            for (String relative : configurationProfileIncludes()) {
                Path incoming = temp.resolve(relative).normalize();
                if (!incoming.startsWith(temp) || !Files.exists(incoming)) continue;
                Path destination = ssuRoot.resolve(relative).normalize();
                if (!destination.startsWith(ssuRoot)) continue;
                if (Files.isDirectory(incoming)) {
                    if (Files.exists(destination)) deleteTree(destination);
                    copyDirectory(incoming, destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(incoming, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Path serverOpsProfile = temp.resolve("server_operations/profile_settings.json").normalize();
            if (serverOpsProfile.startsWith(temp) && Files.isRegularFile(serverOpsProfile)) {
                JsonObject ops = GSON.fromJson(Files.readString(serverOpsProfile, StandardCharsets.UTF_8), JsonObject.class);
                applyServerOperationsProfile(ops);
            }
            SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));
            SsuReloadService.ReloadResult result = SsuReloadService.reloadAll(server);
            if (!result.successful()) throw new IOException(result.message());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Profile import failed. Pre-import safety profile '" + safetyName
                    + "' was preserved; restore it if needed. " + exception.getMessage());
        } finally {
            try { if (Files.exists(temp)) deleteTree(temp); } catch (IOException ignored) { }
        }
        audit(actor, "config.import", name, "configuration profile imported and SSU reloaded; safety=" + safetyName);
        return "Configuration profile '" + name + "' imported. Safety profile: '" + safetyName + "'.";
    }

    public synchronized String deleteProfile(ServerPlayer actor, String rawName) {
        String name = sanitize(rawName);
        Path source = safeChild(profileFolder, name + ".zip");
        try {
            if (!Files.deleteIfExists(source)) throw new IllegalArgumentException("Configuration profile not found.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Profile could not be deleted: " + exception.getMessage());
        }
        audit(actor, "config.delete", name, "");
        return "Configuration profile deleted.";
    }

    /** Builds one bounded snapshot for either the player Support page or the admin Server Operations page. */
    public JsonObject snapshot(ServerPlayer viewer, boolean adminMode) {
        return snapshot(viewer, adminMode, -1L, 0);
    }

    /** Builds the normal snapshot plus one permission-checked, paged ticket thread when requested. */
    public JsonObject snapshot(ServerPlayer viewer, boolean adminMode, long ticketId, int messagePage) {
        JsonObject root = new JsonObject();
        root.addProperty("admin", adminMode);
        root.addProperty("serverTime", System.currentTimeMillis());
        root.add("tickets", ticketsJson(viewer, adminMode));
        if (ticketId > 0L) {
            JsonObject detail = ticketDetailJson(viewer, adminMode, ticketId, messagePage);
            if (detail != null) root.add("ticketDetail", detail);
        }
        if (!adminMode) {
            root.add("reportTargets", reportTargetsJson(viewer));
            return root;
        }

        JsonObject settings = new JsonObject();
        settings.addProperty("activityEnabled", state.activityLoggingEnabled);
        settings.addProperty("activityBreaks", state.activityBreaks);
        settings.addProperty("activityPlaces", state.activityPlaces);
        settings.addProperty("activityRetentionDays", state.activityRetentionDays);
        settings.addProperty("autoBackups", state.automaticBackups);
        settings.addProperty("backupIntervalMinutes", state.automaticBackupIntervalMinutes);
        settings.addProperty("backupRetention", state.backupRetentionCount);
        settings.addProperty("maintenanceEnabled", state.maintenanceEnabled);
        settings.addProperty("maintenanceMessage", state.maintenanceMessage);
        settings.addProperty("chatEnabled", state.chatModerationEnabled);
        settings.addProperty("slowModeSeconds", state.chatSlowModeSeconds);
        settings.addProperty("duplicateWindowSeconds", state.chatDuplicateWindowSeconds);
        settings.addProperty("burstWindowSeconds", state.chatBurstWindowSeconds);
        settings.addProperty("burstMaxMessages", state.chatBurstMaxMessages);
        settings.addProperty("capsPercent", state.chatCapsPercent);
        settings.addProperty("capsMinLength", state.chatCapsMinimumLength);
        settings.addProperty("linksAllowed", state.chatLinksAllowed);
        settings.addProperty("staffChatEnabled", state.staffChatEnabled);
        settings.addProperty("blockedWords", String.join(", ", state.blockedWords));
        settings.addProperty("pregenChunksPerTick", state.pregenChunksPerTick);
        settings.addProperty("pregenPauseMspt", state.pregenPauseAboveMspt);
        settings.addProperty("economyAlertThresholdMinor", state.economyAlertThresholdMinor);
        settings.addProperty("closedTicketRetentionHours", state.closedTicketRetentionHours);
        root.add("settings", settings);

        JsonObject backup = new JsonObject();
        backup.addProperty("running", backupRunning.get());
        backup.addProperty("status", backupStatus);
        backup.addProperty("filesDone", backupFilesDone.get());
        backup.addProperty("filesTotal", backupFilesTotal.get());
        backup.add("files", backupsJson());
        root.add("backup", backup);

        root.add("tasks", tasksJson());
        root.add("activity", activityJson());
        root.add("audit", auditJson());
        root.add("worlds", worldsJson());
        root.add("pregen", pregenJson());
        root.add("rollback", rollbackJson());
        root.add("health", healthJson());
        root.add("economy", economyJson());
        root.add("profiles", profilesJson());
        root.add("mutes", mutesJson());
        root.add("chatHistory", chatHistoryJson());
        return root;
    }

    private JsonArray backupsJson() {
        JsonArray array = new JsonArray();
        if (backupFolder == null || !Files.isDirectory(backupFolder)) return array;
        try (var stream = Files.list(backupFolder)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(this::lastModifiedSafe).reversed())
                    .limit(30)
                    .forEach(path -> {
                        JsonObject value = new JsonObject();
                        value.addProperty("name", path.getFileName().toString());
                        value.addProperty("size", fileSizeSafe(path));
                        value.addProperty("modified", lastModifiedSafe(path));
                        array.add(value);
                    });
        } catch (IOException ignored) { }
        return array;
    }

    private synchronized JsonArray tasksJson() {
        JsonArray array = new JsonArray();
        state.tasks.stream().sorted(Comparator.comparing(task -> task.name, String.CASE_INSENSITIVE_ORDER)).forEach(task -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", task.id); value.addProperty("name", task.name); value.addProperty("action", task.action);
            value.addProperty("payload", task.payload); value.addProperty("interval", task.intervalMinutes);
            value.addProperty("scheduleMode", task.scheduleMode); value.addProperty("scheduleSpec", task.scheduleSpec);
            value.addProperty("next", task.nextRunAt); value.addProperty("enabled", task.enabled); value.addProperty("system", task.system);
            value.addProperty("last", task.lastRunAt); value.addProperty("result", task.lastResult);
            array.add(value);
        });
        return array;
    }

    private synchronized JsonArray activityJson() {
        JsonArray array = new JsonArray();
        var iterator = activity.descendingIterator();
        int count = 0;
        while (iterator.hasNext() && count++ < MAX_VISIBLE_LOGS) {
            ActivityEntry entry = iterator.next();
            JsonObject value = new JsonObject();
            value.addProperty("time", entry.time); value.addProperty("player", entry.playerName); value.addProperty("action", entry.action);
            value.addProperty("dimension", entry.dimension); value.addProperty("x", entry.x); value.addProperty("y", entry.y); value.addProperty("z", entry.z);
            value.addProperty("before", entry.beforeBlock); value.addProperty("after", entry.afterBlock);
            array.add(value);
        }
        return array;
    }

    private synchronized JsonArray auditJson() {
        JsonArray array = new JsonArray();
        var iterator = audit.descendingIterator();
        int count = 0;
        while (iterator.hasNext() && count++ < MAX_VISIBLE_LOGS) {
            AuditEntry entry = iterator.next();
            JsonObject value = new JsonObject();
            value.addProperty("time", entry.time); value.addProperty("actor", entry.actor); value.addProperty("action", entry.action);
            value.addProperty("target", entry.target); value.addProperty("detail", entry.detail);
            array.add(value);
        }
        return array;
    }

    private synchronized JsonArray ticketsJson(ServerPlayer viewer, boolean all) {
        JsonArray array = new JsonArray();
        state.tickets.stream()
                .filter(ticket -> all || ticket.playerId.equals(viewer.getUUID().toString()))
                .sorted(Comparator.comparingLong((ServerOperationsState.SupportTicket ticket) -> ticket.updatedAt).reversed())
                .limit(MAX_VISIBLE_TICKETS)
                .forEach(ticket -> {
                    JsonObject value = new JsonObject();
                    SupportTicketCategory category = SupportTicketCategory.parse(ticket.category);
                    value.addProperty("id", ticket.id);
                    value.addProperty("player", ticket.playerName);
                    value.addProperty("category", category.name());
                    value.addProperty("categoryLabel", category.label());
                    value.addProperty("status", ticket.status);
                    value.addProperty("assigned", ticket.assignedTo);
                    value.addProperty("reportTarget", ticket.reportTargetName);
                    value.addProperty("preview", SupportRichText.compactPreview(ticket.latestBody(), 72));
                    value.addProperty("messageCount", ticket.messages.size());
                    value.addProperty("unread", all ? ticket.unreadForStaff : ticket.unreadForPlayer);
                    value.addProperty("created", ticket.createdAt);
                    value.addProperty("updated", ticket.updatedAt);
                    value.addProperty("closedAt", ticket.closedAt);
                    value.addProperty("closeReason", ticket.closeReason);
                    array.add(value);
                });
        return array;
    }

    private synchronized JsonObject ticketDetailJson(ServerPlayer viewer, boolean admin, long id, int requestedPage) {
        ServerOperationsState.SupportTicket ticket = state.tickets.stream().filter(value -> value.id == id).findFirst().orElse(null);
        if (ticket == null || (!admin && !ticket.playerId.equals(viewer.getUUID().toString()))) return null;
        JsonObject value = new JsonObject();
        SupportTicketCategory category = SupportTicketCategory.parse(ticket.category);
        value.addProperty("id", ticket.id);
        value.addProperty("player", ticket.playerName);
        value.addProperty("playerId", ticket.playerId);
        value.addProperty("category", category.name());
        value.addProperty("categoryLabel", category.label());
        value.addProperty("status", ticket.status);
        value.addProperty("assigned", ticket.assignedTo);
        value.addProperty("reportTarget", ticket.reportTargetName);
        value.addProperty("reportTargetId", ticket.reportTargetId);
        value.addProperty("created", ticket.createdAt);
        value.addProperty("updated", ticket.updatedAt);
        value.addProperty("closedAt", ticket.closedAt);
        value.addProperty("closeReason", ticket.closeReason);
        value.addProperty("messageCount", ticket.messages.size());

        final int pageSize = 5;
        int pages = Math.max(1, (ticket.messages.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        int end = Math.max(0, ticket.messages.size() - page * pageSize);
        int start = Math.max(0, end - pageSize);
        JsonArray messages = new JsonArray();
        for (int index = start; index < end; index++) {
            ServerOperationsState.TicketMessage message = ticket.messages.get(index);
            JsonObject row = new JsonObject();
            row.addProperty("author", message.authorName);
            row.addProperty("authorId", message.authorId);
            row.addProperty("role", message.role);
            row.addProperty("body", message.body);
            row.addProperty("time", message.createdAt);
            messages.add(row);
        }
        value.addProperty("messagePage", page);
        value.addProperty("messagePages", pages);
        value.add("messages", messages);
        return value;
    }

    private synchronized JsonArray reportTargetsJson(ServerPlayer viewer) {
        JsonArray array = new JsonArray();
        if (server == null) return array;
        Map<UUID, String> known = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!online.getUUID().equals(viewer.getUUID())) known.put(online.getUUID(), online.getName().getString());
        }
        if (SsuModuleAccess.active("permissions")) {
            for (var player : SimpleServerUtilities.PERMISSIONS.getKnownPlayers()) {
                if (!player.playerId().equals(viewer.getUUID())) known.putIfAbsent(player.playerId(), player.name());
            }
        }
        known.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .limit(200)
                .forEach(entry -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", entry.getKey().toString());
                    value.addProperty("name", entry.getValue());
                    value.addProperty("online", server.getPlayerList().getPlayer(entry.getKey()) != null);
                    array.add(value);
                });
        return array;
    }

    private JsonArray worldsJson() {
        JsonArray array = new JsonArray();
        MinecraftServer currentServer = server;
        if (currentServer == null) return array;
        for (ServerLevel level : currentServer.getAllLevels()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", level.dimension().location().toString());
            value.addProperty("borderX", level.getWorldBorder().getCenterX());
            value.addProperty("borderZ", level.getWorldBorder().getCenterZ());
            value.addProperty("borderSize", level.getWorldBorder().getSize());
            value.addProperty("loadedChunks", loadedChunkCount(level));
            array.add(value);
        }
        return array;
    }

    private JsonObject pregenJson() {
        JsonObject value = new JsonObject();
        PregenJob job = pregen;
        value.addProperty("active", job != null && !job.done());
        if (job != null) {
            value.addProperty("dimension", job.dimension); value.addProperty("generated", job.generated); value.addProperty("total", job.total);
            value.addProperty("paused", job.pausedForLoad); value.addProperty("cancelled", job.cancelled); value.addProperty("failure", job.failure);
        }
        return value;
    }

    private JsonObject rollbackJson() {
        JsonObject value = new JsonObject();
        RollbackJob job = rollback;
        value.addProperty("active", job != null && !job.done());
        if (job != null) {
            value.addProperty("target", job.target); value.addProperty("processed", job.index); value.addProperty("total", job.entries.size());
            value.addProperty("restored", job.restored); value.addProperty("skipped", job.skipped); value.addProperty("failure", job.failure);
        }
        return value;
    }

    private JsonObject healthJson() {
        Runtime runtime = Runtime.getRuntime();
        JsonObject value = new JsonObject();
        double mspt = average(msptWindow);
        double period = average(tickPeriodWindow);
        value.addProperty("mspt", mspt);
        value.addProperty("p95Mspt", percentile(msptWindow, 0.95D));
        value.addProperty("tps", period <= 0.0D ? 20.0D : Math.min(20.0D, 1000.0D / period));
        value.addProperty("heapUsed", runtime.totalMemory() - runtime.freeMemory());
        value.addProperty("heapMax", runtime.maxMemory());
        value.addProperty("players", server == null ? 0 : server.getPlayerList().getPlayerCount());
        value.addProperty("jobs", SimpleServerUtilities.JOBS.size());
        value.addProperty("uptimeSeconds", server == null ? 0L : server.getTickCount() / 20L);
        SsuPerformanceMonitor.Snapshot performance = SimpleServerUtilities.PERFORMANCE.snapshot();
        value.addProperty("permissionChecks", performance.permissionChecks());
        value.addProperty("permissionCacheHitRate", performance.permissionCacheHitRate());
        value.addProperty("regionLookups", performance.regionLookups());
        JsonArray modules = new JsonArray();
        for (SsuPerformanceMonitor.ModuleTiming timing : SimpleServerUtilities.PERFORMANCE.moduleTimingSnapshot().stream().limit(10).toList()) {
            JsonObject module = new JsonObject();
            module.addProperty("name", timing.module()); module.addProperty("avg", timing.rollingAverageMillis());
            module.addProperty("p95", timing.p95Millis()); module.addProperty("max", timing.maximumMillis()); modules.add(module);
        }
        value.add("modules", modules);
        JsonArray history = new JsonArray();
        synchronized (this) {
            for (HealthSample sample : healthHistory) {
                JsonObject entry = new JsonObject(); entry.addProperty("time", sample.time); entry.addProperty("mspt", sample.mspt); entry.addProperty("tps", sample.tps); history.add(entry);
            }
        }
        value.add("history", history);
        return value;
    }

    private JsonObject economyJson() {
        JsonObject value = new JsonObject();
        if (!SimpleServerUtilities.CORE.modules().isActive("economy")) {
            value.addProperty("enabled", false);
            value.addProperty("accounts", 0);
            value.addProperty("supply", 0L);
            value.addProperty("transactions", 0);
            value.addProperty("prepared", 0);
            value.addProperty("committed", 0);
            value.add("richest", new JsonArray());
            value.addProperty("volume24h", 0L);
            value.add("types", new JsonArray());
            value.add("alerts", new JsonArray());
            return value;
        }
        value.addProperty("enabled", true);
        var stats = SimpleServerUtilities.ECONOMY.statistics();
        value.addProperty("accounts", stats.accounts());
        value.addProperty("supply", stats.totalSupplyMinor());
        value.addProperty("transactions", stats.loadedTransactions());
        value.addProperty("prepared", stats.preparedTransactions());
        value.addProperty("committed", stats.committedTransactions());
        JsonArray richest = new JsonArray();
        SimpleServerUtilities.ECONOMY.playerAccounts().stream()
                .sorted(Comparator.comparingLong(EconomyAccount::getBalanceMinor).reversed())
                .limit(10).forEach(account -> {
                    JsonObject entry = new JsonObject(); entry.addProperty("name", account.getLastKnownName()); entry.addProperty("balance", account.getBalanceMinor()); richest.add(entry);
                });
        value.add("richest", richest);
        long since = System.currentTimeMillis() - 86_400_000L;
        long volume24h = 0L;
        Map<String, Long> byType = new LinkedHashMap<>();
        JsonArray alerts = new JsonArray();
        for (EconomyTransactionRecord record : SimpleServerUtilities.ECONOMY.history(null, 5_000)) {
            if (record.getStatus() != EconomyTransactionStatus.COMMITTED) continue;
            if (record.getCompletedAtEpochMilli() >= since) volume24h = safeAdd(volume24h, record.getAmountMinor());
            byType.merge(record.getType().name(), record.getAmountMinor(), ServerOperationsManager::safeAdd);
            if (state.economyAlertThresholdMinor > 0L && record.getAmountMinor() >= state.economyAlertThresholdMinor && alerts.size() < 20) {
                JsonObject entry = new JsonObject();
                entry.addProperty("time", record.getCompletedAtEpochMilli()); entry.addProperty("amount", record.getAmountMinor());
                entry.addProperty("type", record.getType().name()); entry.addProperty("actor", record.getActorName());
                entry.addProperty("source", record.getSourceName()); entry.addProperty("destination", record.getDestinationName());
                entry.addProperty("reason", record.getReason()); alerts.add(entry);
            }
        }
        value.addProperty("volume24h", volume24h);
        JsonArray types = new JsonArray();
        byType.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(12).forEach(entry -> {
            JsonObject row = new JsonObject(); row.addProperty("type", entry.getKey()); row.addProperty("amount", entry.getValue()); types.add(row);
        });
        value.add("types", types); value.add("alerts", alerts);
        return value;
    }

    private JsonArray profilesJson() {
        JsonArray array = new JsonArray();
        if (profileFolder == null || !Files.isDirectory(profileFolder)) return array;
        try (var stream = Files.list(profileFolder)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .limit(100).forEach(path -> {
                        JsonObject value = new JsonObject(); String file = path.getFileName().toString();
                        value.addProperty("name", file.substring(0, file.length() - 4)); value.addProperty("size", fileSizeSafe(path)); array.add(value);
                    });
        } catch (IOException ignored) { }
        return array;
    }

    private synchronized JsonArray mutesJson() {
        JsonArray array = new JsonArray();
        long now = System.currentTimeMillis();
        state.mutes.values().stream().filter(mute -> mute.active(now)).sorted(Comparator.comparing(mute -> mute.playerName, String.CASE_INSENSITIVE_ORDER)).forEach(mute -> {
            JsonObject value = new JsonObject(); value.addProperty("id", mute.playerId); value.addProperty("name", mute.playerName);
            value.addProperty("expires", mute.expiresAt); value.addProperty("reason", mute.reason); value.addProperty("actor", mute.actor); array.add(value);
        });
        return array;
    }

    private synchronized JsonArray chatHistoryJson() {
        JsonArray array = new JsonArray();
        var iterator = chatHistory.descendingIterator();
        int count = 0;
        while (iterator.hasNext() && count++ < 40) {
            ChatLogEntry entry = iterator.next(); JsonObject value = new JsonObject();
            value.addProperty("time", entry.time); value.addProperty("player", entry.playerName); value.addProperty("message", entry.message); value.addProperty("staff", entry.staff); array.add(value);
        }
        return array;
    }

    private synchronized void sampleHealth(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastHealthSampleAt < 900L) return;
        lastHealthSampleAt = now;
        double mspt = average(msptWindow);
        double period = average(tickPeriodWindow);
        double tps = period <= 0.0D ? 20.0D : Math.min(20.0D, 1000.0D / period);
        healthHistory.addLast(new HealthSample(now, mspt, tps));
        while (healthHistory.size() > MAX_HEALTH_HISTORY) healthHistory.removeFirst();
    }

    private synchronized void ensureAutomaticBackupTask() {
        ServerOperationsState.ScheduledTask task = task(AUTO_BACKUP_TASK_ID);
        if (!state.automaticBackups) {
            if (task != null) state.tasks.remove(task);
            return;
        }
        if (task == null) {
            task = new ServerOperationsState.ScheduledTask();
            task.id = AUTO_BACKUP_TASK_ID;
            task.name = "Automatic backup";
            task.action = "BACKUP";
            task.system = true;
            state.tasks.add(task);
        }
        task.intervalMinutes = state.automaticBackupIntervalMinutes;
        task.scheduleMode = "INTERVAL";
        task.scheduleSpec = Integer.toString(state.automaticBackupIntervalMinutes);
        task.enabled = true;
        if (task.nextRunAt <= System.currentTimeMillis()) task.nextRunAt = System.currentTimeMillis() + task.intervalMinutes * 60_000L;
        task.normalize();
    }

    private synchronized ServerOperationsState.ScheduledTask task(String id) {
        if (id == null) return null;
        return state.tasks.stream().filter(task -> task.id.equalsIgnoreCase(id.trim())).findFirst().orElse(null);
    }

    private static void validateTaskAction(String action) {
        if (!Set.of("BACKUP", "BROADCAST", "MAINTENANCE_ON", "MAINTENANCE_OFF", "SAVE_SSU", "SSU_RELOAD", "STOP_SERVER").contains(action)) {
            throw new IllegalArgumentException("Unsupported scheduler action.");
        }
    }

    private synchronized void saveState() {
        if (stateFile == null) return;
        state.normalize();
        stateStore.queueJson(GSON, stateFile, state);
    }

    private void flushLogQueuesAsync(boolean wait) {
        ExecutorService executor = io;
        if (executor == null) return;
        List<String> activityLines = drain(activityWriteQueue, 20_000);
        List<String> auditLines = drain(auditWriteQueue, 20_000);
        if (activityLines.isEmpty() && auditLines.isEmpty()) return;
        Runnable work = () -> {
            try {
                if (!activityLines.isEmpty()) appendLines(activityFile, activityLines);
                if (!auditLines.isEmpty()) appendLines(auditFile, auditLines);
            } catch (IOException exception) {
                SimpleServerUtilities.LOGGER.error("Failed to flush SSU Server Operations logs.", exception);
            }
        };
        if (wait) {
            try { work.run(); } catch (Exception ignored) { }
        } else executor.execute(work);
    }

    private void pruneLogsAsync() {
        ExecutorService executor = io;
        if (executor == null) return;
        long cutoff = System.currentTimeMillis() - state.activityRetentionDays * 86_400_000L;
        List<ActivityEntry> activitySnapshot;
        List<AuditEntry> auditSnapshot;
        synchronized (this) {
            while (!activity.isEmpty() && (activity.peekFirst().time < cutoff || activity.size() > state.activityMaxEntries)) activity.removeFirst();
            while (audit.size() > 20_000) audit.removeFirst();
            activitySnapshot = List.copyOf(activity);
            auditSnapshot = List.copyOf(audit);
        }
        executor.execute(() -> {
            try { rewriteJsonLines(activityFile, activitySnapshot); rewriteJsonLines(auditFile, auditSnapshot); }
            catch (IOException exception) { SimpleServerUtilities.LOGGER.warn("Failed to prune SSU operation logs.", exception); }
        });
    }

    private synchronized void purgeExpiredClosedTickets() {
        long cutoff = System.currentTimeMillis() - Math.max(1, state.closedTicketRetentionHours) * 3_600_000L;
        boolean changed = state.tickets.removeIf(ticket -> ticket != null && "CLOSED".equals(ticket.status)
                && (ticket.closedAt > 0L ? ticket.closedAt : ticket.updatedAt) <= cutoff);
        if (changed) saveState();
    }

    private synchronized void trimClosedTickets() {
        if (state.tickets.size() <= 2_000) return;
        state.tickets.sort(Comparator.comparingLong(ticket -> ticket.updatedAt));
        while (state.tickets.size() > 2_000) {
            int index = -1;
            for (int i = 0; i < state.tickets.size(); i++) if (state.tickets.get(i).status.equals("CLOSED")) { index = i; break; }
            if (index < 0) break;
            state.tickets.remove(index);
        }
    }

    private synchronized ResolvedPlayer resolvePlayer(String raw) {
        if (raw == null || raw.isBlank() || server == null) return null;
        String query = raw.trim();
        try {
            UUID id = UUID.fromString(query);
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            String name = online == null ? knownPlayerName(id) : online.getName().getString();
            return name == null || name.isBlank() ? null : new ResolvedPlayer(id, name);
        } catch (IllegalArgumentException ignored) { }
        ServerPlayer online = server.getPlayerList().getPlayerByName(query);
        if (online != null) return new ResolvedPlayer(online.getUUID(), online.getName().getString());
        if (SsuModuleAccess.active("moderation")) {
            UUID id = SimpleServerUtilities.MODERATION.resolvePlayer(query);
            if (id != null) {
                String name = SimpleServerUtilities.MODERATION.name(id);
                return new ResolvedPlayer(id, name == null || name.isBlank() ? query : name);
            }
        }
        if (SsuModuleAccess.active("permissions")) {
            for (var known : SimpleServerUtilities.PERMISSIONS.getKnownPlayers()) {
                if (known.name().equalsIgnoreCase(query)) return new ResolvedPlayer(known.playerId(), known.name());
            }
        }
        return null;
    }

    private String knownPlayerName(UUID id) {
        if (id == null) return null;
        if (SsuModuleAccess.active("moderation")) {
            String name = SimpleServerUtilities.MODERATION.name(id);
            if (name != null && !name.isBlank()) return name;
        }
        if (SsuModuleAccess.active("permissions")) {
            for (var known : SimpleServerUtilities.PERMISSIONS.getKnownPlayers()) {
                if (known.playerId().equals(id)) return known.name();
            }
        }
        return null;
    }

    private ServerLevel level(String rawId) {
        if (server == null) throw new IllegalArgumentException("Server is unavailable.");
        String id = rawId == null || rawId.isBlank() ? "minecraft:overworld" : rawId.trim();
        try {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(id));
            ServerLevel level = server.getLevel(key);
            if (level == null) throw new IllegalArgumentException("Dimension is not currently loaded.");
            return level;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid dimension ID.");
        }
    }

    private void pruneBackups() {
        try {
            if (!Files.isDirectory(backupFolder)) return;
            List<Path> files;
            try (var stream = Files.list(backupFolder)) {
                files = stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".zip"))
                        .sorted(Comparator.comparingLong(this::lastModifiedSafe).reversed()).toList();
            }
            for (int i = state.backupRetentionCount; i < files.size(); i++) Files.deleteIfExists(files.get(i));
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.warn("Failed to prune old SSU backups.", exception);
        }
    }

    private static List<String> configurationProfileIncludes() {
        return List.of(
                "permissions/settings.json", "permissions/ranks",
                "dimensions/definitions", "spawn/server_spawn.json", "onboarding/settings.json", "moderation/settings.json",
                "kits/definitions", "regions/_settings.json", "regions/entries", "regions/selection_templates",
                "economy/settings.json", "auction_house/settings.json", "player_claims/tax_settings.json",
                "identity/titles.json", "holograms/holograms.json", "statistics/definitions.json",
                "npcs/definitions", "npcs/instances", "npcs/dialogues", "npcs/shops", "npcs/item_prices.json",
                "quests/definitions", "minigames/definitions", "dungeons/definitions", "mines/definitions", "jails/definitions",
                "visualization/settings.json"
        );
    }

    private synchronized JsonObject serverOperationsProfileJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("activityLoggingEnabled", state.activityLoggingEnabled);
        root.addProperty("activityBreaks", state.activityBreaks);
        root.addProperty("activityPlaces", state.activityPlaces);
        root.addProperty("activityRetentionDays", state.activityRetentionDays);
        root.addProperty("activityMaxEntries", state.activityMaxEntries);
        root.addProperty("rollbackBlocksPerTick", state.rollbackBlocksPerTick);
        root.addProperty("automaticBackups", state.automaticBackups);
        root.addProperty("automaticBackupIntervalMinutes", state.automaticBackupIntervalMinutes);
        root.addProperty("backupRetentionCount", state.backupRetentionCount);
        root.addProperty("maintenanceMessage", state.maintenanceMessage);
        root.addProperty("chatModerationEnabled", state.chatModerationEnabled);
        root.addProperty("chatSlowModeSeconds", state.chatSlowModeSeconds);
        root.addProperty("chatDuplicateWindowSeconds", state.chatDuplicateWindowSeconds);
        root.addProperty("chatBurstWindowSeconds", state.chatBurstWindowSeconds);
        root.addProperty("chatBurstMaxMessages", state.chatBurstMaxMessages);
        root.addProperty("chatCapsPercent", state.chatCapsPercent);
        root.addProperty("chatCapsMinimumLength", state.chatCapsMinimumLength);
        root.addProperty("chatLinksAllowed", state.chatLinksAllowed);
        root.addProperty("staffChatEnabled", state.staffChatEnabled);
        root.add("blockedWords", GSON.toJsonTree(state.blockedWords));
        root.addProperty("pregenChunksPerTick", state.pregenChunksPerTick);
        root.addProperty("pregenPauseAboveMspt", state.pregenPauseAboveMspt);
        root.addProperty("economyAlertThresholdMinor", state.economyAlertThresholdMinor);
        root.addProperty("closedTicketRetentionHours", state.closedTicketRetentionHours);
        JsonArray tasks = new JsonArray();
        for (ServerOperationsState.ScheduledTask task : state.tasks) if (!task.system) tasks.add(GSON.toJsonTree(task));
        root.add("scheduledTasks", tasks);
        return root;
    }

    private synchronized void applyServerOperationsProfile(JsonObject root) {
        if (root == null) return;
        state.activityLoggingEnabled = jsonBool(root, "activityLoggingEnabled", state.activityLoggingEnabled);
        state.activityBreaks = jsonBool(root, "activityBreaks", state.activityBreaks);
        state.activityPlaces = jsonBool(root, "activityPlaces", state.activityPlaces);
        state.activityRetentionDays = jsonInt(root, "activityRetentionDays", state.activityRetentionDays);
        state.activityMaxEntries = jsonInt(root, "activityMaxEntries", state.activityMaxEntries);
        state.rollbackBlocksPerTick = jsonInt(root, "rollbackBlocksPerTick", state.rollbackBlocksPerTick);
        state.automaticBackups = jsonBool(root, "automaticBackups", state.automaticBackups);
        state.automaticBackupIntervalMinutes = jsonInt(root, "automaticBackupIntervalMinutes", state.automaticBackupIntervalMinutes);
        state.backupRetentionCount = jsonInt(root, "backupRetentionCount", state.backupRetentionCount);
        if (root.has("maintenanceMessage")) state.maintenanceMessage = root.get("maintenanceMessage").getAsString();
        state.chatModerationEnabled = jsonBool(root, "chatModerationEnabled", state.chatModerationEnabled);
        state.chatSlowModeSeconds = jsonInt(root, "chatSlowModeSeconds", state.chatSlowModeSeconds);
        state.chatDuplicateWindowSeconds = jsonInt(root, "chatDuplicateWindowSeconds", state.chatDuplicateWindowSeconds);
        state.chatBurstWindowSeconds = jsonInt(root, "chatBurstWindowSeconds", state.chatBurstWindowSeconds);
        state.chatBurstMaxMessages = jsonInt(root, "chatBurstMaxMessages", state.chatBurstMaxMessages);
        state.chatCapsPercent = jsonInt(root, "chatCapsPercent", state.chatCapsPercent);
        state.chatCapsMinimumLength = jsonInt(root, "chatCapsMinimumLength", state.chatCapsMinimumLength);
        state.chatLinksAllowed = jsonBool(root, "chatLinksAllowed", state.chatLinksAllowed);
        state.staffChatEnabled = jsonBool(root, "staffChatEnabled", state.staffChatEnabled);
        if (root.has("blockedWords") && root.get("blockedWords").isJsonArray()) {
            ArrayList<String> words = new ArrayList<>();
            for (var element : root.getAsJsonArray("blockedWords")) if (element.isJsonPrimitive()) words.add(element.getAsString());
            state.blockedWords = words;
        }
        state.pregenChunksPerTick = jsonInt(root, "pregenChunksPerTick", state.pregenChunksPerTick);
        if (root.has("pregenPauseAboveMspt")) state.pregenPauseAboveMspt = root.get("pregenPauseAboveMspt").getAsDouble();
        if (root.has("economyAlertThresholdMinor")) state.economyAlertThresholdMinor = root.get("economyAlertThresholdMinor").getAsLong();
        state.closedTicketRetentionHours = jsonInt(root, "closedTicketRetentionHours", state.closedTicketRetentionHours);
        state.tasks.removeIf(task -> !task.system);
        if (root.has("scheduledTasks") && root.get("scheduledTasks").isJsonArray()) {
            for (var element : root.getAsJsonArray("scheduledTasks")) {
                if (!element.isJsonObject() || state.tasks.size() >= 128) continue;
                ServerOperationsState.ScheduledTask task = GSON.fromJson(element, ServerOperationsState.ScheduledTask.class);
                if (task == null) continue;
                task.system = false; task.lastRunAt = 0L; task.lastResult = "";
                task.normalize();
                if (task.id.isBlank() || AUTO_BACKUP_TASK_ID.equals(task.id) || state.tasks.stream().anyMatch(existing -> existing.id.equals(task.id))) continue;
                long now = System.currentTimeMillis();
                task.nextRunAt = nextScheduledRun(task, now);
                if ("ONCE".equals(task.scheduleMode) && task.nextRunAt <= now) task.enabled = false;
                state.tasks.add(task);
            }
        }
        state.maintenanceEnabled = false;
        state.normalize();
        ensureAutomaticBackupTask();
        saveState();
    }

    private static boolean jsonBool(JsonObject root, String key, boolean fallback) {
        try { return root.has(key) ? root.get(key).getAsBoolean() : fallback; } catch (Exception ignored) { return fallback; }
    }

    private static int jsonInt(JsonObject root, String key, int fallback) {
        try { return root.has(key) ? root.get(key).getAsInt() : fallback; } catch (Exception ignored) { return fallback; }
    }

    private static void addTextToZip(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addPathToZip(Path base, String relative, ZipOutputStream zip) throws IOException {
        Path source = base.resolve(relative).normalize();
        if (!source.startsWith(base) || !Files.exists(source)) return;
        if (Files.isRegularFile(source)) {
            addFileToZip(base, source, zip);
            return;
        }
        try (var stream = Files.walk(source)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) addFileToZip(base, file, zip);
        }
    }

    private static void addFileToZip(Path base, Path file, ZipOutputStream zip) throws IOException {
        String relative = base.relativize(file).toString().replace('\\', '/');
        zip.putNextEntry(new ZipEntry(relative));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static void extractZip(Path zipFile, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Unsafe archive entry: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        if (!Files.exists(source)) return;
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = destination.resolve(source.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.deleteIfExists(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { Files.deleteIfExists(dir); return FileVisitResult.CONTINUE; }
        });
    }

    private static void stopServer(MinecraftServer server) {
        if (server == null) return;
        try {
            Method halt = server.getClass().getMethod("halt", boolean.class);
            halt.invoke(server, false);
            return;
        } catch (ReflectiveOperationException ignored) { }
        try {
            Method stop = server.getClass().getMethod("stopServer");
            stop.invoke(server);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This Minecraft build does not expose a safe server-stop hook.");
        }
    }

    private static void broadcast(MinecraftServer server, String raw) {
        String message = bounded(raw, 512).trim();
        if (message.isBlank()) throw new IllegalArgumentException("Broadcast message is empty.");
        Component component = Component.literal(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(component);
    }

    private void notifyStaff(String raw) {
        MinecraftServer current = server;
        if (current == null) return;
        Component component = Component.literal("[SSU Support] " + bounded(raw, 384));
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            if (canAdmin(player)) player.sendSystemMessage(component);
        }
    }

    private void notifyTicketOwner(ServerOperationsState.SupportTicket ticket, String raw) {
        MinecraftServer current = server;
        if (current == null || ticket == null || ticket.playerId == null || ticket.playerId.isBlank()) return;
        try {
            ServerPlayer owner = current.getPlayerList().getPlayer(UUID.fromString(ticket.playerId));
            if (owner != null) owner.sendSystemMessage(Component.literal("[SSU Support] " + bounded(raw, 384)));
        } catch (IllegalArgumentException ignored) { }
    }

    private static int loadedChunkCount(ServerLevel level) {
        try {
            Object source = level.getChunkSource();
            Method method = source.getClass().getMethod("getLoadedChunksCount");
            Object value = method.invoke(source);
            return value instanceof Number number ? Math.max(0, number.intValue()) : -1;
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static long safeAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException exception) { return Long.MAX_VALUE; }
    }

    private static void addRolling(Deque<Double> values, double value, int max) {
        values.addLast(Math.max(0.0D, value));
        while (values.size() > max) values.removeFirst();
    }

    private static double average(Deque<Double> values) {
        if (values.isEmpty()) return 0.0D;
        double sum = 0.0D; for (double value : values) sum += value; return sum / values.size();
    }

    private static double percentile(Deque<Double> values, double percentile) {
        if (values.isEmpty()) return 0.0D;
        double[] data = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int index = Math.min(data.length - 1, Math.max(0, (int) Math.ceil(data.length * percentile) - 1));
        return data[index];
    }

    private static <T> void loadJsonLines(Path file, Class<T> type, Deque<T> output, int max) throws IOException {
        if (!Files.isRegularFile(file)) return;
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (line == null || line.isBlank()) return;
                try {
                    T value = LINE_GSON.fromJson(line, type);
                    if (value != null) {
                        output.addLast(value);
                        while (output.size() > max) output.removeFirst();
                    }
                } catch (Exception ignored) { }
            });
        }
    }

    private static List<String> drain(ConcurrentLinkedQueue<String> queue, int max) {
        ArrayList<String> result = new ArrayList<>();
        String line;
        while (result.size() < max && (line = queue.poll()) != null) result.add(line);
        return result;
    }

    private static void appendLines(Path file, List<String> lines) throws IOException {
        if (lines.isEmpty()) return;
        Files.createDirectories(file.getParent());
        String content = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void rewriteJsonLines(Path file, List<?> values) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Object value : values) { writer.write(LINE_GSON.toJson(value)); writer.newLine(); }
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path safeChild(Path folder, String rawName) {
        if (folder == null || rawName == null || rawName.isBlank()) throw new IllegalArgumentException("A file name is required.");
        Path result = folder.resolve(rawName.trim()).normalize();
        if (!result.startsWith(folder.normalize())) throw new IllegalArgumentException("Invalid file name.");
        return result;
    }

    private long lastModifiedSafe(Path file) { try { return Files.getLastModifiedTime(file).toMillis(); } catch (IOException ignored) { return 0L; } }
    private static long fileSizeSafe(Path file) { try { return Files.size(file); } catch (IOException ignored) { return 0L; } }
    private static String sanitize(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        return value.length() <= 48 ? value : value.substring(0, 48);
    }
    private static String bounded(String value, int max) { String result = value == null ? "" : value; return result.length() <= max ? result : result.substring(0, max); }

    public record ActivityEntry(long time, String playerId, String playerName, String action, String dimension,
                                int x, int y, int z, String beforeBlock, String afterBlock) { }
    public record AuditEntry(long time, String actor, String actorId, String action, String target, String detail) { }
    private record HealthSample(long time, double mspt, double tps) { }
    private record ChatLogEntry(long time, String playerId, String playerName, String message, boolean staff) { }
    private record ResolvedPlayer(UUID id, String name) { }
    private record PendingRestore(Path worldRoot, Path externalZip, String backupName) { }

    public record ChatDecision(boolean allowed, boolean silent, String message) {
        static ChatDecision allow() { return new ChatDecision(true, false, ""); }
        static ChatDecision block(String message) { return new ChatDecision(false, false, message == null ? "" : message); }
        static ChatDecision silentBlock() { return new ChatDecision(false, true, ""); }
    }

    private static final class ChatWindow {
        private final Deque<Long> timestamps = new ArrayDeque<>();
        private long lastAcceptedAt;
        private String lastText = "";
    }

    private static final class PregenJob {
        final String dimension; final int centerChunkX, centerChunkZ, radius, chunksPerTick, total; final double pauseAboveMspt;
        volatile int index, generated; volatile boolean pausedForLoad, cancelled, completed; volatile String failure = "";
        PregenJob(String dimension, int centerChunkX, int centerChunkZ, int radius, int chunksPerTick, double pauseAboveMspt) {
            this.dimension = dimension; this.centerChunkX = centerChunkX; this.centerChunkZ = centerChunkZ; this.radius = radius;
            this.chunksPerTick = chunksPerTick; this.pauseAboveMspt = pauseAboveMspt; int side = radius * 2 + 1; this.total = side * side;
        }
        boolean done() { return completed || cancelled || !failure.isBlank(); }
    }

    private static final class RollbackJob {
        final UUID actor; final String target, dimension; final List<ActivityEntry> entries; final int blocksPerTick;
        volatile int index, restored, skipped; volatile boolean completed; volatile String failure = "";
        RollbackJob(UUID actor, String target, String dimension, List<ActivityEntry> entries, int blocksPerTick) {
            this.actor = actor; this.target = target; this.dimension = dimension; this.entries = List.copyOf(entries); this.blocksPerTick = blocksPerTick;
        }
        void fail(String message) { failure = message == null ? "Rollback failed." : message; }
        boolean done() { return completed || !failure.isBlank(); }
    }
}
