package be.winnetrie.mod.simpleserverutilities.warp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.mail.MailSource;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.policy.WarpPolicy;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Persistent traditional server warps and recurring player-rented warps. */
public class WarpManager {
    public static final int STORAGE_SCHEMA = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Warp> warps = new HashMap<>();
    private final DirtyJsonRecordStore warpRecordStore = new DirtyJsonRecordStore();
    private RentalSettings rentalSettings = new RentalSettings();
    private Path saveFile;
    private MinecraftServer server;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        Path folder = StoragePaths.root(server);
        this.saveFile = folder.resolve("warps.json");
        warps.clear(); warpRecordStore.reset(); rentalSettings = new RentalSettings();
        rentalSettings.setPriceMinor(defaultRentalPriceMinor());
        try {
            Files.createDirectories(folder);
            if (!Files.exists(saveFile)) { save(); return; }
            warpRecordStore.discoverFile(saveFile);
            WarpSaveData data = JsonStorage.read(GSON, saveFile, WarpSaveData.class);
            if (data == null) return;
            if (data.schemaVersion >= 2 && data.rentalSettings != null) rentalSettings = data.rentalSettings;
            rentalSettings.normalize();
            if (data.warps != null) for (Warp warp : data.warps) {
                if (warp == null) continue;
                warp.ensureDefaults();
                if (warp.getName() == null || warp.getName().isBlank()) continue;
                warps.put(normalizeName(warp.getName()), warp);
            }
            save();
            SimpleServerUtilities.LOGGER.info("Loaded {} warp(s), including {} player rental(s).",
                    warps.size(), warps.values().stream().filter(Warp::isPlayerRental).count());
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(saveFile);
            SimpleServerUtilities.LOGGER.error("Failed to load server warps. Broken file archived as: {}", archived, exception);
        }
    }

    public synchronized void save() {
        if (saveFile == null) return;
        rentalSettings.normalize();
        WarpSaveData data = new WarpSaveData();
        data.schemaVersion = STORAGE_SCHEMA;
        data.rentalSettings = rentalSettings;
        data.warps = new ArrayList<>(warps.values());
        data.warps.sort(Comparator.comparing(Warp::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        warpRecordStore.queueJson(GSON, saveFile, data);
    }

    public synchronized void clear() {
        warps.clear(); warpRecordStore.reset(); saveFile = null; server = null; rentalSettings = new RentalSettings();
    }

    /** Creates or moves only a traditional administrator-owned server warp. */
    public synchronized boolean setWarp(ServerPlayer player, String rawName) {
        String name = sanitizeServerName(rawName); String key = normalizeName(name);
        Warp existing = warps.get(key); long now = System.currentTimeMillis();
        if (existing != null) {
            if (existing.isPlayerRental()) return false;
            existing.update(dimensionId(player), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), player.getUUID(), now);
            save(); return true;
        }
        int max = WarpPolicy.getMaxWarps(player);
        if (max > 0 && countServerWarps() >= max) return false;
        warps.put(key, new Warp(name, dimensionId(player), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.getUUID(), now));
        save(); return true;
    }

    public synchronized RentalResult setPlayerRentalWarp(ServerPlayer player, String rawName, long requestId) {
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!WarpPolicy.canRentWarp(player, context)) return RentalResult.fail("You do not have permission to rent player warps at this location.");
        String name = sanitizeRentalName(rawName); String key = normalizeName(name);
        Warp existing = warps.get(key); long now = System.currentTimeMillis();
        if (existing != null) {
            return existing.isRentedBy(player.getUUID())
                    ? RentalResult.fail("You already rent that warp. Use its Move here button instead.")
                    : RentalResult.fail("That warp name is already in use.");
        }
        if (!SimpleServerUtilities.ECONOMY.settings().isEnabled()) {
            return RentalResult.fail("The economy is disabled, so a prepaid warp rental cannot be created.");
        }
        int maximum = WarpPolicy.getMaxRentedWarps(player, context);
        int current = countPlayerWarps(player.getUUID());
        if (maximum <= 0 || current >= maximum) return RentalResult.fail("You reached your rented-warp limit (" + maximum + ").");
        long price = rentalSettings.getPriceMinor();
        // The manager is synchronized and inserts the warp immediately after a successful debit,
        // so a retransmitted packet observes the existing warp instead of paying twice. A fresh
        // random operation id prevents a later delete/re-rent with a reused client request id from
        // being mistaken for an already paid rental.
        String rentKey = "warps:rent:create:" + player.getUUID() + ":" + key + ":"
                + Math.max(0L, requestId) + ":" + UUID.randomUUID();
        if (price > 0L) {
            EconomyResult charge = SimpleServerUtilities.ECONOMY.debitTyped(player.getUUID(), player.getName().getString(),
                    player.getUUID(), price, EconomyTransactionType.WARP_RENT, "warps",
                    "Initial rental for warp '" + name + "'", rentKey);
            if (!charge.successful()) return RentalResult.fail(charge.message());
        }
        long paidUntil = safeAdd(now, rentalSettings.getPeriodMillis());
        Warp warp = Warp.rented(name, dimensionId(player), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.getUUID(), player.getName().getString(), now, paidUntil);
        warps.put(key, warp); save();
        return RentalResult.ok("Warp '" + name + "' rented until " + paidUntil + ".", warp);
    }


    /** Moves an existing player rental without starting or charging a new rental period. */
    public synchronized RentalResult movePlayerRentalWarp(ServerPlayer player, String rawName) {
        String name = sanitizeRentalName(rawName);
        Warp warp = warps.get(normalizeName(name));
        if (warp == null || !warp.isRentedBy(player.getUUID())) {
            return RentalResult.fail("That rented warp was not found.");
        }
        long now = System.currentTimeMillis();
        warp.update(dimensionId(player), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.getUUID(), now);
        warp.updateRenterName(player.getName().getString());
        save();
        return RentalResult.ok("Warp '" + warp.getDisplayName() + "' moved to your current position without changing its paid period.", warp);
    }

    public synchronized boolean setPlayerWarpVisibility(UUID owner, String rawName, boolean visible) {
        Warp warp = getWarp(rawName);
        if (warp == null || !warp.isRentedBy(owner)) return false;
        warp.setPublicWarp(visible, System.currentTimeMillis()); save(); return true;
    }

    public synchronized boolean deletePlayerWarp(UUID owner, String rawName) {
        Warp warp = getWarp(rawName);
        if (warp == null || !warp.isRentedBy(owner)) return false;
        warps.remove(normalizeName(warp.getName())); save(); return true;
    }

    /** Administrator deletion; may remove either server or player-rented warps. */
    public synchronized boolean deleteWarp(String rawName) {
        Warp removed = warps.remove(normalizeName(sanitizeServerName(rawName)));
        if (removed == null) return false;
        save(); return true;
    }

    public synchronized Warp getWarp(String rawName) {
        if (rawName == null) return null;
        String value = rawName.trim();
        if (value.isBlank()) value = "warp"; // Preserve legacy command lookup semantics.
        return warps.get(normalizeName(value));
    }

    public synchronized Collection<Warp> getWarps() {
        ArrayList<Warp> values = new ArrayList<>(warps.values());
        values.sort(Comparator.comparing(Warp::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return values;
    }

    public synchronized List<Warp> getAccessibleWarps(ServerPlayer player) {
        return warps.values().stream().filter(warp -> canAccess(player, warp))
                .sorted(Comparator.comparing(Warp::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public synchronized List<Warp> getPlayerWarps(UUID playerId) {
        return warps.values().stream().filter(warp -> warp.isRentedBy(playerId))
                .sorted(Comparator.comparing(Warp::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public synchronized boolean canAccess(ServerPlayer player, Warp warp) {
        return warp != null && (!warp.isPlayerRental() || warp.isPublicWarp() || warp.isRentedBy(player.getUUID())
                || WarpPolicy.canAdminWarps(player));
    }

    public synchronized int countWarps() { return warps.size(); }
    public synchronized int countServerWarps() { return (int) warps.values().stream().filter(warp -> !warp.isPlayerRental()).count(); }
    public synchronized int countPlayerWarps(UUID owner) { return (int) warps.values().stream().filter(warp -> warp.isRentedBy(owner)).count(); }

    public synchronized RentalSettings rentalSettings() { rentalSettings.normalize(); return rentalSettings; }

    public synchronized void configureRental(long priceMinor, long periodMillis) {
        rentalSettings.setPriceMinor(priceMinor); rentalSettings.setPeriodMillis(periodMillis); rentalSettings.normalize(); save();
    }

    /** Renews expired rentals or permanently frees their names when payment fails. */
    public synchronized void maintenanceTick() {
        if (server == null || warps.isEmpty() || !SimpleServerUtilities.ECONOMY.settings().isEnabled()) return;
        long now = System.currentTimeMillis(); boolean changed = false;
        List<Warp> expired = warps.values().stream().filter(Warp::isPlayerRental)
                .filter(warp -> warp.getPaidUntil() > 0L && warp.getPaidUntil() <= now).toList();
        for (Warp warp : expired) {
            UUID owner = warp.getRenterId(); long dueAt = warp.getPaidUntil(); long price = rentalSettings.getPriceMinor();
            String renewalKey = "warps:renew:" + owner + ":" + normalizeName(warp.getName()) + ":" + dueAt;
            EconomyResult renewal = price <= 0L || SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(renewalKey)
                    ? EconomyResult.success(null, "Renewal already paid or free.", 0L, 0L)
                    : SimpleServerUtilities.ECONOMY.debitTyped(null, "server", owner, price,
                    EconomyTransactionType.WARP_RENEW, "warps", "Renew warp '" + warp.getDisplayName() + "'",
                    renewalKey);
            if (renewal.successful() || "duplicate".equals(renewal.code())) {
                warp.renewUntil(safeAdd(Math.max(now, dueAt), rentalSettings.getPeriodMillis())); changed = true; continue;
            }
            if (!"insufficient_funds".equals(renewal.code())) {
                SimpleServerUtilities.LOGGER.error("Could not renew warp '{}': {}", warp.getDisplayName(), renewal.message());
                continue;
            }
            warps.remove(normalizeName(warp.getName())); changed = true;
            String body = "Your rented warp '" + warp.getDisplayName() + "' expired. The renewal price of "
                    + SimpleServerUtilities.ECONOMY.format(price) + " could not be paid, so the warp was deleted and its name is available again.";
            SimpleServerUtilities.MAIL.deliverSystemMail(owner, warp.getRenterName(), "Rented warp expired", body,
                    List.of(), 0L, MailSource.SYSTEM, "warp-expired:" + normalizeName(warp.getName()) + ":" + dueAt);
        }
        if (changed) save();
    }

    private long defaultRentalPriceMinor() {
        try { return MoneyFormat.parseMinor("100", SimpleServerUtilities.ECONOMY.settings()); }
        catch (RuntimeException ignored) { return 10_000L; }
    }

    private String dimensionId(ServerPlayer player) { return player.level().dimension().identifier().toString(); }
    private String sanitizeServerName(String name) {
        // Keep the original server-warp compatibility contract: blank means "warp"
        // and existing non-space names remain addressable after the rental migration.
        String value = name == null ? "" : name.trim();
        return value.isBlank() ? "warp" : value;
    }
    private String sanitizeRentalName(String name) {
        String value = name == null ? "" : name.trim();
        if (!value.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Use 1-32 letters, numbers, underscores or dashes for the rented warp name.");
        return value;
    }
    private String normalizeName(String name) { return name.toLowerCase(Locale.ROOT); }
    private static long safeAdd(long first, long second) { try { return Math.addExact(first, second); } catch (ArithmeticException e) { return Long.MAX_VALUE; } }

    public record RentalResult(boolean successful, String message, Warp warp) {
        public static RentalResult ok(String message, Warp warp) { return new RentalResult(true, message, warp); }
        public static RentalResult fail(String message) { return new RentalResult(false, message, null); }
    }

    public static final class RentalSettings {
        private long priceMinor = 10_000L; // €100.00 with the default two-decimal economy.
        private long periodMillis = Duration.ofDays(30).toMillis();
        public void normalize() {
            priceMinor = Math.max(0L, priceMinor);
            periodMillis = Math.max(Duration.ofHours(1).toMillis(), Math.min(Duration.ofDays(3650).toMillis(), periodMillis));
        }
        public long getPriceMinor() { normalize(); return priceMinor; }
        public void setPriceMinor(long value) { priceMinor = Math.max(0L, value); }
        public long getPeriodMillis() { normalize(); return periodMillis; }
        public void setPeriodMillis(long value) { periodMillis = value; }
    }

    private static class WarpSaveData {
        // Missing in schema 1 files, which lets load() apply the correct rental defaults.
        private int schemaVersion;
        private ArrayList<Warp> warps = new ArrayList<>();
        private RentalSettings rentalSettings = new RentalSettings();
    }
}
