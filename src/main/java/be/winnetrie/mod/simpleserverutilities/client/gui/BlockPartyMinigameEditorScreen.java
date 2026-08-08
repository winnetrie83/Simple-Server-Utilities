package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import be.winnetrie.mod.simpleserverutilities.minigame.BlockPartyRules;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameArenaDefinition;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dedicated Block Party editor. The Setup Tool owns player spawns and the playfloor selection. */
final class BlockPartyMinigameEditorScreen extends MinigameEditorScreen {
    private static final int W = 650, H = 350;
    private int page;
    private EditBox id, name, icon, minPlayers, maxPlayers, countdown, duration;
    private EditBox palette, initialRound, minimumRound, speedup, dropSeconds, tileSize, eliminationDepth;
    private EditBox arenaId, arenaName, regionId;
    private boolean enabled, automatic, inventoryLock, arenaEnabled;
    private Button enabledButton, automaticButton, inventoryLockButton, arenaEnabledButton;

    BlockPartyMinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(initial, parent, "Block Party Editor");
        afterDraftReloaded();
    }

    @Override protected void afterDraftReloaded() {
        if (draft.blockParty == null) draft.blockParty = new BlockPartyRules();
        if (draft.arenas == null) draft.arenas = new ArrayList<>();
        if (draft.arenas.isEmpty()) draft.arenas.add(new MinigameArenaDefinition());
    }
    private MinigameArenaDefinition arena() { return draft.arenas.getFirst(); }

    @Override protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        String[] tabs = {"General", "Rounds", "Arena / setup"};
        for (int i = 0; i < tabs.length; i++) {
            int target = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(tabs[i]), ignored -> {
                savePage(); page = target; rebuildWidgets();
            }).bounds(x + 16 + i * 112, y + 12, 104, 20).build());
            b.active = page != i;
        }
        if (page == 0) initGeneral(x, y); else if (page == 1) initRules(x, y); else initArena(x, y);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose()).bounds(x + 16, y + H - 30, 78, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save Block Party"), ignored -> { savePage(); submitDraft(); })
                .bounds(x + W - 142, y + H - 30, 126, 20).build());
        save.active = !awaiting;
    }

    private void initGeneral(int x, int y) {
        id = field(x + 16, y + 70, 170, 64, "Internal ID", draft.id); id.setEditable(initial.originalMinigameId().isBlank());
        name = field(x + 198, y + 70, 240, 128, "Display name", draft.displayName);
        icon = field(x + 450, y + 70, 184, 128, "Icon item", draft.iconItem);
        minPlayers = field(x + 16, y + 136, 118, 3, "Min players", Integer.toString(draft.minPlayers));
        maxPlayers = field(x + 146, y + 136, 118, 3, "Max players", Integer.toString(draft.maxPlayers));
        countdown = field(x + 276, y + 136, 118, 6, "Countdown", Integer.toString(draft.countdownSeconds));
        duration = field(x + 406, y + 136, 128, 8, "Duration", Integer.toString(draft.matchDurationSeconds));
        enabled = draft.enabled; automatic = draft.automaticStart; inventoryLock = draft.lockInventory;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { enabled = !enabled; labels(); }).bounds(x + 16, y + 204, 150, 20).build());
        automaticButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { automatic = !automatic; labels(); }).bounds(x + 178, y + 204, 176, 20).build());
        inventoryLockButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { inventoryLock = !inventoryLock; labels(); }).bounds(x + 366, y + 204, 176, 20).build());
        labels();
    }

    private void initRules(int x, int y) {
        BlockPartyRules r = draft.blockParty;
        palette = field(x + 16, y + 70, 618, 2048, "Palette block IDs (comma-separated)", String.join(", ", r.paletteBlocks));
        initialRound = field(x + 16, y + 136, 100, 5, "Start seconds", Integer.toString(r.initialRoundSeconds));
        minimumRound = field(x + 128, y + 136, 100, 5, "Min seconds", Integer.toString(r.minimumRoundSeconds));
        speedup = field(x + 240, y + 136, 110, 12, "Speedup/round", Double.toString(r.speedupSecondsPerRound));
        dropSeconds = field(x + 362, y + 136, 100, 5, "Drop seconds", Integer.toString(r.dropSeconds));
        tileSize = field(x + 474, y + 136, 76, 3, "Tile size", Integer.toString(r.tileSize));
        eliminationDepth = field(x + 562, y + 136, 72, 3, "Fall depth", Integer.toString(r.eliminationDepth));
    }

    private void initArena(int x, int y) {
        MinigameArenaDefinition a = arena();
        arenaId = field(x + 16, y + 72, 150, 64, "Arena ID", a.id);
        arenaName = field(x + 178, y + 72, 220, 128, "Arena name", a.displayName);
        regionId = field(x + 410, y + 72, 224, 128, "Managed region", a.regionId); regionId.setEditable(!a.managedRegion);
        arenaEnabled = a.enabled;
        arenaEnabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { arenaEnabled = !arenaEnabled; labels(); }).bounds(x + 16, y + 132, 160, 20).build());
        labels();
    }

    private void savePage() {
        if (page == 0 && id != null) {
            draft.id = id.getValue().trim(); draft.displayName = name.getValue().trim(); draft.iconItem = icon.getValue().trim();
            draft.minPlayers = parseInt(minPlayers, "Minimum players", 2, 32);
            draft.maxPlayers = parseInt(maxPlayers, "Maximum players", draft.minPlayers, 32);
            draft.countdownSeconds = parseInt(countdown, "Countdown", 0, 600);
            draft.matchDurationSeconds = parseInt(duration, "Match duration", 0, 86_400);
            draft.enabled = enabled; draft.automaticStart = automatic; draft.lockInventory = inventoryLock;
        } else if (page == 1 && palette != null) {
            BlockPartyRules r = draft.blockParty;
            r.paletteBlocks = Arrays.stream(palette.getValue().split("[,\\n]")).map(String::trim).filter(v -> !v.isBlank())
                    .distinct().limit(BlockPartyRules.MAX_PALETTE).collect(Collectors.toCollection(ArrayList::new));
            r.initialRoundSeconds = parseInt(initialRound, "Initial round seconds", 2, 60);
            r.minimumRoundSeconds = parseInt(minimumRound, "Minimum round seconds", 1, r.initialRoundSeconds);
            r.speedupSecondsPerRound = parseDouble(speedup, "Speedup per round");
            r.dropSeconds = parseInt(dropSeconds, "Drop seconds", 1, 30);
            r.tileSize = parseInt(tileSize, "Tile size", 1, 8);
            r.eliminationDepth = parseInt(eliminationDepth, "Elimination depth", 1, 64);
        } else if (page == 2 && arenaId != null) {
            MinigameArenaDefinition a = arena(); a.id = arenaId.getValue().trim(); a.displayName = arenaName.getValue().trim();
            if (!a.managedRegion) a.regionId = regionId.getValue().trim(); a.enabled = arenaEnabled;
        }
    }

    private void labels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Enabled: " + yes(enabled)));
        if (automaticButton != null) automaticButton.setMessage(Component.literal("Automatic start: " + yes(automatic)));
        if (inventoryLockButton != null) inventoryLockButton.setMessage(Component.literal("Inventory lock: " + yes(inventoryLock)));
        if (arenaEnabledButton != null) arenaEnabledButton.setMessage(Component.literal("Arena enabled: " + yes(arenaEnabled)));
    }
    private static String yes(boolean value) { return value ? "Yes" : "No"; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(0, 0, width, height, 0xA9000000); g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, "Block Party Editor", x + 16, y + 42, TEXT, true);
        if (page == 0) {
            g.text(font, "Free-for-all elimination mode. Every participant gets an independent player slot.", x + 16, y + 54, MUTED, false);
            g.text(font, "Countdown and match duration are in seconds.", x + 16, y + 122, MUTED, false);
        } else if (page == 1) {
            g.text(font, "Use registered block IDs. At least two palette blocks are required.", x + 16, y + 54, MUTED, false);
            g.text(font, "Round/drop values are seconds; speedup is seconds removed after each round.", x + 16, y + 122, MUTED, false);
        } else {
            MinigameArenaDefinition a = arena();
            g.text(font, "Use the Minigame Setup Tool for the dance floor, lobby, spectator and player spawns.", x + 16, y + 54, MUTED, false);
            g.text(font, "Dance floor: " + (a.playFloor.configured() ? a.playFloor.compact() : "Not configured"), x + 16, y + 172, TEXT, false);
            g.text(font, "Player spawns: " + a.teamSpawns.size() + " • Reset snapshot: " + (a.resetRegionAfterMatch ? "ready" : "not ready"), x + 16, y + 192, MUTED, false);
        }
        if (!notice.isBlank()) g.text(font, trim(notice, 82), x + 110, y + H - 24, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
}
