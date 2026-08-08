package be.winnetrie.mod.simpleserverutilities.achievement;

import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichText;

/** Feature-specific bounds for achievement title/info while retaining SSU legacy rich formatting. */
public final class AchievementRichText {
    public static final int TITLE_STORED_LIMIT = 512;
    public static final int INFO_STORED_LIMIT = 16_384;
    public static final int INFO_EDITOR_LIMIT = 8_192;
    private AchievementRichText() {}

    public static String normalizeTitle(String raw) { return normalize(raw, 128, TITLE_STORED_LIMIT, 1); }
    public static String normalizeInfo(String raw) { return normalize(raw, 4_096, INFO_STORED_LIMIT, 96); }
    public static String plain(String raw) { return SsuRichText.stripFormatting(raw == null ? "" : raw); }

    private static String normalize(String raw, int maxVisible, int maxStored, int maxLines) {
        String value = raw == null ? "" : raw.replace("\\n", "\n").replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(Math.min(value.length(), maxStored));
        int visible = 0, lines = 1;
        for (int i = 0; i < value.length() && out.length() < maxStored; i++) {
            char c = value.charAt(i);
            if (c == '\u00A7' && i + 1 < value.length() && formatting(value.charAt(i + 1))) {
                if (out.length() + 2 > maxStored) break;
                out.append(c).append(Character.toLowerCase(value.charAt(++i)));
                continue;
            }
            if (c == '\n') {
                if (lines >= maxLines) break;
                out.append(c); lines++; continue;
            }
            if (visible >= maxVisible) break;
            out.append(c); visible++;
        }
        return out.toString();
    }

    private static boolean formatting(char value) {
        char c = Character.toLowerCase(value);
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r';
    }
}
