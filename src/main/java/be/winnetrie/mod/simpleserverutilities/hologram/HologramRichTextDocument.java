package be.winnetrie.mod.simpleserverutilities.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Selection-friendly rich-text model used by the hologram editor.
 *
 * <p>The editable value contains only the characters the player can see. Legacy
 * Minecraft formatting codes are decoded into per-character style data and are
 * only re-created when the hologram is saved. This prevents control codes from
 * appearing in the editor while retaining backwards-compatible storage.</p>
 */
public final class HologramRichTextDocument {
    private String plainText;
    private final ArrayList<CharacterStyle> styles;
    private final UnaryOperator<String> normalizer;
    private final int maximumStoredCharacters;

    public HologramRichTextDocument(String encodedText) {
        this(encodedText, HologramRichText::normalize, HologramRichText.MAX_STORED_CHARACTERS);
    }

    /** Creates the same selection-friendly document with feature-specific limits. */
    public HologramRichTextDocument(
            String encodedText,
            UnaryOperator<String> normalizer,
            int maximumStoredCharacters
    ) {
        this.normalizer = normalizer == null ? value -> value == null ? "" : value : normalizer;
        this.maximumStoredCharacters = Math.max(16, maximumStoredCharacters);
        Decoded decoded = decodeNormalized(this.normalizer.apply(encodedText));
        this.plainText = decoded.plainText();
        this.styles = new ArrayList<>(decoded.styles());
    }

    public String plainText() {
        return plainText;
    }

    public CharacterStyle styleAt(int index) {
        if (index < 0 || index >= styles.size()) return CharacterStyle.DEFAULT;
        return styles.get(index);
    }

    public void updatePlainText(String newPlainText) {
        String replacement = newPlainText == null ? "" : newPlainText;
        if (Objects.equals(plainText, replacement)) return;

        int prefix = 0;
        int oldLength = plainText.length();
        int newLength = replacement.length();
        while (prefix < oldLength && prefix < newLength
                && plainText.charAt(prefix) == replacement.charAt(prefix)) {
            prefix++;
        }

        int suffix = 0;
        while (suffix < oldLength - prefix && suffix < newLength - prefix
                && plainText.charAt(oldLength - 1 - suffix) == replacement.charAt(newLength - 1 - suffix)) {
            suffix++;
        }

        CharacterStyle inherited = inheritedStyle(prefix);
        ArrayList<CharacterStyle> updated = new ArrayList<>(newLength);
        for (int index = 0; index < prefix; index++) updated.add(styles.get(index));
        int inserted = newLength - prefix - suffix;
        for (int index = 0; index < inserted; index++) updated.add(inherited);
        int oldSuffixStart = oldLength - suffix;
        for (int index = oldSuffixStart; index < oldLength; index++) updated.add(styles.get(index));

        plainText = replacement;
        styles.clear();
        styles.addAll(updated);
        ensureStyleCount();
    }

    public boolean toggle(int start, int end, Format format) {
        int from = boundedStart(start, end);
        int to = boundedEnd(start, end);
        if (from >= to) return false;

        boolean allEnabled = true;
        for (int index = from; index < to; index++) {
            if (plainText.charAt(index) == '\n') continue;
            if (!styles.get(index).has(format)) {
                allEnabled = false;
                break;
            }
        }
        boolean enabled = !allEnabled;
        for (int index = from; index < to; index++) {
            styles.set(index, styles.get(index).with(format, enabled));
        }
        return true;
    }

