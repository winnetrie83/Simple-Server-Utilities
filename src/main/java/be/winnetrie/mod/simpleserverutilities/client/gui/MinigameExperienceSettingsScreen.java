package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.minigame.MinigameDefinition;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameExperienceRules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared release-polish settings used by Spleef, CTF and Domination editors. */
final class MinigameExperienceSettingsScreen extends Screen {
    private static final int W = 660, H = 430;
    private static final int PANEL = 0xF0141920, SUBPANEL = 0xD01C2630, BORDER = 0xFF596B79;
    private static final int TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A,
            ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF;

    private final MinigameDefinition draft;
    private final Screen parent;
    private final MinigameExperienceRules rules;
    private int page;
    private String notice = "";

    private Button results, killFeed, rejoin, afk, overtime, voting, spectator;
    private Button summaryMail, balancing, progression, weeklyChallenges;
    private EditBox rejoinGrace, afkTimeout, afkWarning, overtimeSeconds, voteSeconds;
    private EditBox balanceWeight, participationXp, winnerXp, objectiveXp;
    private EditBox weeklyMatchesRequired, weeklyMatchesXp, weeklyWinsRequired, weeklyWinsXp;
    private EditBox weeklyContributionRequired, weeklyContributionXp;

    MinigameExperienceSettingsScreen(MinigameDefinition draft, Screen parent) {
        super(Component.literal("Minigame Match Flow"));
        this.draft = draft;
        this.parent = parent;
        if (draft.experience == null) draft.experience = new MinigameExperienceRules();
        draft.experience.normalize();
        this.rules = copyOf(draft.experience);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        Button flow = addRenderableWidget(Button.builder(Component.literal("Match flow"), ignored -> switchPage(0))
                .bounds(x + 14, y + 12, 98, 20).build());
        Button progress = addRenderableWidget(Button.builder(Component.literal("Progression & integration"), ignored -> switchPage(1))
                .bounds(x + 118, y + 12, 150, 20).build());
        flow.active = page != 0;
        progress.active = page != 1;
        if (page == 0) initFlow(x, y);
        else initProgression(x, y);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 14, y + H - 30, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), ignored -> apply())
                .bounds(x + W - 84, y + H - 30, 70, 20).build());
        updateLabels();
    }

    private void initFlow(int x, int y) {
        results = toggle(x + 14, y + 54, 196, () -> rules.resultsScreenEnabled = !rules.resultsScreenEnabled);
        killFeed = toggle(x + 222, y + 54, 196, () -> rules.killFeedEnabled = !rules.killFeedEnabled);
        rejoin = toggle(x + 430, y + 54, 216, () -> rules.rejoinEnabled = !rules.rejoinEnabled);
        afk = toggle(x + 14, y + 84, 196, () -> rules.afkDetectionEnabled = !rules.afkDetectionEnabled);
        overtime = toggle(x + 222, y + 84, 196, () -> rules.overtimeEnabled = !rules.overtimeEnabled);
        voting = toggle(x + 430, y + 84, 216, () -> rules.postGameVotingEnabled = !rules.postGameVotingEnabled);
        spectator = toggle(x + 14, y + 114, 196, () -> rules.spectatorToolsEnabled = !rules.spectatorToolsEnabled);

        rejoinGrace = field(x + 14, y + 190, 112, "Rejoin grace", rules.rejoinGraceSeconds);
        afkTimeout = field(x + 138, y + 190, 112, "AFK timeout", rules.afkTimeoutSeconds);
        afkWarning = field(x + 262, y + 190, 112, "AFK warning", rules.afkWarningSeconds);
        overtimeSeconds = field(x + 386, y + 190, 112, "Overtime", rules.overtimeSeconds);
        voteSeconds = field(x + 510, y + 190, 136, "Vote time", rules.postGameVoteSeconds);
    }

    private void initProgression(int x, int y) {
        progression = toggle(x + 14, y + 54, 196, () -> rules.progressionEnabled = !rules.progressionEnabled);
        balancing = toggle(x + 222, y + 54, 196, () -> rules.performanceBalancingEnabled = !rules.performanceBalancingEnabled);
        summaryMail = toggle(x + 430, y + 54, 216, () -> rules.matchSummaryMailEnabled = !rules.matchSummaryMailEnabled);

        balanceWeight = field(x + 14, y + 142, 120, "Balance weight", format(rules.performanceBalanceWeight));
        participationXp = field(x + 146, y + 142, 120, "Participation XP", rules.participationExperience);
        winnerXp = field(x + 278, y + 142, 120, "Winner XP", rules.winnerExperience);
        objectiveXp = field(x + 410, y + 142, 120, "Performance XP cap", rules.objectiveExperienceCap);

        weeklyChallenges = toggle(x + 14, y + 190, 240,
                () -> rules.weeklyChallengesEnabled = !rules.weeklyChallengesEnabled);
        weeklyMatchesRequired = field(x + 14, y + 244, 120, "Matches required", rules.weeklyMatchesRequired);
        weeklyMatchesXp = field(x + 146, y + 244, 120, "Matches XP", rules.weeklyMatchesExperience);
        weeklyWinsRequired = field(x + 278, y + 244, 120, "Wins required", rules.weeklyWinsRequired);
        weeklyWinsXp = field(x + 410, y + 244, 120, "Wins XP", rules.weeklyWinsExperience);
        weeklyContributionRequired = field(x + 14, y + 300, 186, "Impact required", Long.toString(rules.weeklyContributionRequired));
        weeklyContributionXp = field(x + 212, y + 300, 120, "Impact XP", rules.weeklyContributionExperience);
    }

    private Button toggle(int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            action.run(); updateLabels();
        }).bounds(x, y, width, 20).build());
    }

    private EditBox field(int x, int y, int width, String hint, int value) {
        return field(x, y, width, hint, Integer.toString(value));
    }

    private EditBox field(int x, int y, int width, String hint, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(16);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void switchPage(int target) {
        if (target == page) return;
        try {
            saveVisibleFields();
            page = target;
            notice = "";
            rebuildWidgets();
        } catch (RuntimeException exception) {
            notice = message(exception);
        }
    }

    private void apply() {
        try {
            saveVisibleFields();
            rules.normalize();
            draft.experience = copyOf(rules);
            if (minecraft != null) minecraft.setScreenAndShow(parent);
        } catch (RuntimeException exception) {
            notice = message(exception);
        }
    }

    private void saveVisibleFields() {
        if (page == 0) {
            rules.rejoinGraceSeconds = integer(rejoinGrace, "Rejoin grace", 10, 900);
            rules.afkTimeoutSeconds = integer(afkTimeout, "AFK timeout", 30, 3600);
            rules.afkWarningSeconds = integer(afkWarning, "AFK warning", 5, 3595);
            if (rules.afkWarningSeconds >= rules.afkTimeoutSeconds)
                throw new IllegalArgumentException("AFK warning must occur before the timeout.");
            rules.overtimeSeconds = integer(overtimeSeconds, "Overtime", 5, 600);
            rules.postGameVoteSeconds = integer(voteSeconds, "Post-game vote time", 5, 120);
        } else {
            rules.performanceBalanceWeight = decimal(balanceWeight, "Balance weight", 0.0D, 1.0D);
            rules.participationExperience = integer(participationXp, "Participation XP", 0, 100000);
            rules.winnerExperience = integer(winnerXp, "Winner XP", 0, 100000);
            rules.objectiveExperienceCap = integer(objectiveXp, "Performance XP cap", 0, 100000);
            rules.weeklyMatchesRequired = integer(weeklyMatchesRequired, "Weekly matches required", 1, 10000);
            rules.weeklyMatchesExperience = integer(weeklyMatchesXp, "Weekly matches XP", 0, 100000);
            rules.weeklyWinsRequired = integer(weeklyWinsRequired, "Weekly wins required", 1, 10000);
            rules.weeklyWinsExperience = integer(weeklyWinsXp, "Weekly wins XP", 0, 100000);
            rules.weeklyContributionRequired = longInteger(weeklyContributionRequired,
                    "Weekly impact required", 1L, 1_000_000_000L);
            rules.weeklyContributionExperience = integer(weeklyContributionXp, "Weekly impact XP", 0, 100000);
        }
    }

    private void updateLabels() {
        if (results != null) results.setMessage(label("Results screen", rules.resultsScreenEnabled));
        if (killFeed != null) killFeed.setMessage(label("Kill feed", rules.killFeedEnabled));
        if (rejoin != null) rejoin.setMessage(label("Disconnect rejoin", rules.rejoinEnabled));
        if (afk != null) afk.setMessage(label("AFK detection", rules.afkDetectionEnabled));
        if (overtime != null) overtime.setMessage(label("Objective overtime", rules.overtimeEnabled));
        if (voting != null) voting.setMessage(label("Rematch / next voting", rules.postGameVotingEnabled));
        if (spectator != null) spectator.setMessage(label("Spectator tools", rules.spectatorToolsEnabled));
        if (summaryMail != null) summaryMail.setMessage(label("Match summary mail", rules.matchSummaryMailEnabled));
        if (balancing != null) balancing.setMessage(label("Performance balancing", rules.performanceBalancingEnabled));
        if (progression != null) progression.setMessage(label("Cosmetic progression", rules.progressionEnabled));
        if (weeklyChallenges != null) weeklyChallenges.setMessage(label("Weekly challenges", rules.weeklyChallengesEnabled));
    }

    private static Component label(String name, boolean enabled) {
        return Component.literal(name + ": " + (enabled ? "ON" : "OFF"));
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "Shared Minigame Experience", x + 286, y + 18, TEXT, true);
        g.fill(x + 12, y + 42, x + W - 12, y + H - 40, SUBPANEL);
        g.outline(x + 12, y + 42, W - 24, H - 82, BORDER);
        if (page == 0) {
            g.text(font, "Match lifecycle", x + 20, y + 142, ACCENT, true);
            g.text(font, "The match starts automatically when preparation time ends. Players may join until RUNNING.",
                    x + 20, y + 158, MUTED, false);
            g.text(font, "Rejoin grace", x + 14, y + 176, MUTED, false);
            g.text(font, "AFK timeout", x + 138, y + 176, MUTED, false);
            g.text(font, "AFK warning", x + 262, y + 176, MUTED, false);
            g.text(font, "Overtime", x + 386, y + 176, MUTED, false);
            g.text(font, "Vote window", x + 510, y + 176, MUTED, false);
            g.text(font, "Rejoin is the disconnect return window; the AFK warning must occur before removal.",
                    x + 20, y + 222, MUTED, false);
            g.text(font, "The final 10 preparation seconds are shown as a large synchronized countdown with sound.",
                    x + 20, y + 240, MUTED, false);
        } else {
            g.text(font, "Progression is cosmetic/account history only and never changes combat power.",
                    x + 20, y + 92, MUTED, false);
            g.text(font, "Balance weight: 0 uses player count; 1 gives historical rating maximum influence.",
                    x + 20, y + 108, MUTED, false);
            g.text(font, "Balance weight", x + 14, y + 128, MUTED, false);
            g.text(font, "Participation XP", x + 146, y + 128, MUTED, false);
            g.text(font, "Winner XP bonus", x + 278, y + 128, MUTED, false);
            g.text(font, "Performance XP cap", x + 410, y + 128, MUTED, false);
            g.text(font, "Weekly cosmetic challenges use shared player progress and these definition-specific thresholds.",
                    x + 270, y + 195, MUTED, false);
            g.text(font, "Matches required", x + 14, y + 230, MUTED, false);
            g.text(font, "Matches XP", x + 146, y + 230, MUTED, false);
            g.text(font, "Wins required", x + 278, y + 230, MUTED, false);
            g.text(font, "Wins XP", x + 410, y + 230, MUTED, false);
            g.text(font, "Impact required", x + 14, y + 286, MUTED, false);
            g.text(font, "Impact XP", x + 212, y + 286, MUTED, false);
            g.text(font, "Turning weekly challenges off stops both progress and weekly XP for this minigame.",
                    x + 20, y + 334, MUTED, false);
            g.text(font, "Quest, statistics, mail and hologram integrations continue to use normal SSU events.",
                    x + 20, y + 352, MUTED, false);
        }
        if (!notice.isBlank()) g.text(font, notice, x + 96, y + H - 24, ERROR, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private static MinigameExperienceRules copyOf(MinigameExperienceRules source) {
        MinigameExperienceRules copy = new MinigameExperienceRules();
        if (source == null) return copy;
        copy.resultsScreenEnabled = source.resultsScreenEnabled;
        copy.killFeedEnabled = source.killFeedEnabled;
        copy.rejoinEnabled = source.rejoinEnabled;
        copy.rejoinGraceSeconds = source.rejoinGraceSeconds;
        copy.afkDetectionEnabled = source.afkDetectionEnabled;
        copy.afkTimeoutSeconds = source.afkTimeoutSeconds;
        copy.afkWarningSeconds = source.afkWarningSeconds;
        copy.overtimeEnabled = source.overtimeEnabled;
        copy.overtimeSeconds = source.overtimeSeconds;
        copy.postGameVotingEnabled = source.postGameVotingEnabled;
        copy.postGameVoteSeconds = source.postGameVoteSeconds;
        copy.spectatorToolsEnabled = source.spectatorToolsEnabled;
        copy.matchSummaryMailEnabled = source.matchSummaryMailEnabled;
        copy.performanceBalancingEnabled = source.performanceBalancingEnabled;
        copy.performanceBalanceWeight = source.performanceBalanceWeight;
        copy.progressionEnabled = source.progressionEnabled;
        copy.participationExperience = source.participationExperience;
        copy.winnerExperience = source.winnerExperience;
        copy.objectiveExperienceCap = source.objectiveExperienceCap;
        copy.weeklyChallengesEnabled = source.weeklyChallengesEnabled;
        copy.weeklyMatchesRequired = source.weeklyMatchesRequired;
        copy.weeklyMatchesExperience = source.weeklyMatchesExperience;
        copy.weeklyWinsRequired = source.weeklyWinsRequired;
        copy.weeklyWinsExperience = source.weeklyWinsExperience;
        copy.weeklyContributionRequired = source.weeklyContributionRequired;
        copy.weeklyContributionExperience = source.weeklyContributionExperience;
        copy.normalize();
        return copy;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static int integer(EditBox box, String label, int min, int max) {
        try {
            int value = Integer.parseInt(box.getValue().trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }
    private static long longInteger(EditBox box, String label, long min, long max) {
        try {
            long value = Long.parseLong(box.getValue().trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }
    private static double decimal(EditBox box, String label, double min, double max) {
        try {
            double value = Double.parseDouble(box.getValue().trim().replace(',', '.'));
            if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }
    private static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static String message(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null ? "The settings could not be applied." : throwable.getMessage();
    }
}
