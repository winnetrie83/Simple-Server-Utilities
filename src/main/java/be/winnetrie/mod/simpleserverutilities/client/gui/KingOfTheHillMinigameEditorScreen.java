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
    private static final int W = 640, H = 338;
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
            }).bounds(x + 16 + i * 112, y + 12, 104, 20).build());
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
        id = field(x + 16, y + 68, 170, 64, "Internal ID", draft.id);
        id.setEditable(initial.originalMinigameId().isBlank());
        name = field(x + 198, y + 68, 236, 128, "Display name", draft.displayName);
        icon = field(x + 446, y + 68, 178, 128, "Icon item", draft.iconItem);
        minPlayers = field(x + 16, y + 132, 110, 3, "Min players", Integer.toString(draft.minPlayers));
        maxPlayers = field(x + 138, y + 132, 110, 3, "Max players", Integer.toString(draft.maxPlayers));
        countdown = field(x + 260, y + 132, 110, 6, "Countdown", Integer.toString(draft.countdownSeconds));
        duration = field(x + 382, y + 132, 110, 8, "Duration", Integer.toString(draft.matchDurationSeconds));
        respawn = field(x + 504, y + 132, 120, 5, "Respawn delay", Integer.toString(draft.respawnDelaySeconds));
        enabled = draft.enabled; automatic = draft.automaticStart; inventoryLock = draft.lockInventory;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { enabled = !enabled; labels(); })
                .bounds(x + 16, y + 202, 150, 20).build());
        automaticButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { automatic = !automatic; labels(); })
                .bounds(x + 178, y + 202, 176, 20).build());
        inventoryLockButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { inventoryLock = !inventoryLock; labels(); })
                .bounds(x + 366, y + 202, 176, 20).build());
        labels();
    }

    private void initRules(int x, int y) {
        KingOfTheHillRules r = draft.kingOfTheHill;
        MinigameArenaDefinition a = arena();
        scoreToWin = field(x + 16, y + 82, 106, 9, "Score", Integer.toString(r.scoreToWin));
        radius = field(x + 134, y + 82, 94, 16, "Radius", Double.toString(r.hillRadius));
        interval = field(x + 240, y + 82, 104, 5, "Interval", Integer.toString(r.scoreIntervalSeconds));
        points = field(x + 356, y + 82, 104, 8, "Points", Integer.toString(r.pointsPerInterval));
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            a.kothMode = a.rotatingHill() ? "static" : "rotating";
            savePage(); rebuildWidgets();
        }).bounds(x + 472, y + 82, 152, 20).build());

        if (a.rotatingHill()) {
            rotationInterval = field(x + 16, y + 140, 118, 5, "Rotation seconds", Integer.toString(r.rotationIntervalSeconds));
            rotationWarning = field(x + 146, y + 140, 118, 5, "Warning seconds", Integer.toString(r.rotationWarningSeconds));
        } else {
            controlSweep = field(x + 16, y + 140, 118, 5, "Sweep seconds", Integer.toString(r.controlSweepSeconds));
        }
        weapon = field(x + 16, y + 202, 284, 128, "Weapon item", r.weaponItem);
        redName = field(x + 312, y + 202, 146, 32, "Red team", r.team1Name);
        blueName = field(x + 470, y + 202, 154, 32, "Blue team", r.team2Name);
        friendlyFire = r.allowFriendlyFire;
        friendlyFireButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { friendlyFire = !friendlyFire; labels(); })
                .bounds(x + 16, y + 250, 176, 20).build());
        labels();
    }

    private void initArena(int x, int y) {
        MinigameArenaDefinition a = arena();
        arenaId = field(x + 16, y + 72, 150, 64, "Arena ID", a.id);
        arenaName = field(x + 178, y + 72, 220, 128, "Arena name", a.displayName);
        regionId = field(x + 410, y + 72, 214, 128, "Managed region", a.regionId);
        regionId.setEditable(!a.managedRegion);
        arenaEnabled = a.enabled;
        arenaEnabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { arenaEnabled = !arenaEnabled; labels(); })
                .bounds(x + 16, y + 132, 160, 20).build());
        if (draft.arenas.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> switchArena(-1)).bounds(x + 190, y + 132, 28, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> switchArena(1)).bounds(x + 224, y + 132, 28, 20).build());
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
        g.fill(0, 0, width, height, 0xA9000000); g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, "King of the Hill Editor", x + 16, y + 42, TEXT, true);
        if (page == 0) {
            g.text(font, "Configure queue, timing and the combat loadout.", x + 16, y + 54, MUTED, false);
            label(g, "Internal ID", x + 16, y + 58); label(g, "Display name", x + 198, y + 58); label(g, "Icon item", x + 446, y + 58);
            label(g, "Min players", x + 16, y + 120); label(g, "Max players", x + 138, y + 120);
            label(g, "Countdown (sec)", x + 260, y + 120); label(g, "Match duration (sec)", x + 382, y + 120); label(g, "Respawn (sec)", x + 504, y + 120);
        } else if (page == 1) {
            MinigameArenaDefinition a = arena();
            g.text(font, a.rotatingHill() ? "ROTATING: the active hill changes between authored hill points." : "STATIC: team presence pushes a persistent control marker.", x + 16, y + 48, MUTED, false);
            label(g, "Score to win", x + 16, y + 70); label(g, "Hill radius", x + 134, y + 70);
            label(g, "Score interval (sec)", x + 240, y + 70); label(g, "Points / interval", x + 356, y + 70); label(g, "Arena hill mode", x + 472, y + 70);
            if (a.rotatingHill()) {
                label(g, "Rotate every (sec)", x + 16, y + 128); label(g, "Warning (sec)", x + 146, y + 128);
                g.text(font, "Use the Setup Tool Hill point selector to author at least 2 points.", x + 278, y + 145, MUTED, false);
            } else {
                label(g, "Neutral push (sec)", x + 16, y + 128);
                g.text(font, "Control bar: 40% red • 20% neutral • 40% blue. Majority presence pushes the marker.", x + 146, y + 145, MUTED, false);
            }
            label(g, "Weapon item", x + 16, y + 190); label(g, "Red team name", x + 312, y + 190); label(g, "Blue team name", x + 470, y + 190);
        } else {
            MinigameArenaDefinition a = arena();
            g.text(font, "World geometry is edited with the SSU Minigame Setup Tool.", x + 16, y + 54, MUTED, false);
            label(g, "Arena ID", x + 16, y + 60); label(g, "Arena name", x + 178, y + 60); label(g, "Arena Region", x + 410, y + 60);
            g.text(font, "Arena " + (arenaIndex + 1) + " / " + draft.arenas.size(), x + 270, y + 137, MUTED, false);
            g.text(font, "Mode: " + (a.rotatingHill() ? "ROTATING" : "STATIC"), x + 16, y + 172, TEXT, true);
            if (a.rotatingHill()) g.text(font, "Hill points: " + a.hillPoints.size() + " (minimum 2)", x + 16, y + 190, TEXT, false);
            else g.text(font, "Hill center: " + coordinate(a.hillCenter.x) + ", " + coordinate(a.hillCenter.y) + ", " + coordinate(a.hillCenter.z), x + 16, y + 190, TEXT, false);
            g.text(font, "Team/player spawns: " + a.teamSpawns.size() + " • Reset snapshot: " + (a.resetRegionAfterMatch ? "ready" : "not ready"), x + 16, y + 210, MUTED, false);
        }
        if (!notice.isBlank()) g.text(font, trim(notice, 82), x + 116, y + H - 24, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphicsExtractor g, String text, int x, int y) { g.text(font, text, x, y, MUTED, false); }
}
