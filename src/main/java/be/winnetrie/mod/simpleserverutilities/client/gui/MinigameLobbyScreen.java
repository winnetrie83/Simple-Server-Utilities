package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameScoreActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Player-facing queue browser and lightweight match status screen. */
public final class MinigameLobbyScreen extends Screen {
    private static final int W = 720, H = 468, LEFT = 252, ROWS = 12;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, CARD = 0xD0222C36,
            TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A,
            ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF, WARN = 0xFFFFD36A;

    private MinigameLobbyDataPayload data;
    private final Screen parent;
    private String selectedId = "";
    private long nextRequestId = 1L;
    private boolean awaiting;
    private EditBox scorePlayerBox;
    private EditBox scoreAmountBox;
    private String draftScorePlayer = "";
    private String draftScoreAmount = "1";
    private String localNotice = "";
    private boolean localNoticeError;

    public MinigameLobbyScreen(MinigameLobbyDataPayload data, Screen parent) {
        super(Component.literal("Minigames"));
        this.data = data;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, data.requestId() + 1L);
        if (!data.queuedMinigameId().isBlank()) selectedId = data.queuedMinigameId();
        else if (!data.games().isEmpty()) selectedId = data.games().getFirst().id();
    }

    @Override
    protected void init() {
        int x = px(), y = py();
        List<MinigameLobbyDataPayload.GameEntry> games = data.games();
        for (int i = 0; i < Math.min(ROWS, games.size()); i++) {
            MinigameLobbyDataPayload.GameEntry game = games.get(i);
            Button button = addRenderableWidget(Button.builder(
                    Component.literal(trim(game.displayName(), 31)), ignored -> {
                        selectedId = game.id();
                        rebuildWidgets();
                    }).bounds(x + 16, y + 52 + i * 27, LEFT - 32, 23).build());
            button.active = !game.id().equals(selectedId);
        }

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> request("refresh", ""))
                .bounds(x + 16, y + H - 30, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 84, y + H - 30, 68, 20).build());

        MinigameLobbyDataPayload.GameEntry selected = selected();
        if (selected != null) {
            int bx = x + LEFT + 18, by = y + H - 30;
            String primaryLabel = selected.activeHere() ? "Leave match"
                    : selected.queuedHere() ? "Leave queue" : "Join queue";
            Button primary = addRenderableWidget(Button.builder(Component.literal(primaryLabel), ignored -> {
                if (selected.queuedHere() || selected.activeHere()) request("leave", selected.id());
                else request("join", selected.id());
            }).bounds(bx, by, 96, 20).build());
            primary.active = !awaiting && (selected.queuedHere() || selected.activeHere()
                    || selected.enabled() && selected.requirementsMet());

            if (data.canAdmin()) {
                addRenderableWidget(Button.builder(Component.literal("Edit"), ignored -> edit(selected.id()))
                        .bounds(bx + 102, by, 52, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Force"), ignored -> request("force_start", selected.id()))
                        .bounds(bx + 160, by, 78, 20).build());
                Button finish = addRenderableWidget(Button.builder(Component.literal("Finish"), ignored -> request("finish", selected.id()))
                        .bounds(bx + 244, by, 82, 20).build());
                finish.active = selected.runningMatches() > 0;
                addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> request("delete", selected.id()))
                        .bounds(bx + 332, by, 60, 20).build());

                int scoreY = by - 24;
                scorePlayerBox = new EditBox(font, bx, scoreY, 104, 20, Component.literal("Online player"));
                scorePlayerBox.setMaxLength(64);
                scorePlayerBox.setValue(draftScorePlayer);
                scorePlayerBox.setResponder(value -> draftScorePlayer = value);
                addRenderableWidget(scorePlayerBox);
                scoreAmountBox = new EditBox(font, bx + 108, scoreY, 58, 20, Component.literal("Score"));
                scoreAmountBox.setMaxLength(20);
                scoreAmountBox.setValue(draftScoreAmount);
                scoreAmountBox.setResponder(value -> draftScoreAmount = value);
                addRenderableWidget(scoreAmountBox);
                Button addScore = addRenderableWidget(Button.builder(Component.literal("Add"), ignored -> score("add"))
                        .bounds(bx + 170, scoreY, 42, 20).build());
                Button setScore = addRenderableWidget(Button.builder(Component.literal("Set"), ignored -> score("set"))
                        .bounds(bx + 216, scoreY, 42, 20).build());
                addScore.active = setScore.active = !awaiting;
                if (selected.blockedArenas() > 0) {
                    addRenderableWidget(Button.builder(Component.literal("Release blocked"), ignored -> request("release_arena", selected.id()))
                            .bounds(bx + 264, scoreY, 128, 20).build());
                }
            }
        }
        if (data.canAdmin()) {
            addRenderableWidget(Button.builder(Component.literal("Create minigame"), ignored -> edit(""))
                    .bounds(x + W - 130, y + 16, 114, 20).build());
        }
    }

    private MinigameLobbyDataPayload.GameEntry selected() {
        for (MinigameLobbyDataPayload.GameEntry game : data.games()) if (game.id().equals(selectedId)) return game;
        return data.games().isEmpty() ? null : data.games().getFirst();
    }

    private void request(String action, String id) {
        if (awaiting) return;
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameLobbyRequestPayload(action, id, nextRequestId++));
        rebuildWidgets();
    }

    private void score(String mode) {
        if (awaiting) return;
        if (draftScorePlayer.isBlank()) {
            localNotice = "Enter the online player whose score you want to adjust.";
            localNoticeError = true;
            rebuildWidgets();
            return;
        }
        final long amount;
        try { amount = Long.parseLong(draftScoreAmount.trim()); }
        catch (RuntimeException exception) {
            localNotice = "Score must be a whole number.";
            localNoticeError = true;
            rebuildWidgets();
            return;
        }
        awaiting = true;
        localNotice = "";
        ClientPacketDistributor.sendToServer(new MinigameScoreActionPayload(
                mode, draftScorePlayer, amount, nextRequestId++));
        rebuildWidgets();
    }

    private void edit(String id) {
        ClientPacketDistributor.sendToServer(new MinigameEditorRequestPayload(id, nextRequestId++));
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
        rebuildWidgets();
    }

    public void refreshFromEditor() {
        request("refresh", "");
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "Minigame Lobby", x + 16, y + 17, TEXT, true);
        String state = data.activeMatchId().isBlank()
                ? data.queuedMinigameId().isBlank() ? "Not queued" : "Queued: " + data.queuedMinigameId()
                : "Active match: " + data.activeMatchId();
        g.text(font, trim(state, 70), x + 126, y + 18, MUTED, false);
        g.fill(x + LEFT, y + 42, x + LEFT + 1, y + H - 40, BORDER);

        for (int i = 0; i < Math.min(ROWS, data.games().size()); i++) {
            var game = data.games().get(i);
            int ry = y + 52 + i * 27;
            int color = game.activeHere() ? GOOD : game.queuedHere() ? ACCENT
                    : !game.enabled() || !game.requirementsMet() ? MUTED : TEXT;
            String mark = game.activeHere() ? "▶" : game.queuedHere() ? "◆" : game.freeArenas() > 0 ? "•" : "○";
            g.text(font, mark, x + 20, ry + 7, color, true);
            g.text(font, game.queuedPlayers() + " queued", x + LEFT - 78, ry + 7, MUTED, false);
        }

        MinigameLobbyDataPayload.GameEntry game = selected();
        if (game == null) {
            g.text(font, "No minigames are configured.", x + LEFT + 18, y + 62, MUTED, false);
        } else {
            drawGame(g, game, x + LEFT + 18, y + 52);
        }
        if (!localNotice.isBlank()) {
            g.text(font, trim(localNotice, 100), x + 96, y + H - 25, localNoticeError ? ERROR : GOOD, false);
        } else if (!data.notice().isBlank()) {
            g.text(font, trim(data.notice(), 100), x + 96, y + H - 25, data.error() ? ERROR : GOOD, false);
        } else if (awaiting) {
            g.text(font, "Processing minigame action…", x + 96, y + H - 25, MUTED, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawGame(GuiGraphicsExtractor g, MinigameLobbyDataPayload.GameEntry game, int x, int y) {
        g.text(font, game.displayName(), x, y, TEXT, true);
        g.text(font, game.id() + " • " + game.victoryMode().replace('_', ' '), x, y + 16, MUTED, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(game.description()), W - LEFT - 52);
        for (int i = 0; i < Math.min(8, lines.size()); i++) g.text(font, lines.get(i), x, y + 40 + i * 11, TEXT, false);
        int sy = y + 142;
        g.text(font, "Queue", x, sy, ACCENT, true);
        g.text(font, game.queuedPlayers() + " waiting • starts at " + game.minPlayers()
                + " • maximum " + game.maxPlayers(), x, sy + 17, TEXT, false);
        g.text(font, game.teamCount() + " team(s) • " + game.runningMatches() + " running match(es)", x, sy + 32, TEXT, false);
        g.text(font, game.freeArenas() + " arena(s) free • " + game.blockedArenas() + " blocked", x, sy + 47,
                game.freeArenas() > 0 ? GOOD : WARN, false);
        int ry = sy + 78;
        g.text(font, "Requirements", x, ry, ACCENT, true);
        g.text(font, trim(game.requirementsMet() ? "Available" : game.requirementReason(), 72), x, ry + 17,
                game.requirementsMet() ? GOOD : ERROR, false);
        if (game.activeHere()) {
            int my = ry + 52;
            g.text(font, "Your match", x, my, ACCENT, true);
            g.text(font, "State: " + game.matchState().replace('_', ' ') + " • Team " + game.team()
                    + " • Score " + game.score(), x, my + 17, GOOD, false);
        }
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
    @Override public boolean isPauseScreen() { return false; }
}
