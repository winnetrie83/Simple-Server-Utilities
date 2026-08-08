package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument.Format;
import be.winnetrie.mod.simpleserverutilities.mixin.MultiLineEditBoxAccessor;
import be.winnetrie.mod.simpleserverutilities.mixin.MultilineTextFieldAccessor;
import be.winnetrie.mod.simpleserverutilities.network.RankDisplayDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.RankDisplaySavePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Rich text editor for the visible prefix of a permission rank. */
public final class RankDisplayEditorScreen extends Screen {
    private static final int W = 590, H = 350;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;

    private RankDisplayDataPayload data;
    private final Screen parent;
    private MultiLineEditBox text;
    private HologramRichTextDocument document;
    private int rememberedStart = -1, rememberedEnd = -1;
    private String notice;
    private boolean noticeError;

    public RankDisplayEditorScreen(RankDisplayDataPayload data, Screen parent) {
        super(Component.literal("Rank Prefix Editor"));
        this.data = data;
        this.parent = parent;
        this.notice = data.notice();
        this.noticeError = data.error();
        this.document = new HologramRichTextDocument(data.encodedPrefix(), RankDisplayEditorScreen::normalize, 256);
    }

    public void accept(RankDisplayDataPayload payload) {
        if (payload == null || !payload.rankName().equalsIgnoreCase(data.rankName())) return;
        data = payload;
        notice = payload.notice();
        noticeError = payload.error();
        if (!payload.error()) {
            document = new HologramRichTextDocument(payload.encodedPrefix(), RankDisplayEditorScreen::normalize, 256);
            if (text != null) text.setValue(document.plainText());
        }
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        text = MultiLineEditBox.builder().setX(x + 18).setY(y + 64)
                .setPlaceholder(Component.literal("Example: [Admin] "))
                .setShowBackground(true).setShowDecorations(true)
                .build(font, W - 36, 48, Component.literal("Rank prefix"));
        text.setCharacterLimit(96);
        text.setLineLimit(1);
        text.setValue(document.plainText());
        text.setValueListener(value -> { rememberedStart = rememberedEnd = -1; document.updatePlainText(value.replace('\n', ' ')); });
        addRenderableWidget(text);

        int toolY = y + 120;
        addRenderableWidget(Button.builder(Component.literal("B"), ignored -> apply(Format.BOLD)).bounds(x + 18, toolY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("I"), ignored -> apply(Format.ITALIC)).bounds(x + 48, toolY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("U"), ignored -> apply(Format.UNDERLINED)).bounds(x + 78, toolY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("S"), ignored -> apply(Format.STRIKETHROUGH)).bounds(x + 108, toolY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear style"), ignored -> clear()).bounds(x + 142, toolY, 88, 20).build());

        int paletteY = y + 162;
        for (int index = 0; index < 16; index++) {
            int paletteIndex = index;
            int column = index % 8;
            int row = index / 8;
            addRenderableWidget(RichTextPalette.button(
                    x + 18 + column * 26, paletteY + row * 26, 22, paletteIndex, ignored -> applyColor(paletteIndex)
            ));
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 18, y + H - 30, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save prefix"), ignored -> save())
                .bounds(x + W - 116, y + H - 30, 98, 20).build());

        RichTextEditBoxRenderer.register(text, () -> document, () -> 0xFFFFFFFF,
                RichTextPalette::argb, Component.literal("Example: [Admin] "));
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new RankDisplaySavePayload(data.rankName(), document.encode()));
        notice = "Saving…"; noticeError = false;
    }

    private void apply(Format format) {
        int[] range = selection(); if (range == null) return;
        document.toggle(range[0], range[1], format); restore(range);
        notice = "Style applied to selected text."; noticeError = false;
    }

    private void applyColor(int paletteIndex) {
        int[] range = selection(); if (range == null) return;
        document.setColor(range[0], range[1], paletteIndex); restore(range);
        notice = RichTextPalette.name(paletteIndex) + " applied."; noticeError = false;
    }

    private void clear() {
        int[] range = selection(); if (range == null) return;
        document.clear(range[0], range[1]); restore(range);
        notice = "Formatting cleared."; noticeError = false;
    }

    private int[] selection() {
        remember();
        if (rememberedStart < 0 || rememberedEnd <= rememberedStart) {
            notice = "Select part of the prefix first."; noticeError = true;
            setFocused(text); return null;
        }
        return new int[] {rememberedStart, rememberedEnd};
    }

    private void remember() {
        if (text == null) return;
        MultilineTextFieldAccessor access = cursorAccess();
        int length = document.plainText().length();
        int cursor = Math.max(0, Math.min(length, access.ssu$getCursor()));
        int anchor = Math.max(0, Math.min(length, access.ssu$getSelectCursor()));
        if (cursor != anchor) { rememberedStart = Math.min(cursor, anchor); rememberedEnd = Math.max(cursor, anchor); }
    }

    private void restore(int[] range) {
        setFocused(text);
        MultilineTextFieldAccessor access = cursorAccess();
        access.ssu$setSelectCursor(range[0]); access.ssu$setCursor(range[1]);
        rememberedStart = range[0]; rememberedEnd = range[1];
    }

    private MultilineTextFieldAccessor cursorAccess() {
        MultilineTextField field = ((MultiLineEditBoxAccessor) (Object) text).ssu$getTextField();
        return (MultilineTextFieldAccessor) (Object) field;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        remember();
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xA5000000); g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, "Rank Prefix Editor — " + data.rankName(), x + 18, y + 15, TEXT, true);
        g.text(font, "The prefix is shown before the normal player name and in chat.", x + 18, y + 31, MUTED, false);
        g.text(font, "Select text, then apply B / I / U / S or one of the fixed 16 Minecraft colors.", x + 18, y + 48, MUTED, false);
        if (!notice.isBlank()) g.text(font, trim(notice, 78), x + 100, y + H - 25, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }


    @Override public void removed() { RichTextEditBoxRenderer.unregister(text); super.removed(); }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String normalize(String raw) {
        String value = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ');
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
}
