package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.minigame.MinigameGameType;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupAction;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolConfigurePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Right-click action selector for the dedicated in-world Minigame Setup Tool. */
public final class MinigameSetupToolScreen extends Screen {
    private static final int W = 520, H = 330;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7,
            MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF;
    private MinigameSetupToolOpenPayload data;
    private long nextRequestId;
    private boolean awaiting;

    public MinigameSetupToolScreen(MinigameSetupToolOpenPayload data) {
        super(Component.literal("Minigame Setup Tool"));
        this.data = data;
        nextRequestId = Math.max(1L, data.requestId() + 1L);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        addCycle(x + 18, y + 54, 300, "Game", this::previousGame, this::nextGame, gameLabel());
        addCycle(x + 18, y + 94, 300, "Arena", this::previousArena, this::nextArena, arenaLabel());
        addCycle(x + 18, y + 134, 300, "Left-click action", this::previousAction, this::nextAction,
                MinigameSetupAction.parse(data.action()).label());

        MinigameSetupAction action = MinigameSetupAction.parse(data.action());
        if (action == MinigameSetupAction.TEAM_SPAWN || action == MinigameSetupAction.CTF_FLAG) {
            addCycle(x + 18, y + 174, 144, "Team", () -> changeTeam(-1), () -> changeTeam(1), teamLabel());
        }
        if (action == MinigameSetupAction.TEAM_SPAWN || action == MinigameSetupAction.DOMINATION_NODE
                || action == MinigameSetupAction.DOMINATION_NODE_SPAWN || action == MinigameSetupAction.BOOST_SPAWN) {
            String indexLabel = action == MinigameSetupAction.BOOST_SPAWN ? "Boost slot"
                    : (action == MinigameSetupAction.DOMINATION_NODE || action == MinigameSetupAction.DOMINATION_NODE_SPAWN) ? "Node" : "Spawn slot";
            addCycle(x + 174, y + 174, 144, indexLabel,
                    () -> changeIndex(-1), () -> changeIndex(1), Integer.toString(data.index() + 1));
        }

        Button create = addRenderableWidget(Button.builder(Component.literal("Create new game"), ignored -> {
            if (minecraft != null) minecraft.setScreenAndShow(new MinigameSetupCreateScreen(data, this));
        }).bounds(x + 18, y + H - 34, 106, 20).build());
        create.active = data.hasSelection() && !awaiting;

        Button editor = addRenderableWidget(Button.builder(Component.literal("Open game settings"), ignored -> {
            if (!data.selectedMinigameId().isBlank()) ClientPacketDistributor.sendToServer(
                    new MinigameEditorRequestPayload(data.selectedMinigameId(), nextRequestId++));
        }).bounds(x + 130, y + H - 34, 118, 20).build());
        editor.active = !data.selectedMinigameId().isBlank() && !awaiting;

        Button clear = addRenderableWidget(Button.builder(Component.literal("Clear corner"), ignored -> send("clear_point",
                data.selectedMinigameId(), data.selectedArenaId(), data.action(), data.team(), data.index()))
                .bounds(x + 254, y + H - 34, 82, 20).build());
        clear.active = data.hasFirstPoint() || data.hasSelection();
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 76, y + H - 34, 58, 20).build());
    }

    private void addCycle(int x, int y, int width, String label, Runnable previous, Runnable next, String value) {
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> previous.run()).bounds(x, y, 26, 20).build());
        Button current = addRenderableWidget(Button.builder(Component.literal(trim(value, 34)), ignored -> next.run())
                .bounds(x + 30, y, width - 60, 20).build());
        current.active = !awaiting;
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> next.run()).bounds(x + width - 26, y, 26, 20).build());
    }

    private void previousGame() { cycleGame(-1); }
    private void nextGame() { cycleGame(1); }
    private void cycleGame(int delta) {
        if (data.games().isEmpty()) return;
        int current = 0;
        for (int i = 0; i < data.games().size(); i++) if (data.games().get(i).id().equals(data.selectedMinigameId())) current = i;
        var game = data.games().get(Math.floorMod(current + delta, data.games().size()));
        String arena = game.arenas().isEmpty() ? "" : game.arenas().getFirst().id();
        List<MinigameSetupAction> actions = MinigameSetupAction.available(MinigameGameType.parse(game.gameType()), true);
        String action = actions.isEmpty() ? MinigameSetupAction.ARENA_BOUNDS.id() : actions.getFirst().id();
        send("select", game.id(), arena, action, 1, 0);
    }

    private void previousArena() { cycleArena(-1); }
    private void nextArena() { cycleArena(1); }
    private void cycleArena(int delta) {
        var game = game();
        if (game == null || game.arenas().isEmpty()) return;
        int current = 0;
        for (int i = 0; i < game.arenas().size(); i++) if (game.arenas().get(i).id().equals(data.selectedArenaId())) current = i;
        var arena = game.arenas().get(Math.floorMod(current + delta, game.arenas().size()));
        send("select", game.id(), arena.id(), data.action(), data.team(), data.index());
    }

    private void previousAction() { cycleAction(-1); }
    private void nextAction() { cycleAction(1); }
    private void cycleAction(int delta) {
        var game = game();
        MinigameGameType type = game == null ? null : MinigameGameType.parse(game.gameType());
        List<MinigameSetupAction> actions = MinigameSetupAction.available(type, game != null);
        int current = 0;
        MinigameSetupAction selected = MinigameSetupAction.parse(data.action());
        for (int i = 0; i < actions.size(); i++) if (actions.get(i) == selected) current = i;
        MinigameSetupAction action = actions.get(Math.floorMod(current + delta, actions.size()));
        send("select", data.selectedMinigameId(), data.selectedArenaId(), action.id(), data.team(), data.index());
    }

    private void changeTeam(int delta) {
        int maximum = game() == null ? 2 : Math.max(1, MinigameGameType.parse(game().gameType()) == MinigameGameType.SPLEEF ? 16 : 2);
        int team = Math.floorMod((data.team() - 1) + delta, maximum) + 1;
        send("select", data.selectedMinigameId(), data.selectedArenaId(), data.action(), team, data.index());
    }

    private void changeIndex(int delta) {
        int maximum = (MinigameSetupAction.parse(data.action()) == MinigameSetupAction.DOMINATION_NODE || MinigameSetupAction.parse(data.action()) == MinigameSetupAction.DOMINATION_NODE_SPAWN) ? 9 : 64;
        int index = Math.floorMod(data.index() + delta, maximum);
        send("select", data.selectedMinigameId(), data.selectedArenaId(), data.action(), data.team(), index);
    }

    private void send(String operation, String game, String arena, String action, int team, int index) {
        if (awaiting) return;
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameSetupToolConfigurePayload(operation, game, arena, action,
                team, index, nextRequestId++));
        rebuildWidgets();
    }

    public void accept(MinigameSetupToolOpenPayload payload) {
        if (payload == null) return;
        data = payload; awaiting = false;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        rebuildWidgets();
    }

    public void refreshFromEditor() {
        send("select", data.selectedMinigameId(), data.selectedArenaId(), data.action(), data.team(), data.index());
    }

    private MinigameSetupToolOpenPayload.GameEntry game() {
        for (var game : data.games()) if (game.id().equals(data.selectedMinigameId())) return game;
        return null;
    }

    private MinigameSetupToolOpenPayload.ArenaEntry arena() {
        var game = game();
        if (game == null) return null;
        for (var arena : game.arenas()) if (arena.id().equals(data.selectedArenaId())) return arena;
        return null;
    }

    private String gameLabel() { var game = game(); return game == null ? "No target — select new arena bounds" : game.displayName(); }
    private String arenaLabel() { var arena = arena(); return arena == null ? "No arena selected" : arena.displayName(); }
    private String teamLabel() {
        var game = game();
        if (game == null) return "Team " + data.team();
        if (data.team() == 1) return game.team1Name() + " (#" + hex(game.team1Color()) + ")";
        if (data.team() == 2) return game.team2Name() + " (#" + hex(game.team2Color()) + ")";
        return "Team " + data.team();
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(null); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "SSU Minigame Setup Tool", x + 18, y + 14, TEXT, true);
        g.text(font, "Right-click opens this menu. The selected action is performed with left-click in the world.",
                x + 18, y + 31, MUTED, false);
        g.text(font, "Game", x + 18, y + 43, MUTED, false);
        g.text(font, "Arena", x + 18, y + 83, MUTED, false);
        g.text(font, "Left-click action", x + 18, y + 123, MUTED, false);
        MinigameSetupAction action = MinigameSetupAction.parse(data.action());
        List<FormattedCharSequence> actionLines = font.split(Component.literal(action.description()), 174);
        for (int i = 0; i < Math.min(5, actionLines.size()); i++)
            g.text(font, actionLines.get(i), x + 330, y + 54 + i * 11, ACCENT, false);
        if (action == MinigameSetupAction.TEAM_SPAWN || action == MinigameSetupAction.CTF_FLAG)
            g.text(font, "Team", x + 18, y + 163, MUTED, false);
        if (action == MinigameSetupAction.TEAM_SPAWN || action == MinigameSetupAction.DOMINATION_NODE
                || action == MinigameSetupAction.DOMINATION_NODE_SPAWN || action == MinigameSetupAction.BOOST_SPAWN) {
            String indexLabel = action == MinigameSetupAction.BOOST_SPAWN ? "Boost slot"
                    : (action == MinigameSetupAction.DOMINATION_NODE || action == MinigameSetupAction.DOMINATION_NODE_SPAWN) ? "Node" : "Spawn slot";
            g.text(font, indexLabel, x + 174, y + 163, MUTED, false);
        }

        var arena = arena();
        int infoY = y + 216;
        if (arena != null) {
            g.text(font, "Arena region: " + trim(arena.bounds(), 56), x + 18, infoY, TEXT, false);
            g.text(font, "Spleef floor: " + trim(arena.playFloor(), 56), x + 18, infoY + 13, MUTED, false);
            g.text(font, "Spectator bounds: " + trim(arena.spectatorBounds(), 52), x + 18, infoY + 26, MUTED, false);
        }
        if (data.hasFirstPoint()) {
            BlockPos point = BlockPos.of(data.firstPoint());
            g.text(font, "First corner: " + point.getX() + ", " + point.getY() + ", " + point.getZ(), x + 330, infoY, GOOD, false);
        }
        if (data.hasSelection()) {
            BlockPos p1 = BlockPos.of(data.selectionPoint1()), p2 = BlockPos.of(data.selectionPoint2());
            g.text(font, "New arena selection: " + p1.getX() + "," + p1.getY() + "," + p1.getZ()
                    + " -> " + p2.getX() + "," + p2.getY() + "," + p2.getZ(), x + 18, infoY + 45, GOOD, false);
            g.text(font, data.selectionVolume() + " selected blocks", x + 18, infoY + 58, GOOD, false);
        } else g.text(font, "To create a game, select New arena bounds and left-click two corners.", x + 18, infoY + 45, MUTED, false);
        if (!data.notice().isBlank()) g.text(font, trim(data.notice(), 76), x + 18, y + H - 52,
                data.error() ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String trim(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private static String hex(int color) { return String.format(java.util.Locale.ROOT, "%06X", color & 0x00FFFFFF); }
}
