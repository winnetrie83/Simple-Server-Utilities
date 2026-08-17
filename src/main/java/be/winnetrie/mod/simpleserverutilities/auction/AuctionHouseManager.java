package be.winnetrie.mod.simpleserverutilities.auction;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.economy.EconomySystemAccounts;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import be.winnetrie.mod.simpleserverutilities.mail.MailOperationResult;
import be.winnetrie.mod.simpleserverutilities.mail.MailSource;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Persistent, server-authoritative Auction House. */
public final class AuctionHouseManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_MAX_ACTIVE = 5;
    private static final long SESSION_MILLIS = 15L * 60L * 1000L;
    private static final long PURCHASE_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final UUID CLEARING_ACCOUNT_ID = EconomySystemAccounts.AUCTION_HOUSE_TAX;
    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.forLanguageTag("nl-BE")).withZone(ZoneId.systemDefault());

    private final Map<UUID, AuctionListing> listings = new HashMap<>();
    private final Map<UUID, AuctionPurchaseRecord> purchases = new HashMap<>();
    private final Map<UUID, Long> sessions = new HashMap<>();
    private AuctionHouseSettings settings = new AuctionHouseSettings();
    private MinecraftServer server;
    private Path listingFolder;
    private Path purchaseFolder;
    private Path settingsFile;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        Path root = StoragePaths.root(server);
        listingFolder = StoragePaths.auctionListings(root);
        purchaseFolder = StoragePaths.auctionPurchases(root);
        settingsFile = StoragePaths.auctionSettings(root);
        listings.clear();
        purchases.clear();
        sessions.clear();
        settings = new AuctionHouseSettings();
        try {
            Files.createDirectories(listingFolder);
            Files.createDirectories(purchaseFolder);
            if (Files.isRegularFile(settingsFile)) {
                AuctionHouseSettings loaded = JsonStorage.read(GSON, settingsFile, AuctionHouseSettings.class);
                if (loaded != null) settings = loaded;
            }
            settings.normalize();
            for (Path file : JsonStorage.listJsonFiles(listingFolder)) {
                try {
                    UUID id = UUID.fromString(StoragePaths.fileBaseName(file));
                    AuctionListing listing = JsonStorage.read(GSON, file, AuctionListing.class);
                    if (listing == null) continue;
                    listing.normalize(id);
                    listings.put(listing.getId(), listing);
                } catch (Exception exception) {
                    SimpleServerUtilities.LOGGER.error("Failed to load Auction House listing {}", file, exception);
                }
            }
            for (Path file : JsonStorage.listJsonFiles(purchaseFolder)) {
                try {
                    UUID id = UUID.fromString(StoragePaths.fileBaseName(file));
                    AuctionPurchaseRecord purchase = JsonStorage.read(GSON, file, AuctionPurchaseRecord.class);
                    if (purchase == null) continue;
                    purchase.normalize(id);
                    purchases.put(purchase.getId(), purchase);
                } catch (Exception exception) {
                    SimpleServerUtilities.LOGGER.error("Failed to load Auction House purchase {}", file, exception);
                }
            }
            SimpleServerUtilities.ECONOMY.ensureSystemAccount(CLEARING_ACCOUNT_ID, "SSU Auction House Tax");
            saveSettings();
            maintenanceTick();
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to initialize Auction House storage", exception);
        }
    }

    public synchronized void saveAllSync() {
        if (server == null) return;
        saveSettings();
        for (AuctionListing listing : listings.values()) saveListing(listing);
        for (AuctionPurchaseRecord purchase : purchases.values()) savePurchase(purchase);
    }

    public synchronized int saleTaxPermille() {
        return settings.getSaleTaxPermille();
    }

    public synchronized boolean updateSaleTaxPermille(int permille) {
        int normalized = Math.max(0, Math.min(1_000, permille));
        int previous = settings.getSaleTaxPermille();
        settings.setSaleTaxPermille(normalized);
        if (saveSettings()) return true;
        settings.setSaleTaxPermille(previous);
        return false;
    }


    public synchronized void clear() {
        listings.clear();
        purchases.clear();
        sessions.clear();
        settings = new AuctionHouseSettings();
        server = null;
        listingFolder = null;
        purchaseFolder = null;
        settingsFile = null;
    }

    public boolean enabled() {
        return Config.ENABLE_AUCTION_HOUSE.get()
                && SimpleServerUtilities.CORE.modules().isActive("auction_house")
                && SimpleServerUtilities.CORE.modules().isActive("mail")
                && SimpleServerUtilities.ECONOMY.isEnabled();
    }

    public boolean canAccess(ServerPlayer player) {
        return player != null && enabled()
                && PermissionService.getBoolean(player, PermissionKeys.AUCTION_HOUSE_ACCESS, true);
    }

    public boolean dashboardVisible(ServerPlayer player) {
        return canAccess(player)
                && PermissionService.getBooleanWithoutOperatorBypass(player,
                        PermissionKeys.AUCTION_HOUSE_DASHBOARD, true);
    }

    public boolean canAdmin(ServerPlayer player) {
        return player != null && enabled() && PermissionService.getBoolean(player, PermissionKeys.AUCTION_HOUSE_ADMIN, false);
    }

    public int maxAuctions(ServerPlayer player) {
        return Math.max(0, PermissionService.getInt(player, PermissionKeys.AUCTION_HOUSE_MAX_ACTIVE,
                DEFAULT_MAX_ACTIVE));
    }

    public synchronized int activeCount(UUID sellerId) {
        long now = System.currentTimeMillis();
        return (int) listings.values().stream()
                .filter(l -> sellerId != null && sellerId.equals(l.getSellerId()))
                .filter(l -> !l.isSeizurePending())
                .filter(l -> l.getRemainingQuantity() > 0 && l.getExpiresAtEpochMilli() > now)
                .count();
    }

    public synchronized boolean canContinueSession(ServerPlayer player) {
        if (!canAccess(player)) return false;
        long now = System.currentTimeMillis();
        Long expires = sessions.get(player.getUUID());
        if (expires == null || expires < now) {
            sessions.remove(player.getUUID());
            return false;
        }
        sessions.put(player.getUUID(), now + SESSION_MILLIS);
        return true;
    }

    public synchronized void closeSession(UUID playerId) {
        if (playerId != null) sessions.remove(playerId);
    }

    /** Dashboard entry: requires both dashboard visibility and normal AH access. */
    public synchronized void openFromDashboard(ServerPlayer player, long requestId) {
        if (!dashboardVisible(player)) {
            PacketDistributor.sendToPlayer(player, AuctionHouseDataPayload.denied(requestId,
                    "You do not have access to the Auction House from the dashboard."));
            return;
        }
        grantSession(player);
        sendPage(player, "browse", "all", "", "name_asc", 0, 8, requestId, "", false);
    }

    /** Trusted future NPC/server entry: only the general AH access permission is required. */
    public synchronized void openTrusted(ServerPlayer player) {
        if (!canAccess(player)) {
            PacketDistributor.sendToPlayer(player, AuctionHouseDataPayload.denied(0L,
                    "You do not have permission to use the Auction House."));
            return;
        }
        grantSession(player);
        sendPage(player, "browse", "all", "", "name_asc", 0, 8, 0L, "", false);
    }

    private void grantSession(ServerPlayer player) {
        sessions.put(player.getUUID(), System.currentTimeMillis() + SESSION_MILLIS);
    }

    public void handleRequest(AuctionHouseRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("auction_house")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        synchronized (this) {
            if (!canContinueSession(player)) {
                PacketDistributor.sendToPlayer(player, AuctionHouseDataPayload.denied(payload.requestId(),
                        "Your Auction House session is no longer valid."));
                return;
            }
            sendPage(player, payload.mode(), payload.category(), payload.search(), payload.sort(),
                    payload.pageIndex(), payload.pageSize(), payload.requestId(), "", false);
        }
    }

    public void handleAction(AuctionHouseActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("auction_house")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        synchronized (this) {
            String action = payload.action().trim().toLowerCase(Locale.ROOT);
            if ("close".equals(action)) {
                closeSession(player.getUUID());
                return;
            }
            if (!canContinueSession(player)) {
                sendResult(player, false, "Your Auction House session is no longer valid.", payload.requestId(), false, false);
                return;
            }
            switch (action) {
                case "open_sell" -> openSell(player, payload.requestId());
                case "create" -> createAuction(player, payload);
                case "buy" -> buy(player, payload);
                case "cancel" -> cancel(player, payload);
                case "admin_cancel" -> adminCancel(player, payload);
                case "seize" -> seize(player, payload);
                case "blacklist_inventory" -> blacklistInventoryItem(player, payload);
                case "blacklist_id" -> blacklistItemId(player, payload);
                case "blacklist_listing" -> blacklistListingItem(player, payload);
                case "unblacklist" -> unblacklistItem(player, payload);
                default -> sendResult(player, false, "Unknown Auction House action.", payload.requestId(), false, false);
            }
        }
    }

    private void openSell(ServerPlayer player, long requestId) {
        int active = activeCount(player.getUUID());
        int max = maxAuctions(player);
        if (active >= max) {
            sendResult(player, false, "You already have the maximum of " + max + " active auction(s).",
                    requestId, false, false);
            return;
        }
        int tax = settings.getSaleTaxPermille();
        int duration = settings.getDefaultDurationHours();
        String balance = SimpleServerUtilities.ECONOMY.formattedBalance(player);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new AuctionSellMenu(containerId, inventory,
                        new SimpleContainer(AuctionSellMenu.OFFER_SLOTS), active, max, tax, duration, balance),
                Component.literal("Create Auction")
        ), buffer -> {
            buffer.writeVarInt(active);
            buffer.writeVarInt(max);
            buffer.writeVarInt(tax);
            buffer.writeVarInt(duration);
            buffer.writeUtf(balance, 128);
        });
    }

    private void createAuction(ServerPlayer player, AuctionHouseActionPayload payload) {
        if (!(player.containerMenu instanceof AuctionSellMenu menu)) {
            sendResult(player, false, "Open the sell window before creating an auction.", payload.requestId(), false, false);
            return;
        }
        try {
            int expectedContainer = Integer.parseInt(payload.target());
            if (expectedContainer != menu.containerId) throw new IllegalArgumentException("The sell window is no longer current.");
            if (activeCount(player.getUUID()) >= maxAuctions(player)) {
                throw new IllegalArgumentException("You have reached your active-auction limit.");
            }
            long unitPrice = MoneyFormat.parseMinor(payload.value(), SimpleServerUtilities.ECONOMY.settings());
            if (unitPrice <= 0L) throw new IllegalArgumentException("Price per item must be greater than zero.");
            int quantity = payload.quantity();
            if (quantity <= 0) throw new IllegalArgumentException("Enter how many items you want to sell.");
            AuctionSellMenu.Extraction extraction = menu.extractForListing(quantity);
            if (isBlacklisted(extraction.template())) {
                menu.restore(extraction);
                throw new IllegalArgumentException("That item is blacklisted and cannot be sold in the Auction House.");
            }
            AuctionListing listing;
            try {
                listing = AuctionListing.create(server.registryAccess(), player.getUUID(), player.getName().getString(),
                        extraction.template(), extraction.quantity(), unitPrice, System.currentTimeMillis(), payload.durationHours());
                listings.put(listing.getId(), listing);
                if (!saveListing(listing)) {
                    listings.remove(listing.getId());
                    throw new IllegalStateException("The auction could not be saved. Your items were restored.");
                }
            } catch (Exception exception) {
                menu.restore(extraction);
                throw exception;
            }
            player.closeContainer();
            sendResult(player, true, "Auction created for " + extraction.quantity() + " × "
                    + extraction.template().getHoverName().getString() + ".", payload.requestId(), false, true);
            sendPage(player, "my", "all", "", "time_asc", 0, 8, payload.requestId(),
                    "Auction created successfully.", false);
        } catch (Exception exception) {
            sendResult(player, false, safeMessage(exception, "Could not create the auction."),
                    payload.requestId(), false, false);
        }
    }

    private void buy(ServerPlayer buyer, AuctionHouseActionPayload payload) {
        UUID listingId;
        try { listingId = UUID.fromString(payload.target()); }
        catch (IllegalArgumentException exception) {
            sendResult(buyer, false, "That auction no longer exists.", payload.requestId(), false, true);
            return;
        }
        AuctionListing listing = listings.get(listingId);
        int quantity = payload.quantity();
        long now = System.currentTimeMillis();
        if (listing == null || listing.isSeizurePending() || listing.getRemainingQuantity() <= 0
                || listing.getExpiresAtEpochMilli() <= now) {
            sendResult(buyer, false, "That auction is no longer available.", payload.requestId(), false, true);
            return;
        }
        if (buyer.getUUID().equals(listing.getSellerId())) {
            sendResult(buyer, false, "You cannot buy your own auction.", payload.requestId(), false, false);
            return;
        }
        if (quantity <= 0 || quantity > listing.getRemainingQuantity()) {
            sendResult(buyer, false, "Choose between 1 and " + listing.getRemainingQuantity() + " item(s).",
                    payload.requestId(), false, false);
            return;
        }
        long gross;
        long tax;
        try {
            gross = Math.multiplyExact(listing.getPricePerUnitMinor(), quantity);
            tax = BigInteger.valueOf(gross).multiply(BigInteger.valueOf(settings.getSaleTaxPermille()))
                    .divide(BigInteger.valueOf(1_000L)).longValueExact();
        } catch (ArithmeticException exception) {
            sendResult(buyer, false, "That purchase amount is too large.", payload.requestId(), false, false);
            return;
        }
        long net = gross - tax;
        if (SimpleServerUtilities.ECONOMY.balance(buyer) < gross) {
            sendResult(buyer, false, "You do not have enough money for this purchase.", payload.requestId(), false, false);
            return;
        }
        ItemStack item = listing.item(server.registryAccess());
        if (item.isEmpty()) {
            sendResult(buyer, false, "The auction item could not be loaded.", payload.requestId(), false, true);
            return;
        }
        AuctionPurchaseRecord record = AuctionPurchaseRecord.create(listing, listing.getItemData(),
                item.getHoverName().getString(), buyer.getUUID(), buyer.getName().getString(), quantity, gross,
                settings.getSaleTaxPermille(), tax, net);
        purchases.put(record.getId(), record);
        if (!savePurchase(record)) {
            purchases.remove(record.getId());
            sendResult(buyer, false, "The purchase could not be prepared safely. No money was taken.",
                    payload.requestId(), false, false);
            return;
        }
        int remainingBefore = listing.getRemainingQuantity();
        listing.setRemainingQuantity(remainingBefore - quantity);
        if (!saveListing(listing)) {
            listing.setRemainingQuantity(remainingBefore);
            deletePurchase(record.getId());
            sendResult(buyer, false, "The auction could not be reserved safely. No money was taken.",
                    payload.requestId(), false, true);
            return;
        }
        record.setStatus(AuctionPurchaseRecord.Status.LISTING_RESERVED);
        if (!savePurchase(record)) {
            PurchaseOutcome rollback = rollbackPurchase(record,
                    "The purchase could not be journaled safely. No money was taken.");
            sendResult(buyer, false, rollback.message(), payload.requestId(), false, true);
            return;
        }
        PurchaseOutcome outcome = resumePurchase(record);
        if (outcome.committed()) {
            sendResult(buyer, true, "Purchase completed. Your item(s) were delivered by mail.",
                    payload.requestId(), true, true);
        } else if (outcome.pending()) {
            sendResult(buyer, true, "Purchase accepted. Delivery is being completed safely by the server.",
                    payload.requestId(), false, true);
        } else {
            sendResult(buyer, false, outcome.message(), payload.requestId(), false, true);
        }
    }

    private PurchaseOutcome resumePurchase(AuctionPurchaseRecord record) {
        String key = "auction:purchase:" + record.getId();
        String captureKey = key + ":capture";
        String payoutKey = key + ":payout_fund";

        boolean captureCommitted = SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(captureKey);
        if (!captureCommitted) {
            EconomyResult capture = SimpleServerUtilities.ECONOMY.transferTyped(record.getBuyerId(), record.getBuyerName(),
                    record.getBuyerId(), CLEARING_ACCOUNT_ID, record.getGrossMinor(), EconomyTransactionType.AUCTION_PURCHASE,
                    "auction_house", "Auction purchase " + record.getItemName(), captureKey);
            if (!capture.successful()) return rollbackPurchase(record, capture.message());
        }
        if (record.getStatus().ordinal() < AuctionPurchaseRecord.Status.FUNDS_CAPTURED.ordinal()) {
            record.setStatus(AuctionPurchaseRecord.Status.FUNDS_CAPTURED);
            savePurchase(record);
        }

        boolean payoutCommitted = record.getNetMinor() <= 0L
                || SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(payoutKey);
        if (!payoutCommitted) {
            EconomyResult fund = SimpleServerUtilities.ECONOMY.transferTyped(null, "Auction House", CLEARING_ACCOUNT_ID,
                    SimpleServerUtilities.MAIL.escrowAccountId(), record.getNetMinor(),
                    EconomyTransactionType.AUCTION_SELLER_PAYOUT, "auction_house",
                    "Fund auction seller mail " + record.getId(), payoutKey);
            if (!fund.successful()) return rollbackPurchase(record, fund.message());
        }
        if (record.getStatus().ordinal() < AuctionPurchaseRecord.Status.PAYOUT_FUNDED.ordinal()) {
            record.setStatus(AuctionPurchaseRecord.Status.PAYOUT_FUNDED);
            savePurchase(record);
        }

        if (record.getStatus().ordinal() < AuctionPurchaseRecord.Status.SELLER_MAILED.ordinal()) {
            String body = "Sold: " + record.getQuantity() + " × " + record.getItemName()
                    + "\nBuyer: " + record.getBuyerName()
                    + "\nDate: " + MAIL_TIME.format(Instant.ofEpochMilli(record.getCreatedAtEpochMilli()))
                    + "\nGross: " + money(record.getGrossMinor())
                    + "\nTax (" + taxPercent(record.getTaxPermille()) + "): -" + money(record.getTaxMinor())
                    + "\nYou receive: " + money(record.getNetMinor());
            MailOperationResult sellerMail = SimpleServerUtilities.MAIL.deliverPreEscrowedMail(
                    record.getSellerId(), record.getSellerName(), "Auction sold: " + record.getItemName(), body,
                    List.of(), record.getNetMinor(), MailSource.AUCTION, key + ":seller");
            if (!sellerMail.successful()) return PurchaseOutcome.waiting(sellerMail.message());
            record.setStatus(AuctionPurchaseRecord.Status.SELLER_MAILED);
            savePurchase(record);
        }
        if (record.getStatus().ordinal() < AuctionPurchaseRecord.Status.BUYER_MAILED.ordinal()) {
            ItemStack template = MailItemCodec.decode(server.registryAccess(), record.getItem());
            if (template.isEmpty()) return PurchaseOutcome.waiting("The purchased item is waiting for recovery.");
            List<List<ItemStack>> mails = groupAttachments(splitStacks(template, record.getQuantity()));
            for (int index = 0; index < mails.size(); index++) {
                String body = "Purchased: " + record.getQuantity() + " × " + record.getItemName()
                        + "\nSeller: " + record.getSellerName()
                        + "\nUnit price: " + money(record.getUnitPriceMinor())
                        + "\nTotal: " + money(record.getGrossMinor())
                        + "\nDelivery " + (index + 1) + " of " + mails.size() + ".";
                MailOperationResult buyerMail = SimpleServerUtilities.MAIL.deliverSystemMail(
                        record.getBuyerId(), record.getBuyerName(), "Auction purchase: " + record.getItemName(), body,
                        mails.get(index), 0L, MailSource.AUCTION, key + ":buyer:" + index);
                if (!buyerMail.successful()) return PurchaseOutcome.waiting(buyerMail.message());
            }
            record.setStatus(AuctionPurchaseRecord.Status.BUYER_MAILED);
            savePurchase(record);
        }
        record.setStatus(AuctionPurchaseRecord.Status.COMMITTED);
        savePurchase(record);
        if (!record.isContentEventsPublished()) {
            publishPurchaseContentEvents(record);
            record.setContentEventsPublished(true);
            savePurchase(record);
        }
        AuctionListing listing = listings.get(record.getListingId());
        if (listing != null && listing.getRemainingQuantity() <= 0) deleteListing(listing.getId());
        return PurchaseOutcome.complete();
    }

    private void publishPurchaseContentEvents(AuctionPurchaseRecord record) {
        if (record == null || server == null) return;
        ItemStack stack = MailItemCodec.decode(server.registryAccess(), record.getItem());
        String itemId = stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Map<String, String> common = Map.of(
                "durable_event", "true",
                "purchase_id", record.getId().toString(),
                "listing_id", record.getListingId().toString(),
                "item", itemId,
                "quantity", Integer.toString(record.getQuantity()));
        publishDurableAuctionEvent(record, ContentEventTypes.AUCTION_PURCHASE, record.getBuyerId(), itemId,
                record.getQuantity(), common);
        publishDurableAuctionEvent(record, ContentEventTypes.AUCTION_SALE, record.getSellerId(), itemId,
                record.getQuantity(), common);
        if (record.getNetMinor() > 0L) {
            publishDurableAuctionEvent(record, ContentEventTypes.AUCTION_REVENUE, record.getSellerId(), itemId,
                    record.getNetMinor(), common);
        }
    }

    private void publishDurableAuctionEvent(AuctionPurchaseRecord record, String type, UUID playerId,
                                            String subject, long amount, Map<String, String> metadata) {
        if (playerId == null || amount <= 0L) return;
        UUID eventId = UUID.nameUUIDFromBytes(("ssu:auction:" + record.getId() + ":" + type + ":" + playerId)
                .getBytes(StandardCharsets.UTF_8));
        SimpleServerUtilities.CONTENT_EVENTS.publish(server, new ContentEvent(eventId, type, playerId,
                "auction_house", record.getId().toString(), subject, amount, metadata, System.currentTimeMillis()));
    }

    private PurchaseOutcome rollbackPurchase(AuctionPurchaseRecord record, String reason) {
        if (record.getStatus() != AuctionPurchaseRecord.Status.ROLLBACK_PENDING) {
            record.setStatus(AuctionPurchaseRecord.Status.ROLLBACK_PENDING);
            if (!savePurchase(record)) {
                return PurchaseOutcome.waiting("The rollback is waiting for safe journal storage.");
            }
        }
        String key = "auction:purchase:" + record.getId();
        String captureKey = key + ":capture";
        String payoutKey = key + ":payout_fund";
        String rollbackPayoutKey = key + ":rollback_payout";
        String rollbackBuyerKey = key + ":rollback_buyer";

        boolean payoutFunded = record.getNetMinor() > 0L
                && SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(payoutKey);
        if (payoutFunded && !SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(rollbackPayoutKey)) {
            EconomyResult payoutRollback = SimpleServerUtilities.ECONOMY.transferTyped(null, "Auction House",
                    SimpleServerUtilities.MAIL.escrowAccountId(), CLEARING_ACCOUNT_ID, record.getNetMinor(),
                    EconomyTransactionType.AUCTION_PURCHASE_ROLLBACK, "auction_house",
                    "Rollback auction payout funding", rollbackPayoutKey);
            if (!payoutRollback.successful()) return PurchaseOutcome.waiting(payoutRollback.message());
        }

        boolean captured = SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(captureKey);
        if (captured && !SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(rollbackBuyerKey)) {
            EconomyResult buyerRollback = SimpleServerUtilities.ECONOMY.transferTyped(null, "Auction House",
                    CLEARING_ACCOUNT_ID, record.getBuyerId(), record.getGrossMinor(),
                    EconomyTransactionType.AUCTION_PURCHASE_ROLLBACK, "auction_house",
                    "Rollback failed auction purchase", rollbackBuyerKey);
            if (!buyerRollback.successful()) return PurchaseOutcome.waiting(buyerRollback.message());
        }

        AuctionListing listing = listings.get(record.getListingId());
        if (listing != null && listing.getRemainingQuantity() == record.getListingRemainingBefore() - record.getQuantity()) {
            listing.setRemainingQuantity(record.getListingRemainingBefore());
            if (!saveListing(listing)) {
                return PurchaseOutcome.waiting("The rollback is waiting to restore the auction listing safely.");
            }
        }
        record.setStatus(AuctionPurchaseRecord.Status.ROLLED_BACK);
        savePurchase(record);
        return PurchaseOutcome.failed(reason == null || reason.isBlank() ? "The purchase was rolled back safely." : reason);
    }

    private void cancel(ServerPlayer player, AuctionHouseActionPayload payload) {
        UUID id;
        try { id = UUID.fromString(payload.target()); }
        catch (IllegalArgumentException exception) {
            sendResult(player, false, "That auction no longer exists.", payload.requestId(), false, true); return;
        }
        AuctionListing listing = listings.get(id);
        if (listing == null || listing.isSeizurePending()
                || listing.getRemainingQuantity() <= 0 || listing.getExpiresAtEpochMilli() <= System.currentTimeMillis()
                || !player.getUUID().equals(listing.getSellerId())) {
            sendResult(player, false, "You can only cancel your own active auctions.", payload.requestId(), false, true); return;
        }
        MailOperationResult returned = returnListing(listing, "cancelled");
        if (!returned.successful()) {
            sendResult(player, false, returned.message(), payload.requestId(), false, false); return;
        }
        deleteListing(id);
        sendResult(player, true, "Auction cancelled. The remaining items were returned by mail.",
                payload.requestId(), false, true);
    }

    private void adminCancel(ServerPlayer player, AuctionHouseActionPayload payload) {
        if (!canAdmin(player)) {
            sendResult(player, false, "You do not have Auction House administration permission.",
                    payload.requestId(), false, false);
            return;
        }
        String reason;
        try {
            reason = requireAdministrativeReason(payload.value());
        } catch (IllegalArgumentException exception) {
            sendResult(player, false, exception.getMessage(), payload.requestId(), false, false);
            return;
        }
        AuctionListing listing = listing(payload.target());
        if (listing == null || listing.isSeizurePending() || listing.getRemainingQuantity() <= 0
                || listing.getExpiresAtEpochMilli() <= System.currentTimeMillis()) {
            sendResult(player, false, "That auction is no longer active.", payload.requestId(), false, true);
            return;
        }
        String administrator = player.getName().getString();
        MailOperationResult returned = returnListing(listing, "cancelled by an administrator",
                "Administrator: " + administrator + "\nReason: " + reason);
        if (!returned.successful()) {
            sendResult(player, false, returned.message(), payload.requestId(), false, false);
            return;
        }
        deleteListing(listing.getId());
        SimpleServerUtilities.LOGGER.info(
                "Auction {} owned by {} was cancelled by administrator {} ({}) with reason: {}",
                listing.getId(), listing.getSellerName(), administrator, player.getUUID(), reason);
        sendResult(player, true, "Auction cancelled by administrator. The items and reason were mailed to the seller.",
                payload.requestId(), false, true);
    }

    private void seize(ServerPlayer player, AuctionHouseActionPayload payload) {
        if (!canAdmin(player)) {
            sendResult(player, false, "You do not have Auction House administration permission.",
                    payload.requestId(), false, false);
            return;
        }
        String requestedReason;
        try {
            requestedReason = requireAdministrativeReason(payload.value());
        } catch (IllegalArgumentException exception) {
            sendResult(player, false, exception.getMessage(), payload.requestId(), false, false);
            return;
        }
        AuctionListing listing = listing(payload.target());
        if (listing == null || listing.getRemainingQuantity() <= 0) {
            sendResult(player, false, "That auction is no longer active.", payload.requestId(), false, true);
            return;
        }
        if (listing.isSeizurePending() && !player.getUUID().equals(listing.getSeizureRecipientId())) {
            sendResult(player, false, "That auction is already being seized by "
                    + listing.getSeizureRecipientName() + ".", payload.requestId(), false, true);
            return;
        }
        if (!listing.isSeizurePending()) {
            if (listing.getExpiresAtEpochMilli() <= System.currentTimeMillis()) {
                sendResult(player, false, "That auction is no longer active.", payload.requestId(), false, true);
                return;
            }
            if (listing.item(server.registryAccess()).isEmpty()) {
                sendResult(player, false, "The auction item could not be loaded. No seizure was started.",
                        payload.requestId(), false, false);
                return;
            }
            listing.beginSeizure(player.getUUID(), player.getName().getString(), requestedReason,
                    System.currentTimeMillis());
            if (!saveListing(listing)) {
                listing.clearSeizure();
                sendResult(player, false, "The seizure could not be stored safely. No items were moved.",
                        payload.requestId(), false, false);
                return;
            }
        }
        MailOperationResult outcome = completeSeizure(listing);
        if (!outcome.successful()) {
            sendResult(player, true, "The seizure and its reason were stored safely; delivery will retry automatically. "
                    + outcome.message(), payload.requestId(), false, true);
            return;
        }
        deleteListing(listing.getId());
        SimpleServerUtilities.LOGGER.warn(
                "Auction {} owned by {} was seized by administrator {} ({}) with reason: {}",
                listing.getId(), listing.getSellerName(), listing.getSeizureRecipientName(),
                listing.getSeizureRecipientId(), listing.getSeizureReason());
        sendResult(player, true, "Auction seized. The items were delivered to your mailbox and the seller received the reason.",
                payload.requestId(), false, true);
    }

    private MailOperationResult completeSeizure(AuctionListing listing) {
        if (listing == null || !listing.isSeizurePending() || listing.getSeizureRecipientId() == null) {
            return MailOperationResult.failure("seizure_missing", "The seizure recipient is missing.");
        }
        ItemStack template = listing.item(server.registryAccess());
        if (template.isEmpty()) {
            return MailOperationResult.failure("item_missing", "The auction item could not be loaded.");
        }
        String reason = listing.getSeizureReason().isBlank()
                ? "No reason was stored for this legacy seizure." : listing.getSeizureReason();
        List<List<ItemStack>> groups = groupAttachments(splitStacks(template, listing.getRemainingQuantity()));
        for (int index = 0; index < groups.size(); index++) {
            MailOperationResult seized = SimpleServerUtilities.MAIL.deliverSystemMail(
                    listing.getSeizureRecipientId(), listing.getSeizureRecipientName(),
                    "Seized auction item: " + template.getHoverName().getString(),
                    "You seized " + listing.getRemainingQuantity() + " × " + template.getHoverName().getString()
                            + " from " + listing.getSellerName() + "'s auction.\nReason: " + reason
                            + "\nSeizure mail " + (index + 1) + " of " + groups.size() + ".",
                    groups.get(index), 0L, MailSource.AUCTION,
                    "auction:seize:" + listing.getId() + ":" + index);
            if (!seized.successful()) return seized;
        }
        return SimpleServerUtilities.MAIL.deliverSystemMail(
                listing.getSellerId(), listing.getSellerName(),
                "Auction seized: " + template.getHoverName().getString(),
                "An administrator seized your active auction containing " + listing.getRemainingQuantity()
                        + " × " + template.getHoverName().getString() + ". Administrator: "
                        + listing.getSeizureRecipientName() + ".\nReason: " + reason,
                List.of(), 0L, MailSource.AUCTION, "auction:seize:notice:" + listing.getId());
    }

    private void blacklistInventoryItem(ServerPlayer player, AuctionHouseActionPayload payload) {
        if (!canAdmin(player)) {
            sendResult(player, false, "You do not have Auction House administration permission.",
                    payload.requestId(), false, false);
            return;
        }
        final int slot;
        try {
            slot = Integer.parseInt(payload.target());
        } catch (NumberFormatException exception) {
            sendResult(player, false, "Select an item from your inventory first.",
                    payload.requestId(), false, false);
            return;
        }
        if (slot < 0 || slot >= 36) {
            sendResult(player, false, "The selected inventory slot is invalid.",
                    payload.requestId(), false, false);
            return;
        }
        ItemStack selected = player.getInventory().getItem(slot);
        if (selected.isEmpty()) {
            sendResult(player, false, "The selected inventory slot is empty or changed.",
                    payload.requestId(), false, false);
            return;
        }
        addToBlacklist(player, selected, payload.requestId());
    }

    private void blacklistItemId(ServerPlayer player, AuctionHouseActionPayload payload) {
        if (!canAdmin(player)) {
            sendResult(player, false, "You do not have Auction House administration permission.",
                    payload.requestId(), false, false);
            return;
        }
        String raw = payload.target() == null ? "" : payload.target().trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) {
            sendResult(player, false, "Enter an item ID first, for example minecraft:diamond.",
                    payload.requestId(), false, false);
            return;
        }
        try {
            ResourceLocation identifier = ResourceLocation.parse(raw);
            ItemStack item = BuiltInRegistries.ITEM.getOptional(identifier)
                    .map(registeredItem -> new ItemStack(registeredItem))
                    .orElse(ItemStack.EMPTY);
            if (item.isEmpty()) {
                sendResult(player, false, "No registered item exists with ID " + identifier + ".",
                        payload.requestId(), false, false);
                return;
            }
            addToBlacklist(player, item, payload.requestId());
        } catch (RuntimeException exception) {
            sendResult(player, false, "That is not a valid registered item ID.",
                    payload.requestId(), false, false);
        }
    }

    private void blacklistListingItem(ServerPlayer player, AuctionHouseActionPayload payload) {
        if (!canAdmin(player)) {
            sendResult(player, false, "You do not have Auction House administration permission.",
                    payload.requestId(), false, false);
            return;
        }
        AuctionListing listing = listing(payload.target());
        if (listing == null || listing.isSeizurePending() || listing.getRemainingQuantity() <= 0
                || listing.getExpiresAtEpochMilli() <= System.currentTimeMillis()) {
            sendResult(player, false, "That auction is no longer active.", payload.requestId(), false, true);
            return;
        }
        ItemStack item = listing.item(server.registryAccess());
        if (item.isEmpty()) {
            sendResult(player, false, "The auction item could not be loaded.", payload.requestId(), false, false);
            return;
        }
        addToBlacklist(player, item, payload.requestId());
    }

    private void addToBlacklist(ServerPlayer player, ItemStack item, long requestId) {
        String itemId = itemId(item);
        if (itemId.isBlank()) {
            sendResult(player, false, "That item has no valid registry identifier.", requestId, false, false);
            return;
        }
        if (settings.isBlacklisted(itemId)) {
            sendResult(player, false, item.getHoverName().getString() + " is already blacklisted.",
                    requestId, false, false);
            return;
        }
        settings.addBlacklistedItem(itemId);
        if (!saveSettings()) {
            settings.removeBlacklistedItem(itemId);
            sendResult(player, false, "The blacklist could not be saved.", requestId, false, false);
            return;
        }
        SimpleServerUtilities.LOGGER.info("Auction House item {} was blacklisted by {} ({})",
                itemId, player.getName().getString(), player.getUUID());
        sendResult(player, true, item.getHoverName().getString()
                + " was added to the Auction House blacklist. Existing auctions remain active.",
                requestId, false, true);
    }

    private void unblacklistItem(ServerPlayer player, AuctionHouseActionPayload payload) {
        if (!canAdmin(player)) {
            sendResult(player, false, "You do not have Auction House administration permission.",
                    payload.requestId(), false, false);
            return;
        }
        String itemId = payload.target() == null ? "" : payload.target().trim().toLowerCase(Locale.ROOT);
        if (!settings.isBlacklisted(itemId)) {
            sendResult(player, false, "That item is not blacklisted.", payload.requestId(), false, true);
            return;
        }
        settings.removeBlacklistedItem(itemId);
        if (!saveSettings()) {
            settings.addBlacklistedItem(itemId);
            sendResult(player, false, "The blacklist could not be saved.", payload.requestId(), false, false);
            return;
        }
        SimpleServerUtilities.LOGGER.info("Auction House item {} was removed from the blacklist by {} ({})",
                itemId, player.getName().getString(), player.getUUID());
        sendResult(player, true, "Item removed from the Auction House blacklist.",
                payload.requestId(), false, true);
    }

    private AuctionListing listing(String rawId) {
        try {
            return listings.get(UUID.fromString(rawId));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isBlacklisted(ItemStack stack) {
        return settings.isBlacklisted(itemId(stack));
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
    }

    private static String requireAdministrativeReason(String raw) {
        String reason = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ')
                .trim().replaceAll("\\s+", " ");
        if (reason.isBlank()) throw new IllegalArgumentException("Enter a reason before continuing.");
        if (reason.length() > 200) throw new IllegalArgumentException("The reason may contain at most 200 characters.");
        return reason;
    }

    public synchronized void maintenanceTick() {
        if (server == null || !enabled()) return;
        recoverPurchases();
        long now = System.currentTimeMillis();
        for (AuctionListing listing : new ArrayList<>(listings.values())) {
            if (listing.isSeizurePending()) {
                MailOperationResult seized = completeSeizure(listing);
                if (seized.successful()) {
                    SimpleServerUtilities.LOGGER.warn("Recovered and completed Auction House seizure {} for {} ({})",
                            listing.getId(), listing.getSeizureRecipientName(), listing.getSeizureRecipientId());
                    deleteListing(listing.getId());
                } else {
                    SimpleServerUtilities.LOGGER.warn("Auction House seizure {} is still pending: {}",
                            listing.getId(), seized.message());
                }
            } else if (listing.getRemainingQuantity() <= 0) {
                deleteListing(listing.getId());
            } else if (listing.getExpiresAtEpochMilli() <= now) {
                MailOperationResult returned = returnListing(listing, "expired");
                if (returned.successful()) deleteListing(listing.getId());
            }
        }
        sessions.entrySet().removeIf(entry -> entry.getValue() < now);
        for (AuctionPurchaseRecord record : new ArrayList<>(purchases.values())) {
            if (record.getStatus() == AuctionPurchaseRecord.Status.COMMITTED && !record.isContentEventsPublished()) {
                publishPurchaseContentEvents(record);
                record.setContentEventsPublished(true);
                savePurchase(record);
            }
            if ((record.getStatus() == AuctionPurchaseRecord.Status.COMMITTED
                    || record.getStatus() == AuctionPurchaseRecord.Status.ROLLED_BACK)
                    && now - record.getCreatedAtEpochMilli() > PURCHASE_RETENTION_MILLIS) {
                deletePurchase(record.getId());
            }
        }
    }

    private void recoverPurchases() {
        for (AuctionPurchaseRecord record : new ArrayList<>(purchases.values())) {
            if (record.getStatus() == AuctionPurchaseRecord.Status.COMMITTED
                    || record.getStatus() == AuctionPurchaseRecord.Status.ROLLED_BACK) continue;
            if (record.getStatus() == AuctionPurchaseRecord.Status.ROLLBACK_PENDING) {
                rollbackPurchase(record, "Recovered an interrupted auction rollback.");
                continue;
            }
            String captureKey = "auction:purchase:" + record.getId() + ":capture";
            boolean captureCommitted = SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(captureKey);
            if (!captureCommitted && (record.getStatus() == AuctionPurchaseRecord.Status.PREPARED
                    || record.getStatus() == AuctionPurchaseRecord.Status.LISTING_RESERVED)) {
                rollbackPurchase(record, "Recovered an interrupted purchase before payment.");
                continue;
            }
            if (captureCommitted
                    && record.getStatus().ordinal() < AuctionPurchaseRecord.Status.FUNDS_CAPTURED.ordinal()) {
                record.setStatus(AuctionPurchaseRecord.Status.FUNDS_CAPTURED);
                savePurchase(record);
            }
            resumePurchase(record);
        }
    }

    private MailOperationResult returnListing(AuctionListing listing, String reason) {
        return returnListing(listing, reason, "");
    }

    private MailOperationResult returnListing(AuctionListing listing, String reason, String details) {
        ItemStack template = listing.item(server.registryAccess());
        if (template.isEmpty()) return MailOperationResult.failure("item_missing", "The auction item could not be returned.");
        String extra = details == null || details.isBlank() ? "" : "\n" + details.trim();
        List<List<ItemStack>> groups = groupAttachments(splitStacks(template, listing.getRemainingQuantity()));
        for (int index = 0; index < groups.size(); index++) {
            MailOperationResult result = SimpleServerUtilities.MAIL.deliverSystemMail(
                    listing.getSellerId(), listing.getSellerName(), "Auction " + reason + ": " + template.getHoverName().getString(),
                    "Your auction was " + reason + ". Remaining quantity: " + listing.getRemainingQuantity()
                            + "." + extra + "\nReturn mail " + (index + 1) + " of " + groups.size() + ".",
                    groups.get(index), 0L, MailSource.AUCTION,
                    "auction:return:" + listing.getId() + ":" + reason + ":" + index);
            if (!result.successful()) return result;
        }
        return MailOperationResult.success("Auction items returned.");
    }

    private void sendPage(ServerPlayer player, String rawMode, String rawCategory, String rawSearch, String rawSort,
            int requestedPage, int requestedPageSize, long requestId, String notice, boolean error) {
        if (!canContinueSession(player)) {
            PacketDistributor.sendToPlayer(player, AuctionHouseDataPayload.denied(requestId,
                    "You do not have permission to use the Auction House."));
            return;
        }
        boolean administrator = canAdmin(player);
        String requestedMode = rawMode == null ? "browse" : rawMode.trim().toLowerCase(Locale.ROOT);
        String mode = switch (requestedMode) {
            case "my" -> "my";
            case "admin" -> administrator ? "admin" : "browse";
            case "blacklist" -> administrator ? "blacklist" : "browse";
            default -> "browse";
        };
        if (("admin".equals(requestedMode) || "blacklist".equals(requestedMode)) && !administrator) {
            notice = "You do not have Auction House administration permission.";
            error = true;
        }
        String category = rawCategory == null ? "all" : rawCategory.trim().toLowerCase(Locale.ROOT);
        String search = rawSearch == null ? "" : rawSearch.trim().toLowerCase(Locale.ROOT);
        int pageSize = Math.max(1, Math.min(AuctionHouseDataPayload.MAX_ENTRIES, requestedPageSize));

        if ("blacklist".equals(mode)) {
            sendBlacklistPage(player, search, rawSort, requestedPage, pageSize, requestId, notice, error);
            return;
        }

        AuctionSort sort = AuctionSort.byId(rawSort);
        long now = System.currentTimeMillis();
        List<AuctionListingView> all = listings.values().stream()
                .filter(l -> !l.isSeizurePending())
                .filter(l -> l.getRemainingQuantity() > 0 && l.getExpiresAtEpochMilli() > now)
                .filter(l -> !"my".equals(mode) || player.getUUID().equals(l.getSellerId()))
                .map(l -> {
                    ItemStack item = l.item(server.registryAccess());
                    return new AuctionListingView(l, item, item.isEmpty() ? "Unknown item" : item.getHoverName().getString());
                })
                .filter(v -> !v.item().isEmpty())
                .filter(v -> "my".equals(mode) || "all".equals(category) || category.equals(v.listing().getCategory()))
                // Deliberately search the localized display/hover name, never the registry id.
                .filter(v -> search.isBlank() || v.displayName().toLowerCase(Locale.ROOT).contains(search))
                .sorted(sort.comparator())
                .toList();
        int pageCount = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(0, requestedPage), pageCount - 1);
        int from = Math.min(all.size(), page * pageSize);
        int to = Math.min(all.size(), from + pageSize);
        List<AuctionHouseDataPayload.Entry> entries = all.subList(from, to).stream().map(v -> new AuctionHouseDataPayload.Entry(
                v.listing().getId().toString(), v.item().copyWithCount(1), v.displayName(),
                money(v.listing().getPricePerUnitMinor()), v.listing().getPricePerUnitMinor(),
                v.listing().getRemainingQuantity(), v.listing().getSellerName(), v.listing().getCreatedAtEpochMilli(),
                v.listing().getExpiresAtEpochMilli(), v.listing().getCategory(),
                player.getUUID().equals(v.listing().getSellerId()))).toList();
        int active = activeCount(player.getUUID());
        int max = maxAuctions(player);
        PacketDistributor.sendToPlayer(player, new AuctionHouseDataPayload(true, mode, category, rawSearch,
                sort.id(), page, pageSize, all.size(), SimpleServerUtilities.ECONOMY.formattedBalance(player),
                SimpleServerUtilities.ECONOMY.settings().getCurrencySymbol(),
                SimpleServerUtilities.ECONOMY.settings().getDecimalPlaces(),
                active, max, active < max, administrator, settings.getSaleTaxPermille(),
                settings.getDefaultDurationHours(), requestId, notice, error, entries));
    }

    private void sendBlacklistPage(ServerPlayer player, String search, String rawSort, int requestedPage, int pageSize,
            long requestId, String notice, boolean error) {
        boolean descending = "name_desc".equalsIgnoreCase(rawSort);
        Comparator<AuctionHouseDataPayload.Entry> comparator = Comparator.comparing(
                AuctionHouseDataPayload.Entry::name, String.CASE_INSENSITIVE_ORDER);
        if (descending) comparator = comparator.reversed();
        List<AuctionHouseDataPayload.Entry> all = settings.getBlacklistedItemIds().stream()
                .map(itemId -> blacklistEntry(itemId))
                .filter(entry -> search.isBlank()
                        || entry.name().toLowerCase(Locale.ROOT).contains(search)
                        || entry.id().toLowerCase(Locale.ROOT).contains(search))
                .sorted(comparator)
                .toList();
        int pageCount = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(0, requestedPage), pageCount - 1);
        int from = Math.min(all.size(), page * pageSize);
        int to = Math.min(all.size(), from + pageSize);
        int active = activeCount(player.getUUID());
        int max = maxAuctions(player);
        PacketDistributor.sendToPlayer(player, new AuctionHouseDataPayload(true, "blacklist", "all", search,
                descending ? "name_desc" : "name_asc", page, pageSize, all.size(),
                SimpleServerUtilities.ECONOMY.formattedBalance(player),
                SimpleServerUtilities.ECONOMY.settings().getCurrencySymbol(),
                SimpleServerUtilities.ECONOMY.settings().getDecimalPlaces(),
                active, max, active < max, true, settings.getSaleTaxPermille(),
                settings.getDefaultDurationHours(), requestId, notice, error, all.subList(from, to)));
    }

    private AuctionHouseDataPayload.Entry blacklistEntry(String itemId) {
        ItemStack stack = ItemStack.EMPTY;
        String name = itemId;
        try {
            stack = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId))
                    .map(registeredItem -> new ItemStack(registeredItem))
                    .orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) name = stack.getHoverName().getString();
        } catch (RuntimeException ignored) {
            // Keep missing modded entries manageable by their stored identifier.
        }
        return new AuctionHouseDataPayload.Entry(itemId, stack, name, "", 0L, 0, "", 0L, 0L,
                "blacklist", false);
    }

    private static List<ItemStack> splitStacks(ItemStack template, int quantity) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = Math.max(0, quantity);
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int count = Math.min(maxStack, remaining);
            result.add(template.copyWithCount(count));
            remaining -= count;
        }
        return result;
    }

    private static List<List<ItemStack>> groupAttachments(List<ItemStack> stacks) {
        List<List<ItemStack>> groups = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i += 9) {
            groups.add(List.copyOf(stacks.subList(i, Math.min(stacks.size(), i + 9))));
        }
        return groups.isEmpty() ? List.of(List.of()) : List.copyOf(groups);
    }

    private String money(long minor) {
        return MoneyFormat.format(minor, SimpleServerUtilities.ECONOMY.settings());
    }

    private String taxPercent() {
        return taxPercent(settings.getSaleTaxPermille());
    }

    private static String taxPercent(int permille) {
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(Math.max(0, Math.min(1_000, permille)), 1)
                .stripTrailingZeros();
        return value.toPlainString() + "%";
    }

    private void sendResult(ServerPlayer player, boolean success, String message, long requestId,
            boolean sound, boolean refresh) {
        PacketDistributor.sendToPlayer(player, new AuctionHouseActionResultPayload(success, message,
                requestId, sound, refresh));
    }

    private boolean saveSettings() {
        if (settingsFile == null) return false;
        try {
            JsonStorage.write(GSON, settingsFile, settings);
            return true;
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to save Auction House settings", exception);
            return false;
        }
    }

    private boolean saveListing(AuctionListing listing) {
        if (listingFolder == null || listing == null) return false;
        try {
            JsonStorage.write(GSON, StoragePaths.jsonFile(listingFolder, listing.getId().toString()), listing);
            return true;
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to save auction {}", listing.getId(), exception);
            return false;
        }
    }

    private void deleteListing(UUID id) {
        listings.remove(id);
        if (listingFolder == null || id == null) return;
        try { Files.deleteIfExists(StoragePaths.jsonFile(listingFolder, id.toString())); }
        catch (IOException exception) { SimpleServerUtilities.LOGGER.error("Failed to delete auction {}", id, exception); }
    }

    private boolean savePurchase(AuctionPurchaseRecord purchase) {
        if (purchaseFolder == null || purchase == null) return false;
        try {
            JsonStorage.write(GSON, StoragePaths.jsonFile(purchaseFolder, purchase.getId().toString()), purchase);
            return true;
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to save auction purchase {}", purchase.getId(), exception);
            return false;
        }
    }

    private void deletePurchase(UUID id) {
        purchases.remove(id);
        if (purchaseFolder == null || id == null) return;
        try { Files.deleteIfExists(StoragePaths.jsonFile(purchaseFolder, id.toString())); }
        catch (IOException exception) { SimpleServerUtilities.LOGGER.error("Failed to delete auction purchase {}", id, exception); }
    }

    private static String safeMessage(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }

    private record PurchaseOutcome(boolean committed, boolean pending, String message) {
        static PurchaseOutcome complete() { return new PurchaseOutcome(true, false, ""); }
        static PurchaseOutcome waiting(String message) { return new PurchaseOutcome(false, true, message); }
        static PurchaseOutcome failed(String message) { return new PurchaseOutcome(false, false, message); }
    }
}
