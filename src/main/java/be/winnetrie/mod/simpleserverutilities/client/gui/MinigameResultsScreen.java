package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.Comparator;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameResultsPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Post-game scoreboard, progression summary and rematch/next-arena vote. */
public final class MinigameResultsScreen extends Screen {
    private static final int W = 700, H = 390;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978,
            TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A,
            ACCENT = 0xFF7FC8FF, GOLD = 0xFFFFD36A;

    private MinigameResultsPayload data;
    private int scroll;
    private long requestId = 1L;
    private String voted = "";
    private int voteTicksRemaining;

    public MinigameResultsScreen(MinigameResultsPayload data) {
        super(Component.literal("Minigame Results"));
        this.data = data;
        this.requestId = Math.max(1L, data.requestId() + 1L);
        this.voteTicksRemaining = Math.max(0, data.voteSecondsRemaining() * 20);
    }

    public void accept(MinigameResultsPayload payload) {
        if (payload == null || !payload.visible()) return;
        data = payload;
        requestId = Math.max(requestId, payload.requestId() + 1L);
        voteTicksRemaining = Math.max(0, payload.voteSecondsRemaining() * 20);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        int buttonY = y + H - 30;
        Button rematch = addRenderableWidget(Button.builder(Component.literal(voted.equals("vote_rematch") ? "Voted: Rematch" : "Rematch"),
                ignored -> vote("vote_rematch")).bounds(x + 14, buttonY, 112, 20).build());
        Button next = addRenderableWidget(Button.builder(Component.literal(voted.equals("vote_next") ? "Voted: Next arena" : "Next arena"),
                ignored -> vote("vote_next")).bounds(x + 132, buttonY, 112, 20).build());
        Button leave = addRenderableWidget(Button.builder(Component.literal(voted.equals("vote_leave") ? "Voted: Leave" : "Leave"),
                ignored -> vote("vote_leave")).bounds(x + 250, buttonY, 88, 20).build());
        boolean voting = voteTicksRemaining > 0;
        rematch.active = voting && !voted.equals("vote_rematch");
        next.active = voting && !voted.equals("vote_next");
        leave.active = voting && !voted.equals("vote_leave");
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 82, buttonY, 68, 20).build());
        if (data.rows().size() > 12) {
            addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> { scroll = Math.max(0, scroll - 1); })
                    .bounds(x + W - 42, y + 75, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> {
                scroll = Math.min(Math.max(0, data.rows().size() - 12), scroll + 1);
            }).bounds(x + W - 42, y + 99, 24, 20).build());
        }
    }

    private void vote(String action) {
        voted = action;
        ClientPacketDistributor.sendToServer(new MinigameLobbyRequestPayload(action, data.matchId(), requestId++));
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (voteTicksRemaining > 0 && --voteTicksRemaining == 0) rebuildWidgets();
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(null); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xB5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, data.title(), x + 14, y + 14, GOLD, true);
        g.text(font, trim(data.reason(), 85), x + 14, y + 31, MUTED, false);
        String progress = "Level " + data.level() + "  •  +" + data.experienceGained() + " XP";
        if (data.experienceForNextLevel() > 0L) progress += "  •  " + data.experienceIntoLevel() + "/" + data.experienceForNextLevel();
        g.text(font, progress, x + W - font.width(progress) - 14, y + 15, GOOD, true);
        if (!data.badges().isEmpty()) {
            String badges = "Badges: " + String.join(", ", data.badges());
            g.text(font, trim(badges, 72), x + W - font.width(trim(badges, 72)) - 14, y + 32, ACCENT, false);
        }

        int tableX = x + 14, tableY = y + 58;
        int tableW = W - 28, tableH = 268;
        g.fill(tableX, tableY, tableX + tableW, tableY + tableH, 0xB010151C);
        g.outline(tableX, tableY, tableW, tableH, BORDER);
        String header = "Player                    T  Role     K  D  A   Damage  Heal  Cap Def Obj  Impact";
        g.text(font, header, tableX + 8, tableY + 8, ACCENT, true);
        g.fill(tableX + 5, tableY + 22, tableX + tableW - 5, tableY + 23, BORDER);

        List<MinigameResultsPayload.PlayerRow> rows = data.rows().stream()
                .sorted(Comparator.comparing(MinigameResultsPayload.PlayerRow::winner).reversed()
                        .thenComparing(Comparator.comparingLong(
                                MinigameResultsPayload.PlayerRow::contribution).reversed())
                        .thenComparing(MinigameResultsPayload.PlayerRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int end = Math.min(rows.size(), scroll + 12);
        for (int index = scroll; index < end; index++) {
            var row = rows.get(index);
            int lineY = tableY + 31 + (index - scroll) * 19;
            if ((index - scroll) % 2 == 1) g.fill(tableX + 4, lineY - 3, tableX + tableW - 4, lineY + 13, 0x351F2A34);
            int color = row.winner() ? GOOD : TEXT;
            String name = (row.winner() ? "★ " : "  ") + trim(row.name(), 20);
            g.text(font, name, tableX + 8, lineY, color, false);
            g.text(font, Integer.toString(row.team()), tableX + 174, lineY, MUTED, false);
            g.text(font, roleLabel(row.role()), tableX + 194, lineY, MUTED, false);
            g.text(font, Long.toString(row.kills()), tableX + 258, lineY, TEXT, false);
            g.text(font, Long.toString(row.deaths()), tableX + 280, lineY, TEXT, false);
            g.text(font, Long.toString(row.assists()), tableX + 302, lineY, TEXT, false);
            g.text(font, formatHundredths(row.damage()), tableX + 334, lineY, TEXT, false);
            g.text(font, formatHundredths(row.healing()), tableX + 394, lineY, TEXT, false);
            g.text(font, Long.toString(row.captures()), tableX + 446, lineY, TEXT, false);
            g.text(font, Long.toString(row.defenses()), tableX + 476, lineY, TEXT, false);
            g.text(font, row.objectiveSeconds() + "s", tableX + 508, lineY, TEXT, false);
            g.text(font, Long.toString(row.contribution()), tableX + 552, lineY, GOLD, false);
        }
        if (voteTicksRemaining > 0) {
            int seconds = Math.max(1, (voteTicksRemaining + 19) / 20);
            g.text(font, "Voting closes in " + seconds + "s", x + 354, y + H - 25, MUTED, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private static String roleLabel(String role) {
        return switch (role == null ? "" : role.toLowerCase(java.util.Locale.ROOT)) {
            case "tank" -> "Tank";
            case "healer" -> "Healer";
            default -> "DPS";
        };
    }

    private static String formatHundredths(long value) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0L, value) / 100.0D);
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 1)) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
