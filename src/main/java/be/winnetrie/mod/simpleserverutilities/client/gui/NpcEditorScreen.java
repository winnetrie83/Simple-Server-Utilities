package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.NpcAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorLootSlot;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutOpenRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAbilityDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhase;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhaseActionType;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttackPatternAction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttackPatternStep;
import be.winnetrie.mod.simpleserverutilities.npc.NpcPatrolPoint;
import be.winnetrie.mod.simpleserverutilities.npc.NpcPatrolMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBehaviorMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcCombatProfile;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFriendlyDefenseReaction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcHostileSightReaction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcSelfDefenseReaction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFactionRelation;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFunction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInteractionMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcLoadoutMenu;
import be.winnetrie.mod.simpleserverutilities.npc.NpcScheduleActivity;
import be.winnetrie.mod.simpleserverutilities.npc.NpcScheduleEntry;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import be.winnetrie.mod.simpleserverutilities.npc.NpcTextureSource;
import be.winnetrie.mod.simpleserverutilities.npc.NpcVisualMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact multi-page NPC editor. Inventory loadouts are edited in a real container menu. */
public final class NpcEditorScreen extends Screen {
    private static final int W = 510, H = 350;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;
    private enum Page { IDENTITY, APPEARANCE, INTERACTION, BEHAVIOR, MOVEMENT, RELATIONS, COMBAT, TACTICS, ABILITIES, BOSS, STATS, LOADOUT, SCHEDULE, RESPAWN }

    private final NpcEditorOpenPayload initial;
    private final Screen parent;
    private final List<String> models;
    private final List<String> services;
    private Page page = Page.IDENTITY;

    private String definitionIdValue, displayNameValue, entityTypeValue, visualModeValue, textureSourceValue, textureValueValue, textureModelValue, interactionTextValue, dialogueIdValue;
    private String customModelResourceValue, customTextureResourceValue, customAnimationResourceValue;
    private String idleAnimationValue, walkAnimationValue, attackAnimationValue, castAnimationValue, hurtAnimationValue, deathAnimationValue;
    private String roleId, shopIdValue, interactionMode;
    private int roleColor;
    private final List<NpcFunction> functions = new ArrayList<>();
    private int functionIndex;
    private String functionIdValue = "function", functionLabelValue = "Service", functionTargetValue = "";
    private String xValue, yValue, zValue, yawValue;
    private String factionIdValue, factionDisplayNameValue, minimumReputationValue, reputationDeniedTextValue, reputationLossValue;
    private String playerAttitude;
    private String whenAttackedValue, whenFriendlyAttackedValue, whenHostileSeenValue, combatProfileValue;
    private String assistRangeValue, fleeDistanceValue, attackCooldownValue;
    private boolean meleeAttacksEnabled, rangedAttacksEnabled, magicAttacksEnabled;
    private boolean threatEnabled, attackPatternEnabled;
    private String threatRangeValue, threatDamageValue, threatHealingValue, threatDecayValue, threatSwitchValue;
    private final List<NpcAttackPatternStep> attackPattern = new ArrayList<>();
    private int patternIndex;
    private String patternMinRangeValue="0", patternMaxRangeValue="4", patternMinHealthValue="0", patternMaxHealthValue="100";
    private final List<NpcAbilityDefinition> abilities = new ArrayList<>();
    private int abilityIndex;
    private String abilityIdValue = "ability", abilityNameValue = "Ability", abilityPhaseValue = "";
    private String abilityCooldownValue = "100", abilityWindupValue = "12", abilityRecoveryValue = "8";
    private String abilityMinRangeValue = "0", abilityMaxRangeValue = "4", abilityChanceValue = "1";
    private String abilityDamageValue = "8", abilityRadiusValue = "4", abilityKnockbackValue = "0.8", abilityHealValue = "8";
    private boolean bossEnabled, bossBarVisible, bossHealOnReset;
    private String bossBarRangeValue = "64", bossResetDistanceValue = "48", bossResetSecondsValue = "12";
    private final List<NpcBossPhase> bossPhases = new ArrayList<>();
    private int bossPhaseIndex;
    private String bossPhaseIdValue = "phase_1", bossPhaseNameValue = "Phase 1", bossPhaseThresholdValue = "100";
    private String bossPhaseSpeedValue = "1", bossPhaseCooldownValue = "1", bossPhaseDamageValue = "1";
    private final List<NpcFactionRelation> relations = new ArrayList<>();
    private int relationIndex;
    private String relationFactionValue = "";
    private String maxHealthValue, magicResistanceValue, armorMultiplierValue;
    private String meleeDamageMultiplierValue, rangedDamageMultiplierValue, magicDamageMultiplierValue;
    private String walkingSpeedValue, runningSpeedValue, followRangeValue, knockbackValue, scaleValue, homeRadiusValue;
    private boolean enabled, nameVisible, noAi, invulnerable, silent, glowing, gravity, canSwim, canFly;
    private String behaviorModeValue, lookAtRangeValue, wanderRadiusValue, wanderIntervalValue;
    private boolean lookAtBody;
    private String patrolModeValue;
    private final List<NpcPatrolPoint> patrol = new ArrayList<>();
    private int patrolIndex;
    private String patrolXValue = "0", patrolYValue = "64", patrolZValue = "0", patrolYawValue = "0", patrolPauseValue = "1";
    private boolean scheduleEnabled, respawnEnabled;
    private final List<NpcScheduleEntry> schedule = new ArrayList<>();
    private int scheduleIndex;
    private String scheduleTimeValue = "06:00", scheduleXValue = "0", scheduleYValue = "64", scheduleZValue = "0";
    private String scheduleYawValue = "0", scheduleSpeedValue = "1";
    private String scheduleMovement = NpcScheduleEntry.MOVEMENT_WALK;
    private String scheduleActivity = NpcScheduleEntry.ACTIVITY_IDLE;
    private String respawnDelayValue, respawnDimensionValue, respawnXValue, respawnYValue, respawnZValue, respawnYawValue;

    private EditBox definitionId, displayName, roleField, textureValueField, dialogueId, xField, yField, zField, yawField;
    private EditBox customModelResource, customTextureResource, customAnimationResource;
    private EditBox idleAnimation, walkAnimation, attackAnimation, castAnimation, hurtAnimation, deathAnimation;
    private EditBox functionId, functionLabel, functionTarget;
    private EditBox factionId, factionDisplayName, minimumReputation, reputationDeniedText, reputationLoss;
    private EditBox assistRange, fleeDistance, attackCooldown;
    private EditBox threatRange, threatDamage, threatHealing, threatDecay, threatSwitch;
    private EditBox patternMinRange, patternMaxRange, patternMinHealth, patternMaxHealth;
    private EditBox abilityId, abilityName, abilityPhase, abilityCooldown, abilityWindup, abilityRecovery;
    private EditBox abilityMinRange, abilityMaxRange, abilityChance, abilityDamage, abilityRadius, abilityKnockback, abilityHeal;
    private EditBox bossBarRange, bossResetDistance, bossResetSeconds;
    private EditBox bossPhaseId, bossPhaseName, bossPhaseThreshold, bossPhaseSpeed, bossPhaseCooldown, bossPhaseDamage;
    private EditBox maxHealth, magicResistance, armorMultiplier, meleeDamageMultiplier, rangedDamageMultiplier, magicDamageMultiplier;
    private EditBox walkingSpeed, runningSpeed, followRange, knockback, scale, homeRadius;
    private EditBox lookAtRange, wanderRadius, wanderInterval;
    private EditBox patrolX, patrolY, patrolZ, patrolYaw, patrolPause;
    private EditBox scheduleTime, scheduleX, scheduleY, scheduleZ, scheduleYaw, scheduleSpeed;
    private EditBox respawnDelay, respawnDimension, respawnX, respawnY, respawnZ, respawnYaw;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;
    private boolean deleteArmed;
    private boolean pendingDelete;
    private boolean pendingPatrolWorldEdit;
    private boolean pendingScheduleWorldEdit;

    public NpcEditorScreen(NpcEditorOpenPayload initial, Screen parent) {
        super(Component.literal(initial.editing() ? "Edit NPC" : "Create NPC"));
        this.initial = initial; this.parent = parent; this.models = initial.availableModels();
        this.services = initial.availableServices().stream().filter(value -> !"shop".equals(value)).toList();
        definitionIdValue = initial.definitionId(); displayNameValue = initial.displayName();
        entityTypeValue = initial.entityType(); visualModeValue = NpcVisualMode.parse(initial.visualMode()).id();
        textureSourceValue = NpcTextureSource.parse(initial.textureSource()).id(); textureValueValue = initial.textureValue(); textureModelValue = initial.textureModel();
        customModelResourceValue = initial.customModelResource(); customTextureResourceValue = initial.customTextureResource(); customAnimationResourceValue = initial.customAnimationResource();
        idleAnimationValue = initial.idleAnimation(); walkAnimationValue = initial.walkAnimation(); attackAnimationValue = initial.attackAnimation();
        castAnimationValue = initial.castAnimation(); hurtAnimationValue = initial.hurtAnimation(); deathAnimationValue = initial.deathAnimation();
        interactionTextValue = initial.interactionText(); dialogueIdValue = initial.dialogueId();
        roleId = initial.roleId(); roleColor = initial.roleColor(); shopIdValue = initial.shopId(); interactionMode = NpcInteractionMode.parse(initial.interactionMode()).id();
        for (NpcFunction function : initial.functions()) functions.add(function.copy());
        loadFunction();
        xValue = number(initial.x()); yValue = number(initial.y()); zValue = number(initial.z()); yawValue = number(initial.yaw());
        enabled = initial.enabled(); nameVisible = initial.customNameVisible(); noAi = initial.noAi();
        invulnerable = initial.invulnerable(); silent = initial.silent(); glowing = initial.glowing();
        gravity = initial.affectedByGravity(); canSwim = initial.canSwim(); canFly = initial.canFly();
        behaviorModeValue = NpcBehaviorMode.parse(initial.behaviorMode()).id();
        lookAtRangeValue = number(initial.lookAtRange()); lookAtBody = initial.lookAtBody();
        wanderRadiusValue = number(initial.wanderRadius()); wanderIntervalValue = Integer.toString(initial.wanderIntervalSeconds());
        patrolModeValue = NpcPatrolMode.parse(initial.patrolMode()).id();
        for (NpcPatrolPoint point : initial.patrol()) patrol.add(point.copy());
        loadPatrol();
        factionIdValue = initial.factionId(); factionDisplayNameValue = initial.factionDisplayName(); minimumReputationValue = Integer.toString(initial.minimumReputation());
        reputationDeniedTextValue = initial.reputationDeniedText(); reputationLossValue = Integer.toString(initial.reputationLossOnAttack());
        playerAttitude = NpcAttitude.parse(initial.playerAttitude()).id();
        whenAttackedValue = NpcSelfDefenseReaction.parse(initial.whenAttacked()).id();
        whenFriendlyAttackedValue = NpcFriendlyDefenseReaction.parse(initial.whenFriendlyAttacked()).id();
        whenHostileSeenValue = NpcHostileSightReaction.parse(initial.whenHostileSeen()).id();
        combatProfileValue = NpcCombatProfile.parse(initial.combatProfile()).id();
        assistRangeValue = number(initial.assistRange()); fleeDistanceValue = number(initial.fleeDistance());
        attackCooldownValue = Integer.toString(initial.attackCooldownTicks());
        meleeAttacksEnabled = initial.meleeAttacksEnabled(); rangedAttacksEnabled = initial.rangedAttacksEnabled(); magicAttacksEnabled = initial.magicAttacksEnabled();
        threatEnabled = initial.threatEnabled(); threatRangeValue = number(initial.threatRange());
        threatDamageValue = number(initial.threatDamageMultiplier()); threatHealingValue = number(initial.threatHealingMultiplier());
        threatDecayValue = number(initial.threatDecayPerSecond()); threatSwitchValue = number(initial.threatSwitchRatio());
        attackPatternEnabled = initial.attackPatternEnabled(); for (NpcAttackPatternStep step : initial.attackPattern()) attackPattern.add(step.copy());
        loadPatternStep();
        for (NpcAbilityDefinition ability : initial.abilities()) abilities.add(ability.copy());
        loadAbility();
        bossEnabled = initial.bossEnabled(); bossBarVisible = initial.bossBarVisible(); bossHealOnReset = initial.bossHealOnReset();
        bossBarRangeValue = number(initial.bossBarRange()); bossResetDistanceValue = number(initial.bossResetDistance());
        bossResetSecondsValue = Integer.toString(initial.bossResetSeconds());
        for (NpcBossPhase phase : initial.bossPhases()) bossPhases.add(phase.copy());
        loadBossPhase();
        repairOptionalBossPhaseReferences();
        for (NpcFactionRelation relation : initial.factionRelations()) relations.add(relation.copy());
        loadRelation();
        maxHealthValue = optional(initial.maxHealth()); magicResistanceValue = number(initial.magicResistance()); armorMultiplierValue = number(initial.armorMultiplier());
        meleeDamageMultiplierValue = number(initial.meleeDamageMultiplier()); rangedDamageMultiplierValue = number(initial.rangedDamageMultiplier()); magicDamageMultiplierValue = number(initial.magicDamageMultiplier());
        walkingSpeedValue = number(initial.walkingSpeed()); runningSpeedValue = number(initial.runningSpeed()); followRangeValue = optional(initial.followRange());
        knockbackValue = optional(initial.knockbackResistance()); scaleValue = optional(initial.scale());
        homeRadiusValue = number(initial.homeRadius());
        scheduleEnabled = initial.scheduleEnabled(); for (NpcScheduleEntry entry : initial.schedule()) schedule.add(entry.copy());
        loadSchedule();
        respawnEnabled = initial.respawnEnabled(); respawnDelayValue = Integer.toString(initial.respawnDelaySeconds());
        respawnDimensionValue = initial.respawnDimension(); respawnXValue = number(initial.respawnX());
        respawnYValue = number(initial.respawnY()); respawnZValue = number(initial.respawnZ()); respawnYawValue = number(initial.respawnYaw());
    }

