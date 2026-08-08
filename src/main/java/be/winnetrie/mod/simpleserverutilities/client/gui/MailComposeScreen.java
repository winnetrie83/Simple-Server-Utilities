package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument.Format;
import be.winnetrie.mod.simpleserverutilities.mail.MailComposeMenu;
import be.winnetrie.mod.simpleserverutilities.mail.MailRichText;
import be.winnetrie.mod.simpleserverutilities.mixin.MultiLineEditBoxAccessor;
import be.winnetrie.mod.simpleserverutilities.mixin.MultilineTextFieldAccessor;
import be.winnetrie.mod.simpleserverutilities.network.MailActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailComposeResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailComposeSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRecipientSuggestionsPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRecipientSuggestionsRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Larger inventory-backed mail composer with multiline body and player picker. */
public final class MailComposeScreen extends AbstractContainerScreen<MailComposeMenu> {
    private static final int SCREEN_WIDTH = 400;
    private static final int SCREEN_HEIGHT = 350;

    private static final int PANEL = 0xF0181E25;
    private static final int SUBPANEL = 0xB010151A;
    private static final int BORDER = 0xFF596B79;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;
    private static final int DROPDOWN_ROW = 0xFF293540;
    private static final int DROPDOWN_HOVER = 0xFF415868;

    private static final int MESSAGE_X = 16;
    private static final int MESSAGE_Y = 76;
    private static final int MESSAGE_WIDTH = 368;
    private static final int MESSAGE_HEIGHT = 108;
    private static final int BALANCE_X = 268;
    private static final int BALANCE_Y = 231;


    private EditBox recipient;
    private EditBox subject;
    private MultiLineEditBox body;
    private EditBox money;
    private Button playersButton;
    private HologramRichTextDocument richDocument;
    private boolean updatingBody;
    private int rememberedSelectionStart = -1;
    private int rememberedSelectionEnd = -1;

    private String notice = "";
    private boolean noticeError;
    private long nextRequestId = 1L;
    private long latestSuggestionRequest;
    private int suggestionDelay;
    private String suggestionQuery = "";
    private boolean playersExpanded;
    private boolean loadingPlayers;
    private int dropdownScroll;
    private List<String> suggestions = List.of();
    private final List<SuggestionBounds> suggestionBounds = new ArrayList<>();

    public MailComposeScreen(MailComposeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        this.titleLabelX = -10_000;
        this.titleLabelY = -10_000;
        this.inventoryLabelX = -10_000;
        this.inventoryLabelY = -10_000;
    }

    @Override
    protected void init() {
        super.init();

        recipient = singleLineField(leftPos + 16, topPos + 24, 266, 18, "Recipient", 64);
        subject = singleLineField(leftPos + 16, topPos + 48, 188, 18, "Subject", 96);
        money = singleLineField(leftPos + 212, topPos + 48, 172, 18, "Money", 64);
        money.setEditable(menu.canSendMoney());
        recipient.setResponder(this::recipientChanged);

        body = MultiLineEditBox.builder()
                .setX(leftPos + MESSAGE_X)
                .setY(topPos + MESSAGE_Y)
                .setPlaceholder(Component.literal("Message"))
                .setShowBackground(true)
                .setShowDecorations(true)
                .build(font, MESSAGE_WIDTH, MESSAGE_HEIGHT, Component.literal("Message"));
        body.setCharacterLimit(MailRichText.MAX_VISIBLE_CHARACTERS);
        body.setLineLimit(MailRichText.MAX_LINES);
        richDocument = new HologramRichTextDocument("", MailRichText::normalize,
                MailRichText.MAX_STORED_CHARACTERS);
        body.setValueListener(this::onBodyChanged);
        addRenderableWidget(body);

        addRenderableWidget(Button.builder(Component.literal("B"), ignored -> applySelectionFormat('l'))
                .bounds(leftPos + 16, topPos + 188, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("I"), ignored -> applySelectionFormat('o'))
                .bounds(leftPos + 44, topPos + 188, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("U"), ignored -> applySelectionFormat('n'))
                .bounds(leftPos + 72, topPos + 188, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("S"), ignored -> applySelectionFormat('m'))
                .bounds(leftPos + 100, topPos + 188, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear style"), ignored -> clearSelectionFormatting())
                .bounds(leftPos + 132, topPos + 188, 70, 20).build());
        for (int index = 0; index < 16; index++) {
            int colorIndex = index;
            int column = index % 8;
            int row = index / 8;
            addRenderableWidget(RichTextPalette.button(leftPos + 208 + column * 11, topPos + 188 + row * 11, 10, index,
                    ignored -> applySelectionColor(colorIndex)));
        }

        RichTextEditBoxRenderer.register(body, () -> richDocument, () -> TEXT,
                Component.literal("Message"));

        playersButton = Button.builder(Component.literal("Players ▼"), ignored -> togglePlayers())
                .bounds(leftPos + 290, topPos + 24, 94, 20)
                .build();
        addRenderableWidget(playersButton);

        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> backToMailbox())
                .bounds(leftPos + 16, topPos + 326, 70, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Send mail"), ignored -> send())
                .bounds(leftPos + 296, topPos + 326, 88, 20)
                .build());

        setInitialFocus(recipient);
        suggestionQuery = "";
        playersExpanded = false;
        loadingPlayers = false;
        updatePlayersButtonLabel();
    }

