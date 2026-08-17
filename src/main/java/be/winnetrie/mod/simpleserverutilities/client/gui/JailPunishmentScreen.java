package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.jail.JailDefinition;
import be.winnetrie.mod.simpleserverutilities.network.PlayerManagementActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.PlayerManagementDataPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Clear sentencing workflow. Physical Jail setup remains in Jail Administration. */
public final class JailPunishmentScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int W = 540;
    private static final int H = 350;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFB86B;
    private static final String[] MODES = {"CHOICE", "TASK_ONLY", "TIME_ONLY"};

    private final PlayerManagementScreen parent;
    private PlayerManagementDataPayload data;
    private final List<JailDefinition> jails = new ArrayList<>();
    private int jailIndex;
    private int modeIndex = 1; // Task-only is the safest default: Choice requires a configured buyout.
    private String reason = "";
    private Map<String, Integer> requirements = new LinkedHashMap<>();
    private List<String> tools = new ArrayList<>();
    private String timeHoursValue = "24";
    private String deadlineHoursValue = "168"; // one week
    private String buyoutMinorValue = "0";
    private String shareDaysValue = "30";
    private EditBox timeHours;
    private EditBox deadlineHours;
    private EditBox buyoutMinor;
    private EditBox shareDays;
    private long request = 1L;
    private String localNotice = "";

    public JailPunishmentScreen(PlayerManagementScreen parent, PlayerManagementDataPayload data) {
        super(Component.literal("Configure punishment"));
        this.parent = parent;
        this.data = data;
        parse();
    }

    private void parse() {
        jails.clear();
        try {
            JsonObject root = GSON.fromJson(data.json(), JsonObject.class);
            JsonArray array = root != null && root.has("jails") ? root.getAsJsonArray("jails") : new JsonArray();
            for (var element : array) {
                JailDefinition definition = GSON.fromJson(element, JailDefinition.class);
                if (definition != null && definition.enabled) {
                    definition.normalize();
                    jails.add(definition);
                }
            }
        } catch (Exception ignored) { }
        jailIndex = Math.max(0, Math.min(jailIndex, Math.max(0, jails.size() - 1)));
    }

    public void accept(PlayerManagementDataPayload next) {
        data = next;
        if (next.error()) {
            localNotice = next.notice();
            rebuildWidgets();
            return;
        }
        parent.accept(next);
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();

        addRenderableWidget(Button.builder(Component.literal("Jail: " + trim(jailName(), 25)), button -> {
            stashFields();
            if (!jails.isEmpty()) jailIndex = (jailIndex + 1) % jails.size();
            rebuildWidgets();
        }).bounds(x + 18, y + 48, 220, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Punishment: " + modeLabel()), button -> {
            stashFields();
            modeIndex = (modeIndex + 1) % MODES.length;
            rebuildWidgets();
        }).bounds(x + 248, y + 48, 208, 20).build());

        addRenderableWidget(Button.builder(Component.literal(reason.isBlank() ? "Write reason" : "Edit reason"), button -> {
            stashFields();
            minecraft.setScreenAndShow(new RichTextValueEditorScreen(
                    this,
                    "Punishment reason",
                    "Shown to the prisoner and retained in moderation history.",
                    reason,
                    value -> reason = value));
        }).bounds(x + 18, y + 92, 132, 20).build());

        Button task = addRenderableWidget(Button.builder(Component.literal("Configure task"), button -> {
            stashFields();
            minecraft.setScreenAndShow(new JailTaskEditorScreen(this, requirements, tools, (required, issuedTools) -> {
                requirements = new LinkedHashMap<>(required);
                tools = new ArrayList<>(issuedTools);
            }));
        }).bounds(x + 160, y + 92, 132, 20).build());
        task.active = !mode().equals("TIME_ONLY");

        timeHours = box(x + 18, y + 149, 82, timeHoursValue, 5, "Hours");
        deadlineHours = box(x + 112, y + 149, 82, deadlineHoursValue, 5, "Hours");
        buyoutMinor = box(x + 206, y + 149, 110, buyoutMinorValue, 14, "Minor units");
        shareDays = box(x + 328, y + 149, 70, shareDaysValue, 3, "Days");
        timeHours.active = mode().equals("TIME_ONLY");
        deadlineHours.active = !mode().equals("TIME_ONLY");
        buyoutMinor.active = mode().equals("CHOICE");
        shareDays.active = !mode().equals("TIME_ONLY");

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(x + 18, y + H - 28, 66, 20).build());
        Button apply = addRenderableWidget(Button.builder(Component.literal("Jail player"), button -> send())
                .bounds(x + W - 118, y + H - 28, 100, 20).build());
        apply.active = !jails.isEmpty();
    }

    private void send() {
        stashFields();
        if (jails.isEmpty()) {
            localNotice = "Create and configure a Jail facility first.";
            rebuildWidgets();
            return;
        }
        long duration = parsePositive(timeHoursValue) * 3600L;
        long deadline = parsePositive(deadlineHoursValue) * 3600L;
        ClientPacketDistributor.sendToServer(new PlayerManagementActionPayload(
                "jail",
                data.target(),
                reason,
                duration,
                8,
                parsePositive(buyoutMinorValue),
                jails.get(jailIndex).id,
                GSON.toJson(requirements),
                GSON.toJson(tools),
                parseInt(shareDaysValue, 30),
                mode(),
                deadline,
                request++));
    }

    private void stashFields() {
        if (timeHours != null) timeHoursValue = timeHours.getValue();
        if (deadlineHours != null) deadlineHoursValue = deadlineHours.getValue();
        if (buyoutMinor != null) buyoutMinorValue = buyoutMinor.getValue();
        if (shareDays != null) shareDaysValue = shareDays.getValue();
    }

    private EditBox box(int x, int y, int width, String value, int max, String hint) {
        EditBox edit = new EditBox(font, x, y, width, 20, Component.literal(hint));
        edit.setHint(Component.literal(hint));
        edit.setMaxLength(max);
        edit.setValue(value == null ? "" : value);
        addRenderableWidget(edit);
        return edit;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        SsuGuiScale.fullscreenDim(graphics, this, 0xA5000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, "Punishment — " + data.target(), x + 18, y + 16, TEXT, true);
        graphics.text(font, "Choose a physical Jail and a punishment path. Prisoner choices become permanent once selected.", x + 18, y + 34, MUTED, false);

        graphics.text(font, "Reason", x + 18, y + 80, MUTED, false);
        graphics.text(font, reason.isBlank() ? "A reason is required." : trim(strip(reason), 58), x + 302, y + 97, reason.isBlank() ? WARNING : MUTED, false);
        graphics.text(font, "Time sentence (hours)", x + 18, y + 135, MUTED, false);
        graphics.text(font, "Task deadline (hours)", x + 112, y + 135, MUTED, false);
        graphics.text(font, "Buyout", x + 206, y + 135, MUTED, false);
        graphics.text(font, "Share period (days)", x + 328, y + 135, MUTED, false);

        JailDefinition jail = currentJail();
        if (jail != null) {
            graphics.text(font, "Jail cells: " + jail.cells.size() + "  •  Task Area: " + (jail.workBoundsSet ? "set" : "missing"), x + 18, y + 178, MUTED, false);
        }

        int cursor = y + 199;
        String modeText = switch (mode()) {
            case "CHOICE" -> "30s choice: Buyout or Task. Insufficient funds or no choice automatically locks Task.";
            case "TIME_ONLY" -> "Solitude: assigned to one configured physical cell; the Jail dashboard remains available while other SSU functions stay blocked.";
            default -> "Task only: work starts immediately and must be completed before the configured deadline.";
        };
        cursor = drawWrapped(graphics, modeText, x + 18, cursor, W - 36, TEXT, 2) + 4;

        if (!mode().equals("TIME_ONLY")) {
            graphics.text(font, "Task: " + requirements.size() + " required block type(s) • " + tools.size() + " issued tool(s)", x + 18, cursor,
                    requirements.isEmpty() ? WARNING : GOOD, false);
            cursor += 18;
            cursor = drawWrapped(graphics,
                    "If the task is unfinished at its deadline, SSU applies a permanent ban with reason: failed to complete punishment",
                    x + 18, cursor, W - 36, WARNING, 2) + 4;
        }

        cursor = drawWrapped(graphics,
                "Jailing immediately cancels pending SSU teleports, exits minigames/dungeons, stores the normal player state and moves the player to Jail.",
                x + 18, cursor, W - 36, MUTED, 2);

        if (!localNotice.isBlank()) {
            drawWrapped(graphics, localNotice, x + 18, y + H - 52, W - 36, WARNING, 2);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color, int maxLines) {
        var lines = font.split(Component.literal(text), maxWidth);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) graphics.text(font, lines.get(i), x, y + i * 11, color, false);
        return y + count * 11;
    }

    private JailDefinition currentJail() {
        return jails.isEmpty() ? null : jails.get(Math.max(0, Math.min(jailIndex, jails.size() - 1)));
    }

    private String mode() {
        return MODES[Math.max(0, Math.min(modeIndex, MODES.length - 1))];
    }

    private String modeLabel() {
        return switch (mode()) {
            case "CHOICE" -> "Buyout or task";
            case "TIME_ONLY" -> "Time / solitude";
            default -> "Task only";
        };
    }

    private String jailName() {
        JailDefinition jail = currentJail();
        return jail == null ? "No facilities" : jail.displayName;
    }

    private static long parsePositive(String raw) {
        try { return Math.max(0L, Long.parseLong(raw == null ? "0" : raw.trim())); }
        catch (Exception ignored) { return 0L; }
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw == null ? "" : raw.trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").replace('\n', ' ');
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    public void onClose() {
        stashFields();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
