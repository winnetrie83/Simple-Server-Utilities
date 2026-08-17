package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.mail.MailRichText;
import be.winnetrie.mod.simpleserverutilities.network.MailActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Standalone mailbox browser. All mail state and actions remain server-authoritative.
 */
public final class MailScreen extends Screen {
    private static final int PANEL = 0xF0141920;
    private static final int BORDER = 0xFF596B79;
    private static final int ROW = 0xB025303A;
    private static final int ROW_SELECTED = 0xD03C5364;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFBE72;
    private static final int ERROR = 0xFFFF8585;
    private static final int PAGE_SIZE = 6;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private MailDataPayload data;
    private int selectedIndex;
    private long nextRequestId;
    private final List<RowBounds> rows = new ArrayList<>();
    private final Set<String> requestedReadIds = new HashSet<>();
    private String armedClearMode = "";
    private long armedClearUntil;

    public MailScreen(MailDataPayload initial) {
        super(Component.literal("Mailbox"));
        this.data = initial;
        this.nextRequestId = Math.max(1L, initial.requestId() + 1L);
        this.selectedIndex = initial.entries().isEmpty() ? -1 : 0;
    }

    public void acceptData(MailDataPayload updated) {
        if (updated == null || updated.requestId() < data.requestId()) return;
        String selectedId = selected() == null ? "" : selected().id();
        this.data = updated;
        this.nextRequestId = Math.max(nextRequestId, updated.requestId() + 1L);
        this.selectedIndex = findEntry(selectedId);
        if (selectedIndex < 0 && !updated.entries().isEmpty()) selectedIndex = 0;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rows.clear();
        Layout l = layout();
        int toolbarY = l.top() + 34;

        Button inbox = Button.builder(Component.literal("Inbox"), ignored -> request("inbox", 0))
                .bounds(l.left() + 10, toolbarY, 70, 20).build();
        inbox.active = !"inbox".equals(data.mode());
        addRenderableWidget(inbox);

        Button sent = Button.builder(Component.literal("Sent"), ignored -> request("sent", 0))
                .bounds(l.left() + 84, toolbarY, 70, 20).build();
        sent.active = !"sent".equals(data.mode());
        addRenderableWidget(sent);

        Button compose = Button.builder(Component.literal("Compose"), ignored -> action("open_compose", ""))
                .bounds(l.left() + 160, toolbarY, 82, 20).build();
        compose.active = data.accessAllowed() && data.canSend();
        addRenderableWidget(compose);

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> request(data.mode(), data.pageIndex()))
                .bounds(l.right() - 164, toolbarY, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(l.right() - 84, toolbarY, 74, 20).build());

        int listHeight = l.listBottom() - l.listTop();
        int rowHeight = Math.max(30, listHeight / PAGE_SIZE);
        for (int i = 0; i < data.entries().size(); i++) {
            int y = l.listTop() + i * rowHeight;
            rows.add(new RowBounds(i, l.left() + 8, y, l.listWidth() - 16, rowHeight - 3));
        }