    private EditBox singleLineField(int x, int y, int width, int height, String hint, int maxLength) {
        EditBox box = new EditBox(font, x, y, width, height, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maxLength);
        addRenderableWidget(box);
        return box;
    }

    private void togglePlayers() {
        playersExpanded = !playersExpanded;
        updatePlayersButtonLabel();
        if (playersExpanded) {
            requestSuggestionsSoon(1);
        }
    }

    private void updatePlayersButtonLabel() {
        if (playersButton != null) {
            playersButton.setMessage(Component.literal(playersExpanded ? "Players ▲" : "Players ▼"));
        }
    }

    private void recipientChanged(String value) {
        suggestionQuery = value == null ? "" : value.trim();
        dropdownScroll = 0;
        suggestions = List.of();
        loadingPlayers = true;
        playersExpanded = true;
        updatePlayersButtonLabel();
        requestSuggestionsSoon(4);
    }

    private void requestSuggestionsSoon(int delay) {
        suggestionDelay = Math.max(1, delay);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (suggestionDelay > 0 && --suggestionDelay == 0) {
            long id = nextRequestId++;
            latestSuggestionRequest = id;
            loadingPlayers = true;
            ClientPacketDistributor.sendToServer(
                    new MailRecipientSuggestionsRequestPayload(suggestionQuery, id)
            );
        }
    }

    public void acceptSuggestions(MailRecipientSuggestionsPayload payload) {
        if (payload == null || payload.requestId() < latestSuggestionRequest) return;
        if (!safe(payload.query()).equalsIgnoreCase(suggestionQuery)) return;

        latestSuggestionRequest = payload.requestId();
        loadingPlayers = false;
        suggestions = payload.names() == null ? List.of()
                : payload.names().stream()
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .sorted(Comparator.comparing(String::toLowerCase))
                        .toList();
        dropdownScroll = 0;
    }

    public void acceptResult(MailComposeResultPayload result) {
        if (result == null) return;
        nextRequestId = Math.max(nextRequestId, result.requestId() + 1L);
        notice = result.message();
        noticeError = !result.successful();
    }

    private void send() {
        long id = nextRequestId++;
        ClientPacketDistributor.sendToServer(new MailComposeSubmitPayload(
                menu.containerId,
                recipient.getValue(),
                subject.getValue(),
                richDocument == null ? "" : richDocument.encode(),
                money.getValue(),
                id
        ));
    }

    private void onBodyChanged(String value) {
        if (updatingBody || richDocument == null) return;
        rememberedSelectionStart = -1;
        rememberedSelectionEnd = -1;
        String safe = value == null ? "" : value.replace(HologramRichText.FORMAT, '?');
        richDocument.updatePlainText(safe);
        if (safe.equals(value)) return;
        updatingBody = true;
        body.setValue(safe);
        updatingBody = false;
    }

    private void applySelectionFormat(char formatCode) {
        Format format = switch (formatCode) {
            case 'l' -> Format.BOLD;
            case 'o' -> Format.ITALIC;
            case 'n' -> Format.UNDERLINED;
            case 'm' -> Format.STRIKETHROUGH;
            default -> null;
        };
        if (format == null) return;
        int[] range = selectedRange();
        if (range == null) return;
        richDocument.toggle(range[0], range[1], format);
        formattingApplied(range);
    }

    private void applySelectionColor(int paletteIndex) {
        int[] range = selectedRange();
        if (range == null) return;
        richDocument.setColor(range[0], range[1], paletteIndex);
        formattingApplied(range);
    }

    private void clearSelectionFormatting() {
        int[] range = selectedRange();
        if (range == null) return;
        richDocument.clear(range[0], range[1]);
        formattingApplied(range);
    }


