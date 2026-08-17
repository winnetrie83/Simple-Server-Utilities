package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolConfigurePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameScoreActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated Admin Center manager. No administrative controls are exposed in the player lobby. */
public final class MinigameAdminScreen extends Screen {
    private static final int W = 540, H = 351, LEFT = 188, ROWS = 9;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978,
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

    public MinigameAdminScreen(MinigameLobbyDataPayload data, Screen parent) {
        super(Component.literal("Minigame Administration"));
        this.data = data;
        this.parent = parent;
        nextRequestId = Math.max(1L, data.requestId() + 1L);
        if (!data.games().isEmpty()) selectedId = data.games().getFirst().id();
    }

    @Override
    protected void init() {
        int x = px(), y = py();
        for (int i = 0; i < Math.min(ROWS, data.games().size()); i++) {
            var game = data.games().get(i);
            Button row = addRenderableWidget(Button.builder(Component.literal(trim(game.displayName(), 22)), ignored -> {
                selectedId = game.id(); rebuildWidgets();
            }).bounds(x + 12, y + 43 + i * 24, LEFT - 24, 20).build());
            row.active = !game.id().equals(selectedId);
        }
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> request("refresh_admin", ""))
                .bounds(x + 12, y + H - 27, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + 80, y + H - 27, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Setup Tool"), ignored ->
                        PacketDistributor.sendToServer(new MinigameSetupToolConfigurePayload(
                                "give_tool", selectedId, "",
                                "arena_bounds", 1, 0, nextRequestId++)))
                .bounds(x + W - 92, y + 12, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("System health"), ignored -> request("diagnostics", ""))
                .bounds(x + W - 184, y + 12, 88, 20).build());

        var selected = selected();
        if (selected == null) return;
        int bx = x + LEFT + 12, actionY = y + H - 27;
        addRenderableWidget(Button.builder(Component.literal("Edit settings"), ignored -> edit(selected.id()))
                .bounds(bx, actionY, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Force start"), ignored -> request("force_start", selected.id()))
                .bounds(bx + 87, actionY, 70, 20).build());
        Button finish = addRenderableWidget(Button.builder(Component.literal("Finish"), ignored -> request("finish", selected.id()))
                .bounds(bx + 162, actionY, 50, 20).build());
        finish.active = selected.runningMatches() > 0;
        Button restore = addRenderableWidget(Button.builder(Component.literal("Restore"), ignored -> request("release_arena", selected.id()))
                .bounds(bx + 217, actionY, 54, 20).build());
        restore.active = selected.blockedArenas() > 0;
        addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> request("delete", selected.id()))
                .bounds(bx + 276, actionY, 48, 20).build());

        int scoreY = y + H - 55;
        scorePlayerBox = new EditBox(font, bx, scoreY, 112, 20, Component.literal("Player"));
        scorePlayerBox.setHint(Component.literal("Online player"));
        scorePlayerBox.setMaxLength(64);
        scorePlayerBox.setValue(draftScorePlayer);
        scorePlayerBox.setResponder(value -> draftScorePlayer = value);
        addRenderableWidget(scorePlayerBox);
        scoreAmountBox = new EditBox(font, bx + 117, scoreY, 62, 20, Component.literal("Amount"));
        scoreAmountBox.setHint(Component.literal("Score"));
        scoreAmountBox.setMaxLength(18);
        scoreAmountBox.setValue(draftScoreAmount);
        scoreAmountBox.setResponder(value -> draftScoreAmount = value);
        addRenderableWidget(scoreAmountBox);
        Button addScore = addRenderableWidget(Button.builder(Component.literal("Add score"), ignored -> submitScore("add"))
                .bounds(bx + 184, scoreY, 66, 20).build());
        Button setScore = addRenderableWidget(Button.builder(Component.literal("Set score"), ignored -> submitScore("set"))
                .bounds(bx + 255, scoreY, 69, 20).build());
        addScore.active = selected.runningMatches() > 0;
        setScore.active = selected.runningMatches() > 0;
    }


    private MinigameLobbyDataPayload.GameEntry selected() {
        for (var game : data.games()) if (game.id().equals(selectedId)) return game;
        return data.games().isEmpty() ? null : data.games().getFirst();
    }

    private void request(String action, String id) {
        if (awaiting) return;
        awaiting = true;
        PacketDistributor.sendToServer(new MinigameLobbyRequestPayload(action, id, nextRequestId++));
        rebuildWidgets();
    }

    private void submitScore(String mode) {
        if (awaiting) return;
        if (draftScorePlayer == null || draftScorePlayer.isBlank()) return;
        final long amount;
        try { amount = Long.parseLong(draftScoreAmount == null ? "" : draftScoreAmount.trim()); }
        catch (NumberFormatException exception) { return; }
        awaiting = true;
        PacketDistributor.sendToServer(new MinigameScoreActionPayload(
                mode, draftScorePlayer.trim(), amount, nextRequestId++));
        rebuildWidgets();
    }

    private void edit(String id) {
        PacketDistributor.sendToServer(new MinigameEditorRequestPayload(id, nextRequestId++));
    }

    public void accept(MinigameLobbyDataPayload payload) {
        if (payload == null) return;
        data = payload; awaiting = false;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        boolean present = false;
        for (var game : data.games()) if (game.id().equals(selectedId)) { present = true; break; }
        if (!present) selectedId = data.games().isEmpty() ? "" : data.games().getFirst().id();
        rebuildWidgets();
    }

    public void refreshFromEditor() { request("refresh_admin", ""); }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, "Admin Center > Minigames", x + 12, y + 14, TEXT, true);
        g.drawString(font, "Settings, arenas and live-match control", x + 180, y + 15, MUTED, false);
        g.fill(x + LEFT, y + 38, x + LEFT + 1, y + H - 34, BORDER);
        for (int i = 0; i < Math.min(ROWS, data.games().size()); i++) {
            var game = data.games().get(i);
            int ry = y + 43 + i * 24;
            g.drawString(font, game.enabled() ? "●" : "○", x + 16, ry + 6, game.enabled() ? GOOD : MUTED, true);
            g.drawString(font, game.runningMatches() + "r", x + LEFT - 32, ry + 6,
                    game.runningMatches() > 0 ? WARN : MUTED, false);
        }
        var game = selected();
        if (game == null) {
            g.drawString(font, "No minigames configured.", x + LEFT + 12, y + 52, MUTED, false);
            g.drawString(font, "Use the Setup Tool to select bounds and create Spleef, CTF or Domination.",
                    x + LEFT + 12, y + 70, MUTED, false);
        } else {
            drawGame(g, game, x + LEFT + 12, y + 46);
            g.drawString(font, game.runningMatches() > 0 ? "Live score correction" : "Live score correction (no active match)",
                    x + LEFT + 12, y + H - 67, game.runningMatches() > 0 ? ACCENT : MUTED, false);
        }
        String notice = awaiting ? "Processing…" : data.notice();
        if (!notice.isBlank()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(notice), LEFT - 24);
            for (int i = 0; i < Math.min(2, lines.size()); i++)
                g.drawString(font, lines.get(i), x + 12, y + H - 52 + i * 10, data.error() ? ERROR : GOOD, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawGame(GuiGraphics g, MinigameLobbyDataPayload.GameEntry game, int x, int y) {
        int width = W - LEFT - 24;
        g.drawString(font, trim(game.displayName(), 42), x, y, TEXT, true);
        g.drawString(font, game.id() + " • " + modeLabel(game.gameType()) + " • " + (game.enabled() ? "enabled" : "disabled"),
                x, y + 14, game.enabled() ? GOOD : MUTED, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(game.description()), width);
        for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(font, lines.get(i), x, y + 31 + i * 10, TEXT, false);
        int sy = y + 70;
        g.drawString(font, "Configuration", x, sy, ACCENT, true);
        g.drawString(font, game.minPlayers() + "-" + game.maxPlayers() + " players • " + game.teamCount() + " teams", x, sy + 14, TEXT, false);
        g.drawString(font, game.freeArenas() + " free arena(s) • " + game.blockedArenas() + " blocked", x, sy + 27,
                game.blockedArenas() > 0 ? WARN : GOOD, false);
        g.drawString(font, game.queuedPlayers() + " queued • " + game.runningMatches() + " running", x, sy + 40, TEXT, false);
        g.drawString(font, "Edit settings opens the dedicated " + modeLabel(game.gameType()) + " editor.", x, sy + 62, MUTED, false);
        g.drawString(font, "Setup Tool changes bounds, blocks, spawns, flags, nodes and snapshots in-world.", x, sy + 76, MUTED, false);
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String trim(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private static String modeLabel(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        String value = raw.replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