        int footer = l.bottom() - 27;
        Button previous = Button.builder(Component.literal("< Previous"), ignored -> request(data.mode(), data.pageIndex() - 1))
                .bounds(l.left() + 10, footer, 86, 20).build();
        previous.active = data.pageIndex() > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal("Next >"), ignored -> request(data.mode(), data.pageIndex() + 1))
                .bounds(l.left() + 101, footer, 70, 20).build();
        next.active = data.pageIndex() + 1 < pageCount();
        addRenderableWidget(next);

        String clearLabel = isClearArmed() ? "Confirm clear " + data.mode() : "Clear " + data.mode();
        Button clear = Button.builder(Component.literal(clearLabel), ignored -> clearCurrentMode())
                .bounds(l.left() + 178, footer, 118, 20).build();
        clear.active = data.totalEntries() > 0;
        addRenderableWidget(clear);

        MailDataPayload.Entry selected = selected();
        boolean inboxMode = "inbox".equals(data.mode()) && selected != null;
        int detailButtonY = l.detailBottom() - 45;
        if (inboxMode) {
            int gap = 4;
            int buttonWidth = Math.max(54, (l.detailRight() - l.detailLeft() - 20 - gap) / 2);
            int leftButton = l.detailLeft() + 8;
            int rightButton = leftButton + buttonWidth + gap;
            Button claimItems = Button.builder(Component.literal("Claim items"), ignored -> action("claim_items", selected.id()))
                    .bounds(leftButton, detailButtonY, buttonWidth, 20).build();
            claimItems.active = selected.unclaimedItemCount() > 0;
            addRenderableWidget(claimItems);
            Button claimMoney = Button.builder(Component.literal("Claim money"), ignored -> action("claim_money", selected.id()))
                    .bounds(rightButton, detailButtonY, buttonWidth, 20).build();
            claimMoney.active = selected.moneyUnclaimed();
            addRenderableWidget(claimMoney);
            Button claimAll = Button.builder(Component.literal("Claim all"), ignored -> action("claim_all", selected.id()))
                    .bounds(leftButton, detailButtonY + 22, buttonWidth, 20).build();
            claimAll.active = selected.unclaimedItemCount() > 0 || selected.moneyUnclaimed();
            addRenderableWidget(claimAll);
            Button delete = Button.builder(Component.literal("Delete"), ignored -> action("delete", selected.id()))
                    .bounds(rightButton, detailButtonY + 22, buttonWidth, 20).build();
            delete.active = selected.unclaimedItemCount() == 0 && !selected.moneyUnclaimed();
            addRenderableWidget(delete);
        }
        if ("sent".equals(data.mode()) && selected != null) {
            addRenderableWidget(Button.builder(Component.literal("Delete sent mail"),
                            ignored -> action("delete_sent", selected.id()))
                    .bounds(l.detailRight() - 130, l.detailBottom() - 23, 122, 20).build());
        }
        markSelectedReadOnce();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000);
        g.fill(l.left(), l.top(), l.right(), l.bottom(), PANEL);
        g.renderOutline(l.left(), l.top(), l.width(), l.height(), BORDER);
        g.drawString(font, "Mailbox", l.left() + 10, l.top() + 10, TEXT, false);
        String summary = "sent".equals(data.mode())
                ? "Saved sent mail " + data.totalEntries() + "/" + data.sentLimit()
                : "Visible " + data.visibleCount() + "/" + data.inboxSoftCap()
                        + "  •  Unread " + data.unreadCount() + "  •  Waiting " + data.queuedCount();
        g.drawString(font, summary, l.left() + 78, l.top() + 10, MUTED, false);

        if (!data.accessAllowed()) {
            g.drawString(font, data.notice().isBlank() ? "Mailbox access is locked." : data.notice(),
                    l.left() + 20, l.top() + 85, ERROR, false);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        if (data.queuedCount() > 0) {
            g.fill(l.left() + 8, l.top() + 59, l.right() - 8, l.top() + 79, 0x803E311E);
            g.renderOutline(l.left() + 8, l.top() + 59, l.width() - 16, 20, WARNING);
            g.drawString(font, "Mailbox full: " + data.queuedCount()
                    + " mail(s) are stored safely and will appear when space becomes available.",
                    l.left() + 14, l.top() + 65, WARNING, false);
        } else {
            g.drawString(font, "Visible mail expires after " + data.retentionDays()
                    + " days. Queued mail does not expire before it becomes visible.",
                    l.left() + 10, l.top() + 65, MUTED, false);
        }

        g.fill(l.left() + 6, l.listTop() - 3, l.left() + l.listWidth() - 3, l.listBottom(), 0x5011181E);
        g.renderOutline(l.left() + 6, l.listTop() - 3, l.listWidth() - 9, l.listBottom() - l.listTop() + 3, BORDER);
        if (data.entries().isEmpty()) {
            g.drawString(font, "No " + ("sent".equals(data.mode()) ? "sent mail" : "visible mail") + ".",
                    l.left() + 18, l.listTop() + 12, MUTED, false);
        }
        for (RowBounds row : rows) {
            MailDataPayload.Entry entry = data.entries().get(row.index());
            g.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
                    row.index() == selectedIndex ? ROW_SELECTED : ROW);
            int color = !entry.read() && "inbox".equals(data.mode()) ? GOOD : TEXT;
            String prefix = !entry.read() && "inbox".equals(data.mode()) ? "● " : "";
            g.drawString(font, trim(prefix + entry.subject(), 34), row.x() + 6, row.y() + 5, color, false);
            g.drawString(font, trim(("sent".equals(data.mode()) ? "To: " : "From: ") + entry.otherParty(), 29),
                    row.x() + 6, row.y() + 17, MUTED, false);
            String flags;
            if ("sent".equals(data.mode())) {
                flags = entry.openedAt() > 0L ? "Opened" : "Not opened";
                if (entry.itemStackCount() > 0) flags += entry.itemsClaimedAt() > 0L ? "  • Items claimed" : "  • Items pending";
                if (entry.moneyMinor() > 0L) flags += entry.moneyClaimedAt() > 0L ? "  • Money claimed" : "  • Money pending";
            } else {
                flags = (entry.unclaimedItemCount() > 0 ? "Items " + entry.unclaimedItemCount() + "  " : "")
                        + (entry.moneyUnclaimed() ? entry.formattedMoney() : "");
            }
            if (!flags.isBlank()) g.drawString(font, trim(flags, 34), row.x() + 6, row.y() + 29,
                    "sent".equals(data.mode()) && entry.openedAt() > 0L ? GOOD : WARNING, false);
        }

        drawDetail(g, l);
        int pageX = Math.min(l.left() + 304, l.right() - 84);
        g.drawString(font, "Page " + (data.pageIndex() + 1) + "/" + pageCount(), pageX, l.bottom() - 21, MUTED, false);
        if (!data.notice().isBlank()) {
            int noticeX = Math.max(l.left() + 304, l.detailLeft() + 8);
            int noticeWidth = Math.max(120, l.right() - noticeX - 10);
            List<FormattedCharSequence> noticeLines = font.split(Component.literal(data.notice()), noticeWidth);
            int shown = Math.min(2, noticeLines.size());
            int noticeY = l.bottom() - 35;
            for (int line = 0; line < shown; line++) {
                g.drawString(font, noticeLines.get(line), noticeX, noticeY + line * 11, data.error() ? ERROR : GOOD, false);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawDetail(GuiGraphics g, Layout l) {
        g.fill(l.detailLeft(), l.listTop() - 3, l.detailRight(), l.detailBottom(), 0x5011181E);
        g.renderOutline(l.detailLeft(), l.listTop() - 3, l.detailRight() - l.detailLeft(), l.detailBottom() - l.listTop() + 3, BORDER);
        MailDataPayload.Entry entry = selected();
        if (entry == null) {
            g.drawString(font, "Select a mail to read it.", l.detailLeft() + 12, l.listTop() + 12, MUTED, false);
            return;
        }
        int x = l.detailLeft() + 10;
        int y = l.listTop() + 5;
        g.drawString(font, trim(entry.subject(), 56), x, y, TEXT, false);
        g.drawString(font, ("sent".equals(data.mode()) ? "To " : "From ") + entry.otherParty()
                + " • " + formatDate(entry.createdAt()), x, y + 14, MUTED, false);
        int bodyY = y + 34;
        String encodedBody = entry.body();
        String visibleBody = MailRichText.plainText(encodedBody);
        Component bodyComponent;
        if (visibleBody.isBlank()) {
            bodyComponent = Component.literal("(No message)");
        } else {
            HologramRichTextDocument document = new HologramRichTextDocument(
                    encodedBody, MailRichText::normalize, MailRichText.MAX_STORED_CHARACTERS);
            bodyComponent = RichTextEditBoxRenderer.component(
                    document, 0, document.plainText().length(), TEXT);
        }
        List<FormattedCharSequence> bodyLines = font.split(bodyComponent,
                Math.max(120, l.detailRight() - x - 12));
        int maxBodyLines = Math.max(2, (l.detailBottom() - bodyY - 108) / 10);
        for (int i = 0; i < Math.min(bodyLines.size(), maxBodyLines); i++) {
            g.drawString(font, bodyLines.get(i), x, bodyY + i * 10, TEXT, false);
        }
        int attachmentsY = l.detailBottom() - 100;
        if ("sent".equals(data.mode())) {
            g.drawString(font, "Opened: " + statusDate(entry.openedAt(), "No"), x, attachmentsY - 34,
                    entry.openedAt() > 0L ? GOOD : WARNING, false);
            if (entry.itemStackCount() > 0) {
                g.drawString(font, "Items claimed: " + statusDate(entry.itemsClaimedAt(), "No"), x, attachmentsY - 22,
                        entry.itemsClaimedAt() > 0L ? GOOD : WARNING, false);
            }
            if (entry.moneyMinor() > 0L) {
                g.drawString(font, "Money claimed: " + statusDate(entry.moneyClaimedAt(), "No"), x, attachmentsY - 10,
                        entry.moneyClaimedAt() > 0L ? GOOD : WARNING, false);
            }
        }
        if (entry.itemStackCount() > 0) {
            g.drawString(font, trim("Items: " + entry.itemSummary(), 67), x, attachmentsY,
                    entry.unclaimedItemCount() > 0 ? WARNING : MUTED, false);
        }
        if (entry.moneyMinor() > 0L) {
            g.drawString(font, "Money: " + entry.formattedMoney()
                    + (entry.moneyUnclaimed() ? " (unclaimed)" : " (claimed)"),
                    x, attachmentsY + 12, entry.moneyUnclaimed() ? WARNING : MUTED, false);
        }
    }

    private void markSelectedReadOnce() {
        MailDataPayload.Entry entry = selected();
        if (entry != null && "inbox".equals(data.mode()) && !entry.read()
                && requestedReadIds.add(entry.id())) {
            action("mark_read", entry.id());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (RowBounds row : rows) {
            if (row.contains((int) mouseX, (int) mouseY)) {
                selectedIndex = row.index();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isClearArmed() {
        return data.mode().equals(armedClearMode) && System.currentTimeMillis() <= armedClearUntil;
    }

    private void clearCurrentMode() {
        if (!isClearArmed()) {
            armedClearMode = data.mode();
            armedClearUntil = System.currentTimeMillis() + 5_000L;
            rebuildWidgets();
            return;
        }
        String action = "sent".equals(data.mode()) ? "clear_sent" : "clear_inbox";
        armedClearMode = "";
        armedClearUntil = 0L;
        action(action, "");
    }

    private void request(String mode, int page) {
        long id = nextRequestId++;
        PacketDistributor.sendToServer(new MailRequestPayload(mode, Math.max(0, page), PAGE_SIZE, id));
    }

    private void action(String action, String mailId) {
        long id = nextRequestId++;
        PacketDistributor.sendToServer(new MailActionPayload(action, mailId, data.mode(), data.pageIndex(), id));
    }

    private MailDataPayload.Entry selected() {
        return selectedIndex >= 0 && selectedIndex < data.entries().size() ? data.entries().get(selectedIndex) : null;
    }

    private int findEntry(String id) {
        if (id == null || id.isBlank()) return -1;
        for (int i = 0; i < data.entries().size(); i++) if (id.equals(data.entries().get(i).id())) return i;
        return -1;
    }

    private int pageCount() {
        return Math.max(1, (data.totalEntries() + data.pageSize() - 1) / data.pageSize());
    }

    private Layout layout() {
        int panelWidth = Math.min(675, Math.max(340, width - 16));
        int panelHeight = Math.min(375, Math.max(300, height - 16));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int listTop = top + 86;
        int listBottom = top + panelHeight - 38;
        int listWidth = Math.min(290, Math.max(140, panelWidth * 2 / 5));
        int detailLeft = left + listWidth + 12;
        return new Layout(left, top, panelWidth, panelHeight, listTop, listBottom, listWidth,
                detailLeft, left + panelWidth - 8, listBottom);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static String formatDate(long epoch) {
        return epoch <= 0L ? "unknown date" : DATE.format(Instant.ofEpochMilli(epoch));
    }

    private static String statusDate(long epoch, String missing) {
        return epoch <= 0L ? missing : "Yes • " + DATE.format(Instant.ofEpochMilli(epoch));
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private record RowBounds(int index, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record Layout(int left, int top, int width, int height, int listTop, int listBottom,
                          int listWidth, int detailLeft, int detailRight, int detailBottom) {
        int right() { return left + width; }
        int bottom() { return top + height; }
    }
}
