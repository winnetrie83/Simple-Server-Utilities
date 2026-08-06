package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact player-only queue browser. Administrative controls live in Admin Center > Minigames. */
public final class MinigameLobbyScreen extends Screen {
    /** dev2.1: exactly 25% smaller than the previous 720 x 468 lobby. */
    private static final int W = 540, H = 351, LEFT = 188, ROWS = 9;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978,
            TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A,
            ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF, WARN = 0xFFFFD36A;

    private MinigameLobbyDataPayload data;
    private final Screen parent;
    private String selectedId = "";
    private String preferredRole = "dps";
    private long nextRequestId = 1L;
    private boolean awaiting;
    private String localNotice = "";
    private boolean localNoticeError;

    public MinigameLobbyScreen(MinigameLobbyDataPayload data, Screen parent) {
        super(Component.literal("Minigames"));
        this.data = data;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, data.requestId() + 1L);
        if (!data.queuedMinigameId().isBlank()) selectedId = data.queuedMinigameId();
        else if (!data.games().isEmpty()) selectedId = data.games().getFirst().id();
        MinigameLobbyDataPayload.GameEntry selected = selected();
        if (selected != null && !selected.preferredRole().isBlank()) preferredRole = selected.preferredRole();
    }

    @Override
    protected void init() {
        int x = px(), y = py();
        List<MinigameLobbyDataPayload.GameEntry> games = data.games();
        for (int i = 0; i < Math.min(ROWS, games.size()); i++) {
            MinigameLobbyDataPayload.GameEntry game = games.get(i);
            Button button = addRenderableWidget(Button.builder(
                    Component.literal(trim(game.displayName(), 22)), ignored -> {
                        selectedId = game.id();
                        rebuildWidgets();
                    }).bounds(x + 12, y + 43 + i * 24, LEFT - 24, 20).build());
            button.active = !game.id().equals(selectedId);
        }

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> request("refresh", ""))
                .bounds(x + 12, y + H - 27, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + 80, y + H - 27, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Profile"), ignored -> openProfile())
                .bounds(x + 136, y + H - 27, 58, 20).build());
        MinigameLobbyDataPayload.GameEntry selected = selected();
        if (selected != null) {
            int bx = x + LEFT + 12;
            int actionY = y + H - 27;
            boolean activeMatch = !data.activeMatchId().isBlank();
            boolean queued = !data.queuedMinigameId().isBlank();
            if (selected.rolesEnabled() && !activeMatch && !queued) {
                roleButton(bx, actionY - 25, 64, "DPS", "dps");
                roleButton(bx + 70, actionY - 25, 64, "Tank", "tank");
                roleButton(bx + 140, actionY - 25, 64, "Healer", "healer");
            }
            String primaryLabel = activeMatch ? "Leave match" : queued ? "Leave queue" : "Join queue";
            Button primary = addRenderableWidget(Button.builder(Component.literal(primaryLabel), ignored -> {
                if (activeMatch || queued) request("leave", "");
                else request("join", selected.id());
            }).bounds(bx, actionY, 96, 20).build());
            primary.active = !awaiting && (activeMatch || queued
                    || selected.enabled() && selected.requirementsMet());
        }
    }

    private MinigameLobbyDataPayload.GameEntry selected() {
        for (MinigameLobbyDataPayload.GameEntry game : data.games()) if (game.id().equals(selectedId)) return game;
        return data.games().isEmpty() ? null : data.games().getFirst();
    }

    private void openProfile() {
        MinigameLobbyDataPayload.GameEntry selected = selected();
        ClientPacketDistributor.sendToServer(new MinigameLobbyRequestPayload("profile",
                selected == null ? "" : selected.id(), preferredRole, nextRequestId++));
    }

    private void request(String action, String id) {
        if (awaiting) return;
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameLobbyRequestPayload(action, id, preferredRole, nextRequestId++));
        rebuildWidgets();
    }

    public void accept(MinigameLobbyDataPayload payload) {
        if (payload == null) return;
        data = payload;
        awaiting = false;
        localNotice = "";
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        boolean present = false;
        for (var game : data.games()) if (game.id().equals(selectedId)) { present = true; break; }
        if (!present) selectedId = data.games().isEmpty() ? "" : data.games().getFirst().id();
        MinigameLobbyDataPayload.GameEntry selected = selected();
        if (selected != null && !selected.preferredRole().isBlank()) preferredRole = selected.preferredRole();
        rebuildWidgets();
    }

    public void refreshFromEditor() { request("refresh", ""); }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "Minigame Lobby", x + 12, y + 14, TEXT, true);
        String state = data.activeMatchId().isBlank()
                ? data.queuedMinigameId().isBlank() ? "Not queued" : "Queued: " + data.queuedMinigameId()
                : "Active: " + data.activeMatchId();
        g.text(font, trim(state, 38), x + 112, y + 15, MUTED, false);
        g.fill(x + LEFT, y + 38, x + LEFT + 1, y + H - 34, BORDER);

        for (int i = 0; i < Math.min(ROWS, data.games().size()); i++) {
            var game = data.games().get(i);
            int ry = y + 43 + i * 24;
            int color = game.activeHere() ? GOOD : game.queuedHere() ? ACCENT
                    : !game.enabled() || !game.requirementsMet() ? MUTED : TEXT;
            String mark = game.activeHere() ? "▶" : game.queuedHere() ? "◆" : game.freeArenas() > 0 ? "•" : "○";
            g.text(font, mark, x + 16, ry + 6, color, true);
            g.text(font, game.queuedPlayers() + "q", x + LEFT - 32, ry + 6, MUTED, false);
        }

        MinigameLobbyDataPayload.GameEntry game = selected();
        if (game == null) g.text(font, "No minigames configured.", x + LEFT + 12, y + 52, MUTED, false);
        else drawGame(g, game, x + LEFT + 12, y + 46);

        String notice = !localNotice.isBlank() ? localNotice : data.notice();
        boolean error = !localNotice.isBlank() ? localNoticeError : data.error();
        if (notice.isBlank() && awaiting) notice = "Processing…";
        if (!notice.isBlank()) {
            List<FormattedCharSequence> noticeLines = font.split(Component.literal(notice), LEFT - 24);
            int color = awaiting && localNotice.isBlank() && data.notice().isBlank() ? MUTED : error ? ERROR : GOOD;
            for (int i = 0; i < Math.min(2, noticeLines.size()); i++) {
                g.text(font, noticeLines.get(i), x + 12, y + H - 52 + i * 10, color, false);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawGame(GuiGraphicsExtractor g, MinigameLobbyDataPayload.GameEntry game, int x, int y) {
        int width = W - LEFT - 24;
        g.text(font, trim(game.displayName(), 42), x, y, TEXT, true);
        g.text(font, trim(game.id() + " • " + modeLabel(game.gameType()), 50), x, y + 14, MUTED, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(game.description()), width);
        for (int i = 0; i < Math.min(4, lines.size()); i++) g.text(font, lines.get(i), x, y + 31 + i * 10, TEXT, false);

        int sy = y + 78;
        g.text(font, "Queue", x, sy, ACCENT, true);
        g.text(font, game.queuedPlayers() + " waiting • starts at " + game.minPlayers()
                + " • max " + game.maxPlayers(), x, sy + 14, TEXT, false);
        g.text(font, game.teamCount() + " team(s) • " + game.runningMatches() + " running", x, sy + 27, TEXT, false);
        g.text(font, game.freeArenas() + " free arena(s) • " + game.blockedArenas() + " blocked", x, sy + 40,
                game.freeArenas() > 0 ? GOOD : WARN, false);

        int ry = sy + 62;
        g.text(font, "Requirements", x, ry, ACCENT, true);
        g.text(font, trim(game.requirementsMet() ? "Available" : game.requirementReason(), 48), x, ry + 14,
                game.requirementsMet() ? GOOD : ERROR, false);
        if (game.rolesEnabled() && !game.activeHere()) {
            g.text(font, game.queuedHere()
                    ? "Preferred role: " + roleLabel(game.preferredRole()) + " (assignment not guaranteed)"
                    : "Choose a preferred role below; SSU balances the final team composition.",
                    x, ry + 30, game.queuedHere() ? GOOD : MUTED, false);
        }
        if (game.activeHere()) {
            int my = ry + 38;
            g.text(font, "Your match", x, my, ACCENT, true);
            String role = game.assignedRole().isBlank() ? "" : " • Role " + roleLabel(game.assignedRole());
            g.text(font, "State: " + game.matchState().replace('_', ' ') + " • Team " + game.team()
                    + role + " • Score " + game.score(), x, my + 14, GOOD, false);
        }
    }

    private void roleButton(int x, int y, int width, String label, String role) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
            preferredRole = role;
            rebuildWidgets();
        }).bounds(x, y, width, 20).build());
        button.active = !role.equals(preferredRole);
    }

    private static String roleLabel(String raw) {
        if (raw == null || raw.isBlank()) return "DPS";
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "tank" -> "Tank";
            case "healer" -> "Healer";
            default -> "DPS";
        };
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }

    private static String modeLabel(String raw) {
        if (raw == null || raw.isBlank()) return "Generic";
        String value = raw.replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
