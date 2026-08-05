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
import be.winnetrie.mod.simpleserverutilities.minigame.SpleefRules;
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

/** Dedicated, compact administrator-facing Spleef editor. */
final class SpleefMinigameEditorScreen extends MinigameEditorScreen {
    private static final int W = 645, H = 352;
    private static final int TAB_Y = 10;
    private static final String[] TABS = {"General", "Arena", "Player spawns", "Rewards", "Spleef rules", "Projectiles"};
    private static final int[] TAB_WIDTHS = {78, 64, 104, 70, 92, 92};

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

    // Rewards
    private EditBox rewardMoney, actionParameters;
    private Button actionTypeButton;
    private String selectedActionType = "grant_permission";

    // Rules
    private EditBox toolItem, eliminationDepth, breakableBlocks;
    private boolean requireTool, allowPvp, removeDrops;
    private Button requireToolButton, pvpButton, dropsButton;

    // Projectiles
    private EditBox standardUnlock, standardCooldown, burstStart, burstMinInterval, burstMaxInterval, burstMaxStack;
    private boolean standardProjectileEnabled, burstProjectileEnabled;
    private Button standardProjectileButton, burstProjectileButton;

    SpleefMinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(initial, parent, "Spleef Editor");
        afterDraftReloaded();
    }

    @Override protected void afterDraftReloaded() {
        if (draft.arenas == null) draft.arenas = new ArrayList<>();
        if (draft.arenas.isEmpty()) draft.arenas.add(new MinigameArenaDefinition());
        if (draft.spleef == null) draft.spleef = new SpleefRules();
        if (draft.participationReward == null) draft.participationReward = new MinigameRewardSet();
        if (draft.winnerReward == null) draft.winnerReward = new MinigameRewardSet();
        arenaIndex = Math.max(0, Math.min(arenaIndex, draft.arenas.size() - 1));
        MinigameArenaDefinition arena = arena();
        if (arena.teamSpawns == null) arena.teamSpawns = new ArrayList<>();
        if (arena.teamSpawns.isEmpty()) arena.teamSpawns.add(new MinigameSpawnPoint());
        reindexSpawns(arena);
        spawnIndex = Math.max(0, Math.min(spawnIndex, arena.teamSpawns.size() - 1));
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
        else if (page == 4) initRules(left, top);
        else initProjectiles(left, top);

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(left + 14, top + H - 30, 78, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save Spleef"), ignored -> saveAll())
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
        addRenderableWidget(Button.builder(Component.literal("Add player spawn"), ignored -> addSpawn()).bounds(left + 90, top + 48, 126, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete spawn"), ignored -> deleteSpawn())
                .bounds(left + 222, top + 48, 100, 20).build());
        delete.active = arena().teamSpawns.size() > 1;
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

    private void initRules(int left, int top) {
        SpleefRules rules = draft.spleef;
        toolItem = field(left + 14, top + 76, 310, 128, "Spleef tool item", rules.toolItem);
        eliminationDepth = field(left + 340, top + 76, 140, 3, "Elimination depth", Integer.toString(rules.eliminationDepth));
        breakableBlocks = field(left + 14, top + 146, 617, 1_024, "Breakable blocks", String.join(", ", rules.breakableBlocks));
        requireTool = rules.requireConfiguredTool; allowPvp = rules.allowPvp; removeDrops = rules.removeBlockDrops;
        requireToolButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { requireTool = !requireTool; updateLabels(); })
                .bounds(left + 14, top + 208, 180, 20).build());
        pvpButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { allowPvp = !allowPvp; updateLabels(); })
                .bounds(left + 204, top + 208, 142, 20).build());
        dropsButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { })
                .bounds(left + 356, top + 208, 194, 20).build());
        dropsButton.active = false;
        updateLabels();
    }

    private void initProjectiles(int left, int top) {
        SpleefRules rules = draft.spleef;
        standardProjectileEnabled = rules.standardProjectileEnabled;
        burstProjectileEnabled = rules.burstProjectileEnabled;
        standardProjectileButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            standardProjectileEnabled = !standardProjectileEnabled; updateLabels();
        }).bounds(left + 14, top + 52, 286, 20).build());
        burstProjectileButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            burstProjectileEnabled = !burstProjectileEnabled; updateLabels();
        }).bounds(left + 314, top + 52, 317, 20).build());
        standardUnlock = field(left + 14, top + 108, 180, 6, "Unlock after (seconds)", Integer.toString(rules.standardProjectileUnlockSeconds));
        standardCooldown = field(left + 208, top + 108, 180, 6, "Shot cooldown (seconds)", Integer.toString(rules.standardProjectileCooldownSeconds));
        burstStart = field(left + 14, top + 188, 180, 6, "Power shot starts after", Integer.toString(rules.burstProjectileStartSeconds));
        burstMinInterval = field(left + 208, top + 188, 180, 6, "Minimum award interval", Integer.toString(rules.burstProjectileMinIntervalSeconds));
        burstMaxInterval = field(left + 402, top + 188, 180, 6, "Maximum award interval", Integer.toString(rules.burstProjectileMaxIntervalSeconds));
        burstMaxStack = field(left + 14, top + 268, 180, 3, "Maximum saved power shots", Integer.toString(rules.burstProjectileMaximumStack));
        updateLabels();
    }

    private boolean saveCurrentPage() {
        try {
            if (page == 0 && id != null) saveGeneral();
            else if (page == 1 && arenaId != null) saveArena();
            else if (page == 2 && spawnDimension != null) saveSpawn();
            else if (page == 3 && rewardMoney != null) saveRewards();
            else if (page == 4 && toolItem != null) saveRules();
            else if (page == 5 && standardUnlock != null) saveProjectiles();
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
        draft.minPlayers = parseInt(minPlayers, "Minimum players", 2, 16);
        draft.maxPlayers = parseInt(maxPlayers, "Maximum players", draft.minPlayers, 16);
        draft.countdownSeconds = parseInt(countdown, "Countdown seconds", 0, 600);
        draft.matchDurationSeconds = parseInt(duration, "Match duration", 0, 86_400);
        draft.postGameSeconds = parseInt(postGame, "Result screen duration", 0, 600);
        draft.enabled = enabled; draft.automaticStart = automaticStart;
        draft.gameType = "spleef"; draft.allowLateJoin = false; draft.teamCount = draft.maxPlayers;
        draft.victoryMode = "last_team_standing";
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
        spawn.team = spawnIndex + 1;
        spawn.location = readLocation(spawnDimension, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, "Player spawn");
        reindexSpawns(arena());
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

    private void saveRules() {
        draft.spleef.toolItem = toolItem.getValue().trim();
        draft.spleef.eliminationDepth = parseInt(eliminationDepth, "Elimination depth", 0, 64);
        draft.spleef.breakableBlocks = parseList(breakableBlocks.getValue());
        draft.spleef.requireConfiguredTool = requireTool;
        draft.spleef.allowPvp = allowPvp;
        draft.spleef.removeBlockDrops = true;
    }

    private void saveProjectiles() {
        SpleefRules rules = draft.spleef;
        rules.standardProjectileEnabled = standardProjectileEnabled;
        rules.standardProjectileUnlockSeconds = parseInt(standardUnlock, "Standard projectile unlock", 0, 3_600);
        rules.standardProjectileCooldownSeconds = parseInt(standardCooldown, "Standard projectile cooldown", 1, 300);
        rules.burstProjectileEnabled = burstProjectileEnabled;
        rules.burstProjectileStartSeconds = parseInt(burstStart, "Power projectile start", 0, 3_600);
        rules.burstProjectileMinIntervalSeconds = parseInt(burstMinInterval, "Minimum power projectile interval", 1, 3_600);
        rules.burstProjectileMaxIntervalSeconds = parseInt(burstMaxInterval, "Maximum power projectile interval",
                rules.burstProjectileMinIntervalSeconds, 3_600);
        rules.burstProjectileMaximumStack = parseInt(burstMaxStack, "Maximum power projectile stack", 1, 16);
        rules.normalize();
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
        value.id = "arena_" + (draft.arenas.size() + 1); value.displayName = "Spleef Arena " + (draft.arenas.size() + 1);
        value.resetRegionAfterMatch = true;
        draft.arenas.add(value); arenaIndex = draft.arenas.size() - 1; spawnIndex = 0; rebuildWidgets();
    }

    private void deleteArena() {
        if (arena().managedRegion) { setNotice("Selection-created arenas must be removed by deleting the minigame safely.", true); return; }
        if (draft.arenas.size() <= 1) { setNotice("Spleef needs at least one arena.", true); return; }
        draft.arenas.remove(arenaIndex); arenaIndex = Math.max(0, arenaIndex - 1); spawnIndex = 0; rebuildWidgets();
    }

    private void switchSpawn(int delta) {
        if (!saveCurrentPage()) return;
        spawnIndex = Math.floorMod(spawnIndex + delta, arena().teamSpawns.size()); rebuildWidgets();
    }

    private void addSpawn() {
        if (!saveCurrentPage()) return;
        if (arena().teamSpawns.size() >= 16) { setNotice("Spleef supports at most 16 player spawns.", true); return; }
        arena().teamSpawns.add(new MinigameSpawnPoint(arena().teamSpawns.size() + 1, new MinigameLocation()));
        spawnIndex = arena().teamSpawns.size() - 1; rebuildWidgets();
    }

    private void deleteSpawn() {
        if (arena().teamSpawns.size() <= 1) { setNotice("An arena needs at least one player spawn.", true); return; }
        arena().teamSpawns.remove(spawnIndex); reindexSpawns(arena());
        spawnIndex = Math.max(0, Math.min(spawnIndex, arena().teamSpawns.size() - 1)); rebuildWidgets();
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
        g.text(font, "Spleef Editor", left + W - 104, top + 17, TEXT, true);
        if (page == 0) renderGeneral(g, left, top);
        else if (page == 1) renderArena(g, left, top);
        else if (page == 2) renderSpawns(g, left, top);
        else if (page == 3) renderRewards(g, left, top, mouseX, mouseY);
        else if (page == 4) renderRules(g, left, top);
        else renderProjectiles(g, left, top);
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
        g.text(font, "Spleef uses last player standing, individual player slots and no late joining.", left + 14, top + 304, GOOD, false);
    }

    private void renderArena(GuiGraphicsExtractor g, int left, int top) {
        MinigameArenaDefinition arena = arena();
        g.text(font, "Arena " + (arenaIndex + 1) + " / " + draft.arenas.size(), left + 292, top + 50, MUTED, false);
        fieldInfo(g, left + 14, top + 76, "Arena ID", "Unique key inside this Spleef game.", 150);
        fieldInfo(g, left + 176, top + 76, "Arena name", "Readable name for administrators.", 220);
        fieldInfo(g, left + 408, top + 76, "Arena region", arena.managedRegion
                ? "Managed by the Selection Tool and locked." : "SSU region containing the complete arena.", 223);
        wrapped(g, "Arena reset is always enabled so the verified snapshot restores the floor.",
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
        g.text(font, "Player spawn " + (spawnIndex + 1) + " / " + arena().teamSpawns.size(), left + 340, top + 54, TEXT, true);
        wrapped(g, "Each player receives one separate spawn. Technical team numbers are hidden for Spleef.",
                left + 14, top + 84, 605, GOOD, 2);
        fieldInfo(g, left + 14, top + 116, "Dimension", "World containing this spawn.", 180);
        fieldInfo(g, left + 206, top + 116, "X", "East/west.", 72);
        fieldInfo(g, left + 290, top + 116, "Y", "Height.", 72);
        fieldInfo(g, left + 374, top + 116, "Z", "North/south.", 72);
        fieldInfo(g, left + 458, top + 116, "Yaw", "Facing.", 82);
        fieldInfo(g, left + 552, top + 116, "Pitch", "Look angle.", 79);
        g.text(font, "Required: " + draft.maxPlayers + " · Configured: " + arena().teamSpawns.size(), left + 14, top + 232,
                arena().teamSpawns.size() >= draft.maxPlayers ? GOOD : WARNING, false);
        g.text(font, "Place every spawn on a different block inside the arena footprint.", left + 14, top + 254, MUTED, false);
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

    private void renderRules(GuiGraphicsExtractor g, int left, int top) {
        fieldInfo(g, left + 14, top + 60, "Spleef tool", "Exact item temporarily given to players.", 310);
        fieldInfo(g, left + 340, top + 60, "Elimination depth", "Blocks below the region before elimination.", 140);
        fieldInfo(g, left + 14, top + 130, "Breakable floor blocks", "Comma-separated block IDs players may break.", 617);
        wrapped(g, "Require tool limits breaking to the configured shovel. PvP controls direct player damage. Floor blocks always disappear without item drops.",
                left + 14, top + 246, 617, MUTED, 3);
        wrapped(g, "Players are eliminated by death, leaving the arena footprint/dimension, or falling below the configured depth.",
                left + 14, top + 286, 617, GOOD, 2);
    }

    private void renderProjectiles(GuiGraphicsExtractor g, int left, int top) {
        wrapped(g, "The standard Snowball is infinite: SSU automatically restores one after use, but the server enforces the configured cooldown.",
                left + 14, top + 78, 617, MUTED, 2);
        fieldInfo(g, left + 14, top + 92, "Standard unlock", "Seconds after match start.", 180);
        fieldInfo(g, left + 208, top + 92, "Standard cooldown", "Minimum seconds between valid shots.", 180);
        wrapped(g, "The Power Egg removes the hit floor block plus the four directly adjoining floor blocks. Awards begin later and go to one random active player.",
                left + 14, top + 148, 617, GOOD, 3);
        fieldInfo(g, left + 14, top + 172, "Power start", "Seconds before awards begin.", 180);
        fieldInfo(g, left + 208, top + 172, "Minimum interval", "Shortest random award delay.", 180);
        fieldInfo(g, left + 402, top + 172, "Maximum interval", "Longest random award delay.", 180);
        fieldInfo(g, left + 14, top + 252, "Maximum stack", "Players can save this many Power Eggs.", 180);
        wrapped(g, "If every active player already holds the maximum, that award cycle is skipped safely and a new random interval begins.",
                left + 208, top + 268, 423, MUTED, 2);
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
            case "add_claim_chunks" -> "amount=<positive chunks>. Adds permanent personal claim capacity.";
            case "set_reputation", "add_reputation" -> "faction=<faction key>; amount=<whole number>.";
            default -> "Parameters depend on this registered Content Core action; use key=value pairs separated by semicolons.";
        };
    }

    private void updateLabels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Minigame enabled: " + yes(enabled)));
        if (automaticButton != null) automaticButton.setMessage(Component.literal("Automatic start: " + yes(automaticStart)));
        if (arenaEnabledButton != null) arenaEnabledButton.setMessage(Component.literal("Arena enabled: " + yes(arenaEnabled)));
        if (requireToolButton != null) requireToolButton.setMessage(Component.literal("Require configured tool: " + yes(requireTool)));
        if (pvpButton != null) pvpButton.setMessage(Component.literal("Player damage: " + yes(allowPvp)));
        if (dropsButton != null) dropsButton.setMessage(Component.literal("Block drops: Never"));
        if (standardProjectileButton != null) standardProjectileButton.setMessage(Component.literal(
                "Infinite Snowball projectile: " + yes(standardProjectileEnabled)));
        if (burstProjectileButton != null) burstProjectileButton.setMessage(Component.literal(
                "Stackable Power Egg projectile: " + yes(burstProjectileEnabled)));
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

    private static void reindexSpawns(MinigameArenaDefinition arena) {
        for (int index = 0; index < arena.teamSpawns.size(); index++) arena.teamSpawns.get(index).team = index + 1;
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
