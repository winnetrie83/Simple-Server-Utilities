package be.winnetrie.mod.simpleserverutilities.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyAccount;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.network.MailActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailComposeResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailComposeSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRecipientSuggestionsPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRecipientSuggestionsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Durable mailbox store with a permission-driven visible soft cap. Queued mail
 * is persisted on disk and has no retention timer until promoted into the
 * visible inbox.
 */
public final class MailManager {
    public static final int HARD_ATTACHMENT_CAP = 9;
    public static final int DEFAULT_SOFT_CAP = 20;
    public static final int DEFAULT_PAGE_SIZE = 6;
    private static final long DAY_MILLIS = Duration.ofDays(1).toMillis();
    private static final UUID ESCROW_ACCOUNT_ID = UUID.nameUUIDFromBytes(
            "simpleserverutilities:mail_escrow".getBytes(StandardCharsets.UTF_8));

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, MailboxData> mailboxes = new HashMap<>();
    private MinecraftServer server;
    private Path mailboxFolder;
    private int maintenanceCursor;

    public static UUID escrowAccountId() {
        return ESCROW_ACCOUNT_ID;
    }

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        this.mailboxFolder = StoragePaths.mailboxes(StoragePaths.root(server));
        mailboxes.clear();
        maintenanceCursor = 0;
        SimpleServerUtilities.ECONOMY.ensureSystemAccount(ESCROW_ACCOUNT_ID, "SSU Mail Escrow");
        try {
            Files.createDirectories(mailboxFolder);
            for (Path file : JsonStorage.listJsonFiles(mailboxFolder)) {
                try {
                    MailboxData data = JsonStorage.read(gson, file, MailboxData.class);
                    UUID fallback = parseUuid(StoragePaths.fileBaseName(file));
                    if (data == null || (data.getPlayerId() == null && fallback == null)) continue;
                    data.normalize(fallback);
                    mailboxes.put(data.getPlayerId(), data);
                } catch (Exception e) {
                    JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load mailbox {}.", file, e);
                }
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to initialize SSU mail storage.", e);
        }
    }

    public synchronized void saveAllSync() {
        if (mailboxFolder == null) return;
        for (MailboxData mailbox : mailboxes.values()) {
            try {
                JsonStorage.write(gson, file(mailbox.getPlayerId()), mailbox);
            } catch (IOException e) {
                SimpleServerUtilities.LOGGER.error("Failed to save mailbox {}.", mailbox.getPlayerId(), e);
            }
        }
    }

    public synchronized void clear() {
        mailboxes.clear();
        server = null;
        mailboxFolder = null;
        maintenanceCursor = 0;
    }

    /** Performs bounded periodic retention and queue promotion for offline mailboxes. */
    public synchronized void maintenanceTick() {
        if (mailboxes.isEmpty() || mailboxFolder == null) return;
        List<UUID> ids = mailboxes.keySet().stream().sorted().toList();
        int count = Math.min(64, ids.size());
        int start = Math.floorMod(maintenanceCursor, ids.size());
        long now = System.currentTimeMillis();
        for (int offset = 0; offset < count; offset++) {
            UUID id = ids.get((start + offset) % ids.size());
            MailboxData box = mailboxes.get(id);
            if (box == null) continue;
            // Offline maintenance uses the last permission-derived cap observed for this mailbox.
            if (cleanupAndReconcile(box, box.getLastKnownInboxSoftCap(), box.getLastKnownSentLimit(), now)) queueSave(box);
        }
        maintenanceCursor = (start + count) % ids.size();
    }

    public synchronized void ensurePlayer(ServerPlayer player) {
        if (player == null) return;
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        int cap = inboxSoftCap(player);
        int sentLimit = sentLimit(player);
        box.updateInboxSoftCap(cap);
        box.updateSentLimit(sentLimit);
        boolean changed = cleanupAndReconcile(box, cap, sentLimit, System.currentTimeMillis());
        if (changed) queueSave(box);
        if (PermissionService.getBoolean(player, PermissionKeys.MAIL_ACCESS, true)) {
            int queued = queuedCount(box);
            int unread = unreadCount(box);
            if (queued > 0) {
                player.sendSystemMessage(Component.literal("Your mailbox is full: " + queued
                        + " mail(s) are waiting safely on the server."));
            } else if (unread > 0) {
                player.sendSystemMessage(Component.literal("You have " + unread + " unread mail(s)."));
            }
        }
    }

    public void handleRequest(MailRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        sendPage(player, payload.mode(), payload.pageIndex(), payload.pageSize(), payload.requestId(), "", false);
    }

