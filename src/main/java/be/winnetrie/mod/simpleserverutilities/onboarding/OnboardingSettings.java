package be.winnetrie.mod.simpleserverutilities.onboarding;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;

/** Persistent server-wide first-join and rules configuration. */
public final class OnboardingSettings {
    public static final int SCHEMA_VERSION = 1;
    public int schemaVersion = SCHEMA_VERSION;
    public boolean enabled;
    public boolean requireRules = true;
    public boolean introductionSkippable = true;
    public boolean welcomeFireworks = true;
    public String welcomeTitle = "Welcome!";
    public String welcomeSubtitle = "Please read and accept the server rules.";
    public String rules = "§6Server Rules\n§fBe respectful. Do not grief, cheat, or exploit bugs.";
    public List<String> introductionPages = new ArrayList<>(List.of(
            "§6Welcome to the server!\n§fPress the buttons below to learn the basics.",
            "§eOpen SSU\n§fPress your configured SSU menu key to access travel, mail, claims and more."
    ));

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        welcomeTitle = plain(welcomeTitle, 96, "Welcome!");
        welcomeSubtitle = plain(welcomeSubtitle, 192, "Please read and accept the server rules.");
        rules = HologramRichText.normalize(rules);
        if (introductionPages == null) introductionPages = new ArrayList<>();
        ArrayList<String> clean = new ArrayList<>();
        for (String page : introductionPages) {
            if (clean.size() >= 16) break;
            String normalized = HologramRichText.normalize(page);
            if (!normalized.isBlank()) clean.add(normalized);
        }
        introductionPages = clean;
    }

    private static String plain(String value, int maximum, String fallback) {
        String result = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (result.isBlank()) result = fallback;
        return result.length() > maximum ? result.substring(0, maximum) : result;
    }
}
