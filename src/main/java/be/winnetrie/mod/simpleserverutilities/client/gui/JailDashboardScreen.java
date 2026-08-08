package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import be.winnetrie.mod.simpleserverutilities.identity.RichTextComponents;
import be.winnetrie.mod.simpleserverutilities.network.JailDashboardActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.JailDashboardPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Prisoner-only dashboard. Pending punishment choices cannot be dismissed. */
public final class JailDashboardScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int W = 480;
    private static final int H = 330;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFB86B;
    private static final int ERROR = 0xFFFF8585;

    private JailDashboardPayload data;
    private JsonObject json;
    private long request = 1L;

    public JailDashboardScreen(JailDashboardPayload data) {
        super(Component.literal("Jail"));
        accept(data);
    }

    public void accept(JailDashboardPayload next) {
        data = next;
        try { json = GSON.fromJson(next.json(), JsonObject.class); }
        catch (Exception ignored) { json = new JsonObject(); }
        if (json == null) json = new JsonObject();
        if (json.has("active") && !json.get("active").getAsBoolean()) {
            if (minecraft != null) minecraft.setScreenAndShow(null);
            return;
        }
        if (minecraft != null && width > 0) rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        String path = string("selectedPath", "TASK");
        if ("PENDING".equals(path)) {
            long buyout = number("buyoutMinor");
            Button buy = addRenderableWidget(Button.builder(Component.literal("Buy out punishment"), button -> send("buyout"))
                    .bounds(x + 18, y + H - 32, 160, 20).build());
            buy.active = buyout > 0L;
            addRenderableWidget(Button.builder(Component.literal("Accept task punishment"), button -> send("task"))
                    .bounds(x + 188, y + H - 32, 178, 20).build());
        } else if ("TASK".equals(path) && bool("complete")) {
            // Normally task completion releases automatically. This remains a recovery button for migrated saves.
            addRenderableWidget(Button.builder(Component.literal("Complete punishment"), button -> send("complete"))
                    .bounds(x + W - 174, y + H - 32, 156, 20).build());
        }
    }

    private void send(String action) {
        ClientPacketDistributor.sendToServer(new JailDashboardActionPayload(action, request++));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        graphics.fill(0, 0, width, height, 0xA5000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, "Jail dashboard", x + 18, y + 15, TEXT, true);
        graphics.text(font, "Facility: " + string("jailName", string("jailId", "-")), x + 18, y + 34, MUTED, false);
        graphics.text(font, "Punishment reason", x + 18, y + 55, MUTED, false);

        var reason = RichTextComponents.fromEncoded(string("reason", ""));
        var reasonLines = font.split(reason, W - 36);
        for (int i = 0; i < Math.min(3, reasonLines.size()); i++) {
            graphics.text(font, reasonLines.get(i), x + 18, y + 70 + i * 11, TEXT, false);
        }

        String path = string("selectedPath", "TASK");
        long now = System.currentTimeMillis();
        int cursor = y + 112;
        if ("PENDING".equals(path)) {
            long remain = secondsUntil(number("choiceExpiresAt"), now);
            graphics.text(font, "Choose within " + remain + "s", x + 18, cursor, WARNING, true);
            graphics.text(font,
                    "Buyout: " + string("buyoutFormatted", Long.toString(number("buyoutMinor")))
                            + "  •  Balance: " + string("balanceFormatted", Long.toString(number("balanceMinor"))),
                    x + 18, cursor + 20, TEXT, false);
            cursor = drawWrapped(graphics,
                    "If the buyout cannot be paid, or no choice is made in time, Task punishment is selected automatically and locked in.",
                    x + 18, cursor + 38, W - 36, MUTED, 2) + 5;
            drawTask(graphics, x, cursor, now);
        } else if ("TIME".equals(path)) {
            long remain = secondsUntil(number("releaseAt"), now);
            graphics.text(font, "Solitude sentence", x + 18, cursor, TEXT, true);
            graphics.text(font, "Time remaining: " + duration(remain) + "  •  Cell: " + (number("assignedCell") + 1L), x + 18, cursor + 22, GOOD, false);
            cursor = drawWrapped(graphics,
                    "You may move inside your assigned cell. Commands, items, containers, combat, teleportation and unrelated SSU functions are blocked.",
                    x + 18, cursor + 44, W - 36, MUTED, 2) + 5;
            drawWrapped(graphics,
                    "Admins may teleport to you, but neither you nor an admin can keep you teleported outside the Jail before release.",
                    x + 18, cursor, W - 36, MUTED, 2);
        } else {
            graphics.text(font, "Task punishment — locked in", x + 18, cursor, TEXT, true);
            drawTask(graphics, x, cursor + 22, now);
        }

        if (!data.notice().isBlank()) {
            drawWrapped(graphics, data.notice(), x + 18, y + H - 58, W - 36, data.error() ? ERROR : GOOD, 2);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawTask(GuiGraphicsExtractor graphics, int x, int y, long now) {
        long deadline = number("taskDeadlineAt");
        long remain = deadline > 0L ? secondsUntil(deadline, now) : 0L;
        graphics.text(font, "Task deadline: " + (deadline > 0L ? duration(remain) : "No deadline"), x + 18, y, TEXT, false);
        Map<String, Integer> requirements = map("requirements");
        Map<String, Integer> progress = map("progress");
        int i = 0;
        for (var entry : requirements.entrySet()) {
            if (i >= 6) break;
            int current = progress.getOrDefault(entry.getKey(), 0);
            graphics.text(font,
                    trim(entry.getKey(), 31) + "  " + current + " / " + entry.getValue(),
                    x + 18 + (i / 3) * 220,
                    y + 21 + (i % 3) * 17,
                    current >= entry.getValue() ? GOOD : MUTED,
                    false);
            i++;
        }
        if (requirements.isEmpty()) {
            graphics.text(font, "No task requirements configured.", x + 18, y + 22, WARNING, false);
        } else {
            graphics.text(font, "Completion is automatic when every requirement is finished.", x + 18, y + 78, GOOD, false);
        }
    }

    private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color, int maxLines) {
        var lines = font.split(Component.literal(text), maxWidth);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) graphics.text(font, lines.get(i), x, y + i * 11, color, false);
        return y + count * 11;
    }

    private Map<String, Integer> map(String key) {
        try { return GSON.fromJson(json.get(key), new TypeToken<Map<String, Integer>>() { }.getType()); }
        catch (Exception ignored) { return Map.of(); }
    }

    private boolean bool(String key) {
        try { return json.has(key) && json.get(key).getAsBoolean(); }
        catch (Exception ignored) { return false; }
    }

    private long number(String key) {
        try { return json.has(key) ? json.get(key).getAsLong() : 0L; }
        catch (Exception ignored) { return 0L; }
    }

    private String string(String key, String fallback) {
        try { return json.has(key) ? json.get(key).getAsString() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static long secondsUntil(long target, long now) {
        return target <= 0L ? 0L : Math.max(0L, (target - now + 999L) / 1000L);
    }

    private static String duration(long seconds) {
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remain = seconds % 60L;
        if (days > 0L) return days + "d " + hours + "h";
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + remain + "s";
        return remain + "s";
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override
    public void onClose() {
        if (!"PENDING".equals(string("selectedPath", "TASK")) && minecraft != null) minecraft.setScreenAndShow(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
}