    public void handleRecipientSuggestions(MailRecipientSuggestionsRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!Config.ENABLE_MAIL.get()
                || !PermissionService.getBoolean(player, PermissionKeys.MAIL_ACCESS, true)
                || !PermissionService.getBoolean(player, PermissionKeys.MAIL_SEND, true)) {
            PacketDistributor.sendToPlayer(player, new MailRecipientSuggestionsPayload(
                    payload.query(), payload.requestId(), List.of()));
            return;
        }
        PacketDistributor.sendToPlayer(player, new MailRecipientSuggestionsPayload(
                payload.query(), payload.requestId(), recipientSuggestions(player, payload.query())));
    }

    public void handleAction(MailActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String action = payload.action().toLowerCase(Locale.ROOT);
        if (action.equals("open_compose")) {
            openCompose(player, payload.requestId());
            return;
        }
        if (action.equals("open_mailbox")) {
            sendPage(player, "inbox", 0, DEFAULT_PAGE_SIZE, payload.requestId(), "", false);
            return;
        }

        MailOperationResult result = switch (action) {
            case "mark_read" -> markRead(player, payload.mailId());
            case "delete" -> deleteMail(player, payload.mailId());
            case "delete_sent" -> deleteSentMail(player, payload.mailId());
            case "clear_inbox" -> clearInbox(player);
            case "clear_sent" -> clearSent(player);
            case "claim_items" -> claimItems(player, payload.mailId());
            case "claim_money" -> claimMoney(player, payload.mailId());
            case "claim_all" -> claimAll(player, payload.mailId());
            default -> MailOperationResult.failure("unknown_action", "Unknown mail action.");
        };
        sendPage(player, payload.mode(), payload.pageIndex(), DEFAULT_PAGE_SIZE, payload.requestId(),
                result.message(), !result.successful());
    }

    public void handleCompose(MailComposeSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof MailComposeMenu menu) || menu.containerId != payload.containerId()) {
            PacketDistributor.sendToPlayer(player, new MailComposeResultPayload(false,
                    "The mail composer is no longer open.", payload.requestId(), false));
            return;
        }
        MailOperationResult result = sendPlayerMail(player, menu, payload.recipient(), payload.subject(),
                payload.body(), payload.money());
        if (result.successful()) {
            menu.commitAndClear();
            player.closeContainer();
        }
        PacketDistributor.sendToPlayer(player, new MailComposeResultPayload(result.successful(), result.message(),
                payload.requestId(), result.successful()));
        if (result.successful()) {
            sendPage(player, "inbox", 0, DEFAULT_PAGE_SIZE, payload.requestId(), result.message(), false);
        }
    }

    public synchronized MailOperationResult deliverSystemMail(
            UUID recipientId,
            String recipientName,
            String subject,
            String body,
            List<ItemStack> items,
            long moneyMinor,
            MailSource source,
            String correlationKey
    ) {
        if (recipientId == null || server == null) {
            return MailOperationResult.failure("recipient_missing", "Mail recipient is missing.");
        }
        if (hasCorrelation(recipientId, source == null ? MailSource.SYSTEM : source, correlationKey)) {
            return MailOperationResult.success("Mail was already delivered.");
        }
        UUID mailId = UUID.randomUUID();
        if (moneyMinor > 0L) {
            EconomyResult funding = SimpleServerUtilities.ECONOMY.creditTyped(null, "server", ESCROW_ACCOUNT_ID,
                    moneyMinor, EconomyTransactionType.MAIL_ESCROW_DEPOSIT, "mail",
                    "Fund system mail " + mailId, "mail:system-fund:" + mailId);
            if (!funding.successful()) return MailOperationResult.failure(funding.code(), funding.message());
        }
        MailMessage message = createMessage(mailId, null, "Server", recipientId, recipientName, subject, body,
                items, moneyMinor, source == null ? MailSource.SYSTEM : source, correlationKey);
        MailOperationResult delivered = deliverPrepared(message, null);
        if (!delivered.successful() && moneyMinor > 0L) {
            EconomyResult rollback = SimpleServerUtilities.ECONOMY.debitTyped(null, "server", ESCROW_ACCOUNT_ID,
                    moneyMinor, EconomyTransactionType.MAIL_ESCROW_REFUND, "mail",
                    "Rollback failed system mail " + mailId, "mail:system-fund-rollback:" + mailId);
            if (!rollback.successful() && !"duplicate".equals(rollback.code())) {
                SimpleServerUtilities.LOGGER.error("Failed to roll back funded system mail {}: {}",
                        mailId, rollback.message());
            }
        }
        return delivered;
    }

    /** Auction and other modules may call this after they already funded mail escrow. */
    public synchronized MailOperationResult deliverPreEscrowedMail(
            UUID recipientId, String recipientName, String subject, String body, List<ItemStack> items,
            long moneyMinor, MailSource source, String correlationKey
    ) {
        if (recipientId == null || server == null) {
            return MailOperationResult.failure("recipient_missing", "Mail recipient is missing.");
        }
        MailSource safeSource = source == null ? MailSource.SYSTEM : source;
        if (hasCorrelation(recipientId, safeSource, correlationKey)) {
            return MailOperationResult.success("Mail was already delivered.");
        }
        MailMessage message = createMessage(UUID.randomUUID(), null, "Server", recipientId, recipientName,
                subject, body, items, moneyMinor, safeSource, correlationKey);
        return deliverPrepared(message, null);
    }

    public void openMailbox(ServerPlayer player) {
        sendPage(player, "inbox", 0, DEFAULT_PAGE_SIZE, 0L, "", false);
    }

    private synchronized MailOperationResult sendPlayerMail(ServerPlayer sender, MailComposeMenu menu,
            String rawRecipient, String rawSubject, String rawBody, String rawMoney) {
        if (!Config.ENABLE_MAIL.get()) return MailOperationResult.failure("disabled", "The mail system is disabled.");
        if (!PermissionService.getBoolean(sender, PermissionKeys.MAIL_ACCESS, true)) {
            return MailOperationResult.failure("access_denied", "You have not unlocked mailbox access.");
        }
        if (!PermissionService.getBoolean(sender, PermissionKeys.MAIL_SEND, true)) {
            return MailOperationResult.failure("send_denied", "You do not have permission to send mail.");
        }
        Recipient recipient = resolveRecipient(rawRecipient);
        if (recipient == null) return MailOperationResult.failure("recipient_unknown", "Unknown player: " + rawRecipient);
        if (recipient.id().equals(sender.getUUID())) return MailOperationResult.failure("self", "You cannot mail yourself.");

        List<ItemStack> items = menu.attachmentCopies();
        int maxAttachments = maxAttachments(sender);
        if (items.size() > maxAttachments || items.size() > HARD_ATTACHMENT_CAP) {
            return MailOperationResult.failure("attachments", "You may attach at most " + maxAttachments + " stack(s).");
        }
        if (!items.isEmpty() && !PermissionService.getBoolean(sender, PermissionKeys.MAIL_SEND_ITEMS, true)) {
            return MailOperationResult.failure("items_denied", "You do not have permission to send item attachments.");
        }

        long moneyMinor = 0L;
        if (rawMoney != null && !rawMoney.isBlank()) {
            if (!PermissionService.getBoolean(sender, PermissionKeys.MAIL_SEND_MONEY, true)) {
                return MailOperationResult.failure("money_denied", "You do not have permission to send money by mail.");
            }
            try {
                moneyMinor = MoneyFormat.parseMinor(rawMoney, SimpleServerUtilities.ECONOMY.settings());
            } catch (IllegalArgumentException e) {
                return MailOperationResult.failure("invalid_money", e.getMessage());
            }
        }
        String subject = rawSubject == null || rawSubject.isBlank() ? "(No subject)" : rawSubject.trim();
        String body = MailRichText.normalize(rawBody);
        if (subject.length() > 96) {
            return MailOperationResult.failure("text_too_long", "Mail subject is too long.");
        }
        if (items.isEmpty() && moneyMinor == 0L && MailRichText.plainText(body).isBlank()) {
            return MailOperationResult.failure("empty", "A mail needs a message, item, or money attachment.");
        }

        MailboxData senderBox = mailbox(sender.getUUID(), sender.getName().getString());
        senderBox.updateSentLimit(sentLimit(sender));
        long now = System.currentTimeMillis();
        senderBox.pruneOutgoingHistory(now);
        int dailyLimit = Math.max(0, PermissionService.getInt(sender, PermissionKeys.MAIL_DAILY_SEND_LIMIT, 20));
        long recent = senderBox.getOutgoingSendHistory().stream().filter(value -> value >= now - DAY_MILLIS).count();
        if (dailyLimit == 0 || recent >= dailyLimit) {
            return MailOperationResult.failure("daily_limit", "You reached your daily outgoing mail limit (" + dailyLimit + ").");
        }
        int cooldown = Math.max(0, PermissionService.getInt(sender, PermissionKeys.MAIL_SEND_COOLDOWN, 5));
        long latest = senderBox.getOutgoingSendHistory().stream().mapToLong(Long::longValue).max().orElse(0L);
        long remaining = latest + cooldown * 1000L - now;
        if (remaining > 0L) {
            return MailOperationResult.failure("cooldown", "Wait " + Math.max(1L, (remaining + 999L) / 1000L)
                    + " second(s) before sending another mail.");
        }

        UUID mailId = UUID.randomUUID();
        if (moneyMinor > 0L) {
            EconomyResult escrow = SimpleServerUtilities.ECONOMY.transferTyped(sender.getUUID(), sender.getName().getString(),
                    sender.getUUID(), ESCROW_ACCOUNT_ID, moneyMinor, EconomyTransactionType.MAIL_ESCROW_DEPOSIT,
                    "mail", "Mail money attachment to " + recipient.name(), "mail:escrow:" + mailId);
            if (!escrow.successful()) return MailOperationResult.failure(escrow.code(), escrow.message());
        }

        MailMessage message;
        try {
            message = createMessage(mailId, sender.getUUID(), sender.getName().getString(), recipient.id(),
                    recipient.name(), subject, body, items, moneyMinor, MailSource.PLAYER, "");
        } catch (RuntimeException e) {
            refundEscrow(sender, mailId, moneyMinor);
            return MailOperationResult.failure("serialize_failed", "An item attachment could not be serialized.");
        }
        MailOperationResult delivered = deliverPrepared(message, senderBox);
        if (!delivered.successful()) refundEscrow(sender, mailId, moneyMinor);
        return delivered;
    }

    private synchronized MailOperationResult deliverPrepared(MailMessage message, MailboxData existingSenderBox) {
        MailboxData recipientBox = mailbox(message.getRecipientId(), message.getRecipientName());
        ServerPlayer onlineRecipient = server.getPlayerList().getPlayer(message.getRecipientId());
        int cap = onlineRecipient == null ? recipientBox.getLastKnownInboxSoftCap() : inboxSoftCap(onlineRecipient);
        recipientBox.updateInboxSoftCap(cap);
        long now = System.currentTimeMillis();
        cleanupAndReconcile(recipientBox, cap, recipientBox.getLastKnownSentLimit(), now);
        if (visibleCount(recipientBox) < cap) {
            message.setState(MailState.VISIBLE);
            message.setVisibleSinceEpochMilli(now);
            message.setQueuedSinceEpochMilli(0L);
        } else {
            message.setState(MailState.QUEUED);
            message.setVisibleSinceEpochMilli(0L);
            message.setQueuedSinceEpochMilli(now);
        }
        recipientBox.getInbox().add(message);
        boolean receiptAdded = recipientBox.rememberDeliveryReceipt(
                message.getSource(), message.getCorrelationKey(), message.getCreatedAtEpochMilli());

        MailboxData senderBox = existingSenderBox;
        long outgoingTimestamp = 0L;
        if (message.getSenderId() != null) {
            senderBox = senderBox == null ? mailbox(message.getSenderId(), message.getSenderName()) : senderBox;
            senderBox.getSent().add(new MailSentRecord(message));
            outgoingTimestamp = message.getCreatedAtEpochMilli();
            senderBox.recordOutgoingSend(outgoingTimestamp);
        }

        try {
            // Recipient delivery is the authoritative commit. Never report a failure or refund
            // escrow after the recipient mailbox has already been durably written.
            writeSync(recipientBox);
        } catch (IOException e) {
            recipientBox.getInbox().removeIf(mail -> mail.getId().equals(message.getId()));
            if (receiptAdded) recipientBox.forgetDeliveryReceipt(message.getSource(), message.getCorrelationKey());
            if (senderBox != null) {
                senderBox.getSent().removeIf(record -> message.getId().equals(record.getMailId()));
                senderBox.removeOutgoingSend(outgoingTimestamp);
            }
            SimpleServerUtilities.LOGGER.error("Failed to commit recipient mail {}.", message.getId(), e);
            return MailOperationResult.failure("storage_failed", "The mail could not be saved safely.");
        }
        if (senderBox != null) {
            cleanupAndReconcile(senderBox, senderBox.getLastKnownInboxSoftCap(),
                    senderBox.getLastKnownSentLimit(), now);
            try {
                writeSync(senderBox);
            } catch (IOException e) {
                // Delivery already succeeded. Keep the in-memory sent record and queue a retry;
                // an unavailable sent-mail copy must never duplicate or roll back recipient mail.
                queueSave(senderBox);
                SimpleServerUtilities.LOGGER.error("Mail {} was delivered, but the sent-mail copy needs retrying.",
                        message.getId(), e);
            }
        }

        if (onlineRecipient != null && PermissionService.getBoolean(onlineRecipient, PermissionKeys.MAIL_ACCESS, true)) {
            if (message.getState() == MailState.VISIBLE) {
                onlineRecipient.sendSystemMessage(Component.literal("New mail from " + message.getSenderName()
                        + ": " + message.getSubject()));
            } else {
                onlineRecipient.sendSystemMessage(Component.literal("Your mailbox is full. New mail is waiting safely on the server."));
            }
        }
        return MailOperationResult.success("Mail sent to " + message.getRecipientName() + ".");
    }

    private synchronized MailOperationResult markRead(ServerPlayer player, String rawId) {
        MailMessage mail = visibleMail(player, rawId);
        if (mail == null) return MailOperationResult.failure("not_found", "Mail not found.");
        if (!mail.isRead()) {
            mail.setRead(true);
            updateSenderRecord(mail, record -> record.markOpened(System.currentTimeMillis()));
            queueSave(mailbox(player.getUUID(), player.getName().getString()));
        }
        return MailOperationResult.success("");
    }

    private synchronized MailOperationResult deleteMail(ServerPlayer player, String rawId) {
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        MailMessage mail = findVisible(box, rawId);
        if (mail == null) return MailOperationResult.failure("not_found", "Mail not found.");
        if (mail.hasUnclaimedAttachments()) {
            return MailOperationResult.failure("attachments", "Claim all item and money attachments before deleting this mail.");
        }
        box.getInbox().remove(mail);
        cleanupAndReconcile(box, inboxSoftCap(player), sentLimit(player), System.currentTimeMillis());
        queueSave(box);
        return MailOperationResult.success("Mail deleted.");
    }

    private synchronized MailOperationResult deleteSentMail(ServerPlayer player, String rawId) {
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        UUID id = parseUuid(rawId);
        if (id == null || !box.getSent().removeIf(record -> id.equals(record.getMailId()))) {
            return MailOperationResult.failure("not_found", "Sent mail not found.");
        }
        queueSave(box);
        return MailOperationResult.success("Sent mail deleted.");
    }

    private synchronized MailOperationResult clearInbox(ServerPlayer player) {
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        int before = box.getInbox().size();
        box.getInbox().removeIf(mail -> mail.getState() == MailState.VISIBLE
                && mail.isRead() && !mail.hasUnclaimedAttachments());
        int removed = before - box.getInbox().size();
        cleanupAndReconcile(box, inboxSoftCap(player), sentLimit(player), System.currentTimeMillis());
        queueSave(box);
        return MailOperationResult.success("Cleared " + removed
                + " read inbox mail(s). Unread mail and unclaimed attachments were kept.");
    }

    private synchronized MailOperationResult clearSent(ServerPlayer player) {
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        int removed = box.getSent().size();
        box.getSent().clear();
        queueSave(box);
        return MailOperationResult.success("Cleared " + removed + " sent mail(s).");
    }

    private synchronized MailOperationResult claimItems(ServerPlayer player, String rawId) {
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        MailMessage mail = findVisible(box, rawId);
        if (mail == null) return MailOperationResult.failure("not_found", "Mail not found.");
        List<MailItemAttachment> pending = mail.getItems().stream().filter(item -> !item.isClaimed()).toList();
        if (pending.isEmpty()) return MailOperationResult.failure("no_items", "This mail has no unclaimed items.");

        List<ItemStack> stacks = new ArrayList<>(pending.size());
        for (MailItemAttachment attachment : pending) {
            ItemStack decoded = MailItemCodec.decode(server.registryAccess(), attachment.getStack());
            if (decoded.isEmpty()) {
                SimpleServerUtilities.LOGGER.error("Mail {} contains an unreadable item attachment.", mail.getId());
                return MailOperationResult.failure("attachment_corrupt",
                        "An attachment could not be read safely. Contact an administrator.");
            }
            stacks.add(decoded);
        }

        InventoryPlan plan = planInventory(player, stacks);
        if (plan == null) return MailOperationResult.failure("inventory_full", "Make room in your inventory first.");

        applyInventory(player, plan.after());
        pending.forEach(item -> item.setClaimed(true));
        try {
            writeSync(box);
        } catch (IOException e) {
            pending.forEach(item -> item.setClaimed(false));
            applyInventory(player, plan.before());
            SimpleServerUtilities.LOGGER.error("Failed to persist item claim for mail {}.", mail.getId(), e);
            return MailOperationResult.failure("storage_failed",
                    "The attachment claim could not be saved safely; your inventory was restored.");
        }
        updateSenderRecord(mail, record -> record.markItemsClaimed(System.currentTimeMillis()));
        boolean removed = autoDeleteClaimedAttachmentMail(player, box, mail);
        if (removed) queueSave(box);
        return MailOperationResult.success(removed
                ? "Item attachments claimed and the mail was automatically deleted."
                : "Item attachments claimed.");
    }

    private synchronized MailOperationResult claimMoney(ServerPlayer player, String rawId) {
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        MailMessage mail = findVisible(box, rawId);
        if (mail == null) return MailOperationResult.failure("not_found", "Mail not found.");
        if (!mail.hasUnclaimedMoney()) return MailOperationResult.failure("no_money", "This mail has no unclaimed money.");
        EconomyResult result = SimpleServerUtilities.ECONOMY.transferTyped(player.getUUID(), player.getName().getString(),
                ESCROW_ACCOUNT_ID, player.getUUID(), mail.getMoneyMinor(), EconomyTransactionType.MAIL_ESCROW_CLAIM,
                "mail", "Claim mail money from " + mail.getSenderName(), "mail:claim-money:" + mail.getId());
        if (!result.successful() && !"duplicate".equals(result.code())) {
            return MailOperationResult.failure(result.code(), result.message());
        }
        mail.setMoneyClaimed(true);
        updateSenderRecord(mail, record -> record.markMoneyClaimed(System.currentTimeMillis()));
        boolean removed = autoDeleteClaimedAttachmentMail(player, box, mail);
        queueSave(box);
        return MailOperationResult.success("Money attachment claimed: "
                + MoneyFormat.format(mail.getMoneyMinor(), SimpleServerUtilities.ECONOMY.settings())
                + (removed ? ". The mail was automatically deleted." : "."));
    }

    private MailOperationResult claimAll(ServerPlayer player, String rawId) {
        MailOperationResult items = MailOperationResult.success("");
        synchronized (this) {
            MailMessage mail = visibleMail(player, rawId);
            if (mail == null) return MailOperationResult.failure("not_found", "Mail not found.");
            if (mail.hasUnclaimedItems()) items = claimItems(player, rawId);
            if (!items.successful()) return items;
            if (mail.hasUnclaimedMoney()) {
                MailOperationResult money = claimMoney(player, rawId);
                if (!money.successful()) return money;
            }
        }
        return MailOperationResult.success("All available attachments claimed.");
    }

    private void openCompose(ServerPlayer player, long requestId) {
        if (!Config.ENABLE_MAIL.get()
                || !PermissionService.getBoolean(player, PermissionKeys.MAIL_ACCESS, true)
                || !PermissionService.getBoolean(player, PermissionKeys.MAIL_SEND, true)) {
            sendPage(player, "inbox", 0, DEFAULT_PAGE_SIZE, requestId,
                    "You do not have permission to compose mail.", true);
            return;
        }
        int max = maxAttachments(player);
        boolean canItems = PermissionService.getBoolean(player, PermissionKeys.MAIL_SEND_ITEMS, true) && max > 0;
        boolean canMoney = PermissionService.getBoolean(player, PermissionKeys.MAIL_SEND_MONEY, true);
        String balance = SimpleServerUtilities.ECONOMY.formattedBalance(player);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new MailComposeMenu(containerId, inventory,
                        new SimpleContainer(HARD_ATTACHMENT_CAP), max, canItems, canMoney, balance),
                Component.literal("Compose Mail")
        ), buffer -> {
            buffer.writeVarInt(max); buffer.writeBoolean(canItems); buffer.writeBoolean(canMoney); buffer.writeUtf(balance, 128);
        });
    }

    private synchronized void sendPage(ServerPlayer player, String mode, int requestedPage, int pageSize,
            long requestId, String notice, boolean error) {
        if (!Config.ENABLE_MAIL.get() || !PermissionService.getBoolean(player, PermissionKeys.MAIL_ACCESS, true)) {
            PacketDistributor.sendToPlayer(player, MailDataPayload.denied(mode, requestedPage, pageSize, requestId,
                    "You have not unlocked mailbox access."));
            return;
        }
        MailboxData box = mailbox(player.getUUID(), player.getName().getString());
        int cap = inboxSoftCap(player);
        int sentLimit = sentLimit(player);
        box.updateInboxSoftCap(cap);
        box.updateSentLimit(sentLimit);
        boolean changed = cleanupAndReconcile(box, cap, sentLimit, System.currentTimeMillis());
        if (changed) queueSave(box);

        boolean sentMode = "sent".equalsIgnoreCase(mode);
        List<MailDataPayload.Entry> all = sentMode ? sentEntries(box) : inboxEntries(box);
        int safePageSize = Math.max(1, Math.min(20, pageSize));
        int pageCount = Math.max(1, (all.size() + safePageSize - 1) / safePageSize);
        int page = Math.min(Math.max(0, requestedPage), pageCount - 1);
        int from = Math.min(all.size(), page * safePageSize);
        int to = Math.min(all.size(), from + safePageSize);
        List<MailDataPayload.Entry> entries = List.copyOf(all.subList(from, to));

        PacketDistributor.sendToPlayer(player, new MailDataPayload(sentMode ? "sent" : "inbox", page,
                safePageSize, all.size(), requestId, true,
                PermissionService.getBoolean(player, PermissionKeys.MAIL_SEND, true),
                PermissionService.getBoolean(player, PermissionKeys.MAIL_SEND_ITEMS, true),
                PermissionService.getBoolean(player, PermissionKeys.MAIL_SEND_MONEY, true),
                maxAttachments(player), cap, sentLimit, visibleCount(box), queuedCount(box), unreadCount(box),
                Config.MAIL_VISIBLE_RETENTION_DAYS.get(), SimpleServerUtilities.ECONOMY.formattedBalance(player),
                notice, error, entries));
    }

    private List<MailDataPayload.Entry> inboxEntries(MailboxData box) {
        return box.getInbox().stream().filter(mail -> mail.getState() == MailState.VISIBLE)
                .sorted(Comparator.comparingLong(MailMessage::getCreatedAtEpochMilli).reversed())
                .map(mail -> new MailDataPayload.Entry(mail.getId().toString(), mail.getSenderName(), mail.getSubject(),
                        mail.getBody(), mail.getSource().name().toLowerCase(Locale.ROOT), mail.getCreatedAtEpochMilli(),
                        mail.getVisibleSinceEpochMilli(), mail.isRead(), mail.getItems().size(), mail.unclaimedItemCount(),
                        itemSummary(mail), mail.getMoneyMinor(), mail.getMoneyMinor() <= 0L ? "" :
                        MoneyFormat.format(mail.getMoneyMinor(), SimpleServerUtilities.ECONOMY.settings()),
                        mail.hasUnclaimedMoney(), mail.isRead() ? Math.max(mail.getVisibleSinceEpochMilli(), mail.getCreatedAtEpochMilli()) : 0L,
                        mail.hasUnclaimedItems() ? 0L : mail.getCreatedAtEpochMilli(),
                        mail.hasUnclaimedMoney() ? 0L : mail.getCreatedAtEpochMilli())).toList();
    }

    private List<MailDataPayload.Entry> sentEntries(MailboxData box) {
        return box.getSent().stream().sorted(Comparator.comparingLong(MailSentRecord::getSentAtEpochMilli).reversed())
                .map(record -> new MailDataPayload.Entry(record.getMailId() == null ? "" : record.getMailId().toString(),
                        record.getRecipientName(), record.getSubject(), record.getBody(), "sent",
                        record.getSentAtEpochMilli(), record.getSentAtEpochMilli(), record.isOpened(), record.getItemStackCount(),
                        record.areItemsClaimed() ? 0 : record.getItemStackCount(),
                        record.getItemStackCount() == 0 ? "" : record.getItemStackCount() + " item stack(s)",
                        record.getMoneyMinor(), record.getMoneyMinor() <= 0L ? "" :
                        MoneyFormat.format(record.getMoneyMinor(), SimpleServerUtilities.ECONOMY.settings()),
                        record.getMoneyMinor() > 0L && !record.isMoneyClaimed(), record.getOpenedAtEpochMilli(),
                        record.getItemsClaimedAtEpochMilli(), record.getMoneyClaimedAtEpochMilli())).toList();
    }

    private MailMessage createMessage(UUID id, UUID senderId, String senderName, UUID recipientId, String recipientName,
            String subject, String body, List<ItemStack> items, long moneyMinor, MailSource source, String correlationKey) {
        MailMessage message = new MailMessage();
        message.setId(id); message.setSenderId(senderId); message.setSenderName(senderName);
        message.setRecipientId(recipientId); message.setRecipientName(recipientName); message.setSubject(subject);
        message.setBody(body); message.setSource(source); message.setCreatedAtEpochMilli(System.currentTimeMillis());
        message.setMoneyMinor(moneyMinor); message.setMoneyClaimed(moneyMinor == 0L); message.setCorrelationKey(correlationKey);
        List<MailItemAttachment> attachments = new ArrayList<>();
        if (items != null) {
            for (ItemStack stack : items) {
                if (stack != null && !stack.isEmpty()) attachments.add(new MailItemAttachment(
                        MailItemCodec.encode(server.registryAccess(), stack)));
            }
        }
        if (attachments.size() > HARD_ATTACHMENT_CAP) throw new IllegalArgumentException("Too many mail attachments.");
        message.setItems(attachments); message.normalize();
        return message;
    }

    private void refundEscrow(ServerPlayer sender, UUID mailId, long moneyMinor) {
        if (moneyMinor <= 0L) return;
        EconomyResult refund = SimpleServerUtilities.ECONOMY.transferTyped(sender.getUUID(), sender.getName().getString(),
                ESCROW_ACCOUNT_ID, sender.getUUID(), moneyMinor, EconomyTransactionType.MAIL_ESCROW_REFUND,
                "mail", "Rollback failed mail", "mail:refund:" + mailId);
        if (!refund.successful() && !"duplicate".equals(refund.code())) {
            SimpleServerUtilities.LOGGER.error("Failed to refund mail escrow {} to {}: {}", mailId,
                    sender.getName().getString(), refund.message());
        }
    }

    private synchronized List<String> recipientSuggestions(ServerPlayer requester, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        Map<String, String> names = new LinkedHashMap<>();
        SimpleServerUtilities.PERMISSIONS.getKnownPlayers().stream()
                .map(value -> value.name())
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(name -> names.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
        mailboxes.values().stream().map(MailboxData::getLastKnownName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(name -> names.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
        if (server != null) {
            server.getPlayerList().getPlayers().stream().map(player -> player.getName().getString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(name -> names.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
        }
        String self = requester.getName().getString();
        return names.values().stream().filter(name -> !name.equalsIgnoreCase(self))
                .sorted(Comparator.comparingInt((String name) -> suggestionRank(name, query))
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .filter(name -> query.isBlank() || name.toLowerCase(Locale.ROOT).contains(query))
                .limit(256).toList();
    }

    private static int suggestionRank(String name, String query) {
        if (query == null || query.isBlank()) return 0;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals(query)) return 0;
        if (lower.startsWith(query)) return 1;
        return 2;
    }

    private void updateSenderRecord(MailMessage mail, java.util.function.Consumer<MailSentRecord> mutation) {
        if (mail == null || mail.getSenderId() == null || mutation == null) return;
        MailboxData senderBox = mailbox(mail.getSenderId(), mail.getSenderName());
        senderBox.getSent().stream().filter(record -> mail.getId().equals(record.getMailId())).findFirst()
                .ifPresent(record -> {
                    mutation.accept(record);
                    try {
                        writeSync(senderBox);
                    } catch (IOException e) {
                        queueSave(senderBox);
                        SimpleServerUtilities.LOGGER.error(
                                "Failed to persist sent-mail delivery status for {}.", mail.getId(), e);
                    }
                });
    }

    private boolean autoDeleteClaimedAttachmentMail(ServerPlayer player, MailboxData box, MailMessage mail) {
        if (mail == null || mail.hasUnclaimedAttachments()) return false;
        boolean hadAttachments = !mail.getItems().isEmpty() || mail.getMoneyMinor() > 0L;
        if (!hadAttachments) return false;
        var preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        if (!preferences.shouldAutoDeleteAttachmentMail(mail.getSource())) return false;
        boolean removed = box.getInbox().remove(mail);
        if (removed) cleanupAndReconcile(box, inboxSoftCap(player), sentLimit(player), System.currentTimeMillis());
        return removed;
    }

    private Recipient resolveRecipient(String raw) {
        if (server == null || raw == null || raw.isBlank()) return null;
        String name = raw.trim();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new Recipient(online.getUUID(), online.getName().getString());
        UUID id = SimpleServerUtilities.PERMISSIONS.findKnownPlayerId(name);
        if (id != null) {
            var data = SimpleServerUtilities.PERMISSIONS.getPlayerData(id);
            return new Recipient(id, data == null || data.getLastKnownName().isBlank() ? name : data.getLastKnownName());
        }
        MailboxData knownMailbox = mailboxes.values().stream()
                .filter(box -> box.getPlayerId() != null && box.getLastKnownName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (knownMailbox != null) return new Recipient(knownMailbox.getPlayerId(), knownMailbox.getLastKnownName());
        Optional<EconomyAccount> account = SimpleServerUtilities.ECONOMY.findAccountByName(server, name);
        return account.map(value -> new Recipient(value.getPlayerId(), value.getLastKnownName())).orElse(null);
    }

    private boolean hasCorrelation(UUID recipientId, MailSource source, String correlationKey) {
        if (recipientId == null || correlationKey == null || correlationKey.isBlank()) return false;
        MailboxData box = mailboxes.get(recipientId);
        if (box == null) return false;
        String key = correlationKey.trim();
        MailSource safeSource = source == null ? MailSource.SYSTEM : source;
        return box.hasDeliveryReceipt(safeSource, key) || box.getInbox().stream().anyMatch(mail ->
                safeSource == mail.getSource() && key.equals(mail.getCorrelationKey()));
    }

    private MailboxData mailbox(UUID id, String name) {
        MailboxData result = mailboxes.computeIfAbsent(id, key -> new MailboxData(key, name));
        result.updateName(name); result.normalize(id); return result;
    }

    private MailMessage visibleMail(ServerPlayer player, String rawId) {
        return findVisible(mailbox(player.getUUID(), player.getName().getString()), rawId);
    }

    private MailMessage findVisible(MailboxData box, String rawId) {
        UUID id = parseUuid(rawId);
        if (id == null) return null;
        return box.getInbox().stream().filter(mail -> id.equals(mail.getId()) && mail.getState() == MailState.VISIBLE)
                .findFirst().orElse(null);
    }

    private boolean cleanupAndReconcile(MailboxData box, int cap, int sentLimit, long now) {
        boolean changed = false;
        long retention = Duration.ofDays(Math.max(1, Config.MAIL_VISIBLE_RETENTION_DAYS.get())).toMillis();
        var iterator = box.getInbox().iterator();
        while (iterator.hasNext()) {
            MailMessage mail = iterator.next();
            if (mail.getState() != MailState.VISIBLE || mail.getVisibleSinceEpochMilli() <= 0L
                    || now - mail.getVisibleSinceEpochMilli() < retention) continue;
            if (mail.hasUnclaimedAttachments()) {
                mail.setState(MailState.QUEUED); mail.setVisibleSinceEpochMilli(0L);
                mail.setQueuedSinceEpochMilli(now); mail.setRead(false);
            } else {
                iterator.remove();
            }
            changed = true;
        }

        List<MailMessage> visible = box.getInbox().stream().filter(mail -> mail.getState() == MailState.VISIBLE)
                .sorted(Comparator.comparingLong(MailMessage::getVisibleSinceEpochMilli)).toList();
        for (int i = cap; i < visible.size(); i++) {
            MailMessage mail = visible.get(i); mail.setState(MailState.QUEUED); mail.setVisibleSinceEpochMilli(0L);
            mail.setQueuedSinceEpochMilli(now); changed = true;
        }
        int available = Math.max(0, cap - visibleCount(box));
        List<MailMessage> queued = box.getInbox().stream().filter(mail -> mail.getState() == MailState.QUEUED)
                .sorted(Comparator.comparingLong(MailMessage::getQueuedSinceEpochMilli)
                        .thenComparingLong(MailMessage::getCreatedAtEpochMilli)).toList();
        for (int i = 0; i < Math.min(available, queued.size()); i++) {
            MailMessage mail = queued.get(i); mail.setState(MailState.VISIBLE); mail.setVisibleSinceEpochMilli(now);
            mail.setQueuedSinceEpochMilli(0L); mail.setRead(false); changed = true;
        }
        box.pruneOutgoingHistory(now);
        List<MailSentRecord> sentNewestFirst = box.getSent().stream()
                .sorted(Comparator.comparingLong(MailSentRecord::getSentAtEpochMilli).reversed()).toList();
        if (sentNewestFirst.size() > Math.max(0, sentLimit)) {
            java.util.Set<UUID> keep = sentNewestFirst.stream().limit(Math.max(0, sentLimit))
                    .map(MailSentRecord::getMailId).filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            changed |= box.getSent().removeIf(record -> record.getMailId() == null || !keep.contains(record.getMailId()));
        }
        return changed;
    }

    private String itemSummary(MailMessage mail) {
        List<String> names = new ArrayList<>();
        for (MailItemAttachment attachment : mail.getItems()) {
            ItemStack stack = MailItemCodec.decode(server.registryAccess(), attachment.getStack());
            if (stack.isEmpty()) continue;
            names.add(stack.getCount() + "× " + stack.getHoverName().getString()
                    + (attachment.isClaimed() ? " (claimed)" : ""));
        }
        return String.join(", ", names);
    }

    private static InventoryPlan planInventory(ServerPlayer player, List<ItemStack> incoming) {
        final int storageSlots = 36; // 27 main-inventory slots plus the 9-slot hotbar.
        List<ItemStack> before = new ArrayList<>(storageSlots);
        List<ItemStack> after = new ArrayList<>(storageSlots);
        for (int i = 0; i < storageSlots; i++) {
            ItemStack current = player.getInventory().getItem(i).copy();
            before.add(current.copy());
            after.add(current);
        }
        for (ItemStack incomingStack : incoming) {
            ItemStack remaining = incomingStack.copy();
            for (ItemStack existing : after) {
                if (remaining.isEmpty()) break;
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                    int move = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                    if (move > 0) { existing.grow(move); remaining.shrink(move); }
                }
            }
            while (!remaining.isEmpty()) {
                int empty = -1;
                for (int i = 0; i < after.size(); i++) {
                    if (after.get(i).isEmpty()) { empty = i; break; }
                }
                if (empty < 0) return null;
                int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                after.set(empty, remaining.copyWithCount(move));
                remaining.shrink(move);
            }
        }
        return new InventoryPlan(List.copyOf(before), List.copyOf(after));
    }

    private static void applyInventory(ServerPlayer player, List<ItemStack> contents) {
        for (int i = 0; i < contents.size(); i++) {
            player.getInventory().setItem(i, contents.get(i).copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private int inboxSoftCap(ServerPlayer player) {
        return Math.max(1, PermissionService.getInt(player, PermissionKeys.MAIL_INBOX_SOFT_CAP, DEFAULT_SOFT_CAP));
    }

    private int maxAttachments(ServerPlayer player) {
        return Math.max(0, Math.min(HARD_ATTACHMENT_CAP,
                PermissionService.getInt(player, PermissionKeys.MAIL_MAX_ATTACHMENTS, 1)));
    }

    private int sentLimit(ServerPlayer player) {
        return Math.max(0, PermissionService.getInt(player, PermissionKeys.MAIL_SENT_LIMIT, 20));
    }

    private static int visibleCount(MailboxData box) {
        return (int) box.getInbox().stream().filter(mail -> mail.getState() == MailState.VISIBLE).count();
    }
    private static int queuedCount(MailboxData box) {
        return (int) box.getInbox().stream().filter(mail -> mail.getState() == MailState.QUEUED).count();
    }
    private static int unreadCount(MailboxData box) {
        return (int) box.getInbox().stream().filter(mail -> mail.getState() == MailState.VISIBLE && !mail.isRead()).count();
    }

    private void queueSave(MailboxData box) {
        if (mailboxFolder != null) SimpleServerUtilities.STORAGE.queueJson(gson, file(box.getPlayerId()), box);
    }
    private void writeSync(MailboxData box) throws IOException {
        if (mailboxFolder == null) throw new IOException("Mail storage is not initialized.");
        JsonStorage.write(gson, file(box.getPlayerId()), box);
    }
    private Path file(UUID playerId) { return StoragePaths.jsonFile(mailboxFolder, playerId.toString()); }
    private static UUID parseUuid(String raw) {
        try { return raw == null ? null : UUID.fromString(raw.trim()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private record InventoryPlan(List<ItemStack> before, List<ItemStack> after) {}
    private record Recipient(UUID id, String name) {}
}
