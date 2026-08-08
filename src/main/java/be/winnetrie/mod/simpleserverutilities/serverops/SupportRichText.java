package be.winnetrie.mod.simpleserverutilities.serverops;

/**
 * Server-safe rich text format for support ticket messages.
 *
 * <p>Uses the same fixed sixteen Minecraft colours and B/I/U/S styles as SSU mail and
 * holograms. Obfuscated text and arbitrary section-sign sequences are discarded.</p>
 */
public final class SupportRichText {
    public static final char FORMAT = '\u00A7';
    public static final int MAX_VISIBLE_CHARACTERS = 1_500;
    public static final int MAX_STORED_CHARACTERS = 8_192;
    public static final int MAX_LINES = 64;
    public static final int MAX_MESSAGES_PER_TICKET = 96;

    private SupportRichText() {
    }

    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_STORED_CHARACTERS));
        int visible = 0;
        int lines = 1;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == FORMAT && index + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(index + 1));
                if (isAllowedCode(code)) {
                    if (out.length() + 2 > MAX_STORED_CHARACTERS) break;
                    out.append(FORMAT).append(code);
                    index++;
                    continue;
                }
                if (isLegacyFormattingCode(code)) {
                    index++;
                    continue;
                }
                current = '?';
            }
            if (current == '\n') {
                if (lines >= MAX_LINES || out.length() >= MAX_STORED_CHARACTERS) break;
                out.append('\n');
                lines++;
                continue;
            }
            if (visible >= MAX_VISIBLE_CHARACTERS || out.length() >= MAX_STORED_CHARACTERS) break;
            out.append(current);
            visible++;
        }
        return out.toString();
    }

    public static String plainText(String raw) {
        String value = normalize(raw);
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == FORMAT && index + 1 < value.length() && isAllowedCode(value.charAt(index + 1))) {
                index++;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }

    public static String compactPreview(String raw, int maximum) {
        String plain = plainText(raw).replace('\n', ' ').trim().replaceAll("\\s+", " ");
        int max = Math.max(8, maximum);
        return plain.length() <= max ? plain : plain.substring(0, max - 1) + "…";
    }

    private static boolean isAllowedCode(char value) {
        char code = Character.toLowerCase(value);
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                || code == 'l' || code == 'm' || code == 'n' || code == 'o' || code == 'r';
    }

    private static boolean isLegacyFormattingCode(char value) {
        char code = Character.toLowerCase(value);
        return isAllowedCode(code) || code == 'k';
    }
}
