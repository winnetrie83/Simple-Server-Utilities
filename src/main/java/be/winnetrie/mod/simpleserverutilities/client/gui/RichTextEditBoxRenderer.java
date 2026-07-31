package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument.CharacterStyle;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument.Segment;
import be.winnetrie.mod.simpleserverutilities.mixin.MultiLineEditBoxAccessor;
import be.winnetrie.mod.simpleserverutilities.mixin.MultilineTextFieldAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Custom content renderer for the hologram rich-text field.
 *
 * <p>The vanilla widget remains responsible for typing, selection, mouse
 * navigation, clipboard operations and scrolling. Only its content pass is
 * replaced, allowing the editor to display real colors and styles without ever
 * exposing the underlying formatting codes.</p>
 */
public final class RichTextEditBoxRenderer {
    private static final int LINE_HEIGHT = 9;
    private static final int SELECTION_COLOR = 0xFF2F72D6;
    private static final int CURSOR_COLOR = 0xFFD0D0D0;
    private static final int PLACEHOLDER_COLOR = 0xFF707070;
    private static final Map<MultiLineEditBox, Registration> REGISTRATIONS = new WeakHashMap<>();

    private RichTextEditBoxRenderer() {
    }

    public static void register(
            MultiLineEditBox box,
            Supplier<HologramRichTextDocument> document,
            IntSupplier baseColor,
            Component placeholder
    ) {
        if (box == null || document == null || baseColor == null) return;
        REGISTRATIONS.put(box, new Registration(document, baseColor,
                placeholder == null ? Component.empty() : placeholder));
    }

    public static void unregister(MultiLineEditBox box) {
        if (box != null) REGISTRATIONS.remove(box);
    }

    /** Called from the client mixin; returns true when vanilla content was replaced. */
    public static boolean render(MultiLineEditBox box, GuiGraphicsExtractor graphics) {
        Registration registration = REGISTRATIONS.get(box);
        if (registration == null) return false;

        HologramRichTextDocument document = registration.document().get();
        if (document == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) return false;

        MultiLineEditBoxAccessor boxAccess = (MultiLineEditBoxAccessor) (Object) box;
        MultilineTextField field = boxAccess.ssu$getTextField();
        MultilineTextFieldAccessor cursorAccess = (MultilineTextFieldAccessor) (Object) field;
        String value = document.plainText();
        int cursor = Math.max(0, Math.min(value.length(), cursorAccess.ssu$getCursor()));
        int anchor = Math.max(0, Math.min(value.length(), cursorAccess.ssu$getSelectCursor()));
        int selectionStart = Math.min(cursor, anchor);
        int selectionEnd = Math.max(cursor, anchor);

        int innerPadding = AbstractTextAreaWidget.DEFAULT_TOTAL_PADDING / 2;
        int innerLeft = box.getX() + innerPadding;
        int innerTop = box.getY() + innerPadding;
        int contentTop = innerTop - (int) Math.floor(box.scrollAmount());
        int clipLeft = box.getX() + 3;
        int clipTop = box.getY() + 3;
        int clipRight = box.getRight() - box.scrollbarWidth() - 2;
        int clipBottom = box.getBottom() - 3;
        int defaultColor = normalizeColor(registration.baseColor().getAsInt());

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        if (value.isEmpty()) {
            graphics.text(minecraft.font, registration.placeholder().getVisualOrderText(),
                    innerLeft, contentTop, PLACEHOLDER_COLOR, false);
            drawCursorIfNeeded(box, graphics, innerLeft, contentTop, cursor, anchor);
            graphics.disableScissor();
            return true;
        }

        int lineStart = 0;
        int lineNumber = 0;
        while (lineStart <= value.length()) {
            int newline = value.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? value.length() : newline;
            int y = contentTop + lineNumber * LINE_HEIGHT;
            if (y + LINE_HEIGHT >= clipTop && y <= clipBottom) {
                drawSelection(graphics, minecraft, document, lineStart, lineEnd,
                        selectionStart, selectionEnd, innerLeft, y, defaultColor);
                MutableComponent component = component(document, lineStart, lineEnd, defaultColor);
                graphics.text(minecraft.font, component.getVisualOrderText(), innerLeft, y, defaultColor, false);
                if (box.isFocused() && cursor == anchor && cursor >= lineStart && cursor <= lineEnd
                        && cursorVisible()) {
                    int cursorX = innerLeft + minecraft.font.width(
                            component(document, lineStart, cursor, defaultColor));
                    graphics.fill(cursorX, y - 1, cursorX + 1, y + LINE_HEIGHT, CURSOR_COLOR);
                }
            }
            if (newline < 0) break;
            lineStart = newline + 1;
            lineNumber++;
        }
        graphics.disableScissor();
        return true;
    }

    private static void drawSelection(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            HologramRichTextDocument document,
            int lineStart,
            int lineEnd,
            int selectionStart,
            int selectionEnd,
            int x,
            int y,
            int defaultColor
    ) {
        int from = Math.max(lineStart, selectionStart);
        int to = Math.min(lineEnd, selectionEnd);
        if (from >= to) return;
        int left = x + minecraft.font.width(component(document, lineStart, from, defaultColor));
        int right = left + minecraft.font.width(component(document, from, to, defaultColor));
        graphics.fill(left, y - 1, Math.max(left + 1, right), y + LINE_HEIGHT, SELECTION_COLOR);
    }

    private static void drawCursorIfNeeded(
            MultiLineEditBox box,
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int cursor,
            int anchor
    ) {
        if (box.isFocused() && cursor == anchor && cursorVisible()) {
            graphics.fill(x, y - 1, x + 1, y + LINE_HEIGHT, CURSOR_COLOR);
        }
    }

    public static MutableComponent component(
            HologramRichTextDocument document,
            int start,
            int end,
            int baseColor
    ) {
        MutableComponent result = Component.empty();
        for (Segment segment : document.segments(start, end)) {
            CharacterStyle richStyle = segment.style();
            int resolvedColor = richStyle.colorIndex() >= 0
                    ? 0xFF000000 | HologramRichText.minecraftColorRgb(richStyle.colorIndex())
                    : normalizeColor(baseColor);
            result.append(Component.literal(segment.text()).withStyle(style -> style
                    .withColor(resolvedColor & 0xFFFFFF)
                    .withBold(richStyle.bold())
                    .withItalic(richStyle.italic())
                    .withUnderlined(richStyle.underlined())
                    .withStrikethrough(richStyle.strikethrough())));
        }
        return result;
    }

    private static int normalizeColor(int value) {
        return (value >>> 24) == 0 ? 0xFF000000 | value : value;
    }

    private static boolean cursorVisible() {
        return (System.currentTimeMillis() / 500L & 1L) == 0L;
    }

    private record Registration(
            Supplier<HologramRichTextDocument> document,
            IntSupplier baseColor,
            Component placeholder
    ) {
    }
}
