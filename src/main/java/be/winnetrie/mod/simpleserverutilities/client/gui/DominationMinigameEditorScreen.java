package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameArenaDefinition;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameLocation;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameRewardSet;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSpawnPoint;
import be.winnetrie.mod.simpleserverutilities.minigame.DominationRules;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameControlPoint;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameRewardCapturePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Dedicated, compact administrator-facing Domination editor. */
final class DominationMinigameEditorScreen extends MinigameEditorScreen {
    private static final int W = 645, H = 352;
    private static final int TAB_Y = 10;
    private static final String[] TABS = {"General", "Arena", "Team spawns", "Rewards", "Nodes", "Rules"};
    private static final int[] TAB_WIDTHS = {78, 66, 100, 70, 62, 62};

    private int page;
    private int arenaIndex;
    private int spawnIndex;
    private boolean winnerRewards;
    private int participationActionIndex = -1;
    private int winnerActionIndex = -1;
    /** Server inventory slot currently held as a non-consuming ghost copy. */
    private int carriedInventorySlot = -1;

    // General
    private EditBox id, name, icon, minPlayers, maxPlayers, countdown, duration, postGame;
    private MultiLineEditBox description;
    private String descriptionValue = "";
    private boolean enabled, automaticStart;
    private Button enabledButton, automaticButton;

    // Arena
    private EditBox arenaId, arenaName, regionId;
    private boolean arenaEnabled;
    private Button arenaEnabledButton;
    private EditBox lobbyDimension, lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch;
    private EditBox spectatorDimension, spectatorX, spectatorY, spectatorZ, spectatorYaw, spectatorPitch;

    // Spawn
    private EditBox spawnDimension, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch;
    private Button spawnTeamButton;
    private int selectedSpawnTeam = 1;

    // Rewards
    private EditBox rewardMoney, actionParameters;
    private Button actionTypeButton;
    private String selectedActionType = "grant_permission";

    // Capture nodes
    private int nodeIndex;
    private EditBox nodeId, nodeName, nodeDimension, nodeX, nodeY, nodeZ, nodeYaw, nodePitch;

    // Team and scoring rules
    private int ruleTeam = 1;
    private Button ruleTeamButton, friendlyFireButton;
    private EditBox teamName, teamColor, teamBanner, neutralBanner;
    private EditBox scoreToWin, claimCastSeconds, captureDelaySeconds, scoreInterval, pointsPerNode, weaponItem;
    private boolean allowFriendlyFire;

    DominationMinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(initial, parent, "Domination Editor");
        afterDraftReloaded();
    }

    @Override protected void afterDraftReloaded() {
        if (draft.arenas == null) draft.arenas = new ArrayList<>();
        if (draft.arenas.isEmpty()) draft.arenas.add(new MinigameArenaDefinition());
        if (draft.domination == null) draft.domination = new DominationRules();
        if (draft.participationReward == null) draft.participationReward = new MinigameRewardSet();
        if (draft.winnerReward == null) draft.winnerReward = new MinigameRewardSet();
        arenaIndex = Math.max(0, Math.min(arenaIndex, draft.arenas.size() - 1));
        MinigameArenaDefinition arena = arena();
        if (arena.teamSpawns == null) arena.teamSpawns = new ArrayList<>();
        if (arena.teamSpawns.isEmpty()) {
            arena.teamSpawns.add(new MinigameSpawnPoint(1, new MinigameLocation()));
            arena.teamSpawns.add(new MinigameSpawnPoint(2, new MinigameLocation()));
        }
        if (arena.controlPoints == null) arena.controlPoints = new ArrayList<>();
        ensureNodes(arena);
        spawnIndex = Math.max(0, Math.min(spawnIndex, arena.teamSpawns.size() - 1));
        nodeIndex = Math.max(0, Math.min(nodeIndex, arena.controlPoints.size() - 1));
        selectedSpawnTeam = Math.max(1, Math.min(2, arena.teamSpawns.get(spawnIndex).team));
        participationActionIndex = normalizeActionIndex(participationActionIndex, draft.participationReward.directActions);
        winnerActionIndex = normalizeActionIndex(winnerActionIndex, draft.winnerReward.directActions);
        if (carriedInventorySlot >= 0 && clientInventoryItem(carriedInventorySlot).isEmpty()) carriedInventorySlot = -1;
    }

    @Override protected void init() {
        int left = left(), top = top();
        int x = left + 14;
        for (int index = 0; index < TABS.length; index++) {
            int target = index;
            Button tab = addRenderableWidget(Button.builder(Component.literal(TABS[index]), ignored -> switchPage(target))
                    .bounds(x, top + TAB_Y, TAB_WIDTHS[index], 20).build());
            tab.active = page != target;
            x += TAB_WIDTHS[index] + 5;
        }
        if (page == 0) initGeneral(left, top);
        else if (page == 1) initArena(left, top);
        else if (page == 2) initSpawns(left, top);
        else if (page == 3) initRewards(left, top);
        else if (page == 4) initNodes(left, top);
        else initRules(left, top);

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(left + 14, top + H - 30, 78, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save Domination"), ignored -> saveAll())
                .bounds(left + W - 108, top + H - 30, 94, 20).build());
        save.active = !awaiting;
    }

    private void initGeneral(int left, int top) {
        id = field(left + 14, top + 58, 170, 64, "Internal ID", draft.id);
        id.setEditable(initial.originalMinigameId().isBlank());
        name = field(left + 196, top + 58, 220, 128, "Display name", draft.displayName);
        icon = field(left + 428, top + 58, 203, 128, "Menu icon item", draft.iconItem);
        descriptionValue = draft.description == null ? "" : draft.description;
        description = MultiLineEditBox.builder().setX(left + 14).setY(top + 116)
                .setPlaceholder(Component.literal("Player-facing description"))
                .setShowBackground(true).setShowDecorations(true)
                .build(font, 617, 54, Component.literal("Description"));
        description.setCharacterLimit(8_192); description.setLineLimit(24); description.setValue(descriptionValue);
        description.setValueListener(value -> descriptionValue = value); addRenderableWidget(description);
        minPlayers = field(left + 14, top + 208, 112, 3, "Minimum players", Integer.toString(draft.minPlayers));
        maxPlayers = field(left + 134, top + 208, 112, 3, "Maximum players", Integer.toString(draft.maxPlayers));
        countdown = field(left + 254, top + 208, 112, 6, "Countdown", Integer.toString(draft.countdownSeconds));
        duration = field(left + 374, top + 208, 112, 8, "Match duration", Integer.toString(draft.matchDurationSeconds));
        postGame = field(left + 494, top + 208, 137, 6, "Post-game duration", Integer.toString(draft.postGameSeconds));
        enabled = draft.enabled; automaticStart = draft.automaticStart;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { enabled = !enabled; updateLabels(); })
                .bounds(left + 14, top + 270, 146, 20).build());
        automaticButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { automaticStart = !automaticStart; updateLabels(); })
                .bounds(left + 170, top + 270, 174, 20).build());
        updateLabels();
    }

    private void initArena(int left, int top) {
        MinigameArenaDefinition arena = arena();
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> switchArena(-1)).bounds(left + 14, top + 44, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> switchArena(1)).bounds(left + 50, top + 44, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add arena"), ignored -> addArena()).bounds(left + 90, top + 44, 86, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete arena"), ignored -> deleteArena())
                .bounds(left + 182, top + 44, 94, 20).build());
        delete.active = draft.arenas.size() > 1 && !arena.managedRegion;
        arenaId = field(left + 14, top + 92, 150, 64, "Arena ID", arena.id);
        arenaName = field(left + 176, top + 92, 220, 128, "Arena display name", arena.displayName);
        regionId = field(left + 408, top + 92, 223, 128, "Arena region", arena.regionId);
        regionId.setEditable(!arena.managedRegion);
        arenaEnabled = arena.enabled;
        arenaEnabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { arenaEnabled = !arenaEnabled; updateLabels(); })
                .bounds(left + 14, top + 136, 144, 20).build());

        locationFields(left + 14, top + 196, arena.lobby, true);
        addRenderableWidget(Button.builder(Component.literal("Use my position"), ignored -> fillCurrent(
                lobbyDimension, lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch))
                .bounds(left + 527, top + 196, 104, 20).build());
        locationFields(left + 14, top + 272, arena.spectator, false);
        addRenderableWidget(Button.builder(Component.literal("Use my position"), ignored -> fillCurrent(
                spectatorDimension, spectatorX, spectatorY, spectatorZ, spectatorYaw, spectatorPitch))
                .bounds(left + 527, top + 272, 104, 20).build());
        updateLabels();
    }

    private void locationFields(int x, int y, MinigameLocation location, boolean lobby) {
        EditBox dimension = field(x, y, 145, 128, "Dimension", location.dimension);
        EditBox fx = field(x + 153, y, 64, 24, "X", coordinate(location.x));
        EditBox fy = field(x + 225, y, 64, 24, "Y", coordinate(location.y));
        EditBox fz = field(x + 297, y, 64, 24, "Z", coordinate(location.z));
        EditBox yaw = field(x + 369, y, 64, 16, "Yaw", angle(location.yaw));
        EditBox pitch = field(x + 441, y, 64, 16, "Pitch", angle(location.pitch));
        if (lobby) { lobbyDimension=dimension;lobbyX=fx;lobbyY=fy;lobbyZ=fz;lobbyYaw=yaw;lobbyPitch=pitch; }
        else { spectatorDimension=dimension;spectatorX=fx;spectatorY=fy;spectatorZ=fz;spectatorYaw=yaw;spectatorPitch=pitch; }
    }

    private void initSpawns(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> switchSpawn(-1)).bounds(left + 14, top + 48, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> switchSpawn(1)).bounds(left + 50, top + 48, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add team spawn"), ignored -> addSpawn()).bounds(left + 90, top + 48, 118, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete spawn"), ignored -> deleteSpawn())
                .bounds(left + 214, top + 48, 100, 20).build());
        delete.active = arena().teamSpawns.stream().filter(value -> value.team == spawn().team).count() > 1;
        selectedSpawnTeam = Math.max(1, Math.min(2, spawn().team));
        spawnTeamButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleSpawnTeam())
                .bounds(left + 326, top + 48, 150, 20).build());
        MinigameLocation location = spawn().location;
        spawnDimension = field(left + 14, top + 132, 180, 128, "Dimension", location.dimension);
        spawnX = field(left + 206, top + 132, 72, 24, "X", coordinate(location.x));
        spawnY = field(left + 290, top + 132, 72, 24, "Y", coordinate(location.y));
        spawnZ = field(left + 374, top + 132, 72, 24, "Z", coordinate(location.z));
        spawnYaw = field(left + 458, top + 132, 82, 16, "Yaw", angle(location.yaw));
        spawnPitch = field(left + 552, top + 132, 79, 16, "Pitch", angle(location.pitch));
        addRenderableWidget(Button.builder(Component.literal("Use my current position and view"), ignored -> fillCurrent(
                spawnDimension, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch))
                .bounds(left + 14, top + 188, 218, 20).build());
        updateLabels();
    }

    private void initRewards(int left, int top) {
        MinigameRewardSet rewards = rewards();
        addRenderableWidget(Button.builder(Component.literal(winnerRewards ? "Winner reward" : "Participation reward"), ignored -> switchRewardGroup())
                .bounds(left + 14, top + 48, 190, 20).build());
        rewardMoney = field(left + 216, top + 48, 120, 32, "Money amount", formatMoney(rewards.moneyMinor));
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> switchAction(-1)).bounds(left + 14, top + 218, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> switchAction(1)).bounds(left + 50, top + 218, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add direct reward"), ignored -> addAction()).bounds(left + 88, top + 218, 126, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete direct reward"), ignored -> deleteAction())
                .bounds(left + 220, top + 218, 138, 20).build());
        delete.active = action() != null;
        ContentAction action = action();
        selectedActionType = action == null ? firstActionType() : normalizedActionType(action.type());
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> cycleActionType(-1))
                .bounds(left + 14, top + 266, 26, 20).build());
        actionTypeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleActionType(1))
                .bounds(left + 44, top + 266, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> cycleActionType(1))
                .bounds(left + 178, top + 266, 26, 20).build());
        actionParameters = field(left + 216, top + 266, 415, 512, "key=value; key=value", action == null ? "" : parameters(action.parameters()));
        updateActionTypeLabel();
    }

    private void initNodes(int left, int top) {
        ensureNodes(arena());
        MinigameControlPoint point = node();
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> switchNode(-1))
                .bounds(left + 14, top + 48, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> switchNode(1))
                .bounds(left + 50, top + 48, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add node"), ignored -> addNode())
                .bounds(left + 90, top + 48, 82, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete node"), ignored -> deleteNode())
                .bounds(left + 178, top + 48, 94, 20).build());
        delete.active = arena().controlPoints.size() > 3;
        nodeId = field(left + 14, top + 102, 180, 64, "Node ID", point.id);
        nodeName = field(left + 206, top + 102, 250, 64, "Display name", point.displayName);
        nodeDimension = field(left + 14, top + 180, 180, 128, "Dimension", point.location.dimension);
        nodeX = field(left + 206, top + 180, 72, 24, "X", coordinate(point.location.x));
        nodeY = field(left + 290, top + 180, 72, 24, "Y", coordinate(point.location.y));
        nodeZ = field(left + 374, top + 180, 72, 24, "Z", coordinate(point.location.z));
        nodeYaw = field(left + 458, top + 180, 76, 16, "Yaw", angle(point.location.yaw));
        nodePitch = field(left + 546, top + 180, 85, 16, "Pitch", angle(point.location.pitch));
        addRenderableWidget(Button.builder(Component.literal("Use my current position"), ignored -> fillCurrent(
                nodeDimension, nodeX, nodeY, nodeZ, nodeYaw, nodePitch))
                .bounds(left + 14, top + 232, 180, 20).build());
    }

    private void initRules(int left, int top) {
        DominationRules rules = draft.domination;
        ruleTeamButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> switchRuleTeam())
                .bounds(left + 14, top + 48, 146, 20).build());
        teamName = field(left + 172, top + 48, 150, 64, "Team name", rules.teamName(ruleTeam));
        teamColor = field(left + 334, top + 48, 92, 8, "RGB color",
                String.format(Locale.ROOT, "%06X", rules.color(ruleTeam)));
        teamBanner = field(left + 438, top + 48, 193, 128, "Team banner block", rules.bannerBlock(ruleTeam));
        neutralBanner = field(left + 14, top + 112, 210, 128, "Neutral banner block", rules.neutralBannerBlock);
        weaponItem = field(left + 236, top + 112, 220, 128, "Temporary weapon", rules.weaponItem);
        allowFriendlyFire = rules.allowFriendlyFire;
        friendlyFireButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            allowFriendlyFire = !allowFriendlyFire; updateLabels();
        }).bounds(left + 468, top + 112, 163, 20).build());
        scoreToWin = field(left + 14, top + 190, 108, 8, "Score to win", Integer.toString(rules.scoreToWin));
        claimCastSeconds = field(left + 134, top + 190, 108, 4, "Claim cast", Integer.toString(rules.claimCastSeconds));
        captureDelaySeconds = field(left + 254, top + 190, 108, 4, "Capture delay", Integer.toString(rules.captureDelaySeconds));
        scoreInterval = field(left + 374, top + 190, 108, 4, "Score interval", Integer.toString(rules.scoreIntervalSeconds));
        pointsPerNode = field(left + 494, top + 190, 137, 8, "Points per node", Integer.toString(rules.pointsPerNode));
        updateLabels();
    }

    private boolean saveCurrentPage() {
        try {
            if (page == 0 && id != null) saveGeneral();
            else if (page == 1 && arenaId != null) saveArena();
            else if (page == 2 && spawnDimension != null) saveSpawn();
            else if (page == 3 && rewardMoney != null) saveRewards();
            else if (page == 4 && nodeDimension != null) saveNode();
            else if (page == 5 && scoreToWin != null) saveRules();
            return true;
        } catch (RuntimeException exception) {
            setNotice(exception.getMessage() == null ? "A field contains an invalid value." : exception.getMessage(), true);
            return false;
        }
    }

    private void saveGeneral() {
        draft.id = id.getValue().trim();
        draft.displayName = name.getValue().trim();
        draft.iconItem = icon.getValue().trim();
        draft.description = descriptionValue;
        draft.minPlayers = parseInt(minPlayers, "Minimum players", 2, 64);
        draft.maxPlayers = parseInt(maxPlayers, "Maximum players", draft.minPlayers, 64);
        draft.countdownSeconds = parseInt(countdown, "Countdown seconds", 0, 600);
        draft.matchDurationSeconds = parseInt(duration, "Match duration", 0, 86_400);
        draft.postGameSeconds = parseInt(postGame, "Result screen duration", 0, 600);
        draft.enabled = enabled; draft.automaticStart = automaticStart;
        draft.gameType = "domination"; draft.allowLateJoin = false; draft.teamCount = 2;
        draft.victoryMode = "highest_score";
    }

    private void saveArena() {
        MinigameArenaDefinition arena = arena();
        arena.id = arenaId.getValue().trim(); arena.displayName = arenaName.getValue().trim();
        if (!arena.managedRegion) arena.regionId = regionId.getValue().trim();
        arena.enabled = arenaEnabled; arena.resetRegionAfterMatch = true;
        arena.lobby = readLocation(lobbyDimension, lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch, "Lobby");
        arena.spectator = readLocation(spectatorDimension, spectatorX, spectatorY, spectatorZ, spectatorYaw, spectatorPitch, "Spectator point");
    }

    private void saveSpawn() {
        MinigameSpawnPoint spawn = spawn();
        spawn.team = selectedSpawnTeam;
        spawn.location = readLocation(spawnDimension, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, "Team spawn");
    }

    private void saveRewards() {
        MinigameRewardSet rewards = rewards();
        rewards.moneyMinor = parseMoney(rewardMoney.getValue());
        ContentAction current = action();
        if (current != null) {
            String type = normalizedActionType(selectedActionType);
            if (type.isBlank()) throw new IllegalArgumentException("Direct reward type cannot be empty. Delete the row instead.");
            rewards.directActions.set(actionIndex(), new ContentAction(type, parseParameters(actionParameters.getValue())));
        }
        rewards.normalize();
    }

    private void saveNode() {
        MinigameControlPoint point = node();
        MinigameLocation previous = point.location == null ? null : point.location.copy();
        point.id = nodeId.getValue().trim();
        point.displayName = nodeName.getValue().trim();
        MinigameLocation updated = readLocation(nodeDimension, nodeX, nodeY, nodeZ, nodeYaw, nodePitch, "Capture node");
        point.location = updated;
        if (point.respawn == null) point.respawn = updated.copy();
        else if (previous != null) {
            point.respawn = new MinigameLocation(point.respawn.dimension,
                    point.respawn.x + updated.x - previous.x,
                    point.respawn.y + updated.y - previous.y,
                    point.respawn.z + updated.z - previous.z,
                    point.respawn.yaw, point.respawn.pitch);
        }
    }

    private void saveRules() {
        DominationRules rules = draft.domination;
        String colorText = teamColor.getValue().trim().replace("#", "");
        int color;
        try { color = Integer.parseInt(colorText, 16); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Team color must be a six-digit RGB hex value."); }
        if (colorText.length() != 6 || color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("Team color must be a six-digit RGB hex value.");
        }
        if (ruleTeam == 1) {
            rules.team1Name = teamName.getValue().trim();
            rules.team1Color = color;
            rules.team1BannerBlock = teamBanner.getValue().trim();
        } else {
            rules.team2Name = teamName.getValue().trim();
            rules.team2Color = color;
            rules.team2BannerBlock = teamBanner.getValue().trim();
        }
        rules.neutralBannerBlock = neutralBanner.getValue().trim();
        rules.weaponItem = weaponItem.getValue().trim();
        rules.allowFriendlyFire = allowFriendlyFire;
        rules.scoreToWin = parseInt(scoreToWin, "Score to win", 10, 1_000_000);
        rules.claimCastSeconds = parseInt(claimCastSeconds, "Claim cast seconds", 1, 60);
        rules.captureDelaySeconds = parseInt(captureDelaySeconds, "Capture delay seconds", 1, 900);
        rules.scoreIntervalSeconds = parseInt(scoreInterval, "Score interval", 1, 60);
        rules.pointsPerNode = parseInt(pointsPerNode, "Points per node", 1, 10_000);
    }

    private void saveAll() { if (saveCurrentPage()) submitDraft(); }
    private void switchPage(int target) { if (target != page && saveCurrentPage()) { page = target; rebuildWidgets(); } }

    private void switchArena(int delta) {
        if (!saveCurrentPage()) return;
        arenaIndex = Math.floorMod(arenaIndex + delta, draft.arenas.size()); spawnIndex = 0; rebuildWidgets();
    }

    private void addArena() {
        if (!saveCurrentPage()) return;
        if (draft.arenas.size() >= 32) { setNotice("At most 32 arenas are supported.", true); return; }
        MinigameArenaDefinition value = new MinigameArenaDefinition();
        value.id = "arena_" + (draft.arenas.size() + 1); value.displayName = "Domination Battlefield " + (draft.arenas.size() + 1);
        value.resetRegionAfterMatch = true;
        draft.arenas.add(value); arenaIndex = draft.arenas.size() - 1; spawnIndex = 0; rebuildWidgets();
    }

    private void deleteArena() {
        if (arena().managedRegion) { setNotice("Selection-created arenas must be removed by deleting the minigame safely.", true); return; }
        if (draft.arenas.size() <= 1) { setNotice("Domination needs at least one arena.", true); return; }
        draft.arenas.remove(arenaIndex); arenaIndex = Math.max(0, arenaIndex - 1); spawnIndex = 0; rebuildWidgets();
    }

    private void switchSpawn(int delta) {
        if (!saveCurrentPage()) return;
        spawnIndex = Math.floorMod(spawnIndex + delta, arena().teamSpawns.size()); rebuildWidgets();
    }

    private void addSpawn() {
        if (!saveCurrentPage()) return;
        if (arena().teamSpawns.size() >= 64) { setNotice("Domination supports at most 64 team spawns.", true); return; }
        int team = arena().teamSpawns.stream().filter(value -> value.team == 1).count()
                <= arena().teamSpawns.stream().filter(value -> value.team == 2).count() ? 1 : 2;
        arena().teamSpawns.add(new MinigameSpawnPoint(team, new MinigameLocation()));
        spawnIndex = arena().teamSpawns.size() - 1; rebuildWidgets();
    }

    private void deleteSpawn() {
        MinigameSpawnPoint selected = spawn();
        long teamSpawns = arena().teamSpawns.stream().filter(value -> value.team == selected.team).count();
        if (teamSpawns <= 1) {
            setNotice("Each team needs at least one spawn point.", true);
            return;
        }
        arena().teamSpawns.remove(spawnIndex);
        spawnIndex = Math.max(0, Math.min(spawnIndex, arena().teamSpawns.size() - 1));
        selectedSpawnTeam = spawn().team;
        rebuildWidgets();
    }

    private void switchRewardGroup() {
        if (!saveCurrentPage()) return;
        winnerRewards = !winnerRewards; rebuildWidgets();
    }

    private void switchAction(int delta) {
        if (!saveCurrentPage()) return;
        List<ContentAction> actions = rewards().directActions;
        if (actions.isEmpty()) setActionIndex(-1);
        else setActionIndex(Math.floorMod((actionIndex() < 0 ? 0 : actionIndex()) + delta, actions.size()));
        rebuildWidgets();
    }

    private void addAction() {
        if (!saveCurrentPage()) return;
        List<ContentAction> actions = rewards().directActions;
        if (actions.size() >= MinigameRewardSet.MAX_DIRECT_ACTIONS) { setNotice("Maximum direct rewards reached.", true); return; }
        actions.add(defaultAction(firstActionType()));
        setActionIndex(actions.size() - 1); rebuildWidgets();
    }

    private void deleteAction() {
        List<ContentAction> actions = rewards().directActions; int index = actionIndex();
        if (index < 0 || index >= actions.size()) return;
        actions.remove(index); setActionIndex(normalizeActionIndex(index, actions)); rebuildWidgets();
    }

    private void cycleActionType(int delta) {
        if (action() == null) return;
        List<String> types = availableActionTypes();
        int current = Math.max(0, types.indexOf(normalizedActionType(selectedActionType)));
        selectedActionType = types.get(Math.floorMod(current + delta, types.size()));
        updateActionTypeLabel();
        setNotice("Direct action type: " + selectedActionType + ".", false);
    }

    private List<String> availableActionTypes() { return minigameRewardActionTypes(); }

    private String firstActionType() {
        List<String> types = availableActionTypes();
        return types.contains("grant_permission") ? "grant_permission" : types.getFirst();
    }

    private String normalizedActionType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return availableActionTypes().contains(value) ? value : firstActionType();
    }

    private static ContentAction defaultAction(String type) {
        return switch (type) {
            case "set_player_unlock" -> new ContentAction(type, Map.of("key", "minigame_reward", "value", "true"));
            case "set_reputation", "add_reputation" -> new ContentAction(type, Map.of("faction", "default", "amount", "1"));
            case "add_claim_chunks" -> new ContentAction(type, Map.of("amount", "5"));
            case "unset_permission" -> new ContentAction(type, Map.of("permission", "ssu.example.reward"));
            case "set_permission", "grant_permission" -> new ContentAction(type, Map.of("permission", "ssu.example.reward", "value", "true"));
            default -> new ContentAction(type, Map.of());
        };
    }

    private void updateActionTypeLabel() {
        if (actionTypeButton != null) actionTypeButton.setMessage(Component.literal(trim(selectedActionType, 20)));
    }

    private void placeCarriedStack(int rewardSlot, boolean addOne) {
        if (!saveCurrentPage() || awaiting) return;
        ItemStack source = clientInventoryItem(carriedInventorySlot);
        if (source.isEmpty()) {
            carriedInventorySlot = -1;
            setNotice("The held inventory stack is empty or changed.", true);
            return;
        }
        awaiting = true;
        ClientPacketDistributor.sendToServer(new MinigameRewardCapturePayload(
                initial.originalMinigameId(), GSON.toJson(draft), winnerRewards ? "winner" : "participation",
                rewardSlot, carriedInventorySlot, addOne, nextRequestId++));
        setNotice(addOne ? "Adding one item to reward slot…" : "Copying the complete stack to reward slot…", false);
        rebuildWidgets();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (page == 3) {
            int button = event.buttonInfo().button();
            int mouseX = (int) event.x(), mouseY = (int) event.y();
            int rewardSlot = rewardSlotAt(mouseX, mouseY);
            if (rewardSlot >= 0 && (button == 0 || button == 1)) {
                if (carriedInventorySlot >= 0) {
                    placeCarriedStack(rewardSlot, button == 1);
                } else if (button == 1 && !rewardItem(rewardSlot).isEmpty()) {
                    rewards().removeItem(rewardSlot);
                    setNotice("Cleared reward slot " + (rewardSlot + 1) + ".", false);
                    rebuildWidgets();
                } else {
                    setNotice("Pick up a ghost copy from your inventory first.", true);
                }
                return true;
            }
            int inventorySlot = inventorySlotAt(mouseX, mouseY);
            if (inventorySlot >= 0 && (button == 0 || button == 1)) {
                ItemStack stack = clientInventoryItem(inventorySlot);
                if (stack.isEmpty()) {
                    carriedInventorySlot = -1;
                    setNotice("Ghost cursor cleared.", false);
                } else {
                    carriedInventorySlot = inventorySlot;
                    setNotice("Holding a copy of " + stack.getHoverName().getString() + ".", false);
                }
                return true;
            }
            if (button == 1 && carriedInventorySlot >= 0) {
                carriedInventorySlot = -1;
                setNotice("Ghost cursor cleared.", false);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = left(), top = top();
        g.fill(0, 0, width, height, 0xA9000000); g.fill(left, top, left + W, top + H, PANEL); g.outline(left, top, W, H, BORDER);
        g.text(font, "Domination Editor", left + W - 162, top + 17, TEXT, true);
        if (page == 0) renderGeneral(g, left, top);
        else if (page == 1) renderArena(g, left, top);
        else if (page == 2) renderSpawns(g, left, top);
        else if (page == 3) renderRewards(g, left, top, mouseX, mouseY);
        else if (page == 4) renderNodes(g, left, top);
        else renderRules(g, left, top);
        if (!notice.isBlank()) g.text(font, trim(notice, 68), left + 102, top + H - 24, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (page == 3 && carriedInventorySlot >= 0) renderGhostCursor(g, mouseX, mouseY);
    }

    private void renderGeneral(GuiGraphicsExtractor g, int left, int top) {
        fieldInfo(g, left + 14, top + 42, "Internal ID", "Unique storage key; locked after first save.", 170);
        fieldInfo(g, left + 196, top + 42, "Display name", "Name shown to players and in reward mail.", 220);
        fieldInfo(g, left + 428, top + 42, "Menu icon", "Item ID used in the minigame menu.", 203);
        g.text(font, "Description", left + 14, top + 100, TEXT, true);
        g.text(font, "Explain the goal and important rules to players.", left + 104, top + 100, MUTED, false);
        fieldInfo(g, left + 14, top + 192, "Minimum players", "Needed before start.", 112);
        fieldInfo(g, left + 134, top + 192, "Maximum players", "Also required spawns.", 112);
        fieldInfo(g, left + 254, top + 192, "Countdown", "Seconds before start.", 112);
        fieldInfo(g, left + 374, top + 192, "Match time", "0 means unlimited.", 112);
        fieldInfo(g, left + 494, top + 192, "Post-game time", "Seconds before return.", 137);
        g.text(font, "Domination uses two teams, resource scoring, capture nodes and no late joining.", left + 14, top + 304, GOOD, false);
    }

    private void renderArena(GuiGraphicsExtractor g, int left, int top) {
        MinigameArenaDefinition arena = arena();
        g.text(font, "Arena " + (arenaIndex + 1) + " / " + draft.arenas.size(), left + 292, top + 50, MUTED, false);
        fieldInfo(g, left + 14, top + 76, "Arena ID", "Unique key inside this Domination game.", 150);
        fieldInfo(g, left + 176, top + 76, "Arena name", "Readable name for administrators.", 220);
        fieldInfo(g, left + 408, top + 76, "Arena region", arena.managedRegion
                ? "Managed by the Selection Tool and locked." : "SSU region containing the complete arena.", 223);
        wrapped(g, "Arena reset is always enabled so capture markers and changed blocks return to the verified snapshot.",
                left + 172, top + 141, 459, GOOD, 2);
        locationLabels(g, left + 14, top + 166, "Lobby position", "Players wait here before the match.");
        locationLabels(g, left + 14, top + 242, "Spectator position", "Eliminated players appear here and must remain near the arena.");
    }

    private void locationLabels(GuiGraphicsExtractor g, int x, int y, String title, String help) {
        g.text(font, title, x, y, TEXT, true); g.text(font, help, x + 118, y, MUTED, false);
        g.text(font, "Dimension", x, y + 16, MUTED, false); g.text(font, "X", x + 153, y + 16, MUTED, false);
        g.text(font, "Y", x + 225, y + 16, MUTED, false); g.text(font, "Z", x + 297, y + 16, MUTED, false);
        g.text(font, "Yaw", x + 369, y + 16, MUTED, false); g.text(font, "Pitch", x + 441, y + 16, MUTED, false);
        g.text(font, "Yaw = horizontal facing; Pitch = looking up or down.", x, y + 52, MUTED, false);
    }

    private void renderSpawns(GuiGraphicsExtractor g, int left, int top) {
        g.text(font, "Team spawn " + (spawnIndex + 1) + " / " + arena().teamSpawns.size(), left + 490, top + 54, TEXT, true);
        wrapped(g, "Assign each spawn to the Red or Blue team. SSU balances players between the two teams and cycles through that team's spawns.",
                left + 14, top + 84, 605, GOOD, 2);
        fieldInfo(g, left + 14, top + 116, "Dimension", "World containing this spawn.", 180);
        fieldInfo(g, left + 206, top + 116, "X", "East/west.", 72);
        fieldInfo(g, left + 290, top + 116, "Y", "Height.", 72);
        fieldInfo(g, left + 374, top + 116, "Z", "North/south.", 72);
        fieldInfo(g, left + 458, top + 116, "Yaw", "Facing.", 82);
        fieldInfo(g, left + 552, top + 116, "Pitch", "Look angle.", 79);
        long red = arena().teamSpawns.stream().filter(value -> value.team == 1).count();
        long blue = arena().teamSpawns.stream().filter(value -> value.team == 2).count();
        g.text(font, draft.domination.team1Name + " spawns: " + red + " · "
                + draft.domination.team2Name + " spawns: " + blue, left + 14, top + 232,
                red > 0 && blue > 0 ? GOOD : WARNING, false);
        g.text(font, "Place team spawns inside the arena and away from capture nodes.", left + 14, top + 254, MUTED, false);
    }

    private void renderRewards(GuiGraphicsExtractor g, int left, int top, int mouseX, int mouseY) {
        fieldInfo(g, left + 216, top + 32, "Money reward", "Delivered through reward mail.", 120);
        wrapped(g, winnerRewards ? "Only the winner receives this package." : "Every restored participant receives this package.",
                left + 348, top + 54, 283, GOOD, 2);
        g.text(font, "Mail item slots", left + 14, top + 88, TEXT, true);
        renderRewardSlots(g, left + 14, top + 108, mouseX, mouseY);
        g.text(font, "Your inventory", left + 128, top + 88, TEXT, true);
        renderInventory(g, left + 128, top + 108, mouseX, mouseY);
        wrapped(g, "Click an inventory stack to hold a ghost copy. Then left-click a mail slot to copy the full stack, or right-click to add one item.",
                left + 322, top + 88, 309, MUTED, 4);
        wrapped(g, "Your real inventory is never changed. Right-click outside both inventories to release the held copy; with an empty cursor, right-click a mail slot to clear it.",
                left + 322, top + 136, 309, GOOD, 3);
        ItemStack held = heldStack();
        String heldLabel = held.isEmpty() ? "Held copy: none"
                : "Held copy: " + held.getHoverName().getString() + " ×" + held.getCount();
        g.text(font, trim(heldLabel, 48), left + 322, top + 182, held.isEmpty() ? MUTED : GOOD, false);

        ContentAction current = action();
        String position = current == null ? "No direct reward selected" : "Direct reward " + (actionIndex() + 1) + " / " + rewards().directActions.size();
        g.text(font, position, left + 370, top + 224, MUTED, false);
        g.text(font, "Direct action type", left + 14, top + 250, TEXT, true);
        g.text(font, "Action parameters", left + 216, top + 250, TEXT, true);
        g.text(font, "Applied immediately.", left + 14, top + 288, MUTED, false);
        g.text(font, "Use key=value pairs separated by semicolons.", left + 216, top + 288, MUTED, false);
        wrapped(g, actionHelp(new ContentAction(selectedActionType, Map.of())), left + 14, top + 302, 617, MUTED, 1);
    }

    private void renderNodes(GuiGraphicsExtractor g, int left, int top) {
        g.text(font, "Capture node " + (nodeIndex + 1) + " / " + arena().controlPoints.size(), left + 292, top + 54, TEXT, true);
        fieldInfo(g, left + 14, top + 86, "Node ID", "Unique node key used in live match state.", 180);
        fieldInfo(g, left + 206, top + 86, "Display name", "Name shown in HUD and capture messages.", 250);
        fieldInfo(g, left + 14, top + 164, "Dimension", "World containing this physical banner marker.", 180);
        fieldInfo(g, left + 206, top + 164, "X", "East/west.", 72);
        fieldInfo(g, left + 290, top + 164, "Y", "Marker height.", 72);
        fieldInfo(g, left + 374, top + 164, "Z", "North/south.", 72);
        fieldInfo(g, left + 458, top + 164, "Yaw", "Banner facing.", 76);
        fieldInfo(g, left + 546, top + 164, "Pitch", "Stored view angle.", 85);
        MinigameLocation respawn = node().respawn;
        String respawnText = respawn == null ? "Linked respawn: not configured"
                : "Linked respawn: " + coordinate(respawn.x) + ", " + coordinate(respawn.y) + ", " + coordinate(respawn.z)
                + " — place it with the Minigame Setup Tool.";
        g.text(font, trim(respawnText, 96), left + 206, top + 238, ACCENT, false);
        wrapped(g, "Right-click the physical banner to begin an interruptible claim cast. Moving or taking damage cancels the cast.",
                left + 14, top + 270, 617, GOOD, 3);
        g.text(font, "Domination needs between 3 and 9 nodes; the Selection Tool creates five Arathi Basin-style nodes.",
                left + 14, top + 304, MUTED, false);
    }

    private void renderRules(GuiGraphicsExtractor g, int left, int top) {
        fieldInfo(g, left + 172, top + 32, "Team name", "Name shown in the HUD and winner title.", 150);
        fieldInfo(g, left + 334, top + 32, "Team color", "Six-digit RGB used for winner fireworks.", 92);
        fieldInfo(g, left + 438, top + 32, "Team banner", "Standing banner used for owned nodes.", 193);
        fieldInfo(g, left + 14, top + 96, "Neutral banner", "Standing banner used before a node is captured.", 210);
        fieldInfo(g, left + 236, top + 96, "Temporary weapon", "Item issued during this match.", 220);
        fieldInfo(g, left + 14, top + 174, "Score to win", "First team reaching this resource score wins.", 108);
        fieldInfo(g, left + 134, top + 174, "Claim cast", "Seconds the player must stand still after right-clicking.", 108);
        fieldInfo(g, left + 254, top + 174, "Capture delay", "Visible timer before the claimed base changes owner.", 108);
        fieldInfo(g, left + 374, top + 174, "Score interval", "Seconds between resource payouts.", 108);
        fieldInfo(g, left + 494, top + 174, "Points per node", "Points each owned node adds per interval.", 137);
        wrapped(g, "A completed cast starts the capture-delay timer. The base gives no points during that timer and changes owner only when it expires.",
                left + 14, top + 252, 617, GOOD, 3);
        wrapped(g, "The former owning team can right-click the assaulted flag to defend it immediately. Friendly fire only controls teammate damage.",
                left + 14, top + 288, 617, MUTED, 2);
    }

    private void renderRewardSlots(GuiGraphicsExtractor g, int startX, int startY, int mouseX, int mouseY) {
        for (int slot = 0; slot < MinigameRewardSet.MAX_ITEM_STACKS; slot++) {
            int x = startX + (slot % 3) * 34, y = startY + (slot / 3) * 34;
            boolean hovered = SsuGuiGeometry.inside(mouseX, mouseY, x, y, 30, 30);
            g.fill(x, y, x + 30, y + 30, hovered ? 0xE03C5364 : 0xD00B1015);
            g.outline(x, y, 30, 30, hovered ? GOOD : BORDER);
            ItemStack stack = rewardItem(slot);
            if (!stack.isEmpty()) {
                g.item(stack, x + 7, y + 7); g.itemDecorations(font, stack, x + 7, y + 7);
                if (hovered) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            } else g.text(font, Integer.toString(slot + 1), x + 12, y + 11, MUTED, false);
        }
    }

    private void renderInventory(GuiGraphicsExtractor g, int startX, int startY, int mouseX, int mouseY) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawInventorySlot(g, 9 + row * 9 + column, startX + column * 20, startY + row * 20, mouseX, mouseY);
        int hotbarY = startY + 66;
        for (int column = 0; column < 9; column++) drawInventorySlot(g, column, startX + column * 20, hotbarY, mouseX, mouseY);
    }

    private void drawInventorySlot(GuiGraphicsExtractor g, int slot, int x, int y, int mouseX, int mouseY) {
        boolean hovered = SsuGuiGeometry.inside(mouseX, mouseY, x, y, 18, 18);
        boolean carried = slot == carriedInventorySlot;
        g.fill(x, y, x + 18, y + 18, hovered || carried ? 0xE03C5364 : 0xD00B1015);
        g.outline(x, y, 18, 18, hovered || carried ? GOOD : BORDER);
        ItemStack stack = clientInventoryItem(slot);
        if (!stack.isEmpty()) {
            g.item(stack, x + 1, y + 1); g.itemDecorations(font, stack, x + 1, y + 1);
            if (hovered) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private void renderGhostCursor(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ItemStack stack = heldStack();
        if (stack.isEmpty()) return;
        g.item(stack, mouseX - 8, mouseY - 8);
        g.itemDecorations(font, stack, mouseX - 8, mouseY - 8);
    }

    private void fieldInfo(GuiGraphicsExtractor g, int x, int y, String title, String help, int width) {
        g.text(font, title, x, y, TEXT, true);
        wrapped(g, help, x, y + 38, width, MUTED, 2);
    }

    private void wrapped(GuiGraphicsExtractor g, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text == null ? "" : text), Math.max(20, width));
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            g.text(font, lines.get(index), x, y + index * 10, color, false);
        }
    }

    private static String actionHelp(ContentAction action) {
        if (action == null || action.type() == null || action.type().isBlank()) {
            return "Add a direct reward for a permission, unlock, reputation or claim-capacity change.";
        }
        return switch (action.type()) {
            case "grant_permission", "set_permission" -> "permission=<node>; value=<permission value>. grant_permission defaults to true.";
            case "unset_permission" -> "permission=<node>. Removes the player-specific permission override.";
            case "set_player_unlock" -> "key=<unlock key>; value=true|false.";
                        case "set_reputation", "add_reputation" -> "faction=<faction key>; amount=<whole number>.";
            case "add_claim_chunks" -> "amount=<positive chunks>. Adds permanent personal claim capacity.";
                        default -> "Parameters depend on this registered Content Core action; use key=value pairs separated by semicolons.";
        };
    }

    private void updateLabels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Minigame enabled: " + yes(enabled)));
        if (automaticButton != null) automaticButton.setMessage(Component.literal("Automatic start: " + yes(automaticStart)));
        if (arenaEnabledButton != null) arenaEnabledButton.setMessage(Component.literal("Arena enabled: " + yes(arenaEnabled)));
        if (spawnTeamButton != null) spawnTeamButton.setMessage(Component.literal("Team: "
                + draft.domination.teamName(selectedSpawnTeam)));
        if (ruleTeamButton != null) ruleTeamButton.setMessage(Component.literal("Editing team: "
                + draft.domination.teamName(ruleTeam)));
        if (friendlyFireButton != null) friendlyFireButton.setMessage(Component.literal("Friendly fire: " + yes(allowFriendlyFire)));
    }

    private void fillCurrent(EditBox dimension, EditBox x, EditBox y, EditBox z, EditBox yaw, EditBox pitch) {
        if (minecraft == null || minecraft.player == null) return;
        dimension.setValue(minecraft.player.level().dimension().identifier().toString());
        x.setValue(coordinate(minecraft.player.getX())); y.setValue(coordinate(minecraft.player.getY()));
        z.setValue(coordinate(minecraft.player.getZ())); yaw.setValue(angle(minecraft.player.getYRot()));
        pitch.setValue(angle(minecraft.player.getXRot()));
    }

    private MinigameLocation readLocation(EditBox dimension, EditBox x, EditBox y, EditBox z,
                                          EditBox yaw, EditBox pitch, String label) {
        String world = dimension.getValue().trim();
        if (world.isBlank()) throw new IllegalArgumentException(label + " dimension cannot be empty.");
        return new MinigameLocation(world, parseDouble(x, label + " X"), parseDouble(y, label + " Y"),
                parseDouble(z, label + " Z"), (float) parseDouble(yaw, label + " yaw"),
                (float) parseDouble(pitch, label + " pitch"));
    }

    private int rewardSlotAt(int mouseX, int mouseY) {
        int startX = left() + 14, startY = top() + 108;
        for (int slot = 0; slot < 9; slot++) {
            int x = startX + (slot % 3) * 34, y = startY + (slot / 3) * 34;
            if (SsuGuiGeometry.inside(mouseX, mouseY, x, y, 30, 30)) return slot;
        }
        return -1;
    }

    private int inventorySlotAt(int mouseX, int mouseY) {
        int startX = left() + 128, startY = top() + 108;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            int x = startX + column * 20, y = startY + row * 20;
            if (SsuGuiGeometry.inside(mouseX, mouseY, x, y, 18, 18)) return 9 + row * 9 + column;
        }
        int hotbarY = startY + 66;
        for (int column = 0; column < 9; column++)
            if (SsuGuiGeometry.inside(mouseX, mouseY, startX + column * 20, hotbarY, 18, 18)) return column;
        return -1;
    }

    private ItemStack clientInventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private ItemStack heldStack() {
        return carriedInventorySlot < 0 ? ItemStack.EMPTY : clientInventoryItem(carriedInventorySlot);
    }

    private ItemStack rewardItem(int slot) {
        if (minecraft == null || minecraft.level == null) return ItemStack.EMPTY;
        var encoded = rewards().itemAt(slot);
        if (encoded == null) return ItemStack.EMPTY;
        return MailItemCodec.decode(minecraft.level.registryAccess(), encoded);
    }

    private MinigameArenaDefinition arena() { return draft.arenas.get(arenaIndex); }
    private MinigameSpawnPoint spawn() { return arena().teamSpawns.get(spawnIndex); }
    private MinigameRewardSet rewards() { return winnerRewards ? draft.winnerReward : draft.participationReward; }
    private int actionIndex() { return winnerRewards ? winnerActionIndex : participationActionIndex; }
    private void setActionIndex(int value) { if (winnerRewards) winnerActionIndex = value; else participationActionIndex = value; }
    private ContentAction action() {
        int index = actionIndex(); List<ContentAction> values = rewards().directActions;
        return index < 0 || index >= values.size() ? null : values.get(index);
    }

    private void cycleSpawnTeam() {
        int currentTeam = spawn().team;
        long currentTeamSpawns = arena().teamSpawns.stream().filter(value -> value.team == currentTeam).count();
        if (selectedSpawnTeam == currentTeam && currentTeamSpawns <= 1) {
            setNotice("Each team must keep at least one spawn point.", true);
            return;
        }
        selectedSpawnTeam = selectedSpawnTeam == 1 ? 2 : 1;
        updateLabels();
    }

    private void switchNode(int delta) {
        if (!saveCurrentPage()) return;
        nodeIndex = Math.floorMod(nodeIndex + delta, arena().controlPoints.size());
        rebuildWidgets();
    }

    private void addNode() {
        if (!saveCurrentPage()) return;
        if (arena().controlPoints.size() >= 9) { setNotice("Domination supports at most 9 capture nodes.", true); return; }
        int number = arena().controlPoints.size() + 1;
        arena().controlPoints.add(new MinigameControlPoint("node_" + number, "Node " + number, new MinigameLocation()));
        nodeIndex = arena().controlPoints.size() - 1;
        rebuildWidgets();
    }

    private void deleteNode() {
        if (arena().controlPoints.size() <= 3) { setNotice("Domination needs at least 3 capture nodes.", true); return; }
        arena().controlPoints.remove(nodeIndex);
        nodeIndex = Math.max(0, Math.min(nodeIndex, arena().controlPoints.size() - 1));
        rebuildWidgets();
    }

    private void switchRuleTeam() {
        if (!saveCurrentPage()) return;
        ruleTeam = ruleTeam == 1 ? 2 : 1;
        rebuildWidgets();
    }

    private MinigameControlPoint node() {
        ensureNodes(arena());
        return arena().controlPoints.get(nodeIndex);
    }

    private static void ensureNodes(MinigameArenaDefinition arena) {
        if (arena.controlPoints == null) arena.controlPoints = new ArrayList<>();
        while (arena.controlPoints.size() < 3) {
            int number = arena.controlPoints.size() + 1;
            arena.controlPoints.add(new MinigameControlPoint("node_" + number, "Node " + number, new MinigameLocation()));
        }
    }

    private static int normalizeActionIndex(int index, List<ContentAction> values) {
        return values == null || values.isEmpty() ? -1 : Math.max(0, Math.min(index < 0 ? 0 : index, values.size() - 1));
    }

    private static Map<String, String> parseParameters(String raw) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String part : raw.split(";")) {
            String value = part.trim(); if (value.isBlank()) continue;
            int split = value.indexOf('=');
            if (split <= 0) throw new IllegalArgumentException("Direct action parameters must use key=value separated by semicolons.");
            result.put(value.substring(0, split).trim(), value.substring(split + 1).trim());
        }
        return result;
    }

    private static String parameters(Map<String, String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        values.forEach((key, value) -> { if (!out.isEmpty()) out.append("; "); out.append(key).append('=').append(value); });
        return out.toString();
    }

    private static List<String> parseList(String raw) {
        ArrayList<String> result = new ArrayList<>();
        if (raw == null) return result;
        for (String part : raw.split("[,;]")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private static String yes(boolean value) { return value ? "Yes" : "No"; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
}
