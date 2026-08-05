package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Separate fallback editor for the generic framework mode. */
final class GenericMinigameEditorScreen extends MinigameEditorScreen {
    private static final int W = 620, H = 330;
    private EditBox id, name, minPlayers, maxPlayers, countdown, duration;
    private boolean enabled, automatic;
    private Button enabledButton, automaticButton;

    GenericMinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(initial, parent, "Generic Minigame Editor");
    }

    @Override protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        id = field(x + 24, y + 76, 250, 64, "Internal ID", draft.id);
        name = field(x + 296, y + 76, 300, 128, "Display name", draft.displayName);
        minPlayers = field(x + 24, y + 150, 120, 4, "Minimum players", Integer.toString(draft.minPlayers));
        maxPlayers = field(x + 158, y + 150, 120, 4, "Maximum players", Integer.toString(draft.maxPlayers));
        countdown = field(x + 296, y + 150, 140, 6, "Countdown", Integer.toString(draft.countdownSeconds));
        duration = field(x + 450, y + 150, 146, 8, "Match duration", Integer.toString(draft.matchDurationSeconds));
        enabled = draft.enabled; automatic = draft.automaticStart;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { enabled = !enabled; labels(); })
                .bounds(x + 24, y + 214, 150, 20).build());
        automaticButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { automatic = !automatic; labels(); })
                .bounds(x + 184, y + 214, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose()).bounds(x + 24, y + H - 38, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save minigame"), ignored -> save()).bounds(x + W - 144, y + H - 38, 120, 20).build()).active = !awaiting;
        labels();
    }

    private void labels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Enabled: " + (enabled ? "Yes" : "No")));
        if (automaticButton != null) automaticButton.setMessage(Component.literal("Automatic start: " + (automatic ? "Yes" : "No")));
    }

    private void save() {
        try {
            draft.id = id.getValue().trim(); draft.displayName = name.getValue().trim();
            draft.minPlayers = parseInt(minPlayers, "Minimum players", 1, 64);
            draft.maxPlayers = parseInt(maxPlayers, "Maximum players", draft.minPlayers, 128);
            draft.countdownSeconds = parseInt(countdown, "Countdown", 0, 600);
            draft.matchDurationSeconds = parseInt(duration, "Match duration", 0, 86_400);
            draft.enabled = enabled; draft.automaticStart = automatic;
            submitDraft();
        } catch (RuntimeException exception) { setNotice(exception.getMessage(), true); }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(0, 0, width, height, 0xA9000000); g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, "Generic Minigame Editor", x + 24, y + 22, TEXT, true);
        g.text(font, "Generic mode has its own editor. Concrete modes such as Spleef use a dedicated tabbed editor.", x + 24, y + 43, MUTED, false);
        label(g, "Internal ID", "Unique technical key used by commands and storage. Avoid changing it after release.", x + 24, y + 58);
        label(g, "Display name", "Player-facing name shown in minigame menus and messages.", x + 296, y + 58);
        label(g, "Minimum players", "Queue size required before the countdown may begin.", x + 24, y + 132);
        label(g, "Maximum players", "Maximum number of simultaneous participants.", x + 158, y + 132);
        label(g, "Countdown seconds", "Waiting time after enough players are ready.", x + 296, y + 132);
        label(g, "Match seconds", "Maximum match duration. Enter 0 for no time limit.", x + 450, y + 132);
        if (!notice.isBlank()) g.text(font, trim(notice, 82), x + 126, y + H - 32, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphicsExtractor g, String title, String help, int x, int y) {
        g.text(font, title, x, y, TEXT, true); g.text(font, trim(help, 38), x, y + 42, MUTED, false);
    }
}
