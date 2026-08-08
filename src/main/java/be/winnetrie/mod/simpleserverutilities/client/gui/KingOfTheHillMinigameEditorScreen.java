package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;

import be.winnetrie.mod.simpleserverutilities.minigame.KingOfTheHillRules;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameArenaDefinition;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dedicated editor for King of the Hill. Every arena is either STATIC or ROTATING. */
final class KingOfTheHillMinigameEditorScreen extends MinigameEditorScreen {
    private static final int W = 590, H = 330;
    private int page;
    private int arenaIndex;
    private EditBox id, name, icon, minPlayers, maxPlayers, countdown, duration, respawn;
    private EditBox scoreToWin, radius, interval, points, weapon, redName, blueName;
    private EditBox controlSweep, rotationInterval, rotationWarning;
    private EditBox arenaId, arenaName, regionId;
    private boolean enabled, automatic, inventoryLock, friendlyFire, arenaEnabled;
    private Button enabledButton, automaticButton, inventoryLockButton, friendlyFireButton, arenaEnabledButton, modeButton;

    KingOfTheHillMinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(initial, parent, "King of the Hill Editor");
        afterDraftReloaded();
    }

    @Override protected void afterDraftReloaded() {
        if (draft.kingOfTheHill == null) draft.kingOfTheHill = new KingOfTheHillRules();
        if (draft.arenas == null) draft.arenas = new ArrayList<>();
        if (draft.arenas.isEmpty()) draft.arenas.add(new MinigameArenaDefinition());
        arenaIndex = Math.max(0, Math.min(arenaIndex, draft.arenas.size() - 1));
    }

    private MinigameArenaDefinition arena() { return draft.arenas.get(arenaIndex); }

    @Override protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        String[] tabs = {"General", "Hill rules", "Arena / setup"};
        for (int i = 0; i < tabs.length; i++) {
            int target = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(tabs[i]), ignored -> {
                savePage(); page = target; rebuildWidgets();
            }).bounds(x + 16 + i * 106, y + 12, 98, 20).build());
            b.active = page != i;
        }
        if (page == 0) initGeneral(x, y);
        else if (page == 1) initRules(x, y);
        else initArena(x, y);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 16, y + H - 30, 78, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save KOTH"), ignored -> {
            savePage(); submitDraft();
        }).bounds(x + W - 110, y + H - 30, 94, 20).build());
        save.active = !awaiting;
    }

    private void initGeneral(int x, int y) {
        id = field(x + 16, y + 86, 130, 64, "Internal ID", draft.id);
        id.setEditable(initial.originalMinigameId().isBlank());
        name = field(x + 156, y + 86, 210, 128, "Display name", draft.displayName);
        icon = field(x + 376, y + 86, 198, 128, "Icon item", draft.iconItem);

        minPlayers = field(x + 16, y + 148, 72, 3, "Min players", Integer.toString(draft.minPlayers));
        maxPlayers = field(x + 98, y + 148, 72, 3, "Max players", Integer.toString(draft.maxPlayers));
        countdown = field(x + 180, y + 148, 82, 6, "Countdown", Integer.toString(draft.countdownSeconds));
        duration = field(x + 272, y + 148, 102, 8, "Duration", Integer.toString(draft.matchDurationSeconds));
        respawn = field(x + 384, y + 148, 82, 5, "Respawn delay", Integer.toString(draft.respawnDelaySeconds));

        enabled = draft.enabled; automatic = draft.automaticStart; inventoryLock = draft.lockInventory;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { enabled = !enabled; labels(); })
                .bounds(x + 16, y + 208, 136, 20).build());
        automaticButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { automatic = !automatic; labels(); })
                .bounds(x + 162, y + 208, 160, 20).build());
        inventoryLockButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { inventoryLock = !inventoryLock; labels(); })
                .bounds(x + 332, y + 208, 160, 20).build());
        labels();
    }

    private void initRules(int x, int y) {
        KingOfTheHillRules r = draft.kingOfTheHill;
        MinigameArenaDefinition a = arena();
        scoreToWin = field(x + 16, y + 90, 76, 9, "Score", Integer.toString(r.scoreToWin));
        radius = field(x + 102, y + 90, 68, 16, "Radius", Double.toString(r.hillRadius));
        interval = field(x + 180, y + 90, 82, 5, "Interval", Integer.toString(r.scoreIntervalSeconds));
        points = field(x + 272, y + 90, 82, 8, "Points", Integer.toString(r.pointsPerInterval));
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            a.kothMode = a.rotatingHill() ? "static" : "rotating";
            savePage(); rebuildWidgets();
        }).bounds(x + 364, y + 90, 160, 20).build());

        if (a.rotatingHill()) {
            rotationInterval = field(x + 16, y + 148, 92, 5, "Rotation seconds", Integer.toString(r.rotationIntervalSeconds));
            rotationWarning = field(x + 118, y + 148, 92, 5, "Warning seconds", Integer.toString(r.rotationWarningSeconds));
        } else {
            controlSweep = field(x + 16, y + 148, 92, 5, "Sweep seconds", Integer.toString(r.controlSweepSeconds));
        }

        weapon = field(x + 16, y + 206, 250, 128, "Weapon item", r.weaponItem);
        redName = field(x + 276, y + 206, 132, 32, "Red team", r.team1Name);
        blueName = field(x + 418, y + 206, 156, 32, "Blue team", r.team2Name);
        friendlyFire = r.allowFriendlyFire;
        friendlyFireButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { friendlyFire = !friendlyFire; labels(); })
                .bounds(x + 16, y + 246, 160, 20).build());
        labels();
    }

    private void initArena(int x, int y) {
        MinigameArenaDefinition a = arena();
        arenaId = field(x + 16, y + 94, 128, 64, "Arena ID", a.id);
        arenaName = field(x + 154, y + 94, 196, 128, "Arena name", a.displayName);
        regionId = field(x + 360, y + 94, 214, 128, "Arena Region", a.regionId);
        regionId.setEditable(!a.managedRegion);
        arenaEnabled = a.enabled;
        arenaEnabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { arenaEnabled = !arenaEnabled; labels(); })
                .bounds(x + 16, y + 144, 150, 20).build());
        if (draft.arenas.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> switchArena(-1)).bounds(x + 180, y + 144, 28, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> switchArena(1)).bounds(x + 214, y + 144, 28, 20).build());
        }
        labels();
    }

    private void switchArena(int delta) {
        savePage();
        arenaIndex = Math.floorMod(arenaIndex + delta, draft.arenas.size());
        rebuildWidgets();
    }

    private void savePage() {
        if (page == 0 && id != null) {
            draft.id = id.getValue().trim(); draft.displayName = name.getValue().trim(); draft.iconItem = icon.getValue().trim();
            draft.minPlayers = parseInt(minPlayers, "Minimum players", 2, 64);
            draft.maxPlayers = parseInt(maxPlayers, "Maximum players", draft.minPlayers, 64);
            draft.countdownSeconds = parseInt(countdown, "Countdown", 0, 600);
            draft.matchDurationSeconds = parseInt(duration, "Match duration", 0, 86_400);
            draft.respawnDelaySeconds = parseInt(respawn, "Respawn delay", 1, 300);
            draft.enabled = enabled; draft.automaticStart = automatic; draft.lockInventory = inventoryLock;
        } else if (page == 1 && scoreToWin != null) {
            KingOfTheHillRules r = draft.kingOfTheHill;
            r.scoreToWin = parseInt(scoreToWin, "Score to win", 10, 1_000_000);
            r.hillRadius = parseDouble(radius, "Hill radius");
            r.scoreIntervalSeconds = parseInt(interval, "Score interval", 1, 60);
            r.pointsPerInterval = parseInt(points, "Points per interval", 1, 10_000);
            if (controlSweep != null) r.controlSweepSeconds = parseInt(controlSweep, "Neutral control push", 2, 60);
            if (rotationInterval != null) r.rotationIntervalSeconds = parseInt(rotationInterval, "Rotation interval", 15, 900);
            if (rotationWarning != null) r.rotationWarningSeconds = parseInt(rotationWarning, "Rotation warning", 0, 899);
            r.weaponItem = weapon.getValue().trim(); r.team1Name = redName.getValue().trim(); r.team2Name = blueName.getValue().trim();
            r.allowFriendlyFire = friendlyFire;
        } else if (page == 2 && arenaId != null) {
            MinigameArenaDefinition a = arena();
            a.id = arenaId.getValue().trim(); a.displayName = arenaName.getValue().trim();
            if (!a.managedRegion) a.regionId = regionId.getValue().trim();
            a.enabled = arenaEnabled;
        }
    }

    private void labels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Enabled: " + yes(enabled)));
        if (automaticButton != null) automaticButton.setMessage(Component.literal("Automatic start: " + yes(automatic)));
        if (inventoryLockButton != null) inventoryLockButton.setMessage(Component.literal("Inventory lock: " + yes(inventoryLock)));
        if (friendlyFireButton != null) friendlyFireButton.setMessage(Component.literal("Friendly fire: " + yes(friendlyFire)));
        if (arenaEnabledButton != null) arenaEnabledButton.setMessage(Component.literal("Arena enabled: " + yes(arenaEnabled)));
        if (modeButton != null) modeButton.setMessage(Component.literal("Hill mode: " + (arena().rotatingHill() ? "ROTATING" : "STATIC")));
    }
    private static String yes(boolean value) { return value ? "Yes" : "No"; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(0, 0, width, height, 0xA9000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "King of the Hill Editor", x + 16, y + 42, TEXT, true);
        if (page == 0) {
            g.text(font, "Configure queue, timing and the combat loadout.", x + 16, y + 56, MUTED, false);
            label(g, "Internal ID", x + 16, y + 74); label(g, "Display name", x + 156, y + 74); label(g, "Icon item", x + 376, y + 74);
            label(g, "Min players", x + 16, y + 136); label(g, "Max players", x + 98, y + 136);
            label(g, "Countdown (sec)", x + 180, y + 136); label(g, "Match duration (sec)", x + 272, y + 136); label(g, "Respawn (sec)", x + 384, y + 136);
        } else if (page == 1) {
            MinigameArenaDefinition a = arena();
            g.text(font, a.rotatingHill()
                    ? "ROTATING: the active hill changes between authored hill points."
                    : "STATIC: team presence pushes a persistent control marker.", x + 16, y + 56, MUTED, false);
            label(g, "Score to win", x + 16, y + 78); label(g, "Radius", x + 102, y + 78);
            label(g, "Score every (sec)", x + 180, y + 78); label(g, "Points / tick", x + 272, y + 78); label(g, "Arena hill mode", x + 364, y + 78);
            if (a.rotatingHill()) {
                label(g, "Rotate every (sec)", x + 16, y + 136); label(g, "Warning (sec)", x + 118, y + 136);
                g.text(font, "Author at least 2 hill points with the Setup Tool.", x + 220, y + 154, MUTED, false);
            } else {
                label(g, "Neutral push (sec)", x + 16, y + 136);
                g.text(font, "40% red • 20% neutral • 40% blue. Majority presence moves the marker.", x + 118, y + 154, MUTED, false);
            }
            label(g, "Weapon item", x + 16, y + 194); label(g, "Red team name", x + 276, y + 194); label(g, "Blue team name", x + 418, y + 194);
        } else {
            MinigameArenaDefinition a = arena();
            g.text(font, "World geometry is edited with the SSU Minigame Setup Tool.", x + 16, y + 56, MUTED, false);
            label(g, "Arena ID", x + 16, y + 82); label(g, "Arena name", x + 154, y + 82); label(g, "Arena Region", x + 360, y + 82);
            g.text(font, "Arena " + (arenaIndex + 1) + " / " + draft.arenas.size(), x + 258, y + 150, MUTED, false);
            g.text(font, "Mode: " + (a.rotatingHill() ? "ROTATING" : "STATIC"), x + 16, y + 184, TEXT, true);
            if (a.rotatingHill()) g.text(font, "Hill points: " + a.hillPoints.size() + " (minimum 2)", x + 16, y + 203, TEXT, false);
            else g.text(font, "Hill center: " + coordinate(a.hillCenter.x) + ", " + coordinate(a.hillCenter.y) + ", " + coordinate(a.hillCenter.z), x + 16, y + 203, TEXT, false);
            g.text(font, "Team/player spawns: " + a.teamSpawns.size() + " • Reset snapshot: " + (a.resetRegionAfterMatch ? "ready" : "not ready"), x + 16, y + 222, MUTED, false);
        }
        if (!notice.isBlank()) g.text(font, trim(notice, 72), x + 106, y + H - 24, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphicsExtractor g, String text, int x, int y) { g.text(font, text, x, y, MUTED, false); }
}
