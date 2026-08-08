package be.winnetrie.mod.simpleserverutilities.client.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.MinigameHudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Right-aligned scoreboard visible only while the server marks a player in a match. */
public final class MinigameHudClientState {
    private static boolean serverVisible;
    /** 0 compact, 1 expanded, 2 hidden. */
    private static int displayMode;
    private static String title = "";
    private static List<String> lines = List.of();

    private MinigameHudClientState() {}

    public static void apply(MinigameHudPayload payload) {
        boolean wasVisible = serverVisible;
        serverVisible = payload != null && payload.visible();
        if (!wasVisible && serverVisible || wasVisible && !serverVisible) displayMode = 0;
        title = payload == null ? "" : payload.title();
        lines = payload == null ? List.of() : payload.lines();
    }

    public static String cycleDisplayMode() {
        if (!serverVisible) return "unavailable";
        displayMode = (displayMode + 1) % 3;
        return switch (displayMode) {
            case 1 -> "expanded";
            case 2 -> "hidden";
            default -> "compact";
        };
    }

    public static boolean isServerVisible() { return serverVisible; }

    public static void clear() {
        serverVisible = false;
        displayMode = 0;
        title = "";
        lines = List.of();
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!serverVisible || displayMode == 2 || minecraft.player == null || minecraft.gui.screen() != null) return;
        KothData koth = parseKoth(lines);
        if (koth != null) {
            renderKoth(graphics, minecraft, koth);
            return;
        }
        List<String> visibleLines = displayMode == 1 ? visibleUserLines(lines) : compactLines(lines);
        int width = Math.max(150, minecraft.font.width(title) + 24);
        for (String line : visibleLines) width = Math.max(width, minecraft.font.width(line) + 24);
        int height = 26 + visibleLines.size() * 13;
        int x = minecraft.getWindow().getGuiScaledWidth() - width - 10;
        int y = 38;
        graphics.fill(x, y, x + width, y + height, 0xC9161D25);
        graphics.outline(x, y, width, height, 0xFF586978);
        graphics.text(minecraft.font, title, x + 10, y + 8, 0xFFFFD65A, true);
        for (int i = 0; i < visibleLines.size(); i++) {
            graphics.text(minecraft.font, visibleLines.get(i), x + 10, y + 23 + i * 13, 0xFFF3F5F7, false);
        }
    }

    private static void renderKoth(GuiGraphicsExtractor g, Minecraft minecraft, KothData d) {
        int width = 196;
        int height = d.rotating ? 82 : 100;
        int x = minecraft.getWindow().getGuiScaledWidth() - width - 10;
        int y = 38;
        g.fill(x, y, x + width, y + height, 0xD6161D25);
        g.outline(x, y, width, height, 0xFF586978);
        g.text(minecraft.font, trim(title, 22), x + 9, y + 7, 0xFFFFD65A, true);
        String timer = findPrefix(lines, "Time:");
        if (!timer.isBlank()) g.text(minecraft.font, timer.substring(5).trim(), x + width - minecraft.font.width(timer.substring(5).trim()) - 9, y + 7, 0xFF7FC8FF, true);

        String scores = d.redName + " " + d.redScore + "/" + d.target + "  •  " + d.blueScore + "/" + d.target + " " + d.blueName;
        g.centeredText(minecraft.font, trim(scores, 34), x + width / 2, y + 23, 0xFFF3F5F7);
        int red = 0xFF000000 | d.redColor, blue = 0xFF000000 | d.blueColor;
        g.fill(x + 9, y + 36, x + 15, y + 42, red);
        g.fill(x + width - 15, y + 36, x + width - 9, y + 42, blue);
        String presence = d.redPresent + " on hill   " + d.bluePresent + " on hill";
        g.centeredText(minecraft.font, presence, x + width / 2, y + 35, 0xFFAAB5BE);

        if (d.rotating) {
            String state = d.owner == 1 ? d.redName + " scoring" : d.owner == 2 ? d.blueName + " scoring" : "Contested / neutral";
            g.centeredText(minecraft.font, trim(state, 30), x + width / 2, y + 50, d.owner == 1 ? red : d.owner == 2 ? blue : 0xFFF3F5F7);
            g.centeredText(minecraft.font, "Hill " + (d.pointIndex + 1) + "/" + d.pointCount + " • moves in " + d.rotateSeconds + "s",
                    x + width / 2, y + 64, 0xFFFFD36A);
        } else {
            int bx = x + 14, by = y + 53, bw = width - 28, bh = 9;
            int redEnd = bx + (int)Math.round(bw * 0.40D);
            int whiteEnd = bx + (int)Math.round(bw * 0.60D);
            g.fill(bx, by, redEnd, by + bh, 0xCC000000 | d.redColor);
            g.fill(redEnd, by, whiteEnd, by + bh, 0xCCEDEDED);
            g.fill(whiteEnd, by, bx + bw, by + bh, 0xCC000000 | d.blueColor);
            g.outline(bx, by, bw, bh, 0xFFF3F5F7);
            int ballX = bx + (int)Math.round((d.control + 1.0D) * 0.5D * bw);
            ballX = Math.max(bx + 2, Math.min(bx + bw - 3, ballX));
            g.fill(ballX - 2, by - 3, ballX + 3, by + bh + 3, 0xFFFFD700);
            String arrow = d.direction < 0 ? "←" : d.direction > 0 ? "→" : "•";
            g.centeredText(minecraft.font, arrow, ballX, by + bh + 5, 0xFFFFD700);
            String state = d.owner == 1 ? d.redName + " scoring" : d.owner == 2 ? d.blueName + " scoring" : "Neutral zone";
            g.centeredText(minecraft.font, trim(state, 30), x + width / 2, y + 78,
                    d.owner == 1 ? red : d.owner == 2 ? blue : 0xFFF3F5F7);
        }
        g.centeredText(minecraft.font, d.inside ? "YOU ARE INSIDE THE HILL" : "Outside hill range",
                x + width / 2, y + height - 12, d.inside ? 0xFF83E39A : 0xFFAAB5BE);
    }

    private static KothData parseKoth(List<String> source) {
        if (source == null) return null;
        for (String line : source) {
            if (line == null || !line.startsWith("@koth|")) continue;
            try {
                String[] p = line.split("\\|", -1);
                if (p.length < 18) return null;
                return new KothData("R".equals(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                        Double.parseDouble(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]), Integer.parseInt(p[8]),
                        "1".equals(p[9]), Integer.parseInt(p[10]), Long.parseLong(p[11]), Integer.parseInt(p[12]),
                        Integer.parseInt(p[13]), p[14], p[15], Integer.parseInt(p[16], 16), Integer.parseInt(p[17], 16));
            } catch (RuntimeException ignored) { return null; }
        }
        return null;
    }

    private static List<String> visibleUserLines(List<String> source) {
        ArrayList<String> out = new ArrayList<>();
        for (String line : source) if (line != null && !line.startsWith("@")) out.add(line);
        return List.copyOf(out);
    }

    private static List<String> compactLines(List<String> source) {
        ArrayList<String> compact = new ArrayList<>();
        for (String line : source) {
            if (line == null || line.startsWith("@")) continue;
            if (line.startsWith("Time:") || line.startsWith("Starts in:") || line.startsWith("Respawning in:")
                    || line.startsWith("Flags:") || line.startsWith("Nodes:") || line.startsWith("Alive:")
                    || line.contains(" · ") && !line.startsWith("Mode:")) {
                compact.add(line);
                if (compact.size() >= 3) break;
            }
        }
        if (compact.isEmpty()) {
            for (String line : source) {
                if (line == null || line.startsWith("@") || line.startsWith("Mode:") || line.startsWith("State:")) continue;
                compact.add(line);
                if (compact.size() >= 3) break;
            }
        }
        return List.copyOf(compact);
    }

    private static String findPrefix(List<String> source, String prefix) {
        if (source == null) return "";
        for (String line : source) if (line != null && line.startsWith(prefix)) return line;
        return "";
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private record KothData(boolean rotating, int redScore, int blueScore, int target, double control, int direction,
                            int redPresent, int bluePresent, boolean inside, int owner, long rotateSeconds,
                            int pointIndex, int pointCount, String redName, String blueName, int redColor, int blueColor) {}
}