    @Override protected void init() {
        clearRefs(); int x = px(), y = py();
        Page[] pages = Page.values(); String[] labels = {"Identity", "Appearance", "Interaction", "Behavior", "Movement", "Relations", "Combat", "Tactics", "Abilities", "Boss", "Stats", "Loadout", "Schedule", "Respawn"};
        for (int i = 0; i < pages.length; i++) {
            int col = i % 7, row = i / 7;
            Page targetPage = pages[i];
            Button button = addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> switchPage(targetPage))
                    .bounds(x + 12 + col * 69, y + 30 + row * 21, 66, 18).build());
            button.active = targetPage != page;
        }
        switch (page) {
            case IDENTITY -> initIdentity(x, y);
            case APPEARANCE -> initAppearance(x, y);
            case INTERACTION -> initInteraction(x, y);
            case BEHAVIOR -> initBehavior(x, y);
            case MOVEMENT -> initMovement(x, y);
            case RELATIONS -> initRelations(x, y);
            case COMBAT -> initCombat(x, y);
            case TACTICS -> initTactics(x, y);
            case ABILITIES -> initAbilities(x, y);
            case BOSS -> initBoss(x, y);
            case STATS -> initStats(x, y);
            case LOADOUT -> initLoadout(x, y);
            case SCHEDULE -> initSchedule(x, y);
            case RESPAWN -> initRespawn(x, y);
        }
        addRenderableWidget(Button.builder(Component.literal("×"), b -> onClose())
                .bounds(x + W - 30, y + 8, 18, 18).build());
        if (initial.editing()) addRenderableWidget(Button.builder(Component.literal(deleteArmed ? "Confirm delete" : "Delete"), b -> delete())
                .bounds(x + 12, y + H - 25, 96, 18).build());
        addRenderableWidget(Button.builder(Component.literal(initial.editing() ? "Save" : "Create"), b -> submit(false))
                .bounds(x + W - 84, y + H - 25, 72, 18).build());
    }

    private void initIdentity(int x, int y) {
        definitionId = field(x + 12, y + 88, 230, 64, definitionIdValue);
        definitionId.setEditable(!initial.editing());
        displayName = field(x + 250, y + 88, 248, 64, displayNameValue);
        roleField = field(x + 12, y + 132, 248, 64, roleId);
        int paletteX = x + 276, paletteY = y + 126, cell = 14, gap = 2;
        for (int color = 0; color < 16; color++) {
            int selected = color;
            RichTextPalette.SwatchButton swatch = addRenderableWidget(RichTextPalette.button(
                    paletteX + (color % 8) * (cell + gap), paletteY + (color / 8) * (cell + gap), cell, color,
                    b -> { savePage(); roleColor = selected; rebuildWidgets(); }));
            swatch.active = roleColor != color;
        }
        xField = field(x + 12, y + 190, 95, 20, xValue); yField = field(x + 115, y + 190, 95, 20, yValue);
        zField = field(x + 218, y + 190, 95, 20, zValue); yawField = field(x + 321, y + 190, 95, 16, yawValue);
        toggle(x + 12, y + 232, 145, "Enabled", enabled, () -> enabled = !enabled);
        toggle(x + 165, y + 232, 155, "Name visible", nameVisible, () -> nameVisible = !nameVisible);
        setInitialFocus(definitionId);
    }

    private void initAppearance(int x, int y) {
        NpcVisualMode visual = NpcVisualMode.parse(visualModeValue);
        // dev3.32 intentionally exposes only Minecraft-native render families.
        if (visual == NpcVisualMode.CUSTOM_MODEL) {
            visual = NpcVisualMode.ENTITY;
            visualModeValue = visual.id();
        }
        addRenderableWidget(Button.builder(Component.literal("Visual: " + visual.label()), b -> cycleVisualMode())
                .bounds(x + 12, y + 88, 238, 18).build());
        toggle(x + 258, y + 88, 145, "Glow", glowing, () -> glowing = !glowing);

        if (visual == NpcVisualMode.ENTITY) {
            addRenderableWidget(Button.builder(Component.literal("Entity: " + trim(entityTypeValue, 32)), b -> {
                savePage(); if (minecraft != null) minecraft.setScreen(new NpcModelPickerScreen(this, models, entityTypeValue));
            }).bounds(x + 12, y + 132, 486, 18).build());
            NpcTextureSource source = NpcTextureSource.parse(textureSourceValue);
            addRenderableWidget(Button.builder(Component.literal("Texture source: " + source.label()), b -> cycleTextureSource())
                    .bounds(x + 12, y + 176, 238, 18).build());
            if (source.custom()) {
                int valueWidth = source == NpcTextureSource.LOCAL ? 366 : 486;
                textureValueField = field(x + 12, y + 222, valueWidth, 1_024, textureValueValue);
                if (source == NpcTextureSource.LOCAL) {
                    Button browse = addRenderableWidget(Button.builder(Component.literal("Browse local…"), b -> openLocalSkinPicker())
                            .bounds(x + 386, y + 222, 112, 18).build());
                    browse.active = !initial.availableLocalSkins().isEmpty();
                }
            }
        } else {
            NpcTextureSource source = NpcTextureSource.parse(textureSourceValue);
            addRenderableWidget(Button.builder(Component.literal("Skin source: " + source.label()), b -> cycleTextureSource())
                    .bounds(x + 12, y + 132, 238, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Player model: " + ("slim".equals(textureModelValue) ? "Slim / Alex" : "Wide / Steve")), b -> {
                savePage(); textureModelValue = "slim".equals(textureModelValue) ? "wide" : "slim"; rebuildWidgets();
            }).bounds(x + 258, y + 132, 240, 18).build());
            if (source.custom()) {
                int valueWidth = source == NpcTextureSource.LOCAL ? 366 : 486;
                textureValueField = field(x + 12, y + 178, valueWidth, 1_024, textureValueValue);
                if (source == NpcTextureSource.LOCAL) {
                    Button browse = addRenderableWidget(Button.builder(Component.literal("Browse local…"), b -> openLocalSkinPicker())
                            .bounds(x + 386, y + 178, 112, 18).build());
                    browse.active = !initial.availableLocalSkins().isEmpty();
                }
            }
        }
    }

    private void initInteraction(int x, int y) {
        NpcInteractionMode mode = NpcInteractionMode.parse(interactionMode);
        addRenderableWidget(Button.builder(Component.literal("Mode: " + mode.label()), b -> cycleInteractionMode())
                .bounds(x + 12, y + 88, 220, 18).build());
        dialogueId = field(x + 240, y + 88, 130, 64, dialogueIdValue);
        Button dialogue = addRenderableWidget(Button.builder(Component.literal("Advanced dialogue"), b -> {
            savePage(); PacketDistributor.sendToServer(new NpcDialogueEditorRequestPayload(initial.originalInstanceId()));
        }).bounds(x + 378, y + 88, 120, 18).build());
        dialogue.active = initial.editing();
        Button quests = addRenderableWidget(Button.builder(Component.literal("Manage quests…"), b -> {
            savePage(); PacketDistributor.sendToServer(new NpcQuestWorkflowRequestPayload(initial.originalInstanceId()));
        }).bounds(x + 12, y + 120, 210, 18).build());
        quests.active = initial.editing();

        addRenderableWidget(Button.builder(Component.literal(shopIdValue.isBlank() ? "Create NPC shop" : "Edit NPC shop"),
                b -> openOrCreateNpcShop()).bounds(x + 12, y + 154, 210, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Shared shop…"), b -> {
            savePage(); if (minecraft != null) minecraft.setScreen(new NpcChoicePickerScreen(this,
                    NpcChoicePickerScreen.Kind.SHOP, initial.availableShops(), shopIdValue));
        }).bounds(x + 230, y + 154, 130, 18).build());
        Button unlinkShop = addRenderableWidget(Button.builder(Component.literal("Unlink shop"), b -> {
            savePage(); shopIdValue = ""; notice = "NPC shop unlinked."; noticeError = false; rebuildWidgets();
        }).bounds(x + 368, y + 154, 130, 18).build());
        unlinkShop.active = !shopIdValue.isBlank();

        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> functionMove(-1))
                .bounds(x + 12, y + 194, 28, 18).build()); previous.active = functionIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> functionMove(1))
                .bounds(x + 44, y + 194, 28, 18).build()); next.active = functionIndex + 1 < functions.size();
        addRenderableWidget(Button.builder(Component.literal("Add action"), b -> addFunction()).bounds(x + 80, y + 194, 76, 18).build());
        Button remove = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteFunction())
                .bounds(x + 164, y + 194, 60, 18).build()); remove.active = !functions.isEmpty();
        if (functions.isEmpty()) return;

        NpcFunction current = functions.get(functionIndex);
        functionId = field(x + 12, y + 238, 145, 64, functionIdValue);
        functionLabel = field(x + 165, y + 238, 155, 64, functionLabelValue);
        addRenderableWidget(Button.builder(Component.literal("Service: " + trim(current.service.isBlank() ? "none" : current.service, 22)),
                b -> cycleFunctionService()).bounds(x + 328, y + 238, 170, 18).build());
        functionTarget = field(x + 12, y + 286, 308, 256, functionTargetValue);
        toggle(x + 328, y + 286, 170, "Action enabled", current.enabled, () -> current.enabled = !current.enabled);
    }

    private void initBehavior(int x, int y) {
        NpcBehaviorMode behavior = NpcBehaviorMode.parse(behaviorModeValue);
        addRenderableWidget(Button.builder(Component.literal("Mode: " + behavior.label()), b -> cycleBehaviorMode())
                .bounds(x + 12, y + 88, 145, 18).build());
        toggle(x + 165, y + 88, 155, "Invulnerable", invulnerable, () -> invulnerable = !invulnerable);
        toggle(x + 328, y + 88, 170, "Silent", silent, () -> silent = !silent);
        toggle(x + 12, y + 120, 145, "Normal gravity", gravity, () -> gravity = !gravity);
        toggle(x + 165, y + 120, 155, "Can swim", canSwim, () -> canSwim = !canSwim);
        toggle(x + 328, y + 120, 170, "Can fly", canFly, () -> canFly = !canFly);

        if (behavior == NpcBehaviorMode.LOOK_AT_PLAYERS) {
            lookAtRange = field(x + 12, y + 178, 145, 16, lookAtRangeValue);
            toggle(x + 165, y + 178, 155, "Rotate body", lookAtBody, () -> lookAtBody = !lookAtBody);
        } else if (behavior == NpcBehaviorMode.WANDER) {
            wanderRadius = field(x + 12, y + 178, 145, 16, wanderRadiusValue);
            wanderInterval = field(x + 165, y + 178, 155, 8, wanderIntervalValue);
        } else if (behavior == NpcBehaviorMode.PATROL) {
            addRenderableWidget(Button.builder(Component.literal("Configure route →"), b -> switchPage(Page.MOVEMENT))
                    .bounds(x + 12, y + 178, 145, 18).build());
        }
        homeRadius = field(x + 12, y + 232, 145, 16, homeRadiusValue);
    }

    private void initMovement(int x, int y) {
        NpcPatrolMode patrolMode = NpcPatrolMode.parse(patrolModeValue);
        addRenderableWidget(Button.builder(Component.literal("Patrol: " + patrolMode.label()), b -> cyclePatrolMode())
                .bounds(x + 12, y + 88, 145, 18).build());
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> patrolMove(-1))
                .bounds(x + 165, y + 88, 28, 18).build()); previous.active = patrolIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> patrolMove(1))
                .bounds(x + 197, y + 88, 28, 18).build()); next.active = patrolIndex + 1 < patrol.size();
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addPatrol()).bounds(x + 233, y + 88, 54, 18).build());
        Button remove = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deletePatrol())
                .bounds(x + 293, y + 88, 60, 18).build()); remove.active = !patrol.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Use current"), b -> useCurrentPatrol())
                .bounds(x + 361, y + 88, 137, 18).build());

        patrolX = field(x + 12, y + 148, 95, 20, patrolXValue);
        patrolY = field(x + 115, y + 148, 95, 20, patrolYValue);
        patrolZ = field(x + 218, y + 148, 95, 20, patrolZValue);
        patrolYaw = field(x + 321, y + 148, 95, 16, patrolYawValue);
        patrolPause = field(x + 424, y + 148, 74, 4, patrolPauseValue);

        if (initial.editing()) {
            addRenderableWidget(Button.builder(Component.literal("Set NPC home to my location"), b -> useCurrentAsHome())
                    .bounds(x + 12, y + 204, 210, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Edit route in world"), b -> beginWorldPatrolEdit())
                    .bounds(x + 230, y + 204, 180, 18).build());
        }
    }

    private void initRelations(int x, int y) {
        factionId = field(x + 12, y + 88, 145, 64, factionIdValue);
        factionDisplayName = field(x + 165, y + 88, 155, 64, factionDisplayNameValue);
        addRenderableWidget(Button.builder(Component.literal("Players: " + playerAttitude), b -> {
            savePage(); playerAttitude = NpcAttitude.parse(playerAttitude).next().id(); rebuildWidgets();
        }).bounds(x + 328, y + 88, 170, 18).build());
        minimumReputation = field(x + 12, y + 132, 145, 12, minimumReputationValue);
        reputationLoss = field(x + 165, y + 132, 155, 12, reputationLossValue);
        reputationDeniedText = field(x + 12, y + 176, 486, 256, reputationDeniedTextValue);
        addRenderableWidget(Button.builder(Component.literal("Faction: " + trim(choiceLabel(initial.availableFactions(), relationFactionValue, "None"), 23)), b -> {
            savePage(); if (minecraft != null) minecraft.setScreen(new NpcChoicePickerScreen(this,
                    NpcChoicePickerScreen.Kind.RELATION_FACTION, initial.availableFactions(), relationFactionValue));
        }).bounds(x + 12, y + 232, 180, 18).build());
        String relationAttitude = relations.isEmpty() ? NpcAttitude.NEUTRAL.id() : relations.get(relationIndex).attitude;
        addRenderableWidget(Button.builder(Component.literal("Attitude: " + relationAttitude), b -> cycleRelation())
                .bounds(x + 200, y + 232, 140, 18).build());
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> relationMove(-1))
                .bounds(x + 348, y + 232, 28, 18).build()); previous.active = relationIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> relationMove(1))
                .bounds(x + 380, y + 232, 28, 18).build()); next.active = relationIndex + 1 < relations.size();
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addRelation()).bounds(x + 412, y + 232, 40, 18).build());
        Button remove = addRenderableWidget(Button.builder(Component.literal("Del"), b -> deleteRelation())
                .bounds(x + 456, y + 232, 42, 18).build()); remove.active = !relations.isEmpty();
    }

    private void initCombat(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("Profile: " + NpcCombatProfile.parse(combatProfileValue).label()), b -> {
            savePage(); combatProfileValue = NpcCombatProfile.parse(combatProfileValue).next().id(); rebuildWidgets();
        }).bounds(x + 12, y + 88, 238, 18).build());
        toggle(x + 258, y + 88, 74, "Melee", meleeAttacksEnabled, () -> meleeAttacksEnabled = !meleeAttacksEnabled);
        toggle(x + 336, y + 88, 78, "Ranged", rangedAttacksEnabled, () -> rangedAttacksEnabled = !rangedAttacksEnabled);
        toggle(x + 418, y + 88, 80, "Magic", magicAttacksEnabled, () -> magicAttacksEnabled = !magicAttacksEnabled);
        addRenderableWidget(Button.builder(Component.literal("When attacked: " + NpcSelfDefenseReaction.parse(whenAttackedValue).label()), b -> {
            savePage(); whenAttackedValue = NpcSelfDefenseReaction.parse(whenAttackedValue).next().id(); rebuildWidgets();
        }).bounds(x + 12, y + 132, 238, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Hostile seen: " + NpcHostileSightReaction.parse(whenHostileSeenValue).label()), b -> {
            savePage(); whenHostileSeenValue = NpcHostileSightReaction.parse(whenHostileSeenValue).next().id(); rebuildWidgets();
        }).bounds(x + 258, y + 132, 240, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Friendly attacked: " + NpcFriendlyDefenseReaction.parse(whenFriendlyAttackedValue).label()), b -> {
            savePage(); whenFriendlyAttackedValue = NpcFriendlyDefenseReaction.parse(whenFriendlyAttackedValue).next().id(); rebuildWidgets();
        }).bounds(x + 12, y + 176, 238, 18).build());
        assistRange = field(x + 258, y + 176, 115, 12, assistRangeValue);
        fleeDistance = field(x + 383, y + 176, 115, 12, fleeDistanceValue);
        attackCooldown = field(x + 12, y + 230, 145, 4, attackCooldownValue);
    }

    private void initTactics(int x, int y) {
        toggle(x + 12, y + 88, 145, "Threat targeting", threatEnabled, () -> threatEnabled = !threatEnabled);
        toggle(x + 165, y + 88, 155, "Attack pattern", attackPatternEnabled, () -> attackPatternEnabled = !attackPatternEnabled);
        threatRange = field(x + 12, y + 132, 82, 12, threatRangeValue);
        threatDamage = field(x + 102, y + 132, 82, 12, threatDamageValue);
        threatHealing = field(x + 192, y + 132, 82, 12, threatHealingValue);
        threatDecay = field(x + 282, y + 132, 94, 12, threatDecayValue);
        threatSwitch = field(x + 384, y + 132, 114, 12, threatSwitchValue);
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> patternMove(-1)).bounds(x+12,y+176,28,18).build()); previous.active=patternIndex>0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> patternMove(1)).bounds(x+44,y+176,28,18).build()); next.active=patternIndex+1<attackPattern.size();
        Button add = addRenderableWidget(Button.builder(Component.literal("Add step"), b -> addPatternStep()).bounds(x+80,y+176,82,18).build()); add.active=attackPattern.size()<NpcAttackPatternStep.MAX_STEPS;
        Button remove = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deletePatternStep()).bounds(x+170,y+176,64,18).build()); remove.active=!attackPattern.isEmpty();
        if (attackPattern.isEmpty()) return;
        NpcAttackPatternStep step=attackPattern.get(patternIndex);
        toggle(x+242,y+176,105,"Enabled",step.enabled,()->step.enabled=!step.enabled);
        Button moveUp=addRenderableWidget(Button.builder(Component.literal("Move ↑"),b->movePatternStep(-1)).bounds(x+355,y+176,66,18).build()); moveUp.active=patternIndex>0;
        Button moveDown=addRenderableWidget(Button.builder(Component.literal("Move ↓"),b->movePatternStep(1)).bounds(x+429,y+176,69,18).build()); moveDown.active=patternIndex+1<attackPattern.size();
        addRenderableWidget(Button.builder(Component.literal("Action: "+step.actionType().label()), b -> cyclePatternAction()).bounds(x+12,y+220,145,18).build());
        Button ability = addRenderableWidget(Button.builder(Component.literal("Ability: "+patternAbilityLabel(step.abilityId)), b -> cyclePatternAbility()).bounds(x+165,y+220,155,18).build());
        ability.active=step.actionType()==NpcAttackPatternAction.ABILITY;
        addRenderableWidget(Button.builder(Component.literal("Phase: "+abilityPhaseLabel(step.phaseId)), b -> cyclePatternPhase()).bounds(x+328,y+220,170,18).build());
        patternMinRange=field(x+12,y+264,112,12,patternMinRangeValue); patternMaxRange=field(x+132,y+264,112,12,patternMaxRangeValue);
        patternMinHealth=field(x+252,y+264,112,12,patternMinHealthValue); patternMaxHealth=field(x+372,y+264,126,12,patternMaxHealthValue);
    }

    private void initAbilities(int x, int y) {
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> abilityMove(-1)).bounds(x + 12, y + 98, 28, 20).build());
        previous.active = abilityIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> abilityMove(1)).bounds(x + 44, y + 98, 28, 20).build());
        next.active = abilityIndex + 1 < abilities.size();
        addRenderableWidget(Button.builder(Component.literal("Open Ability Library →"), b -> openAbilityLibrary())
                .bounds(x + 82, y + 98, 190, 20).build());
        Button unassign = addRenderableWidget(Button.builder(Component.literal("Unassign"), b -> unassignAbility())
                .bounds(x + 280, y + 98, 96, 20).build());
        unassign.active = !abilities.isEmpty();
        Button clear = addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> clearAssignedAbilities())
                .bounds(x + 384, y + 98, 114, 20).build());
        clear.active = !abilities.isEmpty();
        if (!abilities.isEmpty()) {
            NpcAbilityDefinition ability = abilities.get(Math.max(0, Math.min(abilityIndex, abilities.size() - 1)));
            addRenderableWidget(Button.builder(Component.literal("Phase: " + abilityPhaseLabel(ability.phaseId)), b -> cycleAbilityPhase())
                    .bounds(x + 12, y + 154, 230, 20).build());
        }
    }

    private void openAbilityLibrary() {
        savePage();
        PacketDistributor.sendToServer(new NpcAbilityLibraryRequestPayload("", 0, nextRequestId++));
    }

    /** Called by the standalone Ability Library when an admin assigns the selected shared ability. */
    public void assignSharedAbility(String id, String displayName) {
        String safeId = id == null ? "" : id.trim();
        if (safeId.isBlank()) return;
        for (int i = 0; i < abilities.size(); i++) {
            if (safeId.equals(abilities.get(i).id)) {
                abilityIndex = i;
                notice = "Ability is already assigned: " + abilities.get(i).displayName;
                noticeError = false;
                rebuildWidgets();
                return;
            }
        }
        if (abilities.size() >= NpcAbilityDefinition.MAX_ABILITIES) {
            notice = "This NPC already has the maximum number of assigned abilities.";
            noticeError = true;
            return;
        }
        NpcAbilityDefinition view = new NpcAbilityDefinition();
        view.id = safeId;
        view.displayName = displayName == null || displayName.isBlank() ? safeId : displayName;
        view.phaseId = "";
        view.normalize();
        abilities.add(view);
        abilityIndex = abilities.size() - 1;
        notice = "Assigned shared ability: " + view.displayName;
        noticeError = false;
        rebuildWidgets();
    }

    private void unassignAbility() {
        savePage();
        if (abilities.isEmpty()) return;
        String removed = abilities.remove(Math.max(0, Math.min(abilityIndex, abilities.size() - 1))).id;
        clearAbilityReferences(removed);
        abilityIndex = Math.max(0, Math.min(abilityIndex, Math.max(0, abilities.size() - 1)));
        notice = "Ability unassigned from this NPC. The shared library entry was kept.";
        noticeError = false;
        rebuildWidgets();
    }

    private void clearAssignedAbilities() {
        savePage();
        java.util.HashSet<String> removed = new java.util.HashSet<>();
        for (NpcAbilityDefinition ability : abilities) removed.add(ability.id);
        abilities.clear();
        abilityIndex = 0;
        for (String id : removed) clearAbilityReferences(id);
        rebuildWidgets();
    }

    private void clearAbilityReferences(String removed) {
        for (NpcAttackPatternStep step : attackPattern) if (removed.equals(step.abilityId)) step.abilityId = "";
        for (NpcBossPhase phase : bossPhases) for (var action : phase.actions)
            if (action.actionType() == NpcBossPhaseActionType.TRIGGER_ABILITY && removed.equals(action.value)) action.value = "";
    }

    private void initBoss(int x, int y) {
        toggle(x + 12, y + 88, 145, "Boss encounter", bossEnabled, () -> {
            bossEnabled = !bossEnabled;
            if (bossEnabled && bossPhases.isEmpty()) { bossPhases.add(NpcBossPhase.phaseOne()); bossPhaseIndex = 0; loadBossPhase(); }
        });
        toggle(x + 165, y + 88, 155, "Boss bar", bossBarVisible, () -> bossBarVisible = !bossBarVisible);
        toggle(x + 328, y + 88, 170, "Heal on reset", bossHealOnReset, () -> bossHealOnReset = !bossHealOnReset);
        bossBarRange = field(x + 12, y + 132, 145, 12, bossBarRangeValue);
        bossResetDistance = field(x + 165, y + 132, 155, 12, bossResetDistanceValue);
        bossResetSeconds = field(x + 328, y + 132, 170, 8, bossResetSecondsValue);

        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> bossPhaseMove(-1))
                .bounds(x + 12, y + 176, 28, 18).build()); previous.active = bossPhaseIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> bossPhaseMove(1))
                .bounds(x + 44, y + 176, 28, 18).build()); next.active = bossPhaseIndex + 1 < bossPhases.size();
        Button add = addRenderableWidget(Button.builder(Component.literal("Add phase"), b -> addBossPhase())
                .bounds(x + 80, y + 176, 82, 18).build()); add.active = bossPhases.size() < NpcBossPhase.MAX_PHASES;
        Button remove = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteBossPhase())
                .bounds(x + 170, y + 176, 64, 18).build()); remove.active = bossPhases.size() > 1 || (!bossEnabled && !bossPhases.isEmpty());
        if (bossPhases.isEmpty()) return;
        bossPhaseId = field(x + 12, y + 220, 145, 48, bossPhaseIdValue);
        bossPhaseName = field(x + 165, y + 220, 155, 64, bossPhaseNameValue);
        bossPhaseThreshold = field(x + 328, y + 220, 170, 8, bossPhaseThresholdValue);
        bossPhaseSpeed = field(x + 12, y + 264, 112, 8, bossPhaseSpeedValue);
        bossPhaseCooldown = field(x + 132, y + 264, 112, 8, bossPhaseCooldownValue);
        bossPhaseDamage = field(x + 252, y + 264, 112, 8, bossPhaseDamageValue);
        addRenderableWidget(Button.builder(Component.literal("Phase actions: " + bossPhases.get(bossPhaseIndex).actions.size()), b -> openBossPhaseActions())
                .bounds(x + 372, y + 264, 126, 18).build());
    }

    private void initStats(int x, int y) {
        maxHealth = field(x + 12, y + 88, 145, 16, maxHealthValue);
        magicResistance = field(x + 165, y + 88, 155, 12, magicResistanceValue);
        armorMultiplier = field(x + 328, y + 88, 170, 12, armorMultiplierValue);

        meleeDamageMultiplier = field(x + 12, y + 142, 145, 12, meleeDamageMultiplierValue);
        rangedDamageMultiplier = field(x + 165, y + 142, 155, 12, rangedDamageMultiplierValue);
        magicDamageMultiplier = field(x + 328, y + 142, 170, 12, magicDamageMultiplierValue);

        walkingSpeed = field(x + 12, y + 196, 145, 12, walkingSpeedValue);
        runningSpeed = field(x + 165, y + 196, 155, 12, runningSpeedValue);
        followRange = field(x + 328, y + 196, 170, 16, followRangeValue);

        knockback = field(x + 12, y + 250, 145, 16, knockbackValue);
        scale = field(x + 165, y + 250, 155, 16, scaleValue);
    }

    private void initLoadout(int x, int y) {
        Button equipment = addRenderableWidget(Button.builder(Component.literal("Edit combat equipment"),
                b -> openLoadout(NpcLoadoutMenu.MODE_EQUIPMENT)).bounds(x + 40, y + 116, 190, 22).build());
        Button loot = addRenderableWidget(Button.builder(Component.literal("Edit 9-slot loot table"),
                b -> openLoadout(NpcLoadoutMenu.MODE_LOOT)).bounds(x + 280, y + 116, 190, 22).build());
        equipment.active = loot.active = initial.editing();
    }

    private void initSchedule(int x, int y) {
        toggle(x + 12, y + 88, 145, "Schedule", scheduleEnabled, () -> scheduleEnabled = !scheduleEnabled);
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> scheduleMove(-1))
                .bounds(x + 165, y + 88, 28, 18).build()); previous.active = scheduleIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> scheduleMove(1))
                .bounds(x + 197, y + 88, 28, 18).build()); next.active = scheduleIndex + 1 < schedule.size();
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addSchedule()).bounds(x + 233, y + 88, 54, 18).build());
        Button remove = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSchedule())
                .bounds(x + 293, y + 88, 60, 18).build()); remove.active = !schedule.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Use my location"), b -> useCurrentSchedule())
                .bounds(x + 361, y + 88, 101, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Now"), b -> useCurrentScheduleTime())
                .bounds(x + 466, y + 88, 32, 18).build());
        scheduleTime = field(x + 12, y + 136, 82, 5, scheduleTimeValue);
        scheduleX = field(x + 102, y + 136, 95, 20, scheduleXValue); scheduleY = field(x + 205, y + 136, 95, 20, scheduleYValue);
        scheduleZ = field(x + 308, y + 136, 95, 20, scheduleZValue); scheduleYaw = field(x + 411, y + 136, 87, 16, scheduleYawValue);
        scheduleSpeed = field(x + 12, y + 190, 82, 12, scheduleSpeedValue);
        addRenderableWidget(Button.builder(Component.literal("Move: " + scheduleMovement), b -> cycleScheduleMovement())
                .bounds(x + 102, y + 190, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("On arrival: " + NpcScheduleActivity.parse(scheduleActivity).label()), b -> cycleScheduleActivity())
                .bounds(x + 260, y + 190, 238, 18).build());
        if (initial.editing()) {
            addRenderableWidget(Button.builder(Component.literal("Edit destinations in world"), b -> beginWorldScheduleEdit())
                    .bounds(x + 12, y + 218, 190, 18).build());
        }
    }

    private void initRespawn(int x, int y) {
        toggle(x + 12, y + 88, 145, "Respawn", respawnEnabled, () -> respawnEnabled = !respawnEnabled);
        respawnDelay = field(x + 165, y + 88, 155, 8, respawnDelayValue);
        respawnDimension = field(x + 328, y + 88, 170, 256, respawnDimensionValue);
        respawnX = field(x + 12, y + 148, 95, 20, respawnXValue); respawnY = field(x + 115, y + 148, 95, 20, respawnYValue);
        respawnZ = field(x + 218, y + 148, 95, 20, respawnZValue); respawnYaw = field(x + 321, y + 148, 95, 16, respawnYawValue);
        addRenderableWidget(Button.builder(Component.literal("Use NPC placement"), b -> usePlacementRespawn())
                .bounds(x + 12, y + 198, 180, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Use my location"), b -> useCurrentRespawn())
                .bounds(x + 200, y + 198, 180, 18).build());
    }

    private void switchPage(Page next) { savePage(); page = next; rebuildWidgets(); }
    private void toggle(int x, int y, int w, String label, boolean value, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (value ? "ON" : "OFF")), b -> {
            savePage(); action.run(); rebuildWidgets();
        }).bounds(x, y, w, 18).build());
    }
    private EditBox field(int x, int y, int w, int max, String value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty()); box.setMaxLength(max); box.setValue(value == null ? "" : value);
        return addRenderableWidget(box);
    }

    private void savePage() {
        if (definitionId != null) definitionIdValue = definitionId.getValue(); if (displayName != null) displayNameValue = displayName.getValue(); if (roleField != null) roleId = roleField.getValue();
        if (textureValueField != null) textureValueValue = textureValueField.getValue();
        if (customModelResource != null) customModelResourceValue = customModelResource.getValue();
        if (customTextureResource != null) customTextureResourceValue = customTextureResource.getValue();
        if (customAnimationResource != null) customAnimationResourceValue = customAnimationResource.getValue();
        if (idleAnimation != null) idleAnimationValue = idleAnimation.getValue();
        if (walkAnimation != null) walkAnimationValue = walkAnimation.getValue();
        if (attackAnimation != null) attackAnimationValue = attackAnimation.getValue();
        if (castAnimation != null) castAnimationValue = castAnimation.getValue();
        if (hurtAnimation != null) hurtAnimationValue = hurtAnimation.getValue();
        if (deathAnimation != null) deathAnimationValue = deathAnimation.getValue();
        if (dialogueId != null) dialogueIdValue = dialogueId.getValue();
        saveFunction();
        if (xField != null) xValue = xField.getValue(); if (yField != null) yValue = yField.getValue(); if (zField != null) zValue = zField.getValue(); if (yawField != null) yawValue = yawField.getValue();
        if (factionId != null) factionIdValue = factionId.getValue(); if (factionDisplayName != null) factionDisplayNameValue = factionDisplayName.getValue(); if (minimumReputation != null) minimumReputationValue = minimumReputation.getValue();
        if (reputationDeniedText != null) reputationDeniedTextValue = reputationDeniedText.getValue(); if (reputationLoss != null) reputationLossValue = reputationLoss.getValue();
        saveRelation();
        if (assistRange != null) assistRangeValue = assistRange.getValue(); if (fleeDistance != null) fleeDistanceValue = fleeDistance.getValue(); if (attackCooldown != null) attackCooldownValue = attackCooldown.getValue();
        if (threatRange != null) threatRangeValue=threatRange.getValue(); if(threatDamage!=null) threatDamageValue=threatDamage.getValue(); if(threatHealing!=null) threatHealingValue=threatHealing.getValue(); if(threatDecay!=null) threatDecayValue=threatDecay.getValue(); if(threatSwitch!=null) threatSwitchValue=threatSwitch.getValue();
        savePatternStep();
        if (bossBarRange != null) bossBarRangeValue = bossBarRange.getValue(); if (bossResetDistance != null) bossResetDistanceValue = bossResetDistance.getValue(); if (bossResetSeconds != null) bossResetSecondsValue = bossResetSeconds.getValue();
        saveBossPhase();
        if (maxHealth != null) maxHealthValue = maxHealth.getValue(); if (magicResistance != null) magicResistanceValue = magicResistance.getValue();
        if (armorMultiplier != null) armorMultiplierValue = armorMultiplier.getValue();
        if (meleeDamageMultiplier != null) meleeDamageMultiplierValue = meleeDamageMultiplier.getValue();
        if (rangedDamageMultiplier != null) rangedDamageMultiplierValue = rangedDamageMultiplier.getValue();
        if (magicDamageMultiplier != null) magicDamageMultiplierValue = magicDamageMultiplier.getValue();
        if (walkingSpeed != null) walkingSpeedValue = walkingSpeed.getValue(); if (runningSpeed != null) runningSpeedValue = runningSpeed.getValue();
        if (followRange != null) followRangeValue = followRange.getValue();
        if (knockback != null) knockbackValue = knockback.getValue(); if (scale != null) scaleValue = scale.getValue(); if (homeRadius != null) homeRadiusValue = homeRadius.getValue();
        if (lookAtRange != null) lookAtRangeValue = lookAtRange.getValue();
        if (wanderRadius != null) wanderRadiusValue = wanderRadius.getValue();
        if (wanderInterval != null) wanderIntervalValue = wanderInterval.getValue();
        savePatrol();
        saveSchedule();
        if (respawnDelay != null) respawnDelayValue = respawnDelay.getValue(); if (respawnDimension != null) respawnDimensionValue = respawnDimension.getValue();
        if (respawnX != null) respawnXValue = respawnX.getValue(); if (respawnY != null) respawnYValue = respawnY.getValue();
        if (respawnZ != null) respawnZValue = respawnZ.getValue(); if (respawnYaw != null) respawnYawValue = respawnYaw.getValue();
    }

    private boolean submit(boolean deleteRequested) {
        try {
            savePage();
            List<NpcFactionRelation> savedRelations = new ArrayList<>();
            for (NpcFactionRelation relation : relations) if (relation.copy().normalize().configured()) savedRelations.add(relation.copy().normalize());
            List<NpcEditorLootSlot> loot = initial.loot();
            PacketDistributor.sendToServer(new NpcEditorSubmitPayload(initial.originalInstanceId(), initial.originalDefinitionId(), deleteRequested,
                    definitionIdValue.trim(), displayNameValue.trim(), entityTypeValue, visualModeValue, textureSourceValue, textureValueValue.trim(), textureModelValue,
                    customModelResourceValue.trim(), customTextureResourceValue.trim(), customAnimationResourceValue.trim(),
                    idleAnimationValue.trim(), walkAnimationValue.trim(), attackAnimationValue.trim(), castAnimationValue.trim(), hurtAnimationValue.trim(), deathAnimationValue.trim(),
                    interactionTextValue, dialogueIdValue.trim(),
                    roleId, roleColor, shopIdValue.trim(), interactionMode, copiedFunctions(),
                    parse(xValue, -30_000_000, 30_000_000, "X"), parse(yValue, -4096, 4096, "Y"), parse(zValue, -30_000_000, 30_000_000, "Z"),
                    (float) parse(yawValue, -360, 360, "yaw"), initial.pitch(), enabled, nameVisible,
                    behaviorNoAi(), invulnerable, silent, glowing,
                    gravity, canSwim, canFly, behaviorModeValue,
                    parse(lookAtRangeValue, 0, 64, "look-at range"), lookAtBody,
                    parse(wanderRadiusValue, 0, 128, "wander radius"),
                    parseInt(wanderIntervalValue, 1, 300, "wander interval"),
                    parse(walkingSpeedValue, 0.05, 4, "walking speed"),
                    factionIdValue.trim(), factionDisplayNameValue.trim(), parseInt(minimumReputationValue, -1_000_000, 1_000_000, "minimum reputation"),
                    reputationDeniedTextValue, parseInt(reputationLossValue, 0, 1_000_000, "reputation loss"), playerAttitude, savedRelations,
                    whenAttackedValue, whenFriendlyAttackedValue, whenHostileSeenValue, combatProfileValue, parse(assistRangeValue, 0, 64, "assist range"), parse(fleeDistanceValue, 2, 64, "flee distance"), parseInt(attackCooldownValue, 4, 200, "attack cooldown"),
                    meleeAttacksEnabled, rangedAttacksEnabled, magicAttacksEnabled,
                    threatEnabled, parse(threatRangeValue,4,128,"threat range"), parse(threatDamageValue,0,100,"damage threat multiplier"), parse(threatHealingValue,0,100,"healing threat multiplier"), parse(threatDecayValue,0,10000,"threat decay"), parse(threatSwitchValue,1,10,"threat switch ratio"), attackPatternEnabled, copiedAttackPattern(),
                    copiedAbilities(), bossEnabled, bossBarVisible, parse(bossBarRangeValue, 8, 256, "boss bar range"),
                    parse(bossResetDistanceValue, 4, 512, "boss reset distance"), parseInt(bossResetSecondsValue, 1, 3600, "boss reset seconds"), bossHealOnReset, copiedBossPhases(),
                    parseOptional(maxHealthValue, 1, 2048, "max health"),
                    parse(magicResistanceValue, 0, 0.95, "magic resistance"), parse(armorMultiplierValue, 0, 10, "armor multiplier"),
                    parse(meleeDamageMultiplierValue, 0, 20, "melee damage multiplier"), parse(rangedDamageMultiplierValue, 0, 20, "ranged damage multiplier"),
                    parse(magicDamageMultiplierValue, 0, 20, "magic damage multiplier"), parse(walkingSpeedValue, 0.05, 4, "walking speed"),
                    parse(runningSpeedValue, 0.05, 6, "running speed"), parseOptional(followRangeValue, 1, 2048, "follow range"),
                    parseOptional(knockbackValue, 0, 1, "knockback resistance"), parseOptional(scaleValue, 0.0625, 16, "scale"),
                    parse(homeRadiusValue, 0, 2048, "home radius"), copy(initial.mainHandItem()), copy(initial.offHandItem()), copy(initial.headItem()),
                    copy(initial.chestItem()), copy(initial.legsItem()), copy(initial.feetItem()), initial.lootRolls(), loot,
                    scheduleEnabled, copiedSchedule(), patrolModeValue, copiedPatrol(),
                    respawnEnabled, parseInt(respawnDelayValue, 0, 86_400, "respawn delay"),
                    respawnDimensionValue.trim(), parse(respawnXValue, -30_000_000, 30_000_000, "respawn X"),
                    parse(respawnYValue, -4096, 4096, "respawn Y"), parse(respawnZValue, -30_000_000, 30_000_000, "respawn Z"),
                    (float) parse(respawnYawValue, -360, 360, "respawn yaw"), initial.respawnPitch(), nextRequestId++));
            pendingDelete = deleteRequested;
            notice = deleteRequested ? "Deleting…" : "Saving…"; noticeError = false;
            return true;
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage(); noticeError = true;
            return false;
        }
    }

    private void delete() { if (!deleteArmed) { deleteArmed = true; rebuildWidgets(); } else submit(true); }
    private void openLoadout(int mode) {
        if (!initial.editing()) { notice = "Create the NPC first, then reopen it to edit inventory slots."; noticeError = true; return; }
        savePage(); PacketDistributor.sendToServer(new NpcLoadoutOpenRequestPayload(initial.originalInstanceId(), mode));
    }

    public void acceptResult(NpcEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        if (!payload.successful()) {
            pendingDelete = false;
            pendingPatrolWorldEdit = false;
            pendingScheduleWorldEdit = false;
            notice = payload.message(); noticeError = true;
            return;
        }
        if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(payload.message()));
        if (pendingPatrolWorldEdit) {
            pendingPatrolWorldEdit = false;
            PacketDistributor.sendToServer(new NpcAdminActionPayload("patrol_route", initial.originalInstanceId(), nextRequestId++));
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }
        if (pendingScheduleWorldEdit) {
            pendingScheduleWorldEdit = false;
            PacketDistributor.sendToServer(new NpcAdminActionPayload("schedule_route", initial.originalInstanceId(), nextRequestId++));
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }
        if (pendingDelete) {
            pendingDelete = false;
            closeToParent(true);
            return;
        }
        deleteArmed = false;
        notice = payload.message(); noticeError = false;
        if (!initial.editing()) closeToParent(true);
    }
    public void acceptDialogueLink(String id, String message) { dialogueIdValue = id == null ? "" : id; notice = message == null ? "Dialogue linked." : message; noticeError = false; }
    public void acceptModel(String id) { entityTypeValue = id == null ? "minecraft:villager" : id; notice = "Selected model: " + entityTypeValue; noticeError = false; }
    public void acceptChoice(NpcChoicePickerScreen.Kind kind, String id) {
        String safe = id == null ? "" : id;
        if (kind == NpcChoicePickerScreen.Kind.SHOP) {
            shopIdValue = safe;
            notice = safe.isBlank() ? "No shop linked." : "Linked shop selected: " + safe;
        } else if (kind == NpcChoicePickerScreen.Kind.LOCAL_TEXTURE) {
            textureValueValue = safe;
            notice = safe.isBlank() ? "Local texture cleared." : "Local texture selected: " + safe;
        } else {
            if (relations.isEmpty()) {
                NpcFactionRelation relation = new NpcFactionRelation(); relations.add(relation); relationIndex = 0;
            }
            relationFactionValue = safe;
            relations.get(relationIndex).factionId = safe;
            notice = safe.isBlank() ? "Faction relation cleared." : "Target faction selected: " + safe;
        }
        noticeError = false;
    }

    private void saveFunction() {
        if (functions.isEmpty() || functionId == null) return;
        NpcFunction function = functions.get(functionIndex);
        function.id = functionId.getValue(); function.label = functionLabel.getValue(); function.target = functionTarget.getValue();
        function.normalize(); loadFunction();
    }
    private void loadFunction() {
        functionIndex = Math.max(0, Math.min(functionIndex, Math.max(0, functions.size() - 1)));
        if (functions.isEmpty()) { functionIdValue = "function"; functionLabelValue = "Service"; functionTargetValue = ""; return; }
        NpcFunction function = functions.get(functionIndex);
        functionIdValue = function.id; functionLabelValue = function.label; functionTargetValue = function.target;
    }
    private void addFunction() {
        try { savePage(); } catch (RuntimeException ignored) {}
        if (functions.size() < NpcFunction.MAX_FUNCTIONS) {
            NpcFunction function = new NpcFunction(); function.id = uniqueFunctionId("function");
            if (!services.isEmpty()) function.service = services.get(0);
            functions.add(function.normalize()); functionIndex = functions.size() - 1; loadFunction();
        }
        rebuildWidgets();
    }
    private void deleteFunction() { savePage(); if (!functions.isEmpty()) functions.remove(functionIndex); loadFunction(); rebuildWidgets(); }
    private void functionMove(int delta) { savePage(); functionIndex = Math.max(0, Math.min(functions.size() - 1, functionIndex + delta)); loadFunction(); rebuildWidgets(); }
    private void cycleFunctionService() {
        savePage(); if (functions.isEmpty()) return; NpcFunction function = functions.get(functionIndex);
        if (services.isEmpty()) function.service = "";
        else { int index = services.indexOf(function.service); function.service = services.get((index + 1 + services.size()) % services.size()); }
        rebuildWidgets();
    }

    private void cycleVisualMode() {
        savePage();
        visualModeValue = NpcVisualMode.parse(visualModeValue).next().id();
        rebuildWidgets();
    }

    private void cycleTextureSource() {
        savePage();
        NpcTextureSource next = NpcTextureSource.parse(textureSourceValue).next();
        textureSourceValue = next.id();
        if (next == NpcTextureSource.NONE) textureValueValue = "";
        rebuildWidgets();
    }


    private void openLocalSkinPicker() {
        savePage();
        if (minecraft == null) return;
        List<NpcEditorOpenPayload.Choice> choices = new ArrayList<>();
        for (String skin : initial.availableLocalSkins()) {
            choices.add(new NpcEditorOpenPayload.Choice(skin, skin));
        }
        minecraft.setScreen(new NpcChoicePickerScreen(this,
                NpcChoicePickerScreen.Kind.LOCAL_TEXTURE, choices, textureValueValue));
    }

    private void cycleBehaviorMode() {
        savePage();
        behaviorModeValue = NpcBehaviorMode.parse(behaviorModeValue).next().id();
        noAi = behaviorNoAi();
        rebuildWidgets();
    }

    private boolean behaviorNoAi() {
        NpcBehaviorMode mode = NpcBehaviorMode.parse(behaviorModeValue);
        return mode == NpcBehaviorMode.STATIONARY || mode == NpcBehaviorMode.LOOK_AT_PLAYERS;
    }

    private void cyclePatrolMode() {
        savePage();
        patrolModeValue = NpcPatrolMode.parse(patrolModeValue).next().id();
        rebuildWidgets();
    }

    private void openOrCreateNpcShop() {
        savePage();
        if (!shopIdValue.isBlank()) {
            openLinkedShop();
            return;
        }
        String proposed = automaticNpcShopId();
        shopIdValue = proposed;
        PacketDistributor.sendToServer(new NpcShopAdminActionPayload(
                "new", "", proposed, "", 0, nextRequestId++));
        notice = "Creating the NPC-managed shop…"; noticeError = false;
    }

    private String automaticNpcShopId() {
        String base = definitionIdValue == null ? "npc" : definitionIdValue.trim().toLowerCase(Locale.ROOT);
        base = base.replaceAll("[^a-z0-9._-]", "_");
        if (base.isBlank()) base = "npc";
        return (base + "_shop").substring(0, Math.min(64, (base + "_shop").length()));
    }

    private void openLinkedShop() {
        savePage();
        if (shopIdValue.isBlank()) {
            notice = "Select a linked shop first."; noticeError = true; return;
        }
        PacketDistributor.sendToServer(new NpcShopAdminActionPayload(
                "open", shopIdValue, "", "", 0, nextRequestId++));
    }

    private void createLinkedShop() {
        savePage();
        String proposed = automaticNpcShopId();
        shopIdValue = proposed;
        PacketDistributor.sendToServer(new NpcShopAdminActionPayload(
                "new", "", proposed, "", 0, nextRequestId++));
        notice = "New shared shop linked: " + proposed; noticeError = false;
    }

    private void cycleInteractionMode() { savePage(); interactionMode = NpcInteractionMode.parse(interactionMode).next().id(); rebuildWidgets(); }
    private String uniqueFunctionId(String base) {
        String candidate = base; int suffix = 2;
        while (containsFunctionId(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }
    private boolean containsFunctionId(String id) {
        for (NpcFunction function : functions) if (function.id.equals(id)) return true;
        return false;
    }
    private List<NpcFunction> copiedFunctions() { List<NpcFunction> result = new ArrayList<>(); for (NpcFunction function : functions) result.add(function.copy().normalize()); return result; }

    private void savePatternStep(){ if(patternMinRange==null||attackPattern.isEmpty())return; NpcAttackPatternStep step=attackPattern.get(patternIndex); step.minRange=parse(patternMinRange.getValue(),0,128,"pattern min range"); step.maxRange=parse(patternMaxRange.getValue(),step.minRange,128,"pattern max range"); step.minHealthPercent=parse(patternMinHealth.getValue(),0,100,"pattern min health"); step.maxHealthPercent=parse(patternMaxHealth.getValue(),step.minHealthPercent,100,"pattern max health"); step.normalize(); loadPatternStep(); }
    private void loadPatternStep(){ patternIndex=Math.max(0,Math.min(patternIndex,Math.max(0,attackPattern.size()-1))); if(attackPattern.isEmpty())return; NpcAttackPatternStep s=attackPattern.get(patternIndex); patternMinRangeValue=number(s.minRange);patternMaxRangeValue=number(s.maxRange);patternMinHealthValue=number(s.minHealthPercent);patternMaxHealthValue=number(s.maxHealthPercent);}
    private void addPatternStep(){try{savePage();}catch(RuntimeException ignored){} if(attackPattern.size()<NpcAttackPatternStep.MAX_STEPS){NpcAttackPatternStep step=new NpcAttackPatternStep().normalize();attackPattern.add(step);patternIndex=attackPattern.size()-1;loadPatternStep();}rebuildWidgets();}
    private void deletePatternStep(){try{savePage();}catch(RuntimeException ignored){}if(!attackPattern.isEmpty())attackPattern.remove(patternIndex);loadPatternStep();rebuildWidgets();}
    private void patternMove(int d){savePage();patternIndex=Math.max(0,Math.min(attackPattern.size()-1,patternIndex+d));loadPatternStep();rebuildWidgets();}
    private void cyclePatternAction(){savePage();if(attackPattern.isEmpty())return;NpcAttackPatternStep step=attackPattern.get(patternIndex);NpcAttackPatternAction next=step.actionType().next();if(next==NpcAttackPatternAction.ABILITY&&abilities.isEmpty()){notice="Add an ability before using an Ability pattern step.";noticeError=true;return;}step.action=next.id();if(next==NpcAttackPatternAction.ABILITY&&step.abilityId.isBlank())step.abilityId=abilities.get(0).id;step.normalize();loadPatternStep();rebuildWidgets();}
    private void movePatternStep(int delta){savePage();int target=patternIndex+delta;if(target<0||target>=attackPattern.size())return;NpcAttackPatternStep step=attackPattern.remove(patternIndex);attackPattern.add(target,step);patternIndex=target;loadPatternStep();rebuildWidgets();}
    private String patternAbilityLabel(String id){if(id==null||id.isBlank())return "None";for(NpcAbilityDefinition a:abilities)if(id.equals(a.id))return a.displayName;return id+" (missing)";}
    private void cyclePatternAbility(){savePage();if(attackPattern.isEmpty())return;NpcAttackPatternStep step=attackPattern.get(patternIndex);if(abilities.isEmpty())step.abilityId="";else{int idx=-1;for(int i=0;i<abilities.size();i++)if(abilities.get(i).id.equals(step.abilityId)){idx=i;break;}step.abilityId=abilities.get((idx+1)%abilities.size()).id;}rebuildWidgets();}
    private void cyclePatternPhase(){savePage();if(attackPattern.isEmpty())return;NpcAttackPatternStep step=attackPattern.get(patternIndex);if(bossPhases.isEmpty())step.phaseId="";else if(step.phaseId.isBlank())step.phaseId=bossPhases.get(0).id;else{int idx=-1;for(int i=0;i<bossPhases.size();i++)if(bossPhases.get(i).id.equals(step.phaseId)){idx=i;break;}step.phaseId=idx<0||idx+1>=bossPhases.size()?"":bossPhases.get(idx+1).id;}rebuildWidgets();}
    private List<NpcAttackPatternStep> copiedAttackPattern(){List<NpcAttackPatternStep> out=new ArrayList<>();for(NpcAttackPatternStep s:attackPattern)out.add(s.copy());return out;}

    private void loadAbility() {
        abilityIndex = Math.max(0, Math.min(abilityIndex, Math.max(0, abilities.size() - 1)));
    }
    private void abilityMove(int delta) {
        savePage();
        if (abilities.isEmpty()) return;
        abilityIndex = Math.max(0, Math.min(abilities.size() - 1, abilityIndex + delta));
        rebuildWidgets();
    }
    private String abilityPhaseLabel(String phaseId) {
        if (phaseId == null || phaseId.isBlank()) return "All phases";
        for (NpcBossPhase phase : bossPhases) if (phaseId.equals(phase.id)) return phase.displayName;
        return phaseId + " (missing)";
    }
    private void cycleAbilityPhase() {
        savePage();
        if (abilities.isEmpty()) return;
        NpcAbilityDefinition ability = abilities.get(abilityIndex);
        if (bossPhases.isEmpty()) ability.phaseId = "";
        else if (ability.phaseId == null || ability.phaseId.isBlank()) ability.phaseId = bossPhases.get(0).id;
        else {
            int index = -1;
            for (int i = 0; i < bossPhases.size(); i++) if (ability.phaseId.equals(bossPhases.get(i).id)) { index = i; break; }
            ability.phaseId = index < 0 || index + 1 >= bossPhases.size() ? "" : bossPhases.get(index + 1).id;
        }
        rebuildWidgets();
    }
    private List<NpcAbilityDefinition> copiedAbilities() {
        List<NpcAbilityDefinition> out = new ArrayList<>();
        for (NpcAbilityDefinition ability : abilities) {
            NpcAbilityDefinition assignmentView = new NpcAbilityDefinition();
            assignmentView.id = ability.id;
            assignmentView.displayName = ability.displayName;
            assignmentView.phaseId = ability.phaseId;
            assignmentView.normalize();
            out.add(assignmentView);
        }
        return out;
    }


    /** Optional phase gates must never make a migrated/edited NPC impossible to save. */
    private void repairOptionalBossPhaseReferences() {
        java.util.HashSet<String> valid = new java.util.HashSet<>();
        for (NpcBossPhase phase : bossPhases) if (phase != null && phase.id != null && !phase.id.isBlank()) valid.add(phase.id);
        for (NpcAbilityDefinition ability : abilities) {
            if (ability != null && ability.phaseId != null && !ability.phaseId.isBlank() && !valid.contains(ability.phaseId)) ability.phaseId = "";
        }
        for (NpcAttackPatternStep step : attackPattern) {
            if (step != null && step.phaseId != null && !step.phaseId.isBlank() && !valid.contains(step.phaseId)) step.phaseId = "";
        }
    }

    private void openBossPhaseActions() {
        try {
            saveBossPhase();
            if (!bossPhases.isEmpty() && minecraft != null) minecraft.setScreen(new NpcBossPhaseActionsScreen(this, bossPhases.get(bossPhaseIndex)));
        } catch (RuntimeException exception) { notice = exception.getMessage() == null ? "Could not open phase actions." : exception.getMessage(); noticeError = true; }
    }

    private void saveBossPhase() {
        if (bossPhaseId == null || bossPhases.isEmpty()) return;
        String previousId = bossPhases.get(bossPhaseIndex).id;
        NpcBossPhase phase=bossPhases.get(bossPhaseIndex); phase.id=bossPhaseId.getValue(); phase.displayName=bossPhaseName.getValue();
        phase.healthThresholdPercent=parse(bossPhaseThreshold.getValue(),0.1,100,"phase health threshold");
        phase.movementSpeedMultiplier=parse(bossPhaseSpeed.getValue(),0.1,4,"phase movement multiplier");
        phase.cooldownMultiplier=parse(bossPhaseCooldown.getValue(),0.1,5,"phase cooldown multiplier");
        phase.abilityDamageMultiplier=parse(bossPhaseDamage.getValue(),0,8,"phase damage multiplier");
        phase.normalize();
        if (!previousId.equals(phase.id)) { for (NpcAbilityDefinition ability:abilities) if (previousId.equals(ability.phaseId)) ability.phaseId=phase.id; for(NpcAttackPatternStep step:attackPattern) if(previousId.equals(step.phaseId)) step.phaseId=phase.id; }
        loadBossPhase();
    }
    private void loadBossPhase() {
        bossPhaseIndex=Math.max(0,Math.min(bossPhaseIndex,Math.max(0,bossPhases.size()-1))); if (bossPhases.isEmpty()) return;
        NpcBossPhase phase=bossPhases.get(bossPhaseIndex); bossPhaseIdValue=phase.id; bossPhaseNameValue=phase.displayName; bossPhaseThresholdValue=number(phase.healthThresholdPercent);
        bossPhaseSpeedValue=number(phase.movementSpeedMultiplier); bossPhaseCooldownValue=number(phase.cooldownMultiplier); bossPhaseDamageValue=number(phase.abilityDamageMultiplier);
    }
    private void addBossPhase() { try { savePage(); } catch(RuntimeException ignored){} if(bossPhases.size()<NpcBossPhase.MAX_PHASES){ NpcBossPhase phase=NpcBossPhase.phaseOne(); phase.id=uniquePhaseId("phase_"+(bossPhases.size()+1)); phase.displayName="Phase "+(bossPhases.size()+1); phase.healthThresholdPercent=Math.max(1,100.0D/(bossPhases.size()+1)); phase.normalize(); bossPhases.add(phase); bossPhaseIndex=bossPhases.size()-1; loadBossPhase(); } rebuildWidgets(); }
    private String uniquePhaseId(String base) { String id=base; int n=2; while (containsPhaseId(id)) id=base+"_"+n++; return id; }
    private boolean containsPhaseId(String id) { for (NpcBossPhase phase : bossPhases) if (phase.id.equals(id)) return true; return false; }
    private void deleteBossPhase() {
        try { savePage(); } catch(RuntimeException ignored){}
        if (bossPhases.isEmpty() || (bossEnabled && bossPhases.size()<=1)) return;
        String removed=bossPhases.remove(bossPhaseIndex).id; for(NpcAbilityDefinition a:abilities) if(removed.equals(a.phaseId)) a.phaseId=""; for(NpcAttackPatternStep step:attackPattern) if(removed.equals(step.phaseId)) step.phaseId=""; loadBossPhase(); rebuildWidgets();
    }
    private void bossPhaseMove(int delta) { savePage(); bossPhaseIndex=Math.max(0,Math.min(bossPhases.size()-1,bossPhaseIndex+delta)); loadBossPhase(); rebuildWidgets(); }
    private List<NpcBossPhase> copiedBossPhases() { List<NpcBossPhase> out=new ArrayList<>(); for(NpcBossPhase p:bossPhases) out.add(p.copy()); out.sort(java.util.Comparator.comparingDouble((NpcBossPhase p)->p.healthThresholdPercent).reversed()); return out; }

    private void saveRelation() {
        if (relations.isEmpty()) return;
        NpcFactionRelation relation = relations.get(relationIndex); relation.factionId = relationFactionValue; relation.normalize(); relationFactionValue = relation.factionId;
    }
    private void loadRelation() { relationIndex = Math.max(0, Math.min(relationIndex, Math.max(0, relations.size() - 1))); relationFactionValue = relations.isEmpty() ? "" : relations.get(relationIndex).factionId; }
    private void addRelation() { savePage(); if (relations.size() < 16) { NpcFactionRelation r = new NpcFactionRelation(); relations.add(r); relationIndex = relations.size() - 1; loadRelation(); } rebuildWidgets(); }
    private void deleteRelation() { savePage(); if (!relations.isEmpty()) relations.remove(relationIndex); loadRelation(); rebuildWidgets(); }
    private void relationMove(int delta) { savePage(); relationIndex = Math.max(0, Math.min(relations.size() - 1, relationIndex + delta)); loadRelation(); rebuildWidgets(); }
    private void cycleRelation() { savePage(); if (relations.isEmpty()) { NpcFactionRelation r = new NpcFactionRelation(); relations.add(r); relationIndex = 0; } NpcFactionRelation r = relations.get(relationIndex); r.attitude = NpcAttitude.parse(r.attitude).next().id(); loadRelation(); rebuildWidgets(); }

    private void savePatrol() {
        if (patrolX == null || patrol.isEmpty()) return;
        NpcPatrolPoint point = patrol.get(patrolIndex);
        point.x = parse(patrolX.getValue(), -30_000_000, 30_000_000, "patrol X");
        point.y = parse(patrolY.getValue(), -4096, 4096, "patrol Y");
        point.z = parse(patrolZ.getValue(), -30_000_000, 30_000_000, "patrol Z");
        point.yaw = (float) parse(patrolYaw.getValue(), -360, 360, "patrol yaw");
        point.pauseSeconds = parseInt(patrolPause.getValue(), 0, 300, "patrol pause");
        point.normalize();
        loadPatrol();
    }

    private void beginWorldPatrolEdit() {
        if (!initial.editing()) {
            notice = "Create the NPC first before editing its patrol route in-world.";
            noticeError = true;
            return;
        }
        pendingPatrolWorldEdit = submit(false);
        if (pendingPatrolWorldEdit) {
            notice = "Saving before opening the in-world route editor…";
            noticeError = false;
        }
    }

    private void beginWorldScheduleEdit() {
        if (!initial.editing()) {
            notice = "Create the NPC first before editing its schedule destinations in-world.";
            noticeError = true;
            return;
        }
        pendingScheduleWorldEdit = submit(false);
        if (pendingScheduleWorldEdit) {
            notice = "Saving before opening the in-world schedule editor…";
            noticeError = false;
        }
    }

    private void loadPatrol() {
        patrolIndex = Math.max(0, Math.min(patrolIndex, Math.max(0, patrol.size() - 1)));
        if (patrol.isEmpty()) return;
        NpcPatrolPoint point = patrol.get(patrolIndex);
        patrolXValue = number(point.x); patrolYValue = number(point.y); patrolZValue = number(point.z);
        patrolYawValue = number(point.yaw); patrolPauseValue = Integer.toString(point.pauseSeconds);
    }

    private void addPatrol() {
        try { savePage(); } catch (RuntimeException ignored) {}
        if (patrol.size() < 32) {
            NpcPatrolPoint point = new NpcPatrolPoint();
            point.x = parseSafe(xValue, initial.x()); point.y = parseSafe(yValue, initial.y()); point.z = parseSafe(zValue, initial.z());
            point.yaw = (float) parseSafe(yawValue, initial.yaw());
            patrol.add(point.normalize()); patrolIndex = patrol.size() - 1; loadPatrol();
        }
        rebuildWidgets();
    }

    private void deletePatrol() {
        try { savePage(); } catch (RuntimeException ignored) {}
        if (!patrol.isEmpty()) patrol.remove(patrolIndex);
        loadPatrol(); rebuildWidgets();
    }

    private void patrolMove(int delta) {
        savePage();
        patrolIndex = Math.max(0, Math.min(patrol.size() - 1, patrolIndex + delta));
        loadPatrol(); rebuildWidgets();
    }

    private void useCurrentPatrol() {
        if (minecraft == null || minecraft.player == null) return;
        if (patrol.isEmpty()) addPatrol();
        patrolXValue = number(minecraft.player.getX()); patrolYValue = number(minecraft.player.getY());
        patrolZValue = number(minecraft.player.getZ()); patrolYawValue = number(minecraft.player.getYRot());
        rebuildWidgets();
    }

    private void useCurrentAsHome() {
        if (minecraft == null || minecraft.player == null) return;
        savePage();
        xValue = number(minecraft.player.getX()); yValue = number(minecraft.player.getY());
        zValue = number(minecraft.player.getZ()); yawValue = number(minecraft.player.getYRot());
        notice = "NPC home position updated to your current location."; noticeError = false;
        rebuildWidgets();
    }

    private List<NpcPatrolPoint> copiedPatrol() {
        List<NpcPatrolPoint> result = new ArrayList<>();
        for (NpcPatrolPoint point : patrol) result.add(point.copy().normalize());
        return result;
    }

    private void saveSchedule() {
        if (scheduleTime == null || schedule.isEmpty()) return;
        NpcScheduleEntry entry = schedule.get(scheduleIndex); entry.minuteOfDay = parseClock(scheduleTime.getValue());
        entry.x = parse(scheduleX.getValue(), -30_000_000, 30_000_000, "schedule X"); entry.y = parse(scheduleY.getValue(), -4096, 4096, "schedule Y");
        entry.z = parse(scheduleZ.getValue(), -30_000_000, 30_000_000, "schedule Z"); entry.yaw = (float) parse(scheduleYaw.getValue(), -360, 360, "schedule yaw");
        entry.speed = parse(scheduleSpeed.getValue(), 0.05, 4, "schedule speed"); entry.movement = scheduleMovement; entry.activity = scheduleActivity; entry.normalize();
    }
    private void loadSchedule() {
        scheduleIndex = Math.max(0, Math.min(scheduleIndex, Math.max(0, schedule.size() - 1)));
        if (schedule.isEmpty()) return; NpcScheduleEntry e = schedule.get(scheduleIndex);
        scheduleTimeValue = String.format(Locale.ROOT, "%02d:%02d", e.minuteOfDay / 60, e.minuteOfDay % 60);
        scheduleXValue = number(e.x); scheduleYValue = number(e.y); scheduleZValue = number(e.z); scheduleYawValue = number(e.yaw); scheduleSpeedValue = number(e.speed);
        scheduleMovement = e.movement; scheduleActivity = e.activity;
    }
    private void addSchedule() {
        try { savePage(); } catch (RuntimeException ignored) {}
        if (schedule.size() < 16) {
            NpcScheduleEntry e = new NpcScheduleEntry();
            if (minecraft != null && minecraft.player != null) {
                e.x = minecraft.player.getX(); e.y = minecraft.player.getY(); e.z = minecraft.player.getZ();
                e.yaw = minecraft.player.getYRot();
                if (minecraft.level != null) e.minuteOfDay = GameCalendar.fromClockTime(minecraft.level.getDayTime()).minuteOfDay();
            } else {
                e.x = parseSafe(xValue, 0); e.y = parseSafe(yValue, 64); e.z = parseSafe(zValue, 0);
            }
            schedule.add(e.normalize()); scheduleIndex = schedule.size() - 1; loadSchedule();
        }
        rebuildWidgets();
    }
    private void deleteSchedule() { try { savePage(); } catch (RuntimeException ignored) {} if (!schedule.isEmpty()) schedule.remove(scheduleIndex); loadSchedule(); rebuildWidgets(); }
    private void scheduleMove(int delta) { savePage(); scheduleIndex = Math.max(0, Math.min(schedule.size() - 1, scheduleIndex + delta)); loadSchedule(); rebuildWidgets(); }
    private void cycleScheduleMovement() { savePage(); scheduleMovement = NpcScheduleEntry.MOVEMENT_TELEPORT.equals(scheduleMovement) ? NpcScheduleEntry.MOVEMENT_WALK : NpcScheduleEntry.MOVEMENT_TELEPORT; rebuildWidgets(); }
    private void cycleScheduleActivity() { savePage(); scheduleActivity = NpcScheduleActivity.parse(scheduleActivity).next().id(); rebuildWidgets(); }
    private void useCurrentSchedule() { if (minecraft == null || minecraft.player == null) return; if (schedule.isEmpty()) addSchedule(); scheduleXValue = number(minecraft.player.getX()); scheduleYValue = number(minecraft.player.getY()); scheduleZValue = number(minecraft.player.getZ()); scheduleYawValue = number(minecraft.player.getYRot()); rebuildWidgets(); }
    private void useCurrentScheduleTime() { if (minecraft == null || minecraft.level == null) return; if (schedule.isEmpty()) addSchedule(); scheduleTimeValue = GameCalendar.fromClockTime(minecraft.level.getDayTime()).clockText(); rebuildWidgets(); }
    private List<NpcScheduleEntry> copiedSchedule() { List<NpcScheduleEntry> result = new ArrayList<>(); for (NpcScheduleEntry entry : schedule) result.add(entry.copy().normalize()); return result; }

    private void usePlacementRespawn() { savePage(); respawnDimensionValue = initial.dimension(); respawnXValue = xValue; respawnYValue = yValue; respawnZValue = zValue; respawnYawValue = yawValue; rebuildWidgets(); }
    private void useCurrentRespawn() { if (minecraft == null || minecraft.player == null) return; savePage(); respawnDimensionValue = minecraft.player.level().dimension().location().toString(); respawnXValue = number(minecraft.player.getX()); respawnYValue = number(minecraft.player.getY()); respawnZValue = number(minecraft.player.getZ()); respawnYawValue = number(minecraft.player.getYRot()); rebuildWidgets(); }

    @Override public void onClose() { closeToParent(initial.editing()); }

    private void closeToParent(boolean refreshManager) {
        if (minecraft == null) return;
        minecraft.setScreen(parent);
        if (refreshManager && parent instanceof NpcAdminScreen manager) manager.refresh();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py(); SsuGuiScale.fullscreenDim(g, this, 0xA9000000); g.fill(x, y, x + W, y + H, PANEL); g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, initial.editing() ? "Edit NPC" : "Create NPC", x + 12, y + 12, TEXT, true);
        renderLabels(g, x, y);
        if (!notice.isBlank()) g.drawString(font, trim(notice, 58), x + 194, y + H - 20, noticeError ? ERROR : GOOD, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderLabels(GuiGraphics g, int x, int y) {
        switch (page) {
            case IDENTITY -> { label(g,"Template ID",x+12,y+77);label(g,"Display name",x+250,y+77);label(g,"Role / occupation",x+12,y+121);label(g,"X",x+12,y+179);label(g,"Y",x+115,y+179);label(g,"Z",x+218,y+179);label(g,"Yaw",x+321,y+179); }
            case APPEARANCE -> {
                label(g,"Visual mode",x+12,y+77);
                NpcVisualMode visual = NpcVisualMode.parse(visualModeValue);
                if (visual == NpcVisualMode.ENTITY) {
                    label(g,"Minecraft living entity model",x+12,y+121);
                    label(g,"Optional texture override",x+12,y+165);
                    NpcTextureSource source = NpcTextureSource.parse(textureSourceValue);
                    if (source.custom()) label(g, source == NpcTextureSource.LOCAL ? "Relative PNG path" : "HTTPS PNG URL", x+12,y+211);
                    g.drawString(font,"Use the same UV layout as the selected vanilla mob texture.",x+258,y+181,MUTED,false);
                } else {
                    label(g,"Player skin",x+12,y+121);
                    NpcTextureSource source = NpcTextureSource.parse(textureSourceValue);
                    if (source.custom()) label(g, source == NpcTextureSource.LOCAL ? "Relative 64x64 PNG path" : "HTTPS 64x64 PNG URL", x+12,y+167);
                    g.drawString(font,"Uses Minecraft's player model. Player skins must be 64x64.",x+12,y+214,MUTED,false);
                }
            }
            case INTERACTION -> {
                label(g,"Interaction mode",x+12,y+77); label(g,"Advanced dialogue ID",x+240,y+77);
                label(g,"Simple quest workflow",x+12,y+109);
                label(g,"Optional linked shop",x+12,y+143);
                label(g,"Advanced actions " + (functions.isEmpty()?"0/0":(functionIndex+1)+"/"+functions.size()),x+12,y+183);
                if(!functions.isEmpty()){label(g,"Action ID",x+12,y+227);label(g,"Button label",x+165,y+227);label(g,"Service",x+328,y+227);label(g,"Target (warp, quest, game, dungeon…)",x+12,y+275);}
            }
            case BEHAVIOR -> {
                NpcBehaviorMode behavior = NpcBehaviorMode.parse(behaviorModeValue);
                if (behavior == NpcBehaviorMode.LOOK_AT_PLAYERS) { label(g,"Look-at range",x+12,y+167); }
                else if (behavior == NpcBehaviorMode.WANDER) { label(g,"Wander radius",x+12,y+167); label(g,"New target every (sec)",x+165,y+167); }
                else if (behavior == NpcBehaviorMode.PATROL) { label(g,"Patrol route",x+12,y+167); }
                label(g,"Home radius (0 = none)",x+12,y+221);
                g.drawString(font,"Walking/running speed is configured under Stats. Schedules may override route movement.",x+12,y+260,MUTED,false);
                g.drawString(font,"AI family: " + trim(initial.aiProfileLabel(), 42),x+12,y+276,MUTED,false);
                g.drawString(font,"Runtime: " + trim(initial.aiRuntimeState(), 55),x+12,y+290,MUTED,false);
            }
            case MOVEMENT -> {
                label(g,"Patrol traversal",x+12,y+77);
                label(g,"Patrol point " + (patrol.isEmpty()?"0/0":(patrolIndex+1)+"/"+patrol.size()),x+165,y+77);
                label(g,"X",x+12,y+137); label(g,"Y",x+115,y+137); label(g,"Z",x+218,y+137);
                label(g,"Yaw",x+321,y+137); label(g,"Pause sec",x+424,y+137);
                g.drawString(font,"Patrol routes belong to this placement; linked copies keep independent waypoints.",x+12,y+246,MUTED,false);
                if (initial.editing()) g.drawString(font,"World editor: RMB block add • sneak+RMB remove • sneak+RMB air undo • RMB air finish.",x+12,y+258,MUTED,false);
            }
            case RELATIONS -> { label(g,"Faction ID",x+12,y+77); label(g,"Faction name",x+165,y+77); label(g,"Attitude to players",x+328,y+77); label(g,"Minimum reputation",x+12,y+121); label(g,"Loss when attacked",x+165,y+121); label(g,"Denied message",x+12,y+165); label(g,"Faction relation " + (relations.isEmpty()?"0/0":(relationIndex+1)+"/"+relations.size()) + " — choose from known factions",x+12,y+221); }
            case COMBAT -> { label(g,"Combat profile + allowed attack channels",x+12,y+77); label(g,"Self-defense reaction",x+12,y+121); label(g,"Hostile sight reaction",x+258,y+121); label(g,"Friendly-defense reaction",x+12,y+165); label(g,"Assist range",x+258,y+165); label(g,"Flee distance",x+383,y+165); label(g,"Attack cooldown (ticks)",x+12,y+219); g.drawString(font,"Melee/ranged use equipped weapons; Magic gates magic abilities. Any combination is allowed.",x+12,y+267,MUTED,false); g.drawString(font,"Patrol/schedules use Walking speed; combat chase uses Running speed.",x+12,y+279,MUTED,false); }
            case TACTICS -> { label(g,"Threat range",x+12,y+121);label(g,"Damage ×",x+102,y+121);label(g,"Healing ×",x+192,y+121);label(g,"Decay/sec",x+282,y+121);label(g,"Switch ratio",x+384,y+121);label(g,"Pattern step "+(attackPattern.isEmpty()?"0/0":(patternIndex+1)+"/"+attackPattern.size()),x+12,y+165);if(!attackPattern.isEmpty()){label(g,"Action",x+12,y+209);label(g,"Ability",x+165,y+209);label(g,"Boss phase",x+328,y+209);label(g,"Min range",x+12,y+253);label(g,"Max range",x+132,y+253);label(g,"Min own HP %",x+252,y+253);label(g,"Max own HP %",x+372,y+253);}g.drawString(font,"Threat tracks damage/healing aggro; switch ratio prevents target ping-pong.",x+12,y+298,MUTED,false);}
            case ABILITIES -> {
                label(g,"Shared Ability assignments",x+12,y+77);
                g.drawString(font,"Assigned abilities: " + abilities.size() + "/" + NpcAbilityDefinition.MAX_ABILITIES,x+12,y+132,TEXT,false);
                if (!abilities.isEmpty()) {
                    NpcAbilityDefinition selectedAbility = abilities.get(Math.max(0, Math.min(abilityIndex, abilities.size()-1)));
                    g.drawString(font,"Selected: " + selectedAbility.displayName + " [" + selectedAbility.id + "]",x+12,y+184,TEXT,false);
                }
                g.drawString(font,"Abilities are server-wide. Create/edit them once in the Ability Library and assign them to any NPC.",x+12,y+218,MUTED,false);
                g.drawString(font,"The Phase button is an NPC-specific restriction; editing the shared ability updates every assigned NPC.",x+12,y+232,MUTED,false);
            }
            case BOSS -> {
                label(g,"Boss bar range",x+12,y+121); label(g,"Reset distance",x+165,y+121); label(g,"Reset after idle seconds",x+328,y+121);
                label(g,"Boss phase " + (bossPhases.isEmpty()?"0/0":(bossPhaseIndex+1)+"/"+bossPhases.size()),x+12,y+165);
                if(!bossPhases.isEmpty()){ label(g,"Phase ID",x+12,y+209); label(g,"Display name",x+165,y+209); label(g,"Health threshold %",x+328,y+209); label(g,"Move multiplier",x+12,y+253); label(g,"Cooldown multiplier",x+132,y+253); label(g,"Ability damage multiplier",x+252,y+253); label(g,"Entry actions",x+372,y+253); }
                g.drawString(font,"Phases are evaluated from highest to lowest health threshold.",x+12,y+298,MUTED,false);
            }
            case STATS -> {
                label(g,"Max health (blank=native)",x+12,y+77); label(g,"Magic resistance (0-0.95)",x+165,y+77); label(g,"Armor multiplier",x+328,y+77);
                label(g,"Melee damage ×",x+12,y+131); label(g,"Ranged damage ×",x+165,y+131); label(g,"Magic damage ×",x+328,y+131);
                label(g,"Walking speed ×",x+12,y+185); label(g,"Running speed ×",x+165,y+185); label(g,"Follow range",x+328,y+185);
                label(g,"Knockback resistance",x+12,y+239); label(g,"Scale",x+165,y+239);
                g.drawString(font,"Armor/toughness and ordinary attack power come from equipped items + their gameplay modifiers/enchantments. Running speed must be >= walking.",x+12,y+286,MUTED,false);
            }
            case LOADOUT -> { g.drawString(font,"Equipped weapons/armor are real combat gear; attributes + enchantments count and durability never decreases.",x+40,y+88,MUTED,false); g.drawString(font,"The nine loot slots are still the NPC's only loot table; equipped gear never drops.",x+40,y+154,MUTED,false); if(!initial.editing())g.drawString(font,"Create this NPC first to open its real inventory editor.",x+40,y+190,ERROR,false); }
            case SCHEDULE -> { label(g,"Schedule point " + (schedule.isEmpty()?"0/0":(scheduleIndex+1)+"/"+schedule.size()),x+165,y+77); label(g,"Time",x+12,y+125); label(g,"X",x+102,y+125); label(g,"Y",x+205,y+125); label(g,"Z",x+308,y+125); label(g,"Yaw",x+411,y+125); label(g,"Speed",x+12,y+179); g.drawString(font,"Arrival action runs here until the next schedule time.",x+214,y+223,MUTED,false); g.drawString(font,"Add uses your current position/time. Combat interrupts movement, then the schedule resumes.",x+12,y+252,MUTED,false); if(initial.editing())g.drawString(font,"World editor: RMB add • sneak+RMB remove • sneak+RMB air undo • RMB air finish.",x+12,y+264,MUTED,false); }
            case RESPAWN -> { label(g,"Delay seconds",x+165,y+77); label(g,"Dimension",x+328,y+77); label(g,"X",x+12,y+137); label(g,"Y",x+115,y+137); label(g,"Z",x+218,y+137); label(g,"Yaw",x+321,y+137); }
        }
    }
    private void label(GuiGraphics g, String text, int x, int y) { g.drawString(font, text, x, y, MUTED, false); }

    private static String choiceLabel(List<NpcEditorOpenPayload.Choice> choices, String id, String fallback) {
        String safe = id == null ? "" : id;
        if (safe.isBlank()) return fallback;
        if (choices != null) for (NpcEditorOpenPayload.Choice choice : choices) if (safe.equals(choice.id())) return choice.label();
        return safe + " (missing)";
    }

    private void clearRefs() { definitionId=displayName=roleField=textureValueField=dialogueId=xField=yField=zField=yawField=null; customModelResource=customTextureResource=customAnimationResource=idleAnimation=walkAnimation=attackAnimation=castAnimation=hurtAnimation=deathAnimation=null; functionId=functionLabel=functionTarget=null; factionId=factionDisplayName=minimumReputation=reputationDeniedText=reputationLoss=null; assistRange=fleeDistance=attackCooldown=null; threatRange=threatDamage=threatHealing=threatDecay=threatSwitch=null; patternMinRange=patternMaxRange=patternMinHealth=patternMaxHealth=null; abilityId=abilityName=abilityPhase=abilityCooldown=abilityWindup=abilityRecovery=abilityMinRange=abilityMaxRange=abilityChance=abilityDamage=abilityRadius=abilityKnockback=abilityHeal=null; bossBarRange=bossResetDistance=bossResetSeconds=bossPhaseId=bossPhaseName=bossPhaseThreshold=bossPhaseSpeed=bossPhaseCooldown=bossPhaseDamage=null; maxHealth=magicResistance=armorMultiplier=meleeDamageMultiplier=rangedDamageMultiplier=magicDamageMultiplier=walkingSpeed=runningSpeed=followRange=knockback=scale=homeRadius=null; lookAtRange=wanderRadius=wanderInterval=null; patrolX=patrolY=patrolZ=patrolYaw=patrolPause=null; scheduleTime=scheduleX=scheduleY=scheduleZ=scheduleYaw=scheduleSpeed=null; respawnDelay=respawnDimension=respawnX=respawnY=respawnZ=respawnYaw=null; }
    private int px() { return (width - W) / 2; } private int py() { return (height - H) / 2; }
    private static ItemStack copy(ItemStack stack) { return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy(); }
    private static int parseInt(String raw, int min, int max, String label) { try { int v=Integer.parseInt(raw.trim()); if(v<min||v>max)throw new NumberFormatException(); return v; } catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+" ("+min+" to "+max+").");} }
    private static double parse(String raw, double min, double max, String label) { try { double v=Double.parseDouble(raw.trim().replace(',','.')); if(!Double.isFinite(v)||v<min||v>max)throw new NumberFormatException(); return v; } catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+".");} }
    private static double parseOptional(String raw,double min,double max,String label){return raw==null||raw.isBlank()?-1.0D:parse(raw,min,max,label);}
    private static int parseClock(String raw) { try { String[] p=raw.trim().split(":",-1); int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]); if(p.length!=2||h<0||h>23||m<0||m>59)throw new Exception(); return h*60+m; } catch(Exception e){throw new IllegalArgumentException("Schedule time must use HH:MM.");} }
    private static double parseSafe(String raw,double fallback){try{return Double.parseDouble(raw.replace(',','.'));}catch(Exception ignored){return fallback;}}
    private static String optional(double value){return value<0?"":number(value);} private static String number(double value){if(Math.rint(value)==value)return Long.toString((long)value);return String.format(Locale.ROOT,"%.4f",value).replaceAll("0+$","").replaceAll("\\.$","");}
    private static String trim(String value,int max){if(value==null)return"";return value.length()<=max?value:value.substring(0,Math.max(0,max-1))+"…";}
}
