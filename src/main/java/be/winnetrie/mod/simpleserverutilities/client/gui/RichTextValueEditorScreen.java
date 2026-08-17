package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument.Format;
import be.winnetrie.mod.simpleserverutilities.mixin.MultiLineEditBoxAccessor;
import be.winnetrie.mod.simpleserverutilities.mixin.MultilineTextFieldAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Reusable rich-text editor using SSU's fixed 16-colour + B/I/U/S document model. */
public final class RichTextValueEditorScreen extends Screen {
    private static final int W = 620, H = 270;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;

    private final Screen parent;
    private final String heading;
    private final String help;
    private final Consumer<String> saver;
    private final UnaryOperator<String> normalizer;
    private final int editorCharacterLimit;
    private final int storedCharacterLimit;
    private final int lineLimit;
    private SsuRichTextDocument document;
    private MultiLineEditBox box;
    private int rememberedStart = -1, rememberedEnd = -1;
    private String notice = "";

    public RichTextValueEditorScreen(Screen parent, String heading, String help, String initial, Consumer<String> saver) {
        this(parent, heading, help, initial, HologramRichText::normalize,
                HologramRichText.MAX_STORED_CHARACTERS, HologramRichText.MAX_STORED_CHARACTERS, 16, saver);
    }

    /** Feature-specific variant used by mail/support style editors without hologram line wrapping. */
    public RichTextValueEditorScreen(
            Screen parent,
            String heading,
            String help,
            String initial,
            UnaryOperator<String> normalizer,
            int editorCharacterLimit,
            int storedCharacterLimit,
            int lineLimit,
            Consumer<String> saver
    ) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.help = help == null ? "" : help;
        this.saver = saver;
        this.normalizer = normalizer == null ? value -> value == null ? "" : value : normalizer;
        this.editorCharacterLimit = Math.max(16, editorCharacterLimit);
        this.storedCharacterLimit = Math.max(this.editorCharacterLimit, storedCharacterLimit);
        this.lineLimit = Math.max(1, lineLimit);
        this.document = new SsuRichTextDocument(this.normalizer.apply(initial), this.normalizer, this.storedCharacterLimit);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        box = MultiLineEditBox.builder()
                .setX(x + 16).setY(y + 54)
                .setPlaceholder(Component.literal("Enter formatted text…"))
                .setShowBackground(true).setShowDecorations(true)
                .build(font, W - 32, 116, Component.literal(heading));
        box.setCharacterLimit(editorCharacterLimit);
        box.setLineLimit(lineLimit);
        box.setValue(document.plainText());
        box.setValueListener(value -> {
            rememberedStart = rememberedEnd = -1;
            document.updatePlainText(value);
        });
        addRenderableWidget(box);

        int toolbarY = y + 178;
        addRenderableWidget(Button.builder(Component.literal("B"), button -> apply(Format.BOLD)).bounds(x + 16, toolbarY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("I"), button -> apply(Format.ITALIC)).bounds(x + 46, toolbarY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("U"), button -> apply(Format.UNDERLINED)).bounds(x + 76, toolbarY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("S"), button -> apply(Format.STRIKETHROUGH)).bounds(x + 106, toolbarY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear style"), button -> clear()).bounds(x + 138, toolbarY, 88, 20).build());

        int paletteX = x + 240;
        for (int index = 0; index < 16; index++) {
            int colorIndex = index;
            int column = index % 8;
            int row = index / 8;
            addRenderableWidget(RichTextPalette.button(paletteX + column * 20, toolbarY + 1 + row * 20, 18, index,
                    button -> color(colorIndex)));
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(x + 16, y + H - 28, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Use text"), button -> {
            saver.accept(normalizer.apply(document.encode()));
            if (minecraft != null) minecraft.setScreenAndShow(parent);
        }).bounds(x + W - 102, y + H - 28, 86, 20).build());

        RichTextEditBoxRenderer.register(box, () -> document, () -> 0xFFFFFFFF,
                RichTextPalette::argb,
                Component.literal("Enter formatted text…"));
    }

    private void apply(Format format) {
        int[] range = selection();
        if (range == null) return;
        document.toggle(range[0], range[1], format);
        restore(range);
    }

    private void color(int index) {
        int[] range = selection();
        if (range == null) return;
        document.setColor(range[0], range[1], index);
        restore(range);
    }

    private void clear() {
        int[] range = selection();
        if (range == null) return;
        document.clear(range[0], range[1]);
        restore(range);
    }

    private int[] selection() {
        remember();
        if (rememberedStart < 0 || rememberedEnd <= rememberedStart) {
            notice = "Select text first.";
            setFocused(box);
            return null;
        }
        return new int[]{rememberedStart, rememberedEnd};
    }

    private void remember() {
        if (box == null) return;
        var access = access();
        int length = document.plainText().length();
        int cursor = Math.max(0, Math.min(length, access.ssu$getCursor()));
        int selection = Math.max(0, Math.min(length, access.ssu$getSelectCursor()));
        if (cursor != selection) {
            rememberedStart = Math.min(cursor, selection);
            rememberedEnd = Math.max(cursor, selection);
        }
    }

    private void restore(int[] range) {
        setFocused(box);
        var access = access();
        access.ssu$setSelectCursor(range[0]);
        access.ssu$setCursor(range[1]);
        rememberedStart = range[0];
        rememberedEnd = range[1];
    }

    private MultilineTextFieldAccessor access() {
        MultilineTextField field = ((MultiLineEditBoxAccessor) (Object) box).ssu$getTextField();
        return (MultilineTextFieldAccessor) (Object) field;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        remember();
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(graphics, this, 0xA5000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, heading, x + 16, y + 14, TEXT, true);
        graphics.text(font, help, x + 16, y + 31, MUTED, false);
        if (!notice.isBlank()) graphics.text(font, notice, x + 102, y + H - 23, 0xFFFFB86B, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        RichTextEditBoxRenderer.unregister(box);
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

}
