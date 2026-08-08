package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorLootSlot;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutOpenRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFactionRelation;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFunction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInteractionMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcRole;
import be.winnetrie.mod.simpleserverutilities.npc.NpcLoadoutMenu;
import be.winnetrie.mod.simpleserverutilities.npc.NpcScheduleEntry;
import be.winnetrie.mod.simpleserverutilities.npc.NpcTextureSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact multi-page NPC editor. Inventory loadouts are edited in a real container menu. */
public final class NpcEditorScreen extends Screen {
    private static final int W = 510, H = 350;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;
    private enum Page { IDENTITY, APPEARANCE, INTERACTION, BEHAVIOR, RELATIONS, STATS, LOADOUT, SCHEDULE, RESPAWN }

    private final NpcEditorOpenPayload initial;
    private final Screen parent;
    private final List<String> models;
    private final List<String> services;
    private Page page = Page.IDENTITY;

    private String definitionIdValue, displayNameValue, entityTypeValue, textureSourceValue, textureValueValue, textureModelValue, interactionTextValue, dialogueIdValue;
    private String roleId, shopIdValue, interactionMode;
    private final List<NpcFunction> functions = new ArrayList<>();
    private int functionIndex;
    private String functionIdValue = "function", functionLabelValue = "Service", functionTargetValue = "";
    private String xValue, yValue, zValue, yawValue;
    private String factionIdValue, factionDisplayNameValue, minimumReputationValue, reputationDeniedTextValue, reputationLossValue;
    private String playerAttitude;
    private final List<NpcFactionRelation> relations = new ArrayList<>();
    private int relationIndex;
    private String relationFactionValue = "";
    private String maxHealthValue, movementSpeedValue, attackDamageValue, armorValue;
    private String armorToughnessValue, followRangeValue, knockbackValue, scaleValue, homeRadiusValue;
    private boolean enabled, nameVisible, noAi, invulnerable, silent, glowing, gravity, canSwim, canFly;
    private boolean scheduleEnabled, respawnEnabled;
    private final List<NpcScheduleEntry> schedule = new ArrayList<>();
    private int scheduleIndex;
    private String scheduleTimeValue = "06:00", scheduleXValue = "0", scheduleYValue = "64", scheduleZValue = "0";
    private String scheduleYawValue = "0", scheduleSpeedValue = "1";
    private String scheduleMovement = NpcScheduleEntry.MOVEMENT_WALK;
    private String scheduleActivity = NpcScheduleEntry.ACTIVITY_IDLE;
    private String respawnDelayValue, respawnDimensionValue, respawnXValue, respawnYValue, respawnZValue, respawnYawValue;

    private EditBox definitionId, displayName, textureValueField, dialogueId, xField, yField, zField, yawField;
    private EditBox functionId, functionLabel, functionTarget;
    private EditBox factionId, factionDisplayName, minimumReputation, reputationDeniedText, reputationLoss;
    private EditBox maxHealth, movementSpeed, attackDamage, armor, armorToughness, followRange, knockback, scale, homeRadius;
    private EditBox scheduleTime, scheduleX, scheduleY, scheduleZ, scheduleYaw, scheduleSpeed;
    private EditBox respawnDelay, respawnDimension, respawnX, respawnY, respawnZ, respawnYaw;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;
    private boolean deleteArmed;
    private boolean pendingDelete;

