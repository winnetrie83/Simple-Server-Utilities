package be.winnetrie.mod.simpleserverutilities.mail;

/**
 * Server-safe rich-text limits for mail bodies.
 *
 * <p>Mail uses the same legacy Minecraft formatting representation as floating
 * text, but does not impose hologram-specific 40-character line wrapping.
 * Only the 16 colors plus bold, italic, underline and strikethrough are kept.</p>
 */
public final class MailRichText {
    public static final char FORMAT = '\u00A7';
    public static final int MAX_VISIBLE_CHARACTERS = 1_024;
    public static final int MAX_STORED_CHARACTERS = 16_384;
    public static final int MAX_LINES = 1_024;

    private MailRichText() {
    }

    /** Normalizes line endings, formatting codes and authoritative mail limits. */
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_STORED_CHARACTERS));
        int visibleCharacters = 0;
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
                    // Explicitly discard unsupported formatting such as obfuscated text.
                    index++;
                    continue;
                }
                // A literal section sign is not accepted in editable mail text.
                current = '?';
            }
            if (current == '\n') {
                if (lines >= MAX_LINES || out.length() >= MAX_STORED_CHARACTERS) break;
                out.append('\n');
                lines++;
                continue;
            }
            if (visibleCharacters >= MAX_VISIBLE_CHARACTERS || out.length() >= MAX_STORED_CHARACTERS) break;
            out.append(current);
            visibleCharacters++;
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