    public boolean setColor(int start, int end, int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex > 15) return false;
        int from = boundedStart(start, end);
        int to = boundedEnd(start, end);
        if (from >= to) return false;
        for (int index = from; index < to; index++) {
            styles.set(index, styles.get(index).withColor(paletteIndex));
        }
        return true;
    }

    public boolean clear(int start, int end) {
        int from = boundedStart(start, end);
        int to = boundedEnd(start, end);
        if (from >= to) return false;
        for (int index = from; index < to; index++) styles.set(index, CharacterStyle.DEFAULT);
        return true;
    }

    public List<Segment> segments(int start, int end) {
        int from = Math.max(0, Math.min(plainText.length(), start));
        int to = Math.max(from, Math.min(plainText.length(), end));
        if (from == to) return List.of();

        ArrayList<Segment> result = new ArrayList<>();
        int runStart = from;
        CharacterStyle runStyle = styles.get(from);
        for (int index = from + 1; index < to; index++) {
            CharacterStyle next = styles.get(index);
            if (!next.equals(runStyle)) {
                result.add(new Segment(plainText.substring(runStart, index), runStyle));
                runStart = index;
                runStyle = next;
            }
        }
        result.add(new Segment(plainText.substring(runStart, to), runStyle));
        return List.copyOf(result);
    }

    public String encode() {
        StringBuilder encoded = new StringBuilder(Math.min(maximumStoredCharacters,
                plainText.length() + plainText.length() / 4));
        CharacterStyle active = CharacterStyle.DEFAULT;
        for (int index = 0; index < plainText.length(); index++) {
            CharacterStyle wanted = styles.get(index);
            appendTransition(encoded, active, wanted);
            active = wanted;
            if (encoded.length() >= maximumStoredCharacters) break;
            encoded.append(plainText.charAt(index));
        }
        if (!active.equals(CharacterStyle.DEFAULT)
                && encoded.length() + 2 <= maximumStoredCharacters) {
            encoded.append(HologramRichText.FORMAT).append('r');
        }
        return normalizer.apply(encoded.toString());
    }

    public static Decoded decode(String encodedText) {
        return decodeNormalized(HologramRichText.normalize(encodedText));
    }

    private static Decoded decodeNormalized(String normalizedText) {
        String value = normalizedText == null ? "" : normalizedText;
        StringBuilder plain = new StringBuilder(value.length());
        ArrayList<CharacterStyle> styles = new ArrayList<>(value.length());
        CharacterStyle active = CharacterStyle.DEFAULT;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == HologramRichText.FORMAT && index + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(index + 1));
                if (HologramRichText.isFormattingCode(code)) {
                    active = applyCode(active, code);
                    index++;
                    continue;
                }
            }
            plain.append(current);
            styles.add(active);
        }
        return new Decoded(plain.toString(), List.copyOf(styles));
    }

    private CharacterStyle inheritedStyle(int insertionIndex) {
        if (styles.isEmpty()) return CharacterStyle.DEFAULT;
        if (insertionIndex > 0 && insertionIndex - 1 < styles.size()) return styles.get(insertionIndex - 1);
        if (insertionIndex < styles.size()) return styles.get(insertionIndex);
        return styles.get(styles.size() - 1);
    }

    private int boundedStart(int start, int end) {
        return Math.max(0, Math.min(plainText.length(), Math.min(start, end)));
    }

    private int boundedEnd(int start, int end) {
        return Math.max(0, Math.min(plainText.length(), Math.max(start, end)));
    }

    private void ensureStyleCount() {
        while (styles.size() < plainText.length()) styles.add(CharacterStyle.DEFAULT);
        while (styles.size() > plainText.length()) styles.remove(styles.size() - 1);
    }

    private static CharacterStyle applyCode(CharacterStyle active, char code) {
        int colorIndex = "0123456789abcdef".indexOf(code);
        if (colorIndex >= 0) return CharacterStyle.DEFAULT.withColor(colorIndex);
        return switch (code) {
            case 'l' -> active.with(Format.BOLD, true);
            case 'o' -> active.with(Format.ITALIC, true);
            case 'n' -> active.with(Format.UNDERLINED, true);
            case 'm' -> active.with(Format.STRIKETHROUGH, true);
            case 'r' -> CharacterStyle.DEFAULT;
            default -> active;
        };
    }

    private static void appendTransition(StringBuilder out, CharacterStyle active, CharacterStyle wanted) {
        if (active.equals(wanted)) return;

        boolean requiresReset = active.colorIndex() != wanted.colorIndex()
                || (active.bold() && !wanted.bold())
                || (active.italic() && !wanted.italic())
                || (active.underlined() && !wanted.underlined())
                || (active.strikethrough() && !wanted.strikethrough());
        if (requiresReset) {
            out.append(HologramRichText.FORMAT).append('r');
            appendFullStyle(out, wanted);
            return;
        }

        if (!active.bold() && wanted.bold()) out.append(HologramRichText.FORMAT).append('l');
        if (!active.italic() && wanted.italic()) out.append(HologramRichText.FORMAT).append('o');
        if (!active.underlined() && wanted.underlined()) out.append(HologramRichText.FORMAT).append('n');
        if (!active.strikethrough() && wanted.strikethrough()) out.append(HologramRichText.FORMAT).append('m');
    }

    private static void appendFullStyle(StringBuilder out, CharacterStyle style) {
        if (style.colorIndex() >= 0) {
            out.append(HologramRichText.FORMAT).append(HologramRichText.minecraftColorCode(style.colorIndex()));
        }
        if (style.bold()) out.append(HologramRichText.FORMAT).append('l');
        if (style.italic()) out.append(HologramRichText.FORMAT).append('o');
        if (style.underlined()) out.append(HologramRichText.FORMAT).append('n');
        if (style.strikethrough()) out.append(HologramRichText.FORMAT).append('m');
    }

    public enum Format {
        BOLD,
        ITALIC,
        UNDERLINED,
        STRIKETHROUGH
    }

    public record CharacterStyle(
            int colorIndex,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough
    ) {
        public static final CharacterStyle DEFAULT = new CharacterStyle(-1, false, false, false, false);

        public CharacterStyle {
            if (colorIndex < -1 || colorIndex > 15) colorIndex = -1;
        }

        public CharacterStyle withColor(int value) {
            return new CharacterStyle(value, bold, italic, underlined, strikethrough);
        }

        public CharacterStyle with(Format format, boolean enabled) {
            return switch (format) {
                case BOLD -> new CharacterStyle(colorIndex, enabled, italic, underlined, strikethrough);
                case ITALIC -> new CharacterStyle(colorIndex, bold, enabled, underlined, strikethrough);
                case UNDERLINED -> new CharacterStyle(colorIndex, bold, italic, enabled, strikethrough);
                case STRIKETHROUGH -> new CharacterStyle(colorIndex, bold, italic, underlined, enabled);
            };
        }

        public boolean has(Format format) {
            return switch (format) {
                case BOLD -> bold;
                case ITALIC -> italic;
                case UNDERLINED -> underlined;
                case STRIKETHROUGH -> strikethrough;
            };
        }
    }

    public record Segment(String text, CharacterStyle style) {
        public Segment {
            text = text == null ? "" : text;
            style = style == null ? CharacterStyle.DEFAULT : style;
        }
    }

    public record Decoded(String plainText, List<CharacterStyle> styles) {
        public Decoded {
            plainText = plainText == null ? "" : plainText;
            styles = styles == null ? List.of() : List.copyOf(styles);
        }
    }
}