    private int[] selectedRange() {
        rememberCurrentSelection();
        int length = richDocument == null ? 0 : richDocument.plainText().length();
        if (rememberedSelectionStart < 0 || rememberedSelectionEnd <= rememberedSelectionStart
                || rememberedSelectionEnd > length) {
            notice = "Select part of the message first, then choose a style or color.";
            noticeError = true;
            setFocused(body);
            return null;
        }
        return new int[] {rememberedSelectionStart, rememberedSelectionEnd};
    }

    private void rememberCurrentSelection() {
        if (body == null || richDocument == null) return;
        MultilineTextFieldAccessor access = cursorAccess(textField());
        int cursor = Math.max(0, Math.min(richDocument.plainText().length(), access.ssu$getCursor()));
        int anchor = Math.max(0, Math.min(richDocument.plainText().length(), access.ssu$getSelectCursor()));
        if (cursor != anchor) {
            rememberedSelectionStart = Math.min(cursor, anchor);
            rememberedSelectionEnd = Math.max(cursor, anchor);
        }
    }

    private void restoreSelection(int[] range) {
        if (range == null || body == null) return;
        setFocused(body);
        MultilineTextFieldAccessor access = cursorAccess(textField());
        access.ssu$setSelectCursor(range[0]);
        access.ssu$setCursor(range[1]);
        rememberedSelectionStart = range[0];
        rememberedSelectionEnd = range[1];
    }

    private void formattingApplied(int[] range) {
        restoreSelection(range);
        notice = "Formatting applied to the selected message text.";
        noticeError = false;
    }

    private MultilineTextField textField() {
        return ((MultiLineEditBoxAccessor) (Object) body).ssu$getTextField();
    }

    private static MultilineTextFieldAccessor cursorAccess(MultilineTextField field) {
        return (MultilineTextFieldAccessor) (Object) field;
    }

