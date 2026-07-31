package be.winnetrie.mod.simpleserverutilities.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Legacy-format rich text helpers shared by the hologram editor, storage and renderer.
 * Formatting codes do not count towards the visible 40-character line limit.
 */
public final class HologramRichText {
    public static final int MAX_VISIBLE_CHARACTERS_PER_LINE = 40;
    public static final int MAX_LINES = 64;
    public static final int MAX_VISIBLE_CHARACTERS = 2_048;
    public static final int MAX_STORED_CHARACTERS = 8_192;
    public static final char FORMAT = '\u00A7';

    private HologramRichText() {
    }

    /** Normalizes newlines, bounds content and applies the authoritative visible line limits. */
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.replace("\\n", "\n").replace("\r\n", "\n").replace('\r', '\n');
        if (value.length() > MAX_STORED_CHARACTERS) {
            value = value.substring(0, MAX_STORED_CHARACTERS);
        }

        StringBuilder out = new StringBuilder(Math.min(value.length() + 32, MAX_STORED_CHARACTERS));
        int column = 0;
        int lineCount = 1;
        int visibleCount = 0;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == FORMAT && index + 1 < value.length() && isFormattingCode(value.charAt(index + 1))) {
                if (out.length() + 2 > MAX_STORED_CHARACTERS) break;
                out.append(current).append(Character.toLowerCase(value.charAt(++index)));
                continue;
            }
            if (current == '\n') {
                if (lineCount >= MAX_LINES) break;
                out.append('\n');
                lineCount++;
                column = 0;
                continue;
            }
            if (visibleCount >= MAX_VISIBLE_CHARACTERS) break;
            if (column >= MAX_VISIBLE_CHARACTERS_PER_LINE) {
                if (lineCount >= MAX_LINES) break;
                out.append('\n');
                lineCount++;
                column = 0;
            }
            out.append(current);
            column++;
            visibleCount++;
        }
        return out.toString();
    }

    /**
     * Editor variant that returns remapped cursor and selection indices after automatic wrapping.
     * Existing manual line breaks are preserved.
     */
    public static WrappedText wrapEditorText(String raw, int cursor, int selectionCursor) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        int boundedCursor = Math.max(0, Math.min(value.length(), cursor));
        int boundedSelection = Math.max(0, Math.min(value.length(), selectionCursor));
        int[] indexMap = new int[value.length() + 1];
        StringBuilder out = new StringBuilder(value.length() + Math.max(8, value.length() / 40));
        int column = 0;
        int lineCount = 1;
        int visibleCount = 0;

        for (int index = 0; index < value.length();) {
            indexMap[index] = out.length();
            char current = value.charAt(index);
            if (current == FORMAT && index + 1 < value.length() && isFormattingCode(value.charAt(index + 1))) {
                if (out.length() + 2 > MAX_STORED_CHARACTERS) {
                    fillRemainingMap(indexMap, index, out.length());
                    break;
                }
                out.append(current);
                index++;
                indexMap[index] = out.length();
                out.append(Character.toLowerCase(value.charAt(index)));
                index++;
                continue;
            }
            if (current == '\n') {
                if (lineCount >= MAX_LINES) {
                    fillRemainingMap(indexMap, index, out.length());
                    break;
                }
                out.append('\n');
                lineCount++;
                column = 0;
                index++;
                continue;
            }
            if (visibleCount >= MAX_VISIBLE_CHARACTERS || out.length() >= MAX_STORED_CHARACTERS) {
                fillRemainingMap(indexMap, index, out.length());
                break;
            }
            if (column >= MAX_VISIBLE_CHARACTERS_PER_LINE) {
                if (lineCount >= MAX_LINES) {
                    fillRemainingMap(indexMap, index, out.length());
                    break;
                }
                out.append('\n');
                lineCount++;
                column = 0;
            }
            out.append(current);
            column++;
            visibleCount++;
            index++;
        }
        indexMap[value.length()] = out.length();
        return new WrappedText(out.toString(), indexMap[boundedCursor], indexMap[boundedSelection]);
    }

    /** Editor wrapping for plain visible text. No hidden formatting characters are present. */
    public static WrappedText wrapPlainEditorText(String raw, int cursor, int selectionCursor) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n')
                .replace(FORMAT, '?');
        int boundedCursor = Math.max(0, Math.min(value.length(), cursor));
        int boundedSelection = Math.max(0, Math.min(value.length(), selectionCursor));
        int[] indexMap = new int[value.length() + 1];
        StringBuilder out = new StringBuilder(value.length() + Math.max(8, value.length() / 40));
        int column = 0;
        int lineCount = 1;
        int visibleCount = 0;

        for (int index = 0; index < value.length();) {
            indexMap[index] = out.length();
            char current = value.charAt(index);
            if (current == '\n') {
                if (lineCount >= MAX_LINES) {
                    fillRemainingMap(indexMap, index, out.length());
                    break;
                }
                out.append('\n');
                lineCount++;
                column = 0;
                index++;
                continue;
            }
            if (visibleCount >= MAX_VISIBLE_CHARACTERS || out.length() >= MAX_VISIBLE_CHARACTERS + MAX_LINES) {
                fillRemainingMap(indexMap, index, out.length());
                break;
            }
            if (column >= MAX_VISIBLE_CHARACTERS_PER_LINE) {
                if (lineCount >= MAX_LINES) {
                    fillRemainingMap(indexMap, index, out.length());
                    break;
                }
                out.append('\n');
                lineCount++;
                column = 0;
            }
            out.append(current);
            column++;
            visibleCount++;
            index++;
        }
        indexMap[value.length()] = out.length();
        return new WrappedText(out.toString(), indexMap[boundedCursor], indexMap[boundedSelection]);
    }

    /** Splits into rendered lines and carries active legacy formatting across each TextGizmo line. */
    public static List<String> splitLines(String raw, int maximum) {
        String value = normalize(raw);
        String[] rawLines = value.split("\n", -1);
        int limit = Math.max(1, Math.min(MAX_LINES, maximum));
        List<String> lines = new ArrayList<>(Math.min(rawLines.length, limit));
        FormatState state = new FormatState();
        for (int index = 0; index < rawLines.length && index < limit; index++) {
            String line = rawLines[index];
            String prefix = index == 0 ? "" : state.prefix();
            lines.add(prefix + line);
            state.consume(line);
        }
        return lines.isEmpty() ? List.of("") : List.copyOf(lines);
    }

    public static String migrateWholeTextStyles(
            String raw,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough
    ) {
        StringBuilder prefix = new StringBuilder();
        if (bold) prefix.append(FORMAT).append('l');
        if (italic) prefix.append(FORMAT).append('o');
        if (underlined) prefix.append(FORMAT).append('n');
        if (strikethrough) prefix.append(FORMAT).append('m');
        if (prefix.isEmpty()) return raw == null ? "" : raw;
        return prefix + (raw == null ? "" : raw) + FORMAT + "r";
    }

    public static String stripFormatting(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (current == FORMAT && index + 1 < raw.length() && isFormattingCode(raw.charAt(index + 1))) {
                index++;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }

    /** Returns formatting that is active immediately after the supplied prefix. */
    public static String activeFormattingPrefix(String raw, int endExclusive) {
        FormatState state = new FormatState();
        if (raw != null && !raw.isEmpty()) {
            state.consume(raw.substring(0, Math.max(0, Math.min(raw.length(), endExclusive))));
        }
        return state.prefix();
    }

    public static char minecraftColorCode(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex > 15) throw new IllegalArgumentException("Invalid color index");
        return "0123456789abcdef".charAt(paletteIndex);
    }

    static boolean isFormattingCode(char value) {
        char code = Character.toLowerCase(value);
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                || code == 'k' || code == 'l' || code == 'm' || code == 'n'
                || code == 'o' || code == 'r';
    }


    /** Returns the vanilla 16-color RGB value used by legacy formatting codes. */
    public static int minecraftColorRgb(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex > 15) throw new IllegalArgumentException("Invalid color index");
        return switch (paletteIndex) {
            case 0 -> 0x000000;
            case 1 -> 0x0000AA;
            case 2 -> 0x00AA00;
            case 3 -> 0x00AAAA;
            case 4 -> 0xAA0000;
            case 5 -> 0xAA00AA;
            case 6 -> 0xFFAA00;
            case 7 -> 0xAAAAAA;
            case 8 -> 0x555555;
            case 9 -> 0x5555FF;
            case 10 -> 0x55FF55;
            case 11 -> 0x55FFFF;
            case 12 -> 0xFF5555;
            case 13 -> 0xFF55FF;
            case 14 -> 0xFFFF55;
            case 15 -> 0xFFFFFF;
            default -> throw new IllegalArgumentException("Invalid color index");
        };
    }

    private static void fillRemainingMap(int[] map, int from, int value) {
        for (int index = Math.max(0, from); index < map.length; index++) map[index] = value;
    }

    public record WrappedText(String text, int cursor, int selectionCursor) {
    }

    private static final class FormatState {
        private Character color;
        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        void consume(String value) {
            if (value == null) return;
            for (int index = 0; index + 1 < value.length(); index++) {
                if (value.charAt(index) != FORMAT) continue;
                char code = Character.toLowerCase(value.charAt(++index));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    reset();
                    color = code;
                } else {
                    switch (code) {
                        case 'k' -> obfuscated = true;
                        case 'l' -> bold = true;
                        case 'm' -> strikethrough = true;
                        case 'n' -> underlined = true;
                        case 'o' -> italic = true;
                        case 'r' -> reset();
                        default -> { }
                    }
                }
            }
        }

        String prefix() {
            StringBuilder result = new StringBuilder(12);
            if (color != null) result.append(FORMAT).append(color);
            if (obfuscated) result.append(FORMAT).append('k');
            if (bold) result.append(FORMAT).append('l');
            if (strikethrough) result.append(FORMAT).append('m');
            if (underlined) result.append(FORMAT).append('n');
            if (italic) result.append(FORMAT).append('o');
            return result.toString().toLowerCase(Locale.ROOT);
        }

        void reset() {
            color = null;
            obfuscated = false;
            bold = false;
            strikethrough = false;
            underlined = false;
            italic = false;
        }
    }
}
