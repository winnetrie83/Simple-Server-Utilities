package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameArenaDefinition;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameBoostRules;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameLocation;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameRewardSet;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameRoleProfile;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameRoleRules;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSpawnPoint;
import be.winnetrie.mod.simpleserverutilities.minigame.CaptureTheFlagRules;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameFlagPoint;
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

/** Dedicated, compact administrator-facing Capture the Flag editor. */
final class CaptureTheFlagMinigameEditorScreen extends MinigameEditorScreen {
    private static final int W = 645, H = 352;
    private static final int TAB_Y = 10;
    private static final String[] TABS = {"General", "Arena", "Player spawns", "Rewards", "Flags & rules", "Boosts", "Roles"};
    private static final int[] TAB_WIDTHS = {76, 62, 102, 68, 92, 66, 58};

    private int page;
    private int arenaIndex;
    private int spawnIndex;
    private boolean winnerRewards;
    private int participationActionIndex = -1;
    private int winnerActionIndex = -1;
    /** Server inventory slot currently held as a non-consuming ghost copy. */
    private int carriedInventorySlot = -1;

    // General
    private EditBox id, name, icon, minPlayers, maxPlayers, countdown, respawnDelay, duration, postGame;
    private MultiLineEditBox description;
    private String descriptionValue = "";
    private boolean enabled, automaticStart, inventoryLock;
    private Button enabledButton, automaticButton, inventoryLockButton;

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

    // Flags and rules
    private int flagTeam = 1;
    private Button flagTeamButton, friendlyFireButton;
    private EditBox teamName, flagBlock, flagColor, scoreToWin, captureRadius, flagTakeCast, weaponItem;
    private EditBox flagDimension, flagX, flagY, flagZ, flagYaw, flagPitch;
    private boolean allowFriendlyFire;

    // Boosts
    private boolean boostsEnabled, boostAutoMode, boostSpeed, boostRegeneration, boostArmor, boostJump;
    private Button boostsEnabledButton, boostModeButton, boostSpeedButton, boostRegenerationButton, boostArmorButton, boostJumpButton;
    private EditBox boostMaximumActive, boostInitialDelay, boostRespawnMin, boostRespawnMax, boostSpacing;
    private EditBox boostSpeedDuration, boostSpeedColor, boostRegenerationDuration, boostRegenerationColor, boostRegenerationHealRate;
    private EditBox boostArmorDuration, boostArmorColor, boostArmorPoints, boostJumpDuration, boostJumpColor;

    // Optional tactical roles
    private int rolePage;
    private boolean rolesEnabled;
    private Button rolesEnabledButton, rolePageButton;
    private EditBox dpsMin, dpsMax, dpsHealth, dpsArmor, dpsToughness;
    private EditBox tankMin, tankMax, tankHealth, tankArmor, tankToughness;
    private EditBox healerMin, healerMax, healerHealth, healerArmor, healerToughness;
    private EditBox tankSlowRange, tankKnockback, tankSlowDuration, tankSlowCooldown;
    private EditBox healerSingleAmount, healerSingleCooldown, healerAoeAmount, healerAoeRange, healerAoeCooldown, healerSelfCooldown;
    private EditBox dpsArrowEffect, dpsArrowLevel, dpsArrowDuration;

    CaptureTheFlagMinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(initial, parent, "Capture the Flag Editor");
        afterDraftReloaded();
    }

    @Override protected void afterDraftReloaded() {
        if (draft.arenas == null) draft.arenas = new ArrayList<>();
        if (draft.arenas.isEmpty()) draft.arenas.add(new MinigameArenaDefinition());
        if (draft.captureTheFlag == null) draft.captureTheFlag = new CaptureTheFlagRules();
        if (draft.captureTheFlag.roles == null) draft.captureTheFlag.roles = new MinigameRoleRules();
        draft.captureTheFlag.roles.normalize();
        if (draft.participationReward == null) draft.participationReward = new MinigameRewardSet();
        if (draft.winnerReward == null) draft.winnerReward = new MinigameRewardSet();
        arenaIndex = Math.max(0, Math.min(arenaIndex, draft.arenas.size() - 1));
        MinigameArenaDefinition arena = arena();
        if (arena.teamSpawns == null) arena.teamSpawns = new ArrayList<>();
        if (arena.teamSpawns.isEmpty()) {
            arena.teamSpawns.add(new MinigameSpawnPoint(1, new MinigameLocation()));
            arena.teamSpawns.add(new MinigameSpawnPoint(2, new MinigameLocation()));
        }
        if (arena.flagPoints == null) arena.flagPoints = new ArrayList<>();
        ensureFlags(arena);
        spawnIndex = Math.max(0, Math.min(spawnIndex, arena.teamSpawns.size() - 1));
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
        else if (page == 4) initRules(left, top);
        else if (page == 5) initBoosts(left, top);
        else initRoles(left, top);

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(left + 14, top + H - 30, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Match flow"), ignored -> openMatchFlow())
                .bounds(left + 98, top + H - 30, 92, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save CTF"), ignored -> saveAll())
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
        minPlayers = field(left + 14, top + 208, 96, 3, "Minimum players", Integer.toString(draft.minPlayers));
        maxPlayers = field(left + 118, top + 208, 96, 3, "Maximum players", Integer.toString(draft.maxPlayers));
        countdown = field(left + 222, top + 208, 96, 6, "Countdown", Integer.toString(draft.countdownSeconds));
        respawnDelay = field(left + 326, top + 208, 96, 3, "Respawn delay", Integer.toString(draft.respawnDelaySeconds));
        duration = field(left + 430, top + 208, 96, 8, "Match duration", Integer.toString(draft.matchDurationSeconds));
        postGame = field(left + 534, top + 208, 97, 6, "Post-game duration", Integer.toString(draft.postGameSeconds));
        enabled = draft.enabled; automaticStart = draft.automaticStart; inventoryLock = draft.lockInventory;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { enabled = !enabled; updateLabels(); })
                .bounds(left + 14, top + 270, 146, 20).build());
        automaticButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { automaticStart = !automaticStart; updateLabels(); })
                .bounds(left + 170, top + 270, 174, 20).build());
        inventoryLockButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { inventoryLock = !inventoryLock; updateLabels(); })
                .bounds(left + 354, top + 270, 174, 20).build());
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

    private void initRules(int left, int top) {
        CaptureTheFlagRules rules = draft.captureTheFlag;
        ensureFlags(arena());
        MinigameFlagPoint flag = flag();
        flagTeamButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> switchFlagTeam())
                .bounds(left + 14, top + 48, 160, 20).build());
        teamName = field(left + 184, top + 48, 150, 64, "Team name", rules.teamName(flagTeam));
        flagBlock = field(left + 344, top + 48, 182, 128, "Physical flag block", rules.flagBlock(flagTeam));
        flagColor = field(left + 536, top + 48, 95, 8, "RGB color", String.format(Locale.ROOT, "%06X", rules.color(flagTeam)));

        flagDimension = field(left + 14, top + 120, 145, 128, "Dimension", flag.location.dimension);
        flagX = field(left + 167, top + 120, 64, 24, "X", coordinate(flag.location.x));
        flagY = field(left + 239, top + 120, 64, 24, "Y", coordinate(flag.location.y));
        flagZ = field(left + 311, top + 120, 64, 24, "Z", coordinate(flag.location.z));
        flagYaw = field(left + 383, top + 120, 64, 16, "Yaw", angle(flag.location.yaw));
        flagPitch = field(left + 455, top + 120, 64, 16, "Pitch", angle(flag.location.pitch));
        addRenderableWidget(Button.builder(Component.literal("Use my position"), ignored -> fillCurrent(
                flagDimension, flagX, flagY, flagZ, flagYaw, flagPitch))
                .bounds(left + 527, top + 120, 104, 20).build());

        scoreToWin = field(left + 14, top + 204, 100, 3, "Captures to win", Integer.toString(rules.scoreToWin));
        captureRadius = field(left + 124, top + 204, 100, 12, "Score radius", coordinate(rules.captureRadius));
        flagTakeCast = field(left + 234, top + 204, 112, 3, "Flag take cast", Integer.toString(rules.flagTakeCastSeconds));
        weaponItem = field(left + 356, top + 204, 164, 128, "Temporary weapon", rules.weaponItem);
        allowFriendlyFire = rules.allowFriendlyFire;
        friendlyFireButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            allowFriendlyFire = !allowFriendlyFire; updateLabels();
        }).bounds(left + 530, top + 204, 101, 20).build());
        updateLabels();
    }

    private void initBoosts(int left, int top) {
        MinigameBoostRules boosts = draft.captureTheFlag.boosts;
        boostsEnabled = boosts.enabled;
        boostAutoMode = boosts.automatic();
        boostSpeed = boosts.speedEnabled;
        boostRegeneration = boosts.regenerationEnabled;
        boostArmor = boosts.armorEnabled;
        boostJump = boosts.jumpEnabled;
        boostsEnabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            boostsEnabled = !boostsEnabled; updateLabels();
        }).bounds(left + 14, top + 43, 196, 20).build());
        boostModeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            boostAutoMode = !boostAutoMode; updateLabels();
        }).bounds(left + 220, top + 43, 196, 20).build());
        boostMaximumActive = field(left + 14, top + 88, 96, 3, "Max active", Integer.toString(boosts.maximumActive));
        boostInitialDelay = field(left + 120, top + 88, 96, 6, "Initial delay", Integer.toString(boosts.initialSpawnDelaySeconds));
        boostRespawnMin = field(left + 226, top + 88, 96, 6, "Respawn min", Integer.toString(boosts.respawnMinSeconds));
        boostRespawnMax = field(left + 332, top + 88, 96, 6, "Respawn max", Integer.toString(boosts.respawnMaxSeconds));
        boostSpacing = field(left + 438, top + 88, 96, 3, "Min spacing", Integer.toString((int) Math.round(boosts.minimumSpacing)));

        boostSpeedButton = boostToggle(left + 14, top + 148, 142, () -> { boostSpeed = !boostSpeed; updateLabels(); });
        boostSpeedDuration = field(left + 166, top + 148, 78, 6, "Duration", Integer.toString(boosts.speedDurationSeconds));
        boostSpeedColor = field(left + 254, top + 148, 88, 8, "Mist RGB", rgb(boosts.speedColor));

        boostRegenerationButton = boostToggle(left + 14, top + 180, 142, () -> { boostRegeneration = !boostRegeneration; updateLabels(); });
        boostRegenerationDuration = field(left + 166, top + 180, 78, 6, "Duration", Integer.toString(boosts.regenerationDurationSeconds));
        boostRegenerationColor = field(left + 254, top + 180, 88, 8, "Mist RGB", rgb(boosts.regenerationColor));
        boostRegenerationHealRate = field(left + 352, top + 180, 96, 8, "Heal / second", roleNumber(boosts.regenerationHealthPerSecond));

        boostArmorButton = boostToggle(left + 14, top + 212, 142, () -> { boostArmor = !boostArmor; updateLabels(); });
        boostArmorDuration = field(left + 166, top + 212, 78, 6, "Duration", Integer.toString(boosts.armorDurationSeconds));
        boostArmorColor = field(left + 254, top + 212, 88, 8, "Mist RGB", rgb(boosts.armorColor));
        boostArmorPoints = field(left + 352, top + 212, 96, 8, "Armor points", roleNumber(boosts.armorPoints));

        boostJumpButton = boostToggle(left + 14, top + 244, 142, () -> { boostJump = !boostJump; updateLabels(); });
        boostJumpDuration = field(left + 166, top + 244, 78, 6, "Duration", Integer.toString(boosts.jumpDurationSeconds));
        boostJumpColor = field(left + 254, top + 244, 88, 8, "Mist RGB", rgb(boosts.jumpColor));
        updateLabels();
    }


    private void initRoles(int left, int top) {
        MinigameRoleRules roles = draft.captureTheFlag.roles;
        roles.normalize();
        rolesEnabled = roles.enabled;
        rolesEnabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            rolesEnabled = !rolesEnabled; updateLabels();
        }).bounds(left + 14, top + 43, 196, 20).build());
        rolePageButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> switchRolePage())
                .bounds(left + 220, top + 43, 196, 20).build());

        if (rolePage == 0) {
            dpsMin = roleField(left + 100, top + 108, 70, "DPS minimum", roles.dps.minimumPerTeam);
            dpsMax = roleField(left + 180, top + 108, 70, "DPS maximum", roles.dps.maximumPerTeam);
            dpsHealth = roleField(left + 260, top + 108, 90, "DPS health", roles.dps.maxHealth);
            dpsArmor = roleField(left + 360, top + 108, 90, "DPS armor", roles.dps.armor);
            dpsToughness = roleField(left + 460, top + 108, 100, "DPS toughness", roles.dps.armorToughness);

            tankMin = roleField(left + 100, top + 164, 70, "Tank minimum", roles.tank.minimumPerTeam);
            tankMax = roleField(left + 180, top + 164, 70, "Tank maximum", roles.tank.maximumPerTeam);
            tankHealth = roleField(left + 260, top + 164, 90, "Tank health", roles.tank.maxHealth);
            tankArmor = roleField(left + 360, top + 164, 90, "Tank armor", roles.tank.armor);
            tankToughness = roleField(left + 460, top + 164, 100, "Tank toughness", roles.tank.armorToughness);

            healerMin = roleField(left + 100, top + 220, 70, "Healer minimum", roles.healer.minimumPerTeam);
            healerMax = roleField(left + 180, top + 220, 70, "Healer maximum", roles.healer.maximumPerTeam);
            healerHealth = roleField(left + 260, top + 220, 90, "Healer health", roles.healer.maxHealth);
            healerArmor = roleField(left + 360, top + 220, 90, "Healer armor", roles.healer.armor);
            healerToughness = roleField(left + 460, top + 220, 100, "Healer toughness", roles.healer.armorToughness);
        } else {
            tankSlowRange = roleField(left + 14, top + 108, 100, "Tank slow radius", roles.tankSlowRadius);
            tankSlowDuration = roleField(left + 124, top + 108, 100, "Tank slow duration", roles.tankSlowDurationSeconds);
            tankSlowCooldown = roleField(left + 234, top + 108, 100, "Tank slow cooldown", roles.tankSlowCooldownSeconds);
            tankKnockback = roleField(left + 344, top + 108, 100, "Tank knockback", roles.tankKnockbackStrength);

            healerSingleAmount = roleField(left + 14, top + 174, 90, "Single heal amount", roles.healerSingleHealAmount);
            healerSingleCooldown = roleField(left + 114, top + 174, 90, "Single heal cooldown", roles.healerSingleHealCooldownSeconds);
            healerAoeAmount = roleField(left + 214, top + 174, 90, "AOE heal amount", roles.healerAoeHealAmount);
            healerAoeRange = roleField(left + 314, top + 174, 90, "AOE heal radius", roles.healerAoeHealRadius);
            healerAoeCooldown = roleField(left + 414, top + 174, 90, "AOE heal cooldown", roles.healerAoeHealCooldownSeconds);
            healerSelfCooldown = roleField(left + 514, top + 174, 90, "Self-heal cooldown", roles.healerSelfHealCooldownSeconds);

            dpsArrowEffect = field(left + 14, top + 250, 250, 128, "Arrow effect ID", roles.dpsArrowEffect);
            dpsArrowLevel = roleField(left + 274, top + 250, 100, "Arrow level", roles.dpsArrowEffectAmplifier + 1);
            dpsArrowDuration = roleField(left + 384, top + 250, 120, "Arrow duration", roles.dpsArrowEffectDurationSeconds);
        }
        updateLabels();
    }

    private EditBox roleField(int x, int y, int width, String hint, int value) {
        return field(x, y, width, 8, hint, Integer.toString(value));
    }

    private EditBox roleField(int x, int y, int width, String hint, double value) {
        return field(x, y, width, 12, hint, roleNumber(value));
    }

    private static String roleNumber(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.000001D ? Long.toString(rounded) : Double.toString(value);
    }

    private Button boostToggle(int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    private boolean saveCurrentPage() {
        try {
            if (page == 0 && id != null) saveGeneral();
            else if (page == 1 && arenaId != null) saveArena();
            else if (page == 2 && spawnDimension != null) saveSpawn();
            else if (page == 3 && rewardMoney != null) saveRewards();
            else if (page == 4 && flagDimension != null) saveRules();
            else if (page == 5 && boostMaximumActive != null) saveBoosts();
            else if (page == 6) saveRoles();
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
        draft.respawnDelaySeconds = parseInt(respawnDelay, "Respawn delay", 1, 300);
        draft.matchDurationSeconds = parseInt(duration, "Match duration", 0, 86_400);
        draft.postGameSeconds = parseInt(postGame, "Result screen duration", 0, 600);
        draft.enabled = enabled; draft.automaticStart = automaticStart; draft.lockInventory = inventoryLock;
        draft.gameType = "capture_the_flag"; draft.allowLateJoin = false; draft.teamCount = 2;
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

    private void saveRules() {
        CaptureTheFlagRules rules = draft.captureTheFlag;
        MinigameFlagPoint flag = flag();
        flag.location = readLocation(flagDimension, flagX, flagY, flagZ, flagYaw, flagPitch, "Flag base");
        String colorText = flagColor.getValue().trim().replace("#", "");
        int color;
        try { color = Integer.parseInt(colorText, 16); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Flag color must be a six-digit RGB hex value."); }
        if (colorText.length() != 6 || color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("Flag color must be a six-digit RGB hex value.");
        }
        if (flagTeam == 1) {
            rules.team1Name = teamName.getValue().trim();
            rules.team1FlagBlock = flagBlock.getValue().trim();
            rules.team1Color = color;
        } else {
            rules.team2Name = teamName.getValue().trim();
            rules.team2FlagBlock = flagBlock.getValue().trim();
            rules.team2Color = color;
        }
        rules.scoreToWin = parseInt(scoreToWin, "Captures to win", 1, 100);
        double radius = parseDouble(captureRadius, "Score radius");
        if (radius < 0.5D || radius > 16.0D) throw new IllegalArgumentException("Score radius must be between 0.5 and 16 blocks.");
        rules.captureRadius = radius;
        rules.flagTakeCastSeconds = parseInt(flagTakeCast, "Flag take cast", 1, 60);
        rules.weaponItem = weaponItem.getValue().trim();
        rules.allowFriendlyFire = allowFriendlyFire;
    }

    private void saveBoosts() {
        MinigameBoostRules boosts = draft.captureTheFlag.boosts;
        boosts.enabled = boostsEnabled;
        boosts.placementMode = boostAutoMode ? "automatic" : "manual";
        boosts.maximumActive = parseInt(boostMaximumActive, "Maximum active boosts", 1, 16);
        boosts.initialSpawnDelaySeconds = parseInt(boostInitialDelay, "Initial boost delay", 0, 86_400);
        boosts.respawnMinSeconds = parseInt(boostRespawnMin, "Minimum boost respawn", 1, 86_400);
        boosts.respawnMaxSeconds = parseInt(boostRespawnMax, "Maximum boost respawn", boosts.respawnMinSeconds, 86_400);
        boosts.minimumSpacing = parseInt(boostSpacing, "Minimum boost spacing", 1, 32);
        boosts.speedEnabled = boostSpeed;
        boosts.speedDurationSeconds = parseInt(boostSpeedDuration, "Speed duration", 1, 600);
        boosts.speedColor = parseRgb(boostSpeedColor, "Speed mist color");
        boosts.regenerationEnabled = boostRegeneration;
        boosts.regenerationDurationSeconds = parseInt(boostRegenerationDuration, "Regeneration duration", 1, 600);
        boosts.regenerationColor = parseRgb(boostRegenerationColor, "Regeneration mist color");
        boosts.regenerationHealthPerSecond = roleDouble(boostRegenerationHealRate,
                "Regeneration health per second", 0.1D, 40.0D);
        boosts.armorEnabled = boostArmor;
        boosts.armorDurationSeconds = parseInt(boostArmorDuration, "Armor duration", 1, 600);
        boosts.armorColor = parseRgb(boostArmorColor, "Armor mist color");
        boosts.armorPoints = parseInt(boostArmorPoints, "Temporary armor points", 1, 20);
        boosts.jumpEnabled = boostJump;
        boosts.jumpDurationSeconds = parseInt(boostJumpDuration, "Jump duration", 1, 600);
        boosts.jumpColor = parseRgb(boostJumpColor, "Jump mist color");
        boosts.normalize();
    }


    private void saveRoles() {
        MinigameRoleRules roles = draft.captureTheFlag.roles;
        roles.enabled = rolesEnabled;
        if (rolePage == 0 && dpsMin != null) {
            saveRoleProfile(roles.dps, dpsMin, dpsMax, dpsHealth, dpsArmor, dpsToughness, "DPS");
            saveRoleProfile(roles.tank, tankMin, tankMax, tankHealth, tankArmor, tankToughness, "Tank");
            saveRoleProfile(roles.healer, healerMin, healerMax, healerHealth, healerArmor, healerToughness, "Healer");
        } else if (rolePage == 1 && tankSlowDuration != null) {
            roles.tankSlowRadius = roleDouble(tankSlowRange, "Tank slow radius", 1.0D, 16.0D);
            roles.tankKnockbackStrength = roleDouble(tankKnockback, "Tank knockback strength", 0.0D, 5.0D);
            roles.tankSlowDurationSeconds = parseInt(tankSlowDuration, "Tank slow duration", 1, 60);
            roles.tankSlowCooldownSeconds = parseInt(tankSlowCooldown, "Tank slow cooldown", 1, 600);
            roles.healerSingleHealAmount = roleDouble(healerSingleAmount, "Single-target heal", 1.0D, 100.0D);
            roles.healerSingleHealCooldownSeconds = parseInt(healerSingleCooldown, "Single-target heal cooldown", 1, 600);
            roles.healerAoeHealAmount = roleDouble(healerAoeAmount, "AOE heal", 0.5D, 100.0D);
            roles.healerAoeHealRadius = roleDouble(healerAoeRange, "AOE heal radius", 1.0D, 16.0D);
            if (roles.healerAoeHealAmount >= roles.healerSingleHealAmount)
                throw new IllegalArgumentException("AOE heal must be weaker than the single-target heal.");
            roles.healerAoeHealCooldownSeconds = parseInt(healerAoeCooldown, "AOE heal cooldown", 1, 600);
            roles.healerSelfHealCooldownSeconds = parseInt(healerSelfCooldown, "Self-heal cooldown", 1, 600);
            roles.dpsArrowEffect = dpsArrowEffect.getValue().trim().toLowerCase(Locale.ROOT);
            if (roles.dpsArrowEffect.isBlank()) throw new IllegalArgumentException("DPS arrow effect ID cannot be empty.");
            try { net.minecraft.resources.Identifier.parse(roles.dpsArrowEffect); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("DPS arrow effect ID is invalid."); }
            roles.dpsArrowEffectAmplifier = parseInt(dpsArrowLevel, "DPS arrow effect level", 1, 10) - 1;
            roles.dpsArrowEffectDurationSeconds = parseInt(dpsArrowDuration, "DPS arrow effect duration", 1, 600);
        }
        roles.normalize();
        if (roles.enabled && roles.minimumTotalPerTeam() > draft.minPlayers / 2)
            throw new IllegalArgumentException("Role minimums do not fit the smallest team at the configured minimum player count.");
        if (roles.enabled && roles.maximumTotalPerTeam() < (draft.maxPlayers + 1) / 2)
            throw new IllegalArgumentException("Role maximums cannot hold the largest possible team.");
    }

    private void saveRoleProfile(MinigameRoleProfile profile, EditBox minimum, EditBox maximum, EditBox health,
                                 EditBox armor, EditBox toughness, String label) {
        int min = parseInt(minimum, label + " minimum per team", 0, 64);
        int max = parseInt(maximum, label + " maximum per team", Math.max(1, min), 64);
        profile.minimumPerTeam = min;
        profile.maximumPerTeam = max;
        profile.maxHealth = roleDouble(health, label + " max health", 1.0D, 1024.0D);
        profile.armor = roleDouble(armor, label + " armor", 0.0D, 100.0D);
        profile.armorToughness = roleDouble(toughness, label + " armor toughness", 0.0D, 100.0D);
        profile.normalize();
    }

    private double roleDouble(EditBox box, String label, double minimum, double maximum) {
        double value = parseDouble(box, label);
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException(label + " must be between " + roleNumber(minimum) + " and " + roleNumber(maximum) + ".");
        return value;
    }

    private void switchRolePage() {
        if (!saveCurrentPage()) return;
        rolePage = rolePage == 0 ? 1 : 0;
        rebuildWidgets();
    }

    private void saveAll() { if (saveCurrentPage()) submitDraft(); }
    private void openMatchFlow() {
        if (!saveCurrentPage()) return;
        if (minecraft != null) minecraft.setScreenAndShow(new MinigameExperienceSettingsScreen(draft, this));
    }

    private void switchPage(int target) { if (target != page && saveCurrentPage()) { page = target; rebuildWidgets(); } }

    private void switchArena(int delta) {
        if (!saveCurrentPage()) return;
        arenaIndex = Math.floorMod(arenaIndex + delta, draft.arenas.size()); spawnIndex = 0; rebuildWidgets();
    }

    private void addArena() {
        if (!saveCurrentPage()) return;
        if (draft.arenas.size() >= 32) { setNotice("At most 32 arenas are supported.", true); return; }
        MinigameArenaDefinition value = new MinigameArenaDefinition();
        value.id = "arena_" + (draft.arenas.size() + 1); value.displayName = "Capture the Flag Arena " + (draft.arenas.size() + 1);
        value.resetRegionAfterMatch = true;
        draft.arenas.add(value); arenaIndex = draft.arenas.size() - 1; spawnIndex = 0; rebuildWidgets();
    }

    private void deleteArena() {
        if (arena().managedRegion) { setNotice("Selection-created arenas must be removed by deleting the minigame safely.", true); return; }
        if (draft.arenas.size() <= 1) { setNotice("Capture the Flag needs at least one arena.", true); return; }
        draft.arenas.remove(arenaIndex); arenaIndex = Math.max(0, arenaIndex - 1); spawnIndex = 0; rebuildWidgets();
    }

    private void switchSpawn(int delta) {
        if (!saveCurrentPage()) return;
        spawnIndex = Math.floorMod(spawnIndex + delta, arena().teamSpawns.size()); rebuildWidgets();
    }

    private void addSpawn() {
        if (!saveCurrentPage()) return;
        if (arena().teamSpawns.size() >= 64) { setNotice("Capture the Flag supports at most 64 team spawns.", true); return; }
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
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000); g.fill(left, top, left + W, top + H, PANEL); g.outline(left, top, W, H, BORDER);
        if (page == 0) renderGeneral(g, left, top);
        else if (page == 1) renderArena(g, left, top);
        else if (page == 2) renderSpawns(g, left, top);
        else if (page == 3) renderRewards(g, left, top, mouseX, mouseY);
        else if (page == 4) renderRules(g, left, top);
        else if (page == 5) renderBoosts(g, left, top);
        else renderRoles(g, left, top);
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
        fieldInfo(g, left + 14, top + 192, "Minimum", "Players to start.", 96);
        fieldInfo(g, left + 118, top + 192, "Maximum", "Player limit.", 96);
        fieldInfo(g, left + 222, top + 192, "Countdown", "Lobby seconds.", 96);
        fieldInfo(g, left + 326, top + 192, "Respawn", "Delay after death.", 96);
        fieldInfo(g, left + 430, top + 192, "Match time", "0 = unlimited.", 96);
        fieldInfo(g, left + 534, top + 192, "Post-game", "Return delay.", 97);
        g.text(font, "Capture the Flag uses two teams, highest capture score and no late joining.", left + 14, top + 304, GOOD, false);
    }

    private void renderArena(GuiGraphicsExtractor g, int left, int top) {
        MinigameArenaDefinition arena = arena();
        g.text(font, "Arena " + (arenaIndex + 1) + " / " + draft.arenas.size(), left + 292, top + 50, MUTED, false);
        fieldInfo(g, left + 14, top + 76, "Arena ID", "Unique key inside this Capture the Flag game.", 150);
        fieldInfo(g, left + 176, top + 76, "Arena name", "Readable name for administrators.", 220);
        fieldInfo(g, left + 408, top + 76, "Arena region", arena.managedRegion
                ? "Managed by the Selection Tool and locked." : "SSU region containing the complete arena.", 223);
        wrapped(g, "Arena reset is always enabled so flags and changed blocks return to the verified snapshot.",
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
        g.text(font, draft.captureTheFlag.team1Name + " spawns: " + red + " · "
                + draft.captureTheFlag.team2Name + " spawns: " + blue, left + 14, top + 232,
                red > 0 && blue > 0 ? GOOD : WARNING, false);
        g.text(font, "Place team spawns inside the arena and away from the opposing flag base.", left + 14, top + 254, MUTED, false);
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
        fieldInfo(g, left + 184, top + 32, "Team name", "Name shown in the HUD and winner title.", 150);
        fieldInfo(g, left + 344, top + 32, "Physical flag", "Banner block placed at this team's base.", 182);
        fieldInfo(g, left + 536, top + 32, "Glow color", "Six-digit RGB hex.", 95);
        g.text(font, "Flag base position", left + 14, top + 92, TEXT, true);
        wrapped(g, "Right-click the enemy banner. After the cast, the carrier gets the real flag on their back and a team-colored glow.",
                left + 122, top + 88, 509, MUTED, 2);
        g.text(font, "Dimension", left + 14, top + 106, MUTED, false); g.text(font, "X", left + 167, top + 106, MUTED, false);
        g.text(font, "Y", left + 239, top + 106, MUTED, false); g.text(font, "Z", left + 311, top + 106, MUTED, false);
        g.text(font, "Yaw", left + 383, top + 106, MUTED, false); g.text(font, "Pitch", left + 455, top + 106, MUTED, false);
        fieldInfo(g, left + 14, top + 188, "Captures to win", "Team score required.", 100);
        fieldInfo(g, left + 124, top + 188, "Score radius", "Distance around own base.", 100);
        fieldInfo(g, left + 234, top + 188, "Flag take cast", "Still seconds before pickup.", 112);
        fieldInfo(g, left + 356, top + 188, "Temporary weapon", "Item used during the match.", 164);
        wrapped(g, "Right-click the enemy flag and remain still until the castbar completes. Moving, leaving the flag or taking damage interrupts the pickup.",
                left + 14, top + 246, 617, GOOD, 2);
        wrapped(g, "A carrier scores only while its own flag is at base. Death or crouching drops the physical flag; teammates pick it up instantly and its owner returns it instantly.",
                left + 14, top + 274, 617, GOOD, 2);
        wrapped(g, "Friendly fire controls damage between teammates. Opposing teams can always fight inside the live arena.",
                left + 14, top + 302, 617, MUTED, 1);
    }

    private void renderBoosts(GuiGraphicsExtractor g, int left, int top) {
        g.text(font, "Max active", left + 14, top + 74, TEXT, true);
        g.text(font, "Initial delay", left + 120, top + 74, TEXT, true);
        g.text(font, "Respawn min", left + 226, top + 74, TEXT, true);
        g.text(font, "Respawn max", left + 332, top + 74, TEXT, true);
        g.text(font, "Min spacing", left + 438, top + 74, TEXT, true);

        g.text(font, "Allowed boost", left + 14, top + 132, TEXT, true);
        g.text(font, "Duration", left + 166, top + 132, TEXT, true);
        g.text(font, "Mist RGB", left + 254, top + 132, TEXT, true);
        g.text(font, "Heal/sec | armor", left + 352, top + 132, TEXT, true);

        g.text(font, "2 health points equal 1 heart. RGB values use six digits, for example 40C4FF.",
                left + 14, top + 282, MUTED, false);
    }


    private void renderRoles(GuiGraphicsExtractor g, int left, int top) {
        if (rolePage == 0) {
            g.text(font, "Role", left + 14, top + 90, TEXT, true);
            g.text(font, "Minimum / team", left + 100, top + 90, TEXT, true);
            g.text(font, "Maximum / team", left + 180, top + 90, TEXT, true);
            g.text(font, "Max health", left + 260, top + 90, TEXT, true);
            g.text(font, "Armor", left + 360, top + 90, TEXT, true);
            g.text(font, "Toughness", left + 460, top + 90, TEXT, true);
            g.text(font, "DPS", left + 14, top + 114, GOOD, true);
            g.text(font, "Tank", left + 14, top + 170, 0xFFFFC857, true);
            g.text(font, "Healer", left + 14, top + 226, 0xFF7FE3A1, true);
            wrapped(g, "Players choose a preferred role before joining. SSU first satisfies each team's minimums, then respects preferences while staying within the maxima; a preference is never guaranteed.",
                    left + 14, top + 266, 617, GOOD, 3);
            wrapped(g, "Every role wears team-colored cosmetic leather. These base health, armor and toughness values provide the real combat statistics; two health points equal one heart.",
                    left + 14, top + 298, 617, MUTED, 2);
        } else {
            g.text(font, "Tank defensive field", left + 14, top + 84, TEXT, true);
            g.text(font, "Radius", left + 14, top + 96, MUTED, false);
            g.text(font, "Slow duration", left + 124, top + 96, MUTED, false);
            g.text(font, "Cooldown", left + 234, top + 96, MUTED, false);
            g.text(font, "Knockback", left + 344, top + 96, MUTED, false);
            wrapped(g, "Slows and pushes enemy players away inside the configured AOE radius. Knockback 0 disables the push. Activating without a target still consumes the cooldown.", left + 454, top + 86, 170, GOOD, 4);

            g.text(font, "Healer abilities", left + 14, top + 142, TEXT, true);
            g.text(font, "Single heal", left + 14, top + 160, MUTED, false);
            g.text(font, "Single CD", left + 114, top + 160, MUTED, false);
            g.text(font, "AOE heal", left + 214, top + 160, MUTED, false);
            g.text(font, "AOE radius", left + 314, top + 160, MUTED, false);
            g.text(font, "AOE CD", left + 414, top + 160, MUTED, false);
            g.text(font, "Self CD", left + 514, top + 160, MUTED, false);
            wrapped(g, "The straight single-target beam reaches eight blocks. AOE heals allies inside its configured radius, including the caster, and must remain weaker. Every ability may be fired without a valid target and still consumes its cooldown.",
                    left + 14, top + 204, 570, GOOD, 3);

            g.text(font, "DPS infinite arrow", left + 14, top + 226, TEXT, true);
            g.text(font, "Effect ID", left + 14, top + 238, MUTED, false);
            g.text(font, "Level", left + 274, top + 238, MUTED, false);
            g.text(font, "Duration (s)", left + 384, top + 238, MUTED, false);
            wrapped(g, "Default: minecraft:poison, level 1. The special arrow is replenished automatically after it is fired.",
                    left + 14, top + 278, 570, MUTED, 2);
        }
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
        if (inventoryLockButton != null) inventoryLockButton.setMessage(Component.literal("Inventory lock: " + yes(inventoryLock)));
        if (arenaEnabledButton != null) arenaEnabledButton.setMessage(Component.literal("Arena enabled: " + yes(arenaEnabled)));
        if (spawnTeamButton != null) spawnTeamButton.setMessage(Component.literal("Team: "
                + draft.captureTheFlag.teamName(selectedSpawnTeam)));
        if (flagTeamButton != null) flagTeamButton.setMessage(Component.literal("Editing flag: "
                + draft.captureTheFlag.teamName(flagTeam)));
        if (friendlyFireButton != null) friendlyFireButton.setMessage(Component.literal("Friendly fire: " + yes(allowFriendlyFire)));
        if (boostsEnabledButton != null) boostsEnabledButton.setMessage(Component.literal("Boost system: " + yes(boostsEnabled)));
        if (boostModeButton != null) boostModeButton.setMessage(Component.literal("Placement: " + (boostAutoMode ? "Automatic" : "Manual")));
        if (boostSpeedButton != null) boostSpeedButton.setMessage(Component.literal("Speed: " + yes(boostSpeed)));
        if (boostRegenerationButton != null) boostRegenerationButton.setMessage(Component.literal("Regeneration: " + yes(boostRegeneration)));
        if (boostArmorButton != null) boostArmorButton.setMessage(Component.literal("Temporary armor: " + yes(boostArmor)));
        if (boostJumpButton != null) boostJumpButton.setMessage(Component.literal("Jump boost: " + yes(boostJump)));
        if (rolesEnabledButton != null) rolesEnabledButton.setMessage(Component.literal("Tactical roles: " + yes(rolesEnabled)));
        if (rolePageButton != null) rolePageButton.setMessage(Component.literal(rolePage == 0 ? "Open abilities & arrows" : "Open limits & attributes"));
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

    private void switchFlagTeam() {
        if (!saveCurrentPage()) return;
        flagTeam = flagTeam == 1 ? 2 : 1;
        rebuildWidgets();
    }

    private MinigameFlagPoint flag() {
        ensureFlags(arena());
        return arena().flagForTeam(flagTeam);
    }

    private static void ensureFlags(MinigameArenaDefinition arena) {
        if (arena.flagPoints == null) arena.flagPoints = new ArrayList<>();
        if (arena.flagForTeam(1) == null) arena.flagPoints.add(new MinigameFlagPoint(1, new MinigameLocation()));
        if (arena.flagForTeam(2) == null) arena.flagPoints.add(new MinigameFlagPoint(2, new MinigameLocation()));
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

    private static String rgb(int color) { return String.format(Locale.ROOT, "%06X", color & 0xFFFFFF); }

    private static int parseRgb(EditBox field, String label) {
        String value = field.getValue().trim().replace("#", "");
        if (value.length() != 6) throw new IllegalArgumentException(label + " must be a six-digit RGB hex value.");
        try { return Integer.parseInt(value, 16) & 0xFFFFFF; }
        catch (RuntimeException exception) { throw new IllegalArgumentException(label + " must be a six-digit RGB hex value."); }
    }

    private static String yes(boolean value) { return value ? "Yes" : "No"; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
}