    private void backToMailbox() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.closeContainer();
        ClientPacketDistributor.sendToServer(
                new MailActionPayload("open_mailbox", "", "inbox", 0, nextRequestId++)
        );
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft != null
                && minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(event))) {
            if (recipient != null && recipient.isFocused()) return recipient.keyPressed(event);
            if (subject != null && subject.isFocused()) return subject.keyPressed(event);
            if (body != null && body.isFocused()) return body.keyPressed(event);
            if (money != null && money.isFocused()) return money.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (playersExpanded) {
            for (SuggestionBounds bound : suggestionBounds) {
                if (bound.contains(event.x(), event.y())) {
                    recipient.setValue(bound.name());
                    recipient.setCursorPosition(bound.name().length());
                    suggestionQuery = bound.name();
                    playersExpanded = false;
                    loadingPlayers = false;
                    suggestions = List.of();
                    suggestionDelay = 0;
                    updatePlayersButtonLabel();
                    setFocused(recipient);
                    return true;
                }
            }
            if (dropdownBounds().contains(event.x(), event.y())) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (playersExpanded && dropdownBounds().contains(mouseX, mouseY)) {
            int maxScroll = Math.max(0, suggestions.size() - visibleRows());
            if (scrollY < 0) {
                dropdownScroll = Math.min(maxScroll, dropdownScroll + 1);
            } else if (scrollY > 0) {
                dropdownScroll = Math.max(0, dropdownScroll - 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);

        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        g.outline(leftPos, topPos, imageWidth, imageHeight, BORDER);

        g.text(font, "Compose Mail", leftPos + 16, topPos + 10, TEXT, false);
        g.text(font, "Attachments " + menu.maxAttachments() + "/9", leftPos + 116, topPos + 10,
                menu.canSendItems() ? TEXT : MUTED, false);

        drawAttachments(g);
        drawInventoryAndHotbar(g);

        g.text(font, "Select message text, then apply B / I / U / S or one of the 16 colors",
                leftPos + 16, topPos + 212, MUTED, false);
        g.text(font, "Balance: " + menu.formattedBalance(),
                leftPos + BALANCE_X, topPos + BALANCE_Y, MUTED, false);

        if (!notice.isBlank()) {
            var lines = font.split(Component.literal(notice), 92);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                g.text(font, lines.get(i), leftPos + 16, topPos + 266 + i * 10,
                        noticeError ? ERROR : GOOD, false);
            }
        }
    }

    /** Draw the player picker after every normal widget, so it is always in front. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        rememberCurrentSelection();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (playersExpanded) drawPlayerDropdown(g, mouseX, mouseY);
    }

    private void drawAttachments(GuiGraphicsExtractor g) {
        for (int i = 0; i < MailComposeMenu.ATTACHMENT_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            boolean unlocked = i < menu.maxAttachments() && menu.canSendItems();
            int color = unlocked ? 0xFF758694 : 0xFF3C444B;
            g.fill(x, y, x + 18, y + 18, SUBPANEL);
            g.outline(x, y, 18, 18, color);
            if (!unlocked) drawLockedSlot(g, x, y);
        }
    }

    private void drawInventoryAndHotbar(GuiGraphicsExtractor g) {
        SlotPanel inventoryPanel = slotPanel(MailComposeMenu.ATTACHMENT_SLOTS, 27);
        SlotPanel hotbarPanel = slotPanel(MailComposeMenu.ATTACHMENT_SLOTS + 27, 9);

        g.text(font, "Inventory", inventoryPanel.x, inventoryPanel.y - 12, MUTED, false);
        drawSlotPanel(g, inventoryPanel);

        g.text(font, "Hotbar", hotbarPanel.x, hotbarPanel.y - 10, MUTED, false);
        drawSlotPanel(g, hotbarPanel);
    }

    private void drawSlotPanel(GuiGraphicsExtractor g, SlotPanel panel) {
        g.fill(panel.x - 4, panel.y - 4,
                panel.x + panel.width + 4, panel.y + panel.height + 4, 0x5010171E);
        g.outline(panel.x - 4, panel.y - 4,
                panel.width + 8, panel.height + 8, BORDER);

        for (int i = panel.startIndex; i < panel.startIndex + panel.count; i++) {
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, SUBPANEL);
            g.outline(x, y, 18, 18, 0xFF71818F);
        }
    }

    private SlotPanel slotPanel(int startIndex, int count) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int i = startIndex; i < startIndex + count && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            minX = Math.min(minX, leftPos + slot.x);
            minY = Math.min(minY, topPos + slot.y);
            maxX = Math.max(maxX, leftPos + slot.x + 16);
            maxY = Math.max(maxY, topPos + slot.y + 16);
        }

        return new SlotPanel(minX, minY, maxX - minX, maxY - minY, startIndex, count);
    }

    private void drawPlayerDropdown(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Rect bounds = dropdownBounds();
        suggestionBounds.clear();

        g.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0xFF121922);
        g.outline(bounds.x, bounds.y, bounds.width, bounds.height, BORDER);

        if (loadingPlayers) {
            g.text(font, "Loading...", bounds.x + 8, bounds.y + 8, MUTED, false);
            return;
        }

        if (suggestions.isEmpty()) {
            g.text(font, suggestionQuery.isBlank() ? "No known players" : "No matching player",
                    bounds.x + 8, bounds.y + 8, MUTED, false);
            return;
        }

        int shown = Math.min(visibleRows(), suggestions.size() - dropdownScroll);
        for (int i = 0; i < shown; i++) {
            String name = suggestions.get(dropdownScroll + i);
            int rowY = bounds.y + 6 + i * 14;
            boolean hovered = mouseX >= bounds.x + 4 && mouseX < bounds.x + bounds.width - 10
                    && mouseY >= rowY && mouseY < rowY + 12;

            g.fill(bounds.x + 4, rowY, bounds.x + bounds.width - 10, rowY + 12,
                    hovered ? DROPDOWN_HOVER : DROPDOWN_ROW);
            g.outline(bounds.x + 4, rowY, bounds.width - 14, 12, BORDER);
            g.text(font, trim(name, 22), bounds.x + 8, rowY + 2, TEXT, false);
            suggestionBounds.add(new SuggestionBounds(
                    name, bounds.x + 4, rowY, bounds.width - 14, 12
            ));
        }

        if (suggestions.size() > visibleRows()) {
            int trackX = bounds.x + bounds.width - 5;
            int trackY = bounds.y + 6;
            int trackHeight = bounds.height - 12;
            g.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0xFF596670);

            int maxScroll = Math.max(1, suggestions.size() - visibleRows());
            int thumbHeight = Math.max(12, trackHeight * visibleRows() / suggestions.size());
            int thumbY = trackY + (trackHeight - thumbHeight) * dropdownScroll / maxScroll;
            g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF9BA8B4);
        }
    }

    private Rect dropdownBounds() {
        return new Rect(leftPos + 234, topPos + 46, 150, 158);
    }

    private int visibleRows() {
        return 10;
    }

    private void drawLockedSlot(GuiGraphicsExtractor g, int x, int y) {
        g.outline(x + 5, y + 8, 8, 7, 0xFF8E969C);
        g.fill(x + 6, y + 9, x + 12, y + 14, 0xA05C646A);
        g.outline(x + 7, y + 4, 4, 6, 0xFF8E969C);
    }

    @Override
    public void removed() {
        RichTextEditBoxRenderer.unregister(body);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max
                ? value
                : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }



    private record SuggestionBounds(String name, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }

    private record Rect(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }

    private record SlotPanel(int x, int y, int width, int height, int startIndex, int count) {
    }
}