    public NpcEditorScreen(NpcEditorOpenPayload initial, Screen parent) {
        super(Component.literal(initial.editing() ? "Edit NPC" : "Create NPC"));
        this.initial = initial; this.parent = parent; this.models = initial.availableModels();
        this.services = initial.availableServices().stream().filter(value -> !"shop".equals(value)).toList();
        definitionIdValue = initial.definitionId(); displayNameValue = initial.displayName();
        entityTypeValue = initial.entityType(); textureSourceValue = NpcTextureSource.parse(initial.textureSource()).id(); textureValueValue = initial.textureValue(); textureModelValue = initial.textureModel(); interactionTextValue = initial.interactionText(); dialogueIdValue = initial.dialogueId();
        roleId = NpcRole.parse(initial.roleId()).id(); shopIdValue = initial.shopId(); interactionMode = NpcInteractionMode.parse(initial.interactionMode()).id();
        for (NpcFunction function : initial.functions()) functions.add(function.copy());
        loadFunction();
        xValue = number(initial.x()); yValue = number(initial.y()); zValue = number(initial.z()); yawValue = number(initial.yaw());
        enabled = initial.enabled(); nameVisible = initial.customNameVisible(); noAi = initial.noAi();
        invulnerable = initial.invulnerable(); silent = initial.silent(); glowing = initial.glowing();
        gravity = initial.affectedByGravity(); canSwim = initial.canSwim(); canFly = initial.canFly();
        factionIdValue = initial.factionId(); factionDisplayNameValue = initial.factionDisplayName(); minimumReputationValue = Integer.toString(initial.minimumReputation());
        reputationDeniedTextValue = initial.reputationDeniedText(); reputationLossValue = Integer.toString(initial.reputationLossOnAttack());
        playerAttitude = NpcAttitude.parse(initial.playerAttitude()).id();
        for (NpcFactionRelation relation : initial.factionRelations()) relations.add(relation.copy());
        loadRelation();
        maxHealthValue = optional(initial.maxHealth()); movementSpeedValue = optional(initial.movementSpeed());
        attackDamageValue = optional(initial.attackDamage()); armorValue = optional(initial.armor());
        armorToughnessValue = optional(initial.armorToughness()); followRangeValue = optional(initial.followRange());
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
        Page[] pages = Page.values(); String[] labels = {"Identity", "Appearance", "Interaction", "Behavior", "Relations", "Stats", "Loadout", "Schedule", "Respawn"};
        for (int i = 0; i < pages.length; i++) {
            int col = i % 5, row = i / 5;
            Page targetPage = pages[i];
            Button button = addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> switchPage(targetPage))
                    .bounds(x + 12 + col * 97, y + 30 + row * 21, 91, 18).build());
            button.active = targetPage != page;
        }
        switch (page) {
            case IDENTITY -> initIdentity(x, y);
            case APPEARANCE -> initAppearance(x, y);
            case INTERACTION -> initInteraction(x, y);
            case BEHAVIOR -> initBehavior(x, y);
            case RELATIONS -> initRelations(x, y);
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
        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleRole(-1))
                .bounds(x + 12, y + 132, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Role: " + NpcRole.parse(roleId).label()), b -> cycleRole(1))
                .bounds(x + 44, y + 132, 220, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleRole(1))
                .bounds(x + 268, y + 132, 28, 18).build());
        xField = field(x + 12, y + 190, 95, 20, xValue); yField = field(x + 115, y + 190, 95, 20, yValue);
        zField = field(x + 218, y + 190, 95, 20, zValue); yawField = field(x + 321, y + 190, 95, 16, yawValue);
        toggle(x + 12, y + 232, 145, "Enabled", enabled, () -> enabled = !enabled);
        toggle(x + 165, y + 232, 155, "Name visible", nameVisible, () -> nameVisible = !nameVisible);
        setInitialFocus(definitionId);
    }

    private void initAppearance(int x, int y) {
        NpcTextureSource source = NpcTextureSource.parse(textureSourceValue);
        addRenderableWidget(Button.builder(Component.literal("Model: " + trim(entityTypeValue, 28)), b -> {
            savePage(); if (minecraft != null) minecraft.setScreenAndShow(new NpcModelPickerScreen(this, models, entityTypeValue));
        }).bounds(x + 12, y + 88, 238, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Texture: " + source.label()), b -> cycleTextureSource())
                .bounds(x + 258, y + 88, 240, 18).build());
        if (source.custom()) {
            textureValueField = field(x + 12, y + 142, 486, 1_024, textureValueValue);
            addRenderableWidget(Button.builder(Component.literal("Model shape: " + ("slim".equals(textureModelValue) ? "Slim" : "Wide")), b -> {
                savePage(); textureModelValue = "slim".equals(textureModelValue) ? "wide" : "slim"; rebuildWidgets();
            }).bounds(x + 12, y + 178, 180, 18).build());
        }
        toggle(x + 12, y + 224, 145, "Glow", glowing, () -> glowing = !glowing);
    }

    private void initInteraction(int x, int y) {
        NpcInteractionMode mode = NpcInteractionMode.parse(interactionMode);
        addRenderableWidget(Button.builder(Component.literal("Mode: " + mode.label()), b -> cycleInteractionMode())
                .bounds(x + 12, y + 88, 220, 18).build());
        dialogueId = field(x + 240, y + 88, 150, 64, dialogueIdValue);
        Button dialogue = addRenderableWidget(Button.builder(Component.literal("Edit dialogue"), b -> {
            savePage(); ClientPacketDistributor.sendToServer(new NpcDialogueEditorRequestPayload(initial.originalInstanceId()));
        }).bounds(x + 398, y + 88, 100, 18).build());
        dialogue.active = initial.editing();

        boolean merchant = NpcRole.parse(roleId) == NpcRole.MERCHANT;
        if (merchant) {
            addRenderableWidget(Button.builder(Component.literal(shopIdValue.isBlank() ? "Create NPC shop" : "Edit NPC shop"),
                    b -> openOrCreateNpcShop()).bounds(x + 12, y + 126, 210, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Shared shop…"), b -> {
                savePage(); if (minecraft != null) minecraft.setScreenAndShow(new NpcChoicePickerScreen(this,
                        NpcChoicePickerScreen.Kind.SHOP, initial.availableShops(), shopIdValue));
            }).bounds(x + 230, y + 126, 130, 18).build());
            Button unlinkShop = addRenderableWidget(Button.builder(Component.literal("Unlink shop"), b -> {
                savePage(); shopIdValue = ""; notice = "NPC shop unlinked."; noticeError = false; rebuildWidgets();
            }).bounds(x + 368, y + 126, 130, 18).build());
            unlinkShop.active = !shopIdValue.isBlank();
        } else {
            addRenderableWidget(Button.builder(Component.literal("Optional shop: "
                    + trim(choiceLabel(initial.availableShops(), shopIdValue, "None"), 27)), b -> {
                savePage(); if (minecraft != null) minecraft.setScreenAndShow(new NpcChoicePickerScreen(this,
                        NpcChoicePickerScreen.Kind.SHOP, initial.availableShops(), shopIdValue));
            }).bounds(x + 12, y + 126, 250, 18).build());
            Button editShop = addRenderableWidget(Button.builder(Component.literal("Edit shop"), b -> openLinkedShop())
                    .bounds(x + 270, y + 126, 100, 18).build());
            editShop.active = !shopIdValue.isBlank();
            addRenderableWidget(Button.builder(Component.literal("New shop"), b -> createLinkedShop())
                    .bounds(x + 378, y + 126, 120, 18).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> functionMove(-1))
                .bounds(x + 12, y + 166, 28, 18).build()); previous.active = functionIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> functionMove(1))
                .bounds(x + 44, y + 166, 28, 18).build()); next.active = functionIndex + 1 < functions.size();
        addRenderableWidget(Button.builder(Component.literal("Add action"), b -> addFunction()).bounds(x + 80, y + 166, 76, 18).build());
        Button remove = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteFunction())
                .bounds(x + 164, y + 166, 60, 18).build()); remove.active = !functions.isEmpty();
        if (functions.isEmpty()) return;

        NpcFunction current = functions.get(functionIndex);
        functionId = field(x + 12, y + 210, 145, 64, functionIdValue);
        functionLabel = field(x + 165, y + 210, 155, 64, functionLabelValue);
        addRenderableWidget(Button.builder(Component.literal("Service: " + trim(current.service.isBlank() ? "none" : current.service, 22)),
                b -> cycleFunctionService()).bounds(x + 328, y + 210, 170, 18).build());
        functionTarget = field(x + 12, y + 258, 308, 256, functionTargetValue);
        toggle(x + 328, y + 258, 170, "Action enabled", current.enabled, () -> current.enabled = !current.enabled);
    }

    private void initBehavior(int x, int y) {
        toggle(x + 12, y + 88, 145, "No AI", noAi, () -> noAi = !noAi);
        toggle(x + 165, y + 88, 155, "Invulnerable", invulnerable, () -> invulnerable = !invulnerable);
        toggle(x + 328, y + 88, 170, "Silent", silent, () -> silent = !silent);
        toggle(x + 12, y + 120, 145, "Normal gravity", gravity, () -> gravity = !gravity);
        toggle(x + 165, y + 120, 155, "Can swim", canSwim, () -> canSwim = !canSwim);
        toggle(x + 328, y + 120, 170, "Can fly", canFly, () -> canFly = !canFly);
        homeRadius = field(x + 12, y + 178, 145, 16, homeRadiusValue);
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
            savePage(); if (minecraft != null) minecraft.setScreenAndShow(new NpcChoicePickerScreen(this,
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

    private void initStats(int x, int y) {
        maxHealth = field(x + 12, y + 88, 145, 16, maxHealthValue); movementSpeed = field(x + 165, y + 88, 155, 16, movementSpeedValue);
        attackDamage = field(x + 328, y + 88, 170, 16, attackDamageValue); armor = field(x + 12, y + 142, 145, 16, armorValue);
        armorToughness = field(x + 165, y + 142, 155, 16, armorToughnessValue); followRange = field(x + 328, y + 142, 170, 16, followRangeValue);
        knockback = field(x + 12, y + 196, 145, 16, knockbackValue); scale = field(x + 165, y + 196, 155, 16, scaleValue);
    }

    private void initLoadout(int x, int y) {
        Button equipment = addRenderableWidget(Button.builder(Component.literal("Edit visual equipment"),
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
        addRenderableWidget(Button.builder(Component.literal("Use current"), b -> useCurrentSchedule())
                .bounds(x + 361, y + 88, 137, 18).build());
        scheduleTime = field(x + 12, y + 136, 82, 5, scheduleTimeValue);
        scheduleX = field(x + 102, y + 136, 95, 20, scheduleXValue); scheduleY = field(x + 205, y + 136, 95, 20, scheduleYValue);
        scheduleZ = field(x + 308, y + 136, 95, 20, scheduleZValue); scheduleYaw = field(x + 411, y + 136, 87, 16, scheduleYawValue);
        scheduleSpeed = field(x + 12, y + 190, 82, 12, scheduleSpeedValue);
        addRenderableWidget(Button.builder(Component.literal("Move: " + scheduleMovement), b -> cycleScheduleMovement())
                .bounds(x + 102, y + 190, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Activity: " + scheduleActivity), b -> cycleScheduleActivity())
                .bounds(x + 260, y + 190, 180, 18).build());
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
        if (definitionId != null) definitionIdValue = definitionId.getValue(); if (displayName != null) displayNameValue = displayName.getValue();
        if (textureValueField != null) textureValueValue = textureValueField.getValue();
        if (dialogueId != null) dialogueIdValue = dialogueId.getValue();
        saveFunction();
        if (xField != null) xValue = xField.getValue(); if (yField != null) yValue = yField.getValue(); if (zField != null) zValue = zField.getValue(); if (yawField != null) yawValue = yawField.getValue();
        if (factionId != null) factionIdValue = factionId.getValue(); if (factionDisplayName != null) factionDisplayNameValue = factionDisplayName.getValue(); if (minimumReputation != null) minimumReputationValue = minimumReputation.getValue();
        if (reputationDeniedText != null) reputationDeniedTextValue = reputationDeniedText.getValue(); if (reputationLoss != null) reputationLossValue = reputationLoss.getValue();
        saveRelation();
        if (maxHealth != null) maxHealthValue = maxHealth.getValue(); if (movementSpeed != null) movementSpeedValue = movementSpeed.getValue();
        if (attackDamage != null) attackDamageValue = attackDamage.getValue(); if (armor != null) armorValue = armor.getValue();
        if (armorToughness != null) armorToughnessValue = armorToughness.getValue(); if (followRange != null) followRangeValue = followRange.getValue();
        if (knockback != null) knockbackValue = knockback.getValue(); if (scale != null) scaleValue = scale.getValue(); if (homeRadius != null) homeRadiusValue = homeRadius.getValue();
        saveSchedule();
        if (respawnDelay != null) respawnDelayValue = respawnDelay.getValue(); if (respawnDimension != null) respawnDimensionValue = respawnDimension.getValue();
        if (respawnX != null) respawnXValue = respawnX.getValue(); if (respawnY != null) respawnYValue = respawnY.getValue();
        if (respawnZ != null) respawnZValue = respawnZ.getValue(); if (respawnYaw != null) respawnYawValue = respawnYaw.getValue();
    }

    private void submit(boolean deleteRequested) {
        try {
            savePage();
            List<NpcFactionRelation> savedRelations = new ArrayList<>();
            for (NpcFactionRelation relation : relations) if (relation.copy().normalize().configured()) savedRelations.add(relation.copy().normalize());
            List<NpcEditorLootSlot> loot = initial.loot();
            ClientPacketDistributor.sendToServer(new NpcEditorSubmitPayload(initial.originalInstanceId(), initial.originalDefinitionId(), deleteRequested,
                    definitionIdValue.trim(), displayNameValue.trim(), entityTypeValue, textureSourceValue, textureValueValue.trim(), textureModelValue, interactionTextValue, dialogueIdValue.trim(),
                    roleId, shopIdValue.trim(), interactionMode, copiedFunctions(),
                    parse(xValue, -30_000_000, 30_000_000, "X"), parse(yValue, -4096, 4096, "Y"), parse(zValue, -30_000_000, 30_000_000, "Z"),
                    (float) parse(yawValue, -360, 360, "yaw"), initial.pitch(), enabled, nameVisible, noAi, invulnerable, silent, glowing,
                    gravity, canSwim, canFly, factionIdValue.trim(), factionDisplayNameValue.trim(), parseInt(minimumReputationValue, -1_000_000, 1_000_000, "minimum reputation"),
                    reputationDeniedTextValue, parseInt(reputationLossValue, 0, 1_000_000, "reputation loss"), playerAttitude, savedRelations,
                    parseOptional(maxHealthValue, 1, 2048, "max health"), parseOptional(movementSpeedValue, 0, 4, "movement speed"),
                    parseOptional(attackDamageValue, 0, 2048, "attack damage"), parseOptional(armorValue, 0, 2048, "armor"),
                    parseOptional(armorToughnessValue, 0, 2048, "armor toughness"), parseOptional(followRangeValue, 1, 2048, "follow range"),
                    parseOptional(knockbackValue, 0, 1, "knockback resistance"), parseOptional(scaleValue, 0.0625, 16, "scale"),
                    parse(homeRadiusValue, 0, 2048, "home radius"), copy(initial.mainHandItem()), copy(initial.offHandItem()), copy(initial.headItem()),
                    copy(initial.chestItem()), copy(initial.legsItem()), copy(initial.feetItem()), initial.lootRolls(), loot,
                    scheduleEnabled, copiedSchedule(), respawnEnabled, parseInt(respawnDelayValue, 0, 86_400, "respawn delay"),
                    respawnDimensionValue.trim(), parse(respawnXValue, -30_000_000, 30_000_000, "respawn X"),
                    parse(respawnYValue, -4096, 4096, "respawn Y"), parse(respawnZValue, -30_000_000, 30_000_000, "respawn Z"),
                    (float) parse(respawnYawValue, -360, 360, "respawn yaw"), initial.respawnPitch(), nextRequestId++));
            pendingDelete = deleteRequested;
            notice = deleteRequested ? "Deleting…" : "Saving…"; noticeError = false;
        } catch (IllegalArgumentException exception) { notice = exception.getMessage(); noticeError = true; }
    }

    private void delete() { if (!deleteArmed) { deleteArmed = true; rebuildWidgets(); } else submit(true); }
    private void openLoadout(int mode) {
        if (!initial.editing()) { notice = "Create the NPC first, then reopen it to edit inventory slots."; noticeError = true; return; }
        savePage(); ClientPacketDistributor.sendToServer(new NpcLoadoutOpenRequestPayload(initial.originalInstanceId(), mode));
    }

    public void acceptResult(NpcEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        if (!payload.successful()) {
            pendingDelete = false;
            notice = payload.message(); noticeError = true;
            return;
        }
        if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(payload.message()));
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

    private void cycleTextureSource() {
        savePage();
        NpcTextureSource next = NpcTextureSource.parse(textureSourceValue).next();
        textureSourceValue = next.id();
        if (next == NpcTextureSource.NONE) textureValueValue = "";
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
        ClientPacketDistributor.sendToServer(new NpcShopAdminActionPayload(
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
        ClientPacketDistributor.sendToServer(new NpcShopAdminActionPayload(
                "open", shopIdValue, "", "", 0, nextRequestId++));
    }

    private void createLinkedShop() {
        savePage();
        String proposed = automaticNpcShopId();
        shopIdValue = proposed;
        ClientPacketDistributor.sendToServer(new NpcShopAdminActionPayload(
                "new", "", proposed, "", 0, nextRequestId++));
        notice = "New shared shop linked: " + proposed; noticeError = false;
    }

    private void cycleRole(int delta) {
        savePage();
        roleId = NpcRole.parse(roleId).offset(delta).id();
        rebuildWidgets();
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

    private void saveRelation() {
        if (relations.isEmpty()) return;
        NpcFactionRelation relation = relations.get(relationIndex); relation.factionId = relationFactionValue; relation.normalize(); relationFactionValue = relation.factionId;
    }
    private void loadRelation() { relationIndex = Math.max(0, Math.min(relationIndex, Math.max(0, relations.size() - 1))); relationFactionValue = relations.isEmpty() ? "" : relations.get(relationIndex).factionId; }
    private void addRelation() { savePage(); if (relations.size() < 16) { NpcFactionRelation r = new NpcFactionRelation(); relations.add(r); relationIndex = relations.size() - 1; loadRelation(); } rebuildWidgets(); }
    private void deleteRelation() { savePage(); if (!relations.isEmpty()) relations.remove(relationIndex); loadRelation(); rebuildWidgets(); }
    private void relationMove(int delta) { savePage(); relationIndex = Math.max(0, Math.min(relations.size() - 1, relationIndex + delta)); loadRelation(); rebuildWidgets(); }
    private void cycleRelation() { savePage(); if (relations.isEmpty()) { NpcFactionRelation r = new NpcFactionRelation(); relations.add(r); relationIndex = 0; } NpcFactionRelation r = relations.get(relationIndex); r.attitude = NpcAttitude.parse(r.attitude).next().id(); loadRelation(); rebuildWidgets(); }

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
    private void addSchedule() { try { savePage(); } catch (RuntimeException ignored) {} if (schedule.size() < 16) { NpcScheduleEntry e = new NpcScheduleEntry(); e.x = parseSafe(xValue, 0); e.y = parseSafe(yValue, 64); e.z = parseSafe(zValue, 0); schedule.add(e); scheduleIndex = schedule.size() - 1; loadSchedule(); } rebuildWidgets(); }
    private void deleteSchedule() { try { savePage(); } catch (RuntimeException ignored) {} if (!schedule.isEmpty()) schedule.remove(scheduleIndex); loadSchedule(); rebuildWidgets(); }
    private void scheduleMove(int delta) { savePage(); scheduleIndex = Math.max(0, Math.min(schedule.size() - 1, scheduleIndex + delta)); loadSchedule(); rebuildWidgets(); }
    private void cycleScheduleMovement() { savePage(); scheduleMovement = NpcScheduleEntry.MOVEMENT_TELEPORT.equals(scheduleMovement) ? NpcScheduleEntry.MOVEMENT_WALK : NpcScheduleEntry.MOVEMENT_TELEPORT; rebuildWidgets(); }
    private void cycleScheduleActivity() { savePage(); scheduleActivity = switch (scheduleActivity) { case NpcScheduleEntry.ACTIVITY_IDLE -> NpcScheduleEntry.ACTIVITY_LOOK_AROUND; case NpcScheduleEntry.ACTIVITY_LOOK_AROUND -> NpcScheduleEntry.ACTIVITY_CHOP_TREE; default -> NpcScheduleEntry.ACTIVITY_IDLE; }; rebuildWidgets(); }
    private void useCurrentSchedule() { if (minecraft == null || minecraft.player == null) return; if (schedule.isEmpty()) addSchedule(); scheduleXValue = number(minecraft.player.getX()); scheduleYValue = number(minecraft.player.getY()); scheduleZValue = number(minecraft.player.getZ()); scheduleYawValue = number(minecraft.player.getYRot()); rebuildWidgets(); }
    private List<NpcScheduleEntry> copiedSchedule() { List<NpcScheduleEntry> result = new ArrayList<>(); for (NpcScheduleEntry entry : schedule) result.add(entry.copy().normalize()); return result; }

    private void usePlacementRespawn() { savePage(); respawnDimensionValue = initial.dimension(); respawnXValue = xValue; respawnYValue = yValue; respawnZValue = zValue; respawnYawValue = yawValue; rebuildWidgets(); }
    private void useCurrentRespawn() { if (minecraft == null || minecraft.player == null) return; savePage(); respawnDimensionValue = minecraft.player.level().dimension().identifier().toString(); respawnXValue = number(minecraft.player.getX()); respawnYValue = number(minecraft.player.getY()); respawnZValue = number(minecraft.player.getZ()); respawnYawValue = number(minecraft.player.getYRot()); rebuildWidgets(); }

    @Override public void onClose() { closeToParent(initial.editing()); }

    private void closeToParent(boolean refreshManager) {
        if (minecraft == null) return;
        minecraft.setScreenAndShow(parent);
        if (refreshManager && parent instanceof NpcAdminScreen manager) manager.refresh();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py(); g.fill(0, 0, width, height, 0xA9000000); g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, initial.editing() ? "Edit NPC" : "Create NPC", x + 12, y + 12, TEXT, true);
        renderLabels(g, x, y);
        if (!notice.isBlank()) g.text(font, trim(notice, 58), x + 194, y + H - 20, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderLabels(GuiGraphicsExtractor g, int x, int y) {
        switch (page) {
            case IDENTITY -> { label(g,"Template ID",x+12,y+77);label(g,"Display name",x+250,y+77);label(g,"Role / occupation",x+12,y+121);label(g,"X",x+12,y+179);label(g,"Y",x+115,y+179);label(g,"Z",x+218,y+179);label(g,"Yaw",x+321,y+179); }
            case APPEARANCE -> {
                label(g,"Entity model",x+12,y+77); label(g,"Texture source",x+258,y+77);
                NpcTextureSource source = NpcTextureSource.parse(textureSourceValue);
                if (source.custom()) {
                    label(g, source == NpcTextureSource.LOCAL ? "Relative PNG path" : "HTTPS PNG URL", x+12,y+131);
                    g.text(font,"Custom skins use the player-style mannequin renderer. PNG must be 64x64 and ≤ 512 KiB.",x+12,y+204,MUTED,false);
                    if (source == NpcTextureSource.LOCAL) g.text(font,"Server folder: simpleserverutilities/npcs/textures",x+12,y+216,MUTED,false);
                    else g.text(font,"HTTPS only; failures safely fall back to the vanilla mannequin skin.",x+12,y+216,MUTED,false);
                } else g.text(font,"Use Local or URL for a custom player-style skin, or leave Vanilla for normal entity rendering.",x+12,y+134,MUTED,false);
            }
            case INTERACTION -> {
                label(g,"Interaction mode",x+12,y+77); label(g,"Dialogue ID",x+240,y+77);
                label(g, NpcRole.parse(roleId) == NpcRole.MERCHANT ? "NPC shop" : "Optional linked shop", x+12,y+115);
                label(g,"Advanced actions " + (functions.isEmpty()?"0/0":(functionIndex+1)+"/"+functions.size()),x+12,y+155);
                if(!functions.isEmpty()){label(g,"Action ID",x+12,y+199);label(g,"Button label",x+165,y+199);label(g,"Service",x+328,y+199);label(g,"Target (warp, quest, game, dungeon…)",x+12,y+247);}
            }
            case BEHAVIOR -> { label(g,"Home radius (0 = none)",x+12,y+167); g.text(font,"Gravity is applied even to static/no-AI NPCs.",x+12,y+222,MUTED,false); }
            case RELATIONS -> { label(g,"Faction ID",x+12,y+77); label(g,"Faction name",x+165,y+77); label(g,"Attitude to players",x+328,y+77); label(g,"Minimum reputation",x+12,y+121); label(g,"Loss when attacked",x+165,y+121); label(g,"Denied message",x+12,y+165); label(g,"Faction relation " + (relations.isEmpty()?"0/0":(relationIndex+1)+"/"+relations.size()) + " — choose from known factions",x+12,y+221); }
            case STATS -> { label(g,"Max health (blank=native)",x+12,y+77); label(g,"Movement speed",x+165,y+77); label(g,"Attack damage",x+328,y+77); label(g,"Armor",x+12,y+131); label(g,"Armor toughness",x+165,y+131); label(g,"Follow range",x+328,y+131); label(g,"Knockback resistance",x+12,y+185); label(g,"Scale",x+165,y+185); }
            case LOADOUT -> { g.text(font,"Equipment is visual only and never affects stats or drops.",x+40,y+88,MUTED,false); g.text(font,"The nine loot slots are the NPC's only loot table.",x+40,y+154,MUTED,false); if(!initial.editing())g.text(font,"Create this NPC first to open its real inventory editor.",x+40,y+190,ERROR,false); }
            case SCHEDULE -> { label(g,"Time",x+12,y+125); label(g,"X",x+102,y+125); label(g,"Y",x+205,y+125); label(g,"Z",x+308,y+125); label(g,"Yaw",x+411,y+125); label(g,"Speed",x+12,y+179); g.text(font,"Schedules belong to this placement; linked copies may use different routes.",x+12,y+236,MUTED,false); }
            case RESPAWN -> { label(g,"Delay seconds",x+165,y+77); label(g,"Dimension",x+328,y+77); label(g,"X",x+12,y+137); label(g,"Y",x+115,y+137); label(g,"Z",x+218,y+137); label(g,"Yaw",x+321,y+137); }
        }
    }
    private void label(GuiGraphicsExtractor g, String text, int x, int y) { g.text(font, text, x, y, MUTED, false); }

    private static String choiceLabel(List<NpcEditorOpenPayload.Choice> choices, String id, String fallback) {
        String safe = id == null ? "" : id;
        if (safe.isBlank()) return fallback;
        if (choices != null) for (NpcEditorOpenPayload.Choice choice : choices) if (safe.equals(choice.id())) return choice.label();
        return safe + " (missing)";
    }

    private void clearRefs() { definitionId=displayName=textureValueField=dialogueId=xField=yField=zField=yawField=null; functionId=functionLabel=functionTarget=null; factionId=factionDisplayName=minimumReputation=reputationDeniedText=reputationLoss=null; maxHealth=movementSpeed=attackDamage=armor=armorToughness=followRange=knockback=scale=homeRadius=null; scheduleTime=scheduleX=scheduleY=scheduleZ=scheduleYaw=scheduleSpeed=null; respawnDelay=respawnDimension=respawnX=respawnY=respawnZ=respawnYaw=null; }
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
