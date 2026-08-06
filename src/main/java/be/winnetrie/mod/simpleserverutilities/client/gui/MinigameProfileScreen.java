package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameProfilePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Player-facing cosmetic progression profile; no option on this screen changes combat power. */
public final class MinigameProfileScreen extends Screen {
    private static final int W = 560, H = 360;
    private static final int PANEL = 0xF0161D25, SUB = 0xC010151C, BORDER = 0xFF586978,
            TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A,
            WARN = 0xFFFFD36A, ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF, GOLD = 0xFFFFD36A;

    private MinigameProfilePayload data;
    private final Screen parent;
    private long requestId = 1L;
    private boolean awaiting;

    public MinigameProfileScreen(MinigameProfilePayload data, Screen parent) {
        super(Component.literal("Minigame Profile"));
        this.data = data;
        this.parent = parent;
        this.requestId = Math.max(1L, data.requestId() + 1L);
    }

    public void accept(MinigameProfilePayload payload) {
        if (payload == null) return;
        data = payload;
        requestId = Math.max(requestId, payload.requestId() + 1L);
        awaiting = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        int effectX = x + 18, effectY = y + 101;
        int index = 0;
        for (String effect : data.victoryEffects()) {
            String label = "none".equals(effect) ? "No effect" : capitalize(effect);
            Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> select("select_victory", effect))
                    .bounds(effectX + index * 112, effectY, 104, 20).build());
            button.active = !awaiting && !effect.equalsIgnoreCase(data.selectedVictoryEffect());
            index++;
        }
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> select("profile", ""))
                .bounds(x + 14, y + H - 29, 68, 20).build()).active = !awaiting;
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 72, y + H - 29, 58, 20).build());
    }

    private void select(String action, String value) {
        if (awaiting) return;
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameLobbyRequestPayload(action, value,
                "", data.challengeMinigameId(), requestId++));
        rebuildWidgets();
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "Minigame Profile", x + 14, y + 14, TEXT, true);
        String level = data.experienceForNextLevel() <= 0L
                ? "Level " + data.level() + "  •  MAX"
                : "Level " + data.level() + "  •  " + data.experienceIntoLevel() + "/"
                    + data.experienceForNextLevel() + " XP";
        g.text(font, level, x + W - font.width(level) - 14, y + 15, GOLD, true);
        g.text(font, "Matches " + data.matchesPlayed() + "  •  Wins " + data.matchesWon(), x + 14, y + 34, MUTED, false);
        if (!data.badges().isEmpty()) g.text(font, "Badges: " + String.join(", ", data.badges()), x + 210, y + 34, ACCENT, false);

        box(g, x + 12, y + 54, 354, 120, "Victory effects");
        g.text(font, "Selected: " + capitalize(data.selectedVictoryEffect()), x + 20, y + 78, GOOD, false);
        g.text(font, "Victory effects are cosmetic and only play when your team wins.", x + 20, y + 133, MUTED, false);
        g.text(font, "Player titles are now selected from SSU > Profile.", x + 20, y + 149, ACCENT, false);

        box(g, x + 12, y + 179, 354, 66, "Progression summary");
        g.text(font, "Minigame XP unlocks global titles, badges and victory effects.", x + 20, y + 205, TEXT, false);
        g.text(font, "Combat power is never changed by progression.", x + 20, y + 225, WARN, false);

        box(g, x + 374, y + 54, 174, 191, "Ratings");
        int ratingY = y + 79;
        List<MinigameProfilePayload.Rating> ratings = data.ratings();
        for (int index = 0; index < Math.min(10, ratings.size()); index++) {
            var rating = ratings.get(index);
            g.text(font, trim(rating.displayName(), 16), x + 382, ratingY + index * 15, TEXT, false);
            String value = Integer.toString(rating.rating());
            g.text(font, value, x + 538 - font.width(value), ratingY + index * 15, ACCENT, false);
        }
        if (ratings.isEmpty()) g.text(font, "Play a rated match", x + 382, ratingY, MUTED, false);

        String weeklyTitle = "Weekly cosmetic challenges";
        if (!data.challengeDisplayName().isBlank()) weeklyTitle += " • " + trim(data.challengeDisplayName(), 24);
        box(g, x + 12, y + 252, 536, 66, weeklyTitle);
        if (data.weeklyChallengesEnabled()) {
            challenge(g, x + 20, y + 276, "Play " + data.weeklyMatchesRequired() + " matches",
                    data.weeklyMatches(), data.weeklyMatchesRequired(), data.weeklyMatchesExperience());
            challenge(g, x + 196, y + 276, "Win " + data.weeklyWinsRequired() + " matches",
                    data.weeklyWins(), data.weeklyWinsRequired(), data.weeklyWinsExperience());
            challenge(g, x + 372, y + 276, "Earn " + data.weeklyContributionRequired() + " impact",
                    data.weeklyContribution(), data.weeklyContributionRequired(), data.weeklyContributionExperience());
        } else {
            g.text(font, "Weekly challenges are disabled for this minigame.", x + 20, y + 281, MUTED, false);
        }
        g.text(font, "All progression rewards are cosmetic-only.", x + 20, y + 304, WARN, false);
        String notice = awaiting ? "Updating…" : data.notice();
        if (!notice.isBlank()) g.text(font, trim(notice, 58), x + 96, y + H - 23,
                data.error() ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void box(GuiGraphicsExtractor g, int x, int y, int width, int height, String title) {
        g.fill(x, y, x + width, y + height, SUB);
        g.outline(x, y, width, height, BORDER);
        g.text(font, title, x + 8, y + 7, ACCENT, true);
    }

    private void challenge(GuiGraphicsExtractor g, int x, int y, String label, long value, long maximum, int reward) {
        long safe = Math.min(maximum, Math.max(0L, value));
        int color = safe >= maximum ? GOOD : TEXT;
        g.text(font, trim(label, 24), x, y, color, false);
        g.text(font, safe + "/" + maximum + "  •  +" + reward + " XP", x, y + 14,
                safe >= maximum ? GOOD : MUTED, false);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String capitalize(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw)) return "None";
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
