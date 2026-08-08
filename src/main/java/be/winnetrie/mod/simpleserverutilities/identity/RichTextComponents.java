package be.winnetrie.mod.simpleserverutilities.identity;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.settings.MinecraftColorPalette;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Converts SSU's mail/floating-text formatting format into styled chat/name components. */
public final class RichTextComponents {
    private RichTextComponents() {
    }

    public static MutableComponent fromEncoded(String encoded) {
        HologramRichTextDocument document = new HologramRichTextDocument(encoded, RichTextComponents::normalize, 256);
        MutableComponent result = Component.empty();
        for (SsuRichTextDocument.Segment segment : document.segments(0, document.plainText().length())) {
            SsuRichTextDocument.CharacterStyle style = segment.style();
            int color = style.colorIndex() >= 0
                    ? MinecraftColorPalette.COLORS.get(style.colorIndex()).argb()
                    : MinecraftColorPalette.COLORS.getFirst().argb();
            result.append(Component.literal(segment.text()).withStyle(value -> value
                    .withColor(color & 0x00FFFFFF)
                    .withBold(style.bold())
                    .withItalic(style.italic())
                    .withUnderlined(style.underlined())
                    .withStrikethrough(style.strikethrough())));
        }
        return result;
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.replace('\n', ' ').replace('\r', ' ');
        if (value.length() > 256) value = value.substring(0, 256);
        return value;
    }
}
