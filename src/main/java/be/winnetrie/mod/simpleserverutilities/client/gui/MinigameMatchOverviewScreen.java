package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Detailed server-authoritative snapshot opened by the SSU menu key during a minigame. */
public final class MinigameMatchOverviewScreen extends Screen {
    private static final int W = 720, H = 430;
    private static final int PANEL = 0xF0161D25, SUB = 0xD010151C, BORDER = 0xFF586978,
            TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A,
            WARN = 0xFFFFD36A, ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF, GOLD = 0xFFFFD36A;

    private MinigameMatchOverviewPayload data;
    private long requestId;
    private int playerScroll;
    private boolean confirmingLeave;
    private boolean awaiting;

    public MinigameMatchOverviewScreen(MinigameMatchOverviewPayload data) {
        super(Component.literal("Current Minigame"));
        this.data = data;
        this.requestId = Math.max(1L, data.requestId() + 1L);
    }

    public void accept(MinigameMatchOverviewPayload payload) {
        if (payload == null || !payload.active()) return;
        data = payload;
        requestId = Math.max(requestId, payload.requestId() + 1L);
        playerScroll = Math.min(playerScroll, Math.max(0, data.players().size() - 10));
        confirmingLeave = false;
        awaiting = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        if (confirmingLeave) {
            addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> {
                confirmingLeave = false;
                rebuildWidgets();
            }).bounds(x + W / 2 - 112, y + H - 32, 104, 20).build());
            Button confirm = addRenderableWidget(Button.builder(Component.literal("Confirm leave"), ignored -> leaveMatch())
                    .bounds(x + W / 2 + 8, y + H - 32, 104, 20).build());
            confirm.active = !awaiting;
            return;
        }

        Button refresh = addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestRefresh())
                .bounds(x + 14, y + H - 30, 72, 20).build());
        refresh.active = !awaiting;
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 82, y + H - 30, 68, 20).build());
        Button leave = addRenderableWidget(Button.builder(Component.literal(data.spectator() ? "Leave spectating" : "Leave match"),
                ignored -> {
                    confirmingLeave = true;
                    rebuildWidgets();
                }).bounds(x + W - 216, y + H - 30, 124, 20).build());
        leave.active = !awaiting;

        if (data.players().size() > 10) {
            addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> {
                playerScroll = Math.max(0, playerScroll - 1);
            }).bounds(x + 444, y + 70, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> {
                playerScroll = Math.min(Math.max(0, data.players().size() - 10), playerScroll + 1);
            }).bounds(x + 444, y + 94, 24, 20).build());
        }
    }

    private void requestRefresh() {
        if (awaiting) return;
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameMatchOverviewRequestPayload("open", requestId++));
        rebuildWidgets();
    }

    private void leaveMatch() {
        if (awaiting) return;
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameMatchOverviewRequestPayload("leave", requestId++));
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(null);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xB5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);

        g.text(font, trim(data.displayName(), 48), x + 14, y + 13, GOLD, true);
        String phase = phaseLabel(data.phase());
        String timer = data.remainingSeconds() < 0L ? "No time limit" : formatTime(data.remainingSeconds());
        String header = typeLabel(data.gameType()) + "  •  " + phase + "  •  " + timer;
        g.text(font, header, x + W - font.width(header) - 14, y + 14,
                data.overtime() ? WARN : ACCENT, true);
        if (!data.description().isBlank()) {
            g.text(font, trim(data.description(), 92), x + 14, y + 32, MUTED, false);
        }

        box(g, x + 12, y + 50, 210, 106, "Your match");
        g.text(font, "Team: " + blank(data.yourTeamName(), "—"), x + 20, y + 73, TEXT, false);
        g.text(font, "Role: " + roleLabel(data.yourRole()), x + 20, y + 89, TEXT, false);
        g.text(font, "Score: " + data.yourScore(), x + 20, y + 105, GOOD, false);
        g.text(font, "Status: " + (data.spectator() ? "Spectating" : "Playing"), x + 20, y + 121,
                data.spectator() ? WARN : GOOD, false);
        if (data.overtime()) g.text(font, "OVERTIME", x + 20, y + 137, WARN, true);

        box(g, x + 230, y + 50, 240, 106, "Teams and score");
        int teamY = y + 73;
        for (int index = 0; index < Math.min(5, data.teams().size()); index++) {
            var team = data.teams().get(index);
            String line = team.name() + "  •  " + team.players() + " player" + (team.players() == 1 ? "" : "s");
            g.text(font, trim(line, 28), x + 238, teamY + index * 16,
                    team.team() == data.yourTeam() ? GOOD : TEXT, false);
            String score = Long.toString(team.score());
            g.text(font, score, x + 460 - font.width(score), teamY + index * 16, GOLD, true);
        }

        box(g, x + 478, y + 50, 230, 106, "Current status");
        renderLines(g, data.statusLines(), x + 486, y + 73, 5, 29, MUTED);

        box(g, x + 12, y + 164, 458, 201, "Players");
        g.text(font, "Player", x + 20, y + 188, ACCENT, true);
        g.text(font, "Team", x + 176, y + 188, ACCENT, true);
        g.text(font, "Role", x + 244, y + 188, ACCENT, true);
        g.text(font, "K/D/A", x + 304, y + 188, ACCENT, true);
        g.text(font, "Obj", x + 367, y + 188, ACCENT, true);
        g.text(font, "Score", x + 414, y + 188, ACCENT, true);
        g.fill(x + 18, y + 201, x + 462, y + 202, BORDER);
        List<MinigameMatchOverviewPayload.PlayerRow> players = sortedPlayers();
        int end = Math.min(players.size(), playerScroll + 10);
        for (int index = playerScroll; index < end; index++) {
            var row = players.get(index);
            int lineY = y + 209 + (index - playerScroll) * 15;
            if ((index - playerScroll) % 2 == 1) g.fill(x + 17, lineY - 2, x + 465, lineY + 11, 0x351F2A34);
            String status = row.disconnected() ? " ⏻" : row.eliminated() ? " ✕" : "";
            g.text(font, trim((row.self() ? "★ " : "") + row.name() + status, 22), x + 20, lineY,
                    row.self() ? GOOD : row.disconnected() || row.eliminated() ? MUTED : TEXT, false);
            g.text(font, trim(row.teamName(), 9), x + 176, lineY, TEXT, false);
            g.text(font, roleLabel(row.role()), x + 244, lineY, MUTED, false);
            g.text(font, row.kills() + "/" + row.deaths() + "/" + row.assists(), x + 304, lineY, TEXT, false);
            g.text(font, Long.toString(row.captures() + row.defenses()), x + 371, lineY, TEXT, false);
            String score = Long.toString(row.score());
            g.text(font, score, x + 458 - font.width(score), lineY, GOLD, false);
        }

        box(g, x + 478, y + 164, 230, 96, "Objectives");
        renderLines(g, data.objectiveLines(), x + 486, y + 187, 5, 29, TEXT);
        box(g, x + 478, y + 268, 230, 97, "How to play");
        renderLines(g, data.ruleLines(), x + 486, y + 291, 5, 29, MUTED);

        if (confirmingLeave) {
            g.fill(x + 100, y + 370, x + W - 100, y + H - 40, 0xF0231820);
            g.outline(x + 100, y + 370, W - 200, H - 410, ERROR);
            String warning = "Leave this match? Your slot may be forfeited and your state will be restored.";
            g.text(font, warning, x + W / 2 - font.width(warning) / 2, y + 381, ERROR, true);
        } else if (!data.notice().isBlank() || awaiting) {
            String notice = awaiting ? "Updating…" : data.notice();
            g.text(font, trim(notice, 78), x + 96, y + H - 24, data.error() ? ERROR : GOOD, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private List<MinigameMatchOverviewPayload.PlayerRow> sortedPlayers() {
        ArrayList<MinigameMatchOverviewPayload.PlayerRow> rows = new ArrayList<>(data.players());
        rows.sort(Comparator.comparing(MinigameMatchOverviewPayload.PlayerRow::self).reversed()
                .thenComparingInt(MinigameMatchOverviewPayload.PlayerRow::team)
                .thenComparing(MinigameMatchOverviewPayload.PlayerRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private void box(GuiGraphicsExtractor g, int x, int y, int width, int height, String title) {
        g.fill(x, y, x + width, y + height, SUB);
        g.outline(x, y, width, height, BORDER);
        g.text(font, title, x + 8, y + 7, ACCENT, true);
    }

    private void renderLines(GuiGraphicsExtractor g, List<String> lines, int x, int y,
                             int maximum, int trim, int color) {
        if (lines == null || lines.isEmpty()) {
            g.text(font, "No additional information.", x, y, MUTED, false);
            return;
        }
        for (int index = 0; index < Math.min(maximum, lines.size()); index++) {
            g.text(font, trim(lines.get(index), trim), x, y + index * 15, color, false);
        }
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private static String phaseLabel(String phase) {
        return switch (phase == null ? "" : phase.toLowerCase(java.util.Locale.ROOT)) {
            case "countdown" -> "Preparation";
            case "running" -> "In progress";
            case "post_game" -> "Post-game";
            case "resetting" -> "Resetting";
            default -> "Match";
        };
    }

    private static String typeLabel(String type) {
        if (type == null || type.isBlank()) return "Minigame";
        String value = type.replace('_', ' ').replace('-', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : value.split(" +")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String roleLabel(String role) {
        return switch (role == null ? "" : role.toLowerCase(java.util.Locale.ROOT)) {
            case "tank" -> "Tank";
            case "healer" -> "Healer";
            case "dps" -> "DPS";
            default -> "—";
        };
    }

    private static String formatTime(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(java.util.Locale.ROOT, "%d:%02d", safe / 60L, safe % 60L);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 1)) + "…";
    }
}